package server;

import MIO.*;
import com.zeroc.Ice.Current;
import com.mio.model.ArcState;
import com.mio.model.BusHistory;
import java.util.*;
import java.util.concurrent.*;
import java.io.*;
import java.text.SimpleDateFormat;

public class MasterI implements Master {
    private int arcDebugCounter = 0;

    private final ExecutorService threadPool;
    private final List<WorkerPrx> registeredWorkers;
    private final Map<Integer, WorkerPrx> workerMap;
    private int workerIdCounter = 0;
    private final int numThreads;

    private Map<Integer, double[]> stopsMap = new HashMap<>();

    private Map<String, List<Integer>> stopGrid = new HashMap<>();

    private Map<Integer, ArcState> arcStates = new ConcurrentHashMap<>();

    private static final double STOP_PROXIMITY_THRESHOLD = 0.05;

    private static final double EARTH_RADIUS_KM = 6371.0;

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

    public void setStops(StopInfo[] stops) {
        stopsMap.clear();
        stopGrid.clear();

        for (StopInfo stop : stops) {
            stopsMap.put(stop.stopId, new double[] { stop.latitude, stop.longitude });

            String gridCell = getGridCell(stop.latitude, stop.longitude);
            stopGrid.computeIfAbsent(gridCell, k -> new ArrayList<>()).add(stop.stopId);
        }

        System.out.println("[Master] Paradas del grafo registradas: " + stopsMap.size());
        System.out.println("[Master] Indice espacial construido: " + stopGrid.size() + " celdas");

        if (stops.length > 0) {
            System.out.println("[DEBUG] Ejemplo parada 0: ID=" + stops[0].stopId + " Lat=" + stops[0].latitude + " Lon="
                    + stops[0].longitude);
            System.out.println("[DEBUG] Cell para parada 0: " + getGridCell(stops[0].latitude, stops[0].longitude));
        }
    }

    private String getGridCell(double lat, double lon) {
        int latCell = (int) (lat * 200);
        int lonCell = (int) (lon * 200);
        return latCell + "," + lonCell;
    }

    private Integer findNearestStop(double lat, double lon) {
        String centerCell = getGridCell(lat, lon);
        double minDistance = Double.MAX_VALUE;
        Integer nearestStopId = null;

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

    private void updateArcState(int fromStopId, int toStopId,
            double fromLat, double fromLon,
            double toLat, double toLon,
            long t0, long t1) {
        int arcId = fromStopId * 10000 + toStopId;

        double distance = haversineDistance(fromLat, fromLon, toLat, toLon);
        double timeHours = (t1 - t0) / (1000.0 * 3600.0);

        if (arcDebugCounter < 20) {
            double speed = (timeHours > 0.0001) ? distance / timeHours : 0.0;
            System.out.println("[ARC DEBUG] fromStop=" + fromStopId + " toStop=" + toStopId +
                    " dist=" + String.format("%.3f", distance) + "km time=" + String.format("%.3f", timeHours)
                    + "h speed=" + String.format("%.2f", speed) + "km/h");
            arcDebugCounter++;
        }

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

    @Override
    public GlobalResult processDatagrams(SpeedDatagram[] datagrams, int numTasks, Current current) {
        long startTime = System.currentTimeMillis();

        System.out.println("\n[Master] ========================================");
        System.out.println("[Master] Iniciando procesamiento distribuido");
        System.out.println("[Master] Total datagramas: " + datagrams.length);
        System.out.println("[Master] Numero de tareas: " + numTasks);
        System.out.println("[Master] Threads disponibles: " + numThreads);
        System.out.println("[Master] Workers remotos registrados: " + registeredWorkers.size());
        System.out.println("[Master] ========================================\n");

        long separationStart = System.currentTimeMillis();
        List<Task> tasks = separateDependencies(datagrams, numTasks);
        long separationTime = System.currentTimeMillis() - separationStart;

        long distributionStart = System.currentTimeMillis();
        List<Future<PartialResult>> futures = launchWorkers(tasks);
        long distributionTime = System.currentTimeMillis() - distributionStart;

        long consolidationStart = System.currentTimeMillis();
        GlobalResult globalResult = processResults(futures, startTime);
        long consolidationTime = System.currentTimeMillis() - consolidationStart;
        
        globalResult.separationTimeMs = separationTime;
        globalResult.distributionTimeMs = distributionTime;
        globalResult.consolidationTimeMs = consolidationTime;
        globalResult.activeWorkers = registeredWorkers.size();

        System.out.println("\n[Master] ========================================");
        System.out.println("[Master] Procesamiento completado");
        System.out.println("[Master] Velocidad promedio global: " +
                String.format("%.2f", globalResult.globalAvgSpeed) + " km/h");
        System.out.println("[Master] Tiempo total: " + globalResult.totalProcessingTimeMs + " ms");
        System.out.println("[Master]   - Separación: " + globalResult.separationTimeMs + " ms");
        System.out.println("[Master]   - Distribución: " + globalResult.distributionTimeMs + " ms");
        System.out.println("[Master]   - Consolidación: " + globalResult.consolidationTimeMs + " ms");
        System.out.println("[Master] Workers activos: " + globalResult.activeWorkers);
        System.out.println("[Master] ========================================\n");

        return globalResult;
    }

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

    private List<Future<PartialResult>> launchWorkers(List<Task> tasks) {
        System.out.println("[Master] Lanzando workers...");

        List<Future<PartialResult>> futures = new ArrayList<>();

        if (registeredWorkers.isEmpty()) {
            System.out.println("[Master] [WARNING] No hay workers remotos registrados. Usando ThreadPool LOCAL.");
            for (Task task : tasks) {
                Future<PartialResult> future = threadPool.submit(() -> {
                    WorkerI worker = new WorkerI(task.taskId);
                    return worker.processTask(task, null);
                });
                futures.add(future);
            }
        } else {
            System.out.println("[Master] [OK] Distribuyendo tareas a " + registeredWorkers.size() + " workers REMOTOS");

            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                final int workerIndex = i % registeredWorkers.size();

                WorkerPrx remoteWorker = registeredWorkers.get(workerIndex);

                Future<PartialResult> future = threadPool.submit(() -> {
                    try {
                        System.out.println("[Master] Enviando Task " + task.taskId + " a Worker remoto " + workerIndex);
                        return remoteWorker.processTask(task);
                    } catch (Exception e) {
                        System.err.println("[Master] Error procesando Task " + task.taskId + " en worker remoto: " +
                                e.getMessage());
                        WorkerI localWorker = new WorkerI(task.taskId);
                        return localWorker.processTask(task, null);
                    }
                });
                futures.add(future);
            }
        }

        return futures;
    }

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

        for (Future<PartialResult> future : futures) {
            try {
                PartialResult partial = future.get();
                globalResult.totalDatagrams += partial.datagramCount;

                filteredSpeedSum += partial.filteredSpeedSum;
                filteredCount += partial.filteredCount;

                if (partial instanceof WorkerI.PartialResultWithDatagrams) {
                    SpeedDatagram[] datagrams = ((WorkerI.PartialResultWithDatagrams) partial).datagrams;
                    for (SpeedDatagram dg : datagrams) {
                        if (dg != null && dg.arcId > 0) {
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

        System.out.println("[Master] Arcos procesados: " + filteredCount);
        System.out.println("[Master] Velocidad promedio calculada: "
                + String.format("%.2f", globalResult.globalAvgSpeed) + " km/h");
        return globalResult;
    }

    @Override
    public SpeedDatagram[] loadDatagramsFromCSV(String filePath, int maxCount, Current current) {
        System.out.println("[Master] Cargando datagramas desde: " + filePath);
        List<SpeedDatagram> datagrams = new ArrayList<>();

        Map<Integer, BusHistory> busHistories = new HashMap<>();

        String filterDay = "31-MAY-18";
        int detectedStops = 0;
        int undetectedStops = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineCount = 0;
            br.readLine(); // Skip header

            int maxLines = 100_000_000;
            int limit = (maxCount > 0 && maxCount < maxLines) ? maxCount : maxLines;

            while ((line = br.readLine()) != null && lineCount < limit) {
                lineCount++;
                if (lineCount % 100000 == 0) {
                    System.out.println("[Master] Procesadas " + lineCount + " lineas. Datagramas validos (arcos): "
                            + datagrams.size());
                }

                String[] parts = line.split(",");
                if (parts.length < 12)
                    continue;

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

    private SpeedDatagram parseLine(byte[] buffer, int len, Map<Integer, BusHistory> busHistories) {
        try {
            String line = new String(buffer, 0, len).trim();
            if (line.isEmpty())
                return null;

            String[] parts = line.split(",");
            if (parts.length < 12)
                return null;

            double lat = Double.parseDouble(parts[4]) / 1e7;
            double lon = Double.parseDouble(parts[5]) / 1e7;
            int busId = Integer.parseInt(parts[11]);
            long timestamp = parseDateTimeToTimestamp(parts[10]);

            if (debugCounter < 10) {
                System.out.println("[VALIDACION CSV] lat=" + lat + " lon=" + lon + " busId=" + busId + " datagramDate="
                        + parts[10]);
                debugCounter++;
            }

            if (Math.abs(lat) > 90 || Math.abs(lon) > 180)
                return null;

            Integer currentStopId = findNearestStop(lat, lon);

            if (currentStopId != null && debugCounter < 50) {
                System.out.println("[DEBUG] Bus " + busId + " detectado en parada " + currentStopId + " (Lat: " + lat
                        + ", Lon: " + lon + ")");
                debugCounter++;
            }

            BusHistory history = busHistories.computeIfAbsent(busId, k -> new BusHistory(null, 0, 0, 0));

            SpeedDatagram result = null;

            if (currentStopId != null) {
                if (history.lastStopId != null && !history.lastStopId.equals(currentStopId)) {
                    updateArcState(history.lastStopId, currentStopId,
                                history.lastLat, history.lastLon,
                            lat, lon,
                            history.lastTimestamp, timestamp);

                    result = new SpeedDatagram();
                        result.fromStopId = history.lastStopId;
                        result.toStopId = currentStopId;
                        result.timestamp = timestamp;
                        result.fromLat = history.lastLat;
                        result.fromLon = history.lastLon;
                        result.toLat = lat;
                        result.toLon = lon;
                        result.arcId = result.fromStopId * 10000 + result.toStopId;
                    return result;
                }

                history.lastStopId = currentStopId;
            }

            history.lastLat = lat;
            history.lastLon = lon;
            history.lastTimestamp = timestamp;

            return result;

        } catch (Exception e) {
            return null;
        }
    }

    private long parseDateTimeToTimestamp(String dateTimeStr) {
        try {
            if (dateTimeStr.contains("-")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                return sdf.parse(dateTimeStr).getTime();
            } else {
                return Long.parseLong(dateTimeStr);
            }
        } catch (Exception e) {
            System.err.println("[ERROR PARSE FECHA] No se pudo parsear: '" + dateTimeStr
                    + "'. Usando System.currentTimeMillis(). Error: " + e.getMessage());
            return System.currentTimeMillis();
        }
    }

    @Override
    public String runBenchmark(ArcInfo[] arcs, Current current) {
        String csvPath = "/home/swarch/proyecto-mio/MIO/datagrams4history.csv";
        double proximityThreshold = STOP_PROXIMITY_THRESHOLD;

        Map<Integer, List<BusEvent>> busEvents = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            br.readLine();
            int lineCount = 0;
            while ((line = br.readLine()) != null) {
                lineCount++;
                String[] parts = line.split(",");
                if (parts.length < 12)
                    continue;
                double lat, lon;
                int busId;
                long timestamp;
                try {
                    lat = Double.parseDouble(parts[4]) / 1e7;
                    lon = Double.parseDouble(parts[5]) / 1e7;
                    busId = Integer.parseInt(parts[11]);
                    timestamp = parseDateTimeToTimestamp(parts[10]);
                } catch (Exception e) {
                    continue;
                }

                Integer stopId = findNearestStop(lat, lon);
                if (stopId != null) {
                    BusEvent event = new BusEvent(busId, stopId, lat, lon, timestamp);
                    busEvents.computeIfAbsent(busId, k -> new ArrayList<>()).add(event);
                }
            }
        } catch (Exception e) {
            return "Error leyendo CSV: " + e.getMessage();
        }

        StringBuilder report = new StringBuilder();
        report.append("Benchmark de velocidad por arco (detección por GPS):\n");
        for (ArcInfo arc : arcs) {
            int fromStop = arc.fromStopId;
            int toStop = arc.toStopId;
            List<Double> speeds = new ArrayList<>();
            int trayectos = 0;
            for (Map.Entry<Integer, List<BusEvent>> entry : busEvents.entrySet()) {
                List<BusEvent> events = entry.getValue();
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
                        if (trayectos <= 5) {
                            System.out.println("[BENCHMARK DEBUG] arco=" + fromStop + "->" + toStop + " bus="
                                    + entry.getKey() + " dist=" + String.format("%.3f", distance) + "km time="
                                    + String.format("%.3f", timeHours) + "h speed=" + String.format("%.2f", speed)
                                    + "km/h");
                        }
                    }
                }
            }
            double avgSpeed = speeds.isEmpty() ? 0 : speeds.stream().mapToDouble(d -> d).average().orElse(0);
            report.append("Arco " + fromStop + "->" + toStop + ": " + String.format("%.2f", avgSpeed) + " km/h ("
                    + trayectos + " trayectos)\n");
            System.out.println("[BENCHMARK ARCO] " + fromStop + "->" + toStop + " promedio="
                    + String.format("%.2f", avgSpeed) + "km/h trayectos=" + trayectos);
        }
        return report.toString();
    }

    private static class BusEvent {
        int busId, stopId;
        double lat, lon;
        long timestamp;

        BusEvent(int busId, int stopId, double lat, double lon, long timestamp) {
            this.busId = busId;
            this.stopId = stopId;
            this.lat = lat;
            this.lon = lon;
            this.timestamp = timestamp;
        }
    }

    @Override
    public String runBenchmarkWithRealData(String csvPath, Current current) {
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
