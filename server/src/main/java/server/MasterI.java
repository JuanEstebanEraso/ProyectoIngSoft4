package server;

import MIO.*;
import com.zeroc.Ice.Current;
import com.mio.model.ArcState;
import com.mio.model.BusHistory;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.text.SimpleDateFormat;

/**
 * Implementacion del patron Master con Separable Dependencies
 * 
 * El Master:
 * 1. Separa dependencias dividiendo datos en tareas independientes
 * 2. Replica datos necesarios para cada Task
 * 3. Distribuye Tasks a Workers del ThreadPool
 * 4. Consolida resultados parciales en resultado global
 */
public class MasterI implements Master {
        // Cache de datagramas cargados desde el CSV
        private SpeedDatagram[] cachedDatagrams = null;
        private final String DATAGRAMS_CSV_PATH = "/home/swarch/proyecto-mio/MIO/datagrams4history.csv";

        // Carga y cachea los datagramas si no están cargados
        private void ensureDatagramsLoaded() {
            if (cachedDatagrams == null) {
                System.out.println("[Master] Cargando datagramas en memoria desde: " + DATAGRAMS_CSV_PATH);
                cachedDatagrams = loadDatagramsFromCSV(DATAGRAMS_CSV_PATH, 0, null);
                System.out.println("[Master] Datagramas cargados en cache: " + (cachedDatagrams != null ? cachedDatagrams.length : 0));
            }
        }

        // El servidor envía los datagramas solicitados
        @Override
        public SpeedDatagram[] getDatagramsFromServer(int count, Current current) {
            ensureDatagramsLoaded();
            if (cachedDatagrams == null) return new SpeedDatagram[0];
            int toSend = Math.min(count, cachedDatagrams.length);
            return Arrays.copyOfRange(cachedDatagrams, 0, toSend);
        }

        // El servidor informa cuántos datagramas tiene disponibles
        @Override
        public int getTotalDatagramsCount(Current current) {
            ensureDatagramsLoaded();
            return cachedDatagrams != null ? cachedDatagrams.length : 0;
        }

        // Ejecuta el benchmark usando los datagramas distribuidos por el servidor
        @Override
        public String runBenchmarkWithRealDataFromServer(Current current) {
            ensureDatagramsLoaded();
            if (cachedDatagrams == null || cachedDatagrams.length == 0) return "No hay datagramas cargados en el servidor.";
            int numTasks = Runtime.getRuntime().availableProcessors() * 2;
            GlobalResult result = processDatagrams(cachedDatagrams, numTasks, current);
            StringBuilder sb = new StringBuilder();
            sb.append("Benchmark con datagramas distribuidos por el servidor\n");
            sb.append("Total datagramas: ").append(result.totalDatagrams).append("\n");
            sb.append("Arcos únicos: ").append(result.totalArcs).append("\n");
            sb.append("Velocidad promedio: ").append(String.format("%.2f", result.globalAvgSpeed)).append(" km/h\n");
            sb.append("Tiempo total: ").append(result.totalProcessingTimeMs).append(" ms\n");
            sb.append("Workers utilizados: ").append(result.workerCount).append("\n");
            sb.append("Tareas procesadas: ").append(result.taskCount).append("\n");
            double throughput = (double) result.totalDatagrams / result.totalProcessingTimeMs * 1000;
            sb.append("Throughput: ").append((long) throughput).append(" datagramas/segundo\n");
            return sb.toString();
        }
    // Contador exclusivo para debug de arcos
    private int arcDebugCounter = 0;

    // ThreadPool para gestionar Workers
    private final ExecutorService threadPool;
    private final List<WorkerPrx> registeredWorkers;
    private final Map<Integer, WorkerPrx> workerMap;
    private int workerIdCounter = 0;
    private final int numThreads;

    // Mapa de paradas del grafo con sus coordenadas
    // Key: stopId, Value: [lat, lon]
    private Map<Integer, double[]> stopsMap = new HashMap<>();

    // Indice espacial para busqueda eficiente de paradas cercanas
    // Key: "latCell,lonCell", Value: List<stopId>
    private Map<String, List<Integer>> stopGrid = new HashMap<>();

    // Estado de cada arco del grafo (actualizado en tiempo real)
    // Key: arcId (fromStopId * 10000 + toStopId), Value: ArcState
    private Map<Integer, ArcState> arcStates = new ConcurrentHashMap<>();

    // Umbral de distancia para considerar que un bus esta en una parada (en km)
    private static final double STOP_PROXIMITY_THRESHOLD = 0.05; // 50 metros

    // Radio de la Tierra en km (para Haversine)
    private static final double EARTH_RADIUS_KM = 6371.0;

    // Contador para debug
    private int debugCounter = 0;

    public MasterI(int numThreads) {
        this.numThreads = numThreads;
        this.threadPool = Executors.newFixedThreadPool(numThreads);
        this.registeredWorkers = new CopyOnWriteArrayList<>();
        this.workerMap = new ConcurrentHashMap<>();

        System.out.println("[Master] Inicializado con ThreadPool de " + numThreads + " threads");
    }

    public void shutdown() {
        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
    }

    /**
     * Registra las paradas del grafo con sus coordenadas
     * y construye el indice espacial para busqueda eficiente
     */
    public void setStops(StopInfo[] stops) {
        stopsMap.clear();
        stopGrid.clear();

            for (StopInfo stop : stops) { 
            stopsMap.put(stop.stopId, new double[] { stop.latitude, stop.longitude });

            // Agregar al indice espacial
            String gridCell = getGridCell(stop.latitude, stop.longitude);
            stopGrid.computeIfAbsent(gridCell, k -> new ArrayList<>()).add(stop.stopId);
        }

        System.out.println("[Master] Paradas del grafo registradas: " + stopsMap.size());
        System.out.println("[Master] Indice espacial construido: " + stopGrid.size() + " celdas");

        // DEBUG: Imprimir algunas paradas
        if (stops.length > 0) {
            System.out.println("[DEBUG] Ejemplo parada 0: ID=" + stops[0].stopId + " Lat=" + stops[0].latitude + " Lon="
                    + stops[0].longitude);
            System.out.println("[DEBUG] Cell para parada 0: " + getGridCell(stops[0].latitude, stops[0].longitude));
        }
    }

    /**
     * Calcula la celda de la cuadricula para un punto GPS
     * Divide el area en celdas de ~500m x 500m
     */
    private String getGridCell(double lat, double lon) {
        int latCell = (int) (lat * 200); // ~500m por celda a esta latitud
        int lonCell = (int) (lon * 200);
        return latCell + "," + lonCell;
    }

    /**
     * Determina si el bus esta en una parada basandose en proximidad GPS
     * Usa indice espacial para busqueda eficiente
     * 
     * @param lat Latitud del bus
     * @param lon Longitud del bus
     * @return stopId si esta cerca de una parada, null si no
     */
    private Integer findNearestStop(double lat, double lon) {
        String centerCell = getGridCell(lat, lon);
        double minDistance = Double.MAX_VALUE;
        Integer nearestStopId = null;

        // Buscar en la celda actual y las 8 adyacentes
        String[] parts = centerCell.split(",");
        int centerLat = Integer.parseInt(parts[0]);
        int centerLon = Integer.parseInt(parts[1]);

        for (int dLat = -1; dLat <= 1; dLat++) {
            for (int dLon = -1; dLon <= 1; dLon++) {
                String cell = (centerLat + dLat) + "," + (centerLon + dLon);
                List<Integer> stopsInCell = stopGrid.get(cell);

                if (stopsInCell != null) {
                    for (Integer stopId : stopsInCell) {
                        double[] stopCoords = stopsMap.get(stopId);
                        double distance = haversineDistance(lat, lon, stopCoords[0], stopCoords[1]);

                        if (distance < minDistance && distance <= STOP_PROXIMITY_THRESHOLD) {
                            minDistance = distance;
                            nearestStopId = stopId;
                        }
                    }
                }
            }
        }

        return nearestStopId;
    }

    /**
     * Calcula la distancia entre dos puntos usando la formula de Haversine
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Actualiza el estado de un arco con un nuevo recorrido
     * Implementa la logica del caso de uso AdmCentral
     */
    private void updateArcState(int fromStopId, int toStopId,
            double fromLat, double fromLon,
            double toLat, double toLon,
            long t0, long t1) {
        int arcId = fromStopId * 10000 + toStopId;

        // Calcular distancia y tiempo
        double distance = haversineDistance(fromLat, fromLon, toLat, toLon);
        double timeHours = (t1 - t0) / (1000.0 * 3600.0);

        // Log para depuración de los primeros 20 arcos
        if (arcDebugCounter < 20) {
            double speed = (timeHours > 0.0001) ? distance / timeHours : 0.0;
            System.out.println("[ARC DEBUG] fromStop=" + fromStopId + " toStop=" + toStopId +
                " dist=" + String.format("%.3f", distance) + "km time=" + String.format("%.3f", timeHours) + "h speed=" + String.format("%.2f", speed) + "km/h");
            arcDebugCounter++;
        }

        // Actualizar estado del arco
        if (timeHours > 0.0001 && distance > 0.001) {
            ArcState state = arcStates.computeIfAbsent(arcId,
                    k -> new ArcState(fromStopId, toStopId));
            state.updateSpeed(distance, timeHours);
        }
    }

    @Override
    public void registerWorker(WorkerPrx worker, Current current) {
        registeredWorkers.add(worker);
        int workerId = workerIdCounter++;
        workerMap.put(workerId, worker);
        System.out.println("[Master] Worker registrado. Total workers: " + registeredWorkers.size());
    }

    @Override
    public void unregisterWorker(int workerId, Current current) {
        WorkerPrx worker = workerMap.remove(workerId);
        if (worker != null) {
            registeredWorkers.remove(worker);
            System.out.println("[Master] Worker " + workerId + " desregistrado");
        }
    }

    @Override
    public int getWorkerCount(Current current) {
        return registeredWorkers.size();
    }

    /**
     * Procesa datagramas usando el patron Separable Dependencies
     * 
     * 1. separateDependencies(): Divide los datagramas en tareas independientes
     * 2. createTasks(): Crea Tasks con datos replicados
     * 3. launchWorkers(): Distribuye tareas al ThreadPool
     * 4. processResults(): Consolida resultados parciales
     */
    @Override
    public GlobalResult processDatagrams(SpeedDatagram[] datagrams, int numTasks, Current current) {
        long startTime = System.currentTimeMillis();

        System.out.println("\n[Master] ========================================");
        System.out.println("[Master] Iniciando procesamiento distribuido");
        System.out.println("[Master] Total datagramas: " + datagrams.length);
        System.out.println("[Master] Numero de tareas: " + numTasks);
        System.out.println("[Master] Threads disponibles: " + numThreads);
        System.out.println("[Master] ========================================\n");

        // 1. SEPARATE DEPENDENCIES - Dividir datagramas en chunks independientes
        List<Task> tasks = separateDependencies(datagrams, numTasks);

        // 2. LAUNCH WORKERS - Procesar tareas en el ThreadPool
        List<Future<PartialResult>> futures = launchWorkers(tasks);

        // 3. PROCESS RESULTS - Consolidar resultados parciales
        GlobalResult globalResult = processResults(futures, startTime);

        System.out.println("\n[Master] ========================================");
        System.out.println("[Master] Procesamiento completado");
        System.out.println("[Master] Velocidad promedio global: " +
                String.format("%.2f", globalResult.globalAvgSpeed) + " km/h");
        System.out.println("[Master] Tiempo total: " + globalResult.totalProcessingTimeMs + " ms");
        System.out.println("[Master] ========================================\n");

        return globalResult;
    }

    /**
     * SEPARATE DEPENDENCIES: Divide los datos en tareas independientes
     * Cada tarea recibe una copia (replicacion) de su subset de datos
     */
    private List<Task> separateDependencies(SpeedDatagram[] datagrams, int numTasks) {
        System.out.println("[Master] Separando dependencias...");

        List<Task> tasks = new ArrayList<>();
        int totalDatagrams = datagrams.length;
        int chunkSize = (int) Math.ceil((double) totalDatagrams / numTasks);

        for (int i = 0; i < numTasks; i++) {
            int startIdx = i * chunkSize;
            int endIdx = Math.min(startIdx + chunkSize, totalDatagrams);

            if (startIdx >= totalDatagrams)
                break;

            // Replicar datos para esta tarea (Separable Dependencies)
            SpeedDatagram[] taskDatagrams = Arrays.copyOfRange(datagrams, startIdx, endIdx);

            Task task = new Task();
            task.taskId = i;
            task.datagrams = taskDatagrams;
            tasks.add(task);

            System.out.println("[Master] Task " + i + " creada con " + taskDatagrams.length + " datagramas");
        }

        System.out.println("[Master] Total tareas creadas: " + tasks.size());
        return tasks;
    }

    /**
     * LAUNCH WORKERS: Distribuye tareas al ThreadPool
     * Cada Worker procesa su tarea de forma independiente
     */
    private List<Future<PartialResult>> launchWorkers(List<Task> tasks) {
        System.out.println("[Master] Lanzando workers...");

        List<Future<PartialResult>> futures = new ArrayList<>();

        for (Task task : tasks) {
            Future<PartialResult> future = threadPool.submit(() -> {
                // Crear un Worker local para procesar la tarea
                WorkerI worker = new WorkerI(task.taskId);
                return worker.processTask(task, null);
            });
            futures.add(future);
        }

        return futures;
    }

    /**
     * PROCESS RESULTS: Consolidar resultados parciales
     * Combina los resultados de cada Worker en un resultado global
     */
    private GlobalResult processResults(List<Future<PartialResult>> futures, long startTime) {
        System.out.println("[Master] Procesando resultados...");

        GlobalResult globalResult = new GlobalResult();
        globalResult.globalAvgSpeed = 0;
        globalResult.totalProcessingTimeMs = 0;
        globalResult.totalDatagrams = 0;
        globalResult.totalArcs = 0;
        globalResult.workerCount = numThreads;
        globalResult.taskCount = futures.size();

        double filteredSpeedSum = 0;
        int filteredCount = 0;
        Set<Integer> uniqueArcs = new HashSet<>();
        // Para recolectar arcId de todos los datagramas
        List<SpeedDatagram[]> allDatagrams = new ArrayList<>();

        for (Future<PartialResult> future : futures) {
            try {
                PartialResult partial = future.get();
                globalResult.totalDatagrams += partial.datagramCount;
                // Recolectar los datagramas de cada tarea para extraer arcId y filtrar por velocidad
                if (partial instanceof WorkerI.PartialResultWithDatagrams) {
                    SpeedDatagram[] datagrams = ((WorkerI.PartialResultWithDatagrams)partial).datagrams;
                    allDatagrams.add(datagrams);
                    for (SpeedDatagram dg : datagrams) {
                        if (dg != null) {
                            filteredSpeedSum += dg.speed;
                            filteredCount++;
                            uniqueArcs.add(dg.arcId);
                        }
                    }
                }
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        if (filteredCount > 0) {
            globalResult.globalAvgSpeed = filteredSpeedSum / filteredCount;
        } else {
            globalResult.globalAvgSpeed = 0;
        }

        globalResult.totalProcessingTimeMs = System.currentTimeMillis() - startTime;
        globalResult.totalArcs = uniqueArcs.size();

        System.out.println("[Master] Arcos filtrados por velocidad >= 5 km/h: " + filteredCount);
        return globalResult;
    }

    /**
     * Carga datagramas desde un archivo CSV
     * Implementa la logica de AdmCentral para deteccion de paradas y arcos
     */
    @Override
    public SpeedDatagram[] loadDatagramsFromCSV(String filePath, int maxCount, Current current) {
        System.out.println("[Master] Cargando datagramas desde: " + filePath);
        List<SpeedDatagram> datagrams = new ArrayList<>();

        // Mapa para rastrear el historial de cada bus
        // Key: busId, Value: BusHistory
        Map<Integer, BusHistory> busHistories = new HashMap<>();

        // Día a filtrar (formato yyyy-MM-dd)
        String filterDay = "31-MAY-18";
        int detectedStops = 0;
        int undetectedStops = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineCount = 0;
            br.readLine(); // Skip header

            int maxLines = 20_000_000;
            int limit = (maxCount > 0 && maxCount < maxLines) ? maxCount : maxLines;

            while ((line = br.readLine()) != null && lineCount < limit) {
                lineCount++;
                if (lineCount % 100000 == 0) {
                    System.out.println("[Master] Procesadas " + lineCount + " lineas. Datagramas validos (arcos): "
                            + datagrams.size());
                }

                String[] parts = line.split(",");
                if (parts.length < 12) continue;

                SpeedDatagram dg = parseLine(line.getBytes(), line.length(), busHistories);
                if (dg != null) {
                    datagrams.add(dg);
                    detectedStops++;
                } else {
                    undetectedStops++;
                }
            }
            System.out.println("[Master] Carga completada. Total datagramas validos (arcos): " + datagrams.size());
            System.out.println("[Master] Datagramas con parada detectada: " + detectedStops);
            System.out.println("[Master] Datagramas sin parada detectada: " + undetectedStops);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return datagrams.toArray(new SpeedDatagram[0]);
    }

    /**
     * Parsea una linea del CSV y convierte a SpeedDatagram
     * Implementa la logica de AdmCentral:
     * 1. Detecta paradas por GPS (no por stopId del CSV)
     * 2. Rastrea posicion de buses
     * 3. Actualiza arcos cuando un bus completa un tramo
     */
    private SpeedDatagram parseLine(byte[] buffer, int len, Map<Integer, BusHistory> busHistories) {
        try {
            String line = new String(buffer, 0, len).trim();
            if (line.isEmpty())
                return null;

            String[] parts = line.split(",");
            if (parts.length < 12)
                return null;

            // Extraer datos basicos
            // idx,fecha,stopId,odometer,lat,lon,lineId,variant,orientation,busId,timestamp,orden

            // Validar coordenadas (permitir negativos)
            double lat = Double.parseDouble(parts[4]) / 1e7;
            double lon = Double.parseDouble(parts[5]) / 1e7;
            int busId = Integer.parseInt(parts[11]);
            long timestamp = parseDateTimeToTimestamp(parts[10]); // datagramDate

            // Log para validar columnas extraídas
            if (debugCounter < 10) {
                System.out.println("[VALIDACION CSV] lat=" + lat + " lon=" + lon + " busId=" + busId + " datagramDate=" + parts[10]);
                debugCounter++;
            }

            // Validar rango razonable de coordenadas (aprox Colombia/Cali)
            // Cali aprox: 3.4N, 76.5W
            if (Math.abs(lat) > 90 || Math.abs(lon) > 180)
                return null;

            // 1. Detectar parada por GPS
            Integer currentStopId = findNearestStop(lat, lon);

            // DEBUG log para ver si detecta paradas
            if (currentStopId != null && debugCounter < 50) {
                System.out.println("[DEBUG] Bus " + busId + " detectado en parada " + currentStopId + " (Lat: " + lat
                        + ", Lon: " + lon + ")");
                debugCounter++;
            }

            // 2. Obtener historial del bus
            BusHistory history = busHistories.computeIfAbsent(busId, k -> new BusHistory(null, 0, 0, 0));

            // 3. Logica de deteccion de arcos (AdmCentral)
            SpeedDatagram result = null;

            if (currentStopId != null) {
                // El bus esta en una parada
                if (history.lastStopId != null && !history.lastStopId.equals(currentStopId)) {
                    // Completo un arco: lastStop -> currentStop

                    // Calcular distancia y tiempo para la velocidad
                    double distance = haversineDistance(history.lastLat, history.lastLon, lat, lon);
                    double timeSeconds = (timestamp - history.lastTimestamp) / 1000.0;
                    double timeHours = timeSeconds / 3600.0;
                    double speed = (timeHours > 0.0001) ? distance / timeHours : 0.0;

                    // Filtros de calidad de datos
                    boolean valid = true;
                    if (timeSeconds < 5) valid = false; // tiempo mínimo 5 segundos
                    if (distance < 0.01) valid = false; // distancia mínima 10 metros
                    if (speed > 120) valid = false; // velocidad máxima 120 km/h

                    if (!valid) {
                        System.out.println("[DESCARTADO] arco=" + history.lastStopId + "->" + currentStopId + " bus=" + busId + " dist=" + String.format("%.3f", distance) + "km time=" + String.format("%.1f", timeSeconds) + "s speed=" + String.format("%.2f", speed) + "km/h");
                    } else {
                        // Actualizar estado del arco inmediatamente
                        updateArcState(history.lastStopId, currentStopId,
                            history.lastLat, history.lastLon,
                            lat, lon,
                            history.lastTimestamp, timestamp);

                        // Crear datagrama para el worker
                        result = new SpeedDatagram();
                        result.fromStopId = history.lastStopId;
                        result.toStopId = currentStopId;
                        result.timestamp = timestamp;
                        result.fromLat = history.lastLat;
                        result.fromLon = history.lastLon;
                        result.toLat = lat;
                        result.toLon = lon;
                        result.speed = speed; // Asignar velocidad calculada
                        result.arcId = result.fromStopId * 10000 + result.toStopId; // Asignar identificador de arco
                    }
                }

                // Actualizar ultima parada conocida
                history.lastStopId = currentStopId;
            }

            // Siempre actualizar ultima posicion y tiempo conocida (tracking continuo)
            history.lastLat = lat;
            history.lastLon = lon;
            history.lastTimestamp = timestamp;

            return result;

        } catch (Exception e) {
            // Ignorar lineas mal formadas
            return null;
        }
    }

    private long parseDateTimeToTimestamp(String dateTimeStr) {
        try {
            // Formato esperado: 2024-11-29 10:30:00
            // Ajustar segun el formato real del CSV
            if (dateTimeStr.contains("-")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.parse(dateTimeStr).getTime();
            } else {
                // Asumir que ya es un timestamp numerico o intentar parsear otro formato
                return Long.parseLong(dateTimeStr);
            }
        } catch (Exception e) {
            System.err.println("[ERROR PARSE FECHA] No se pudo parsear: '" + dateTimeStr + "'. Usando System.currentTimeMillis(). Error: " + e.getMessage());
            return System.currentTimeMillis();
        }
    }

    @Override
    public String runBenchmark(ArcInfo[] arcs, Current current) {
        // Permitir configurar el archivo CSV y el umbral si se desea
        String csvPath = "/home/swarch/proyecto-mio/MIO/datagrams4history.csv"; // Usa el archivo real por defecto
        double proximityThreshold = STOP_PROXIMITY_THRESHOLD; // 50 metros por defecto

        // Mapa: busId -> lista de eventos detectados en paradas (por GPS)
        Map<Integer, List<BusEvent>> busEvents = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            br.readLine(); // Saltar header
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                lineCount++;
                String[] parts = line.split(",");
                if (parts.length < 12) continue;
                double lat, lon;
                int busId;
                long timestamp;
                try {
                    lat = Double.parseDouble(parts[4]) / 1e7;
                    lon = Double.parseDouble(parts[5]) / 1e7;
                    busId = Integer.parseInt(parts[11]);
                    timestamp = parseDateTimeToTimestamp(parts[10]);
                } catch (Exception e) { continue; }

                // Detectar parada por GPS (igual que en parseLine)
                Integer stopId = findNearestStop(lat, lon);
                if (stopId != null) {
                    BusEvent event = new BusEvent(busId, stopId, lat, lon, timestamp);
                    busEvents.computeIfAbsent(busId, k -> new ArrayList<>()).add(event);
                }
            }
        } catch (Exception e) {
            return "Error leyendo CSV: " + e.getMessage();
        }

        // Para cada arco del grafo, buscar trayectos en los datos
        StringBuilder report = new StringBuilder();
        report.append("Benchmark de velocidad por arco (detección por GPS):\n");
        for (ArcInfo arc : arcs) {
            int fromStop = arc.fromStopId;
            int toStop = arc.toStopId;
            List<Double> speeds = new ArrayList<>();
            int trayectos = 0;
            for (Map.Entry<Integer, List<BusEvent>> entry : busEvents.entrySet()) {
                List<BusEvent> events = entry.getValue();
                // Ordenar eventos por timestamp
                events.sort(Comparator.comparingLong(ev -> ev.timestamp));
                for (int i = 0; i < events.size() - 1; i++) {
                    BusEvent fromEv = events.get(i);
                    BusEvent toEv = events.get(i + 1);
                    if (fromEv.stopId == fromStop && toEv.stopId == toStop && toEv.timestamp > fromEv.timestamp) {
                        double distance = haversineDistance(fromEv.lat, fromEv.lon, toEv.lat, toEv.lon);
                        double timeHours = (toEv.timestamp - fromEv.timestamp) / (1000.0 * 3600.0);
                        double speed = (timeHours > 0.0001 && distance > 0.001) ? distance / timeHours : 0.0;
                        speeds.add(speed);
                        trayectos++;
                        // Log detallado por trayecto
                        if (trayectos <= 5) {
                            System.out.println("[BENCHMARK DEBUG] arco=" + fromStop + "->" + toStop + " bus=" + entry.getKey() + " dist=" + String.format("%.3f", distance) + "km time=" + String.format("%.3f", timeHours) + "h speed=" + String.format("%.2f", speed) + "km/h");
                        }
                    }
                }
            }
            double avgSpeed = speeds.isEmpty() ? 0 : speeds.stream().mapToDouble(d -> d).average().orElse(0);
            report.append("Arco " + fromStop + "->" + toStop + ": " + String.format("%.2f", avgSpeed) + " km/h (" + trayectos + " trayectos)\n");
            // Log resumen por arco
            System.out.println("[BENCHMARK ARCO] " + fromStop + "->" + toStop + " promedio=" + String.format("%.2f", avgSpeed) + "km/h trayectos=" + trayectos);
        }
        return report.toString();
    }

    // Clase auxiliar para eventos
    private static class BusEvent {
        int busId, stopId;
        double lat, lon;
        long timestamp;
        BusEvent(int busId, int stopId, double lat, double lon, long timestamp) {
            this.busId = busId; this.stopId = stopId; this.lat = lat; this.lon = lon; this.timestamp = timestamp;
        }
    }
    

    @Override
    public String runBenchmarkWithRealData(String csvPath, Current current) {
        // Implementacion dummy para cumplir con la interfaz
        return "Benchmark with real data not implemented in this version";
    }

    @Override
    public SpeedDatagram[] generateTestDatagrams(int count, ArcInfo[] arcs, Current current) {
        SpeedDatagram[] data = new SpeedDatagram[count];
        Random rand = new Random();

        List<Integer> stopIds = new ArrayList<>(stopsMap.keySet());
        if (stopIds.size() < 2)
            return new SpeedDatagram[0];

        for (int i = 0; i < count; i++) {
            data[i] = new SpeedDatagram();
            int idx1 = rand.nextInt(stopIds.size());
            int idx2 = rand.nextInt(stopIds.size());

            data[i].fromStopId = stopIds.get(idx1);
            data[i].toStopId = stopIds.get(idx2);
            data[i].timestamp = System.currentTimeMillis() + i * 1000;

            double[] coords1 = stopsMap.get(data[i].fromStopId);
            double[] coords2 = stopsMap.get(data[i].toStopId);

            data[i].fromLat = coords1[0];
            data[i].fromLon = coords1[1];
            data[i].toLat = coords2[0];
            data[i].toLon = coords2[1];
        }
        return data;
    }
}
