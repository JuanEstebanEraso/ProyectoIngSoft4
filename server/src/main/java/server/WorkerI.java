package server;

import MIO.*;
import com.zeroc.Ice.Current;
import java.util.*;

/**
 * Implementacion del Worker del patron ThreadPool + Separable Dependencies
 * 
 * El Worker:
 * 1. Recibe una Task con datos ya replicados (sin dependencias externas)
 * 2. Agrupa datagramas por arco (fromStopId -> toStopId)
 * 3. Calcula distancia usando formula Haversine
 * 4. Calcula velocidad = distancia / tiempo para cada arco
 * 5. Retorna resultado parcial al Master para consolidacion
 * 
 * SEPARABLE DEPENDENCIES:
 * - Cada Task contiene TODOS los datos necesarios (coordenadas replicadas)
 * - No hay dependencias externas durante el procesamiento
 * - Procesamiento completamente independiente
 */
public class WorkerI implements Worker {
        public static class PartialResultWithDatagrams extends MIO.PartialResult {
            public SpeedDatagram[] datagrams;
        }
    // ...existing code...

    private final int workerId;
    private boolean available = true;

    // Radio de la Tierra en km (para Haversine)
    private static final double EARTH_RADIUS_KM = 6371.0;

    public WorkerI(int workerId) {
        this.workerId = workerId;
    }

    /**
     * Procesa una tarea y calcula velocidad promedio por arco
     * 
     * ALGORITMO:
     * 1. Agrupar datagramas por arcId
     * 2. Para cada arco:
     * - Calcular distancia (Haversine) entre paradas
     * - Calcular tiempo entre primer y ultimo datagrama
     * - Velocidad = distancia / tiempo
     * 3. Promediar velocidades de todos los arcos
     * 
     * @param task Tarea con datagramas (incluyen coordenadas replicadas)
     * @return Resultado parcial con estadisticas del subset
     */
    @Override
    public PartialResult processTask(Task task, Current current) {
        long startTime = System.currentTimeMillis();
        available = false;

        SpeedDatagram[] datagrams = task.datagrams;

        // PASO 1: Agrupar datagramas por arco
        Map<Integer, List<SpeedDatagram>> datagramsByArc = new HashMap<>();

        for (SpeedDatagram dg : datagrams) {
            datagramsByArc.computeIfAbsent(dg.arcId, k -> new ArrayList<>()).add(dg);
        }

        // PASO 2: Calcular velocidad para cada arco
        double totalWeightedSpeed = 0;
        double totalDistance = 0;
        double totalTime = 0;
        int arcCount = 0;
        double filteredSpeedSum = 0;
        int filteredCount = 0;

        for (Map.Entry<Integer, List<SpeedDatagram>> entry : datagramsByArc.entrySet()) {
            List<SpeedDatagram> arcDatagrams = entry.getValue();

            if (arcDatagrams.isEmpty())
                continue;

            // Obtener coordenadas del primer datagrama (todas tienen las mismas coords para
            // el arco)
            SpeedDatagram first = arcDatagrams.get(0);

            // Calcular distancia Haversine entre paradas
            double distanceKm = haversineDistance(
                    first.fromLat, first.fromLon,
                    first.toLat, first.toLon);

            // Si la distancia es 0 o invalida, usar velocidad del CSV
            if (distanceKm < 0.001) {
                // Usar promedio de velocidades del CSV para este arco
                double avgCsvSpeed = arcDatagrams.stream()
                        .mapToDouble(dg -> dg.speed)
                        .average()
                        .orElse(0);
                totalWeightedSpeed += avgCsvSpeed * arcDatagrams.size();
            } else {
                // Ordenar por timestamp para calcular tiempo
                arcDatagrams.sort(Comparator.comparingLong(dg -> dg.timestamp));

                SpeedDatagram firstDg = arcDatagrams.get(0);
                SpeedDatagram lastDg = arcDatagrams.get(arcDatagrams.size() - 1);

                // Tiempo en horas
                double timeHours = (lastDg.timestamp - firstDg.timestamp) / (1000.0 * 3600.0);

                // Solo calcular velocidad si hay tiempo y distancia validos
                if (timeHours > 0.0001 && distanceKm > 0.001) { // Mas de 0.36 segundos y distancia > 1 metro
                    // Velocidad = Distancia / Tiempo
                    double arcSpeed = distanceKm / timeHours;

                    // Limitar velocidad a valores razonables (0-120 km/h para bus urbano)
                    arcSpeed = Math.min(Math.max(arcSpeed, 0), 120);

                    // Ponderar por numero de datagramas del arco
                    totalWeightedSpeed += arcSpeed * arcDatagrams.size();
                    totalDistance += distanceKm * arcDatagrams.size();
                    totalTime += timeHours * arcDatagrams.size();

                    // Filtrar velocidades mayores a 5 km/h por datagrama individual
                    filteredSpeedSum += arcSpeed;
                    filteredCount++;
                }
                // Si tiempo o distancia son invalidos, simplemente omitir este arco del calculo
            }

            arcCount++;
        }

        // PASO 3: Construir resultado parcial
        PartialResultWithDatagrams result = new PartialResultWithDatagrams();
        result.taskId = task.taskId;
        result.arcCount = arcCount;
        result.datagramCount = datagrams.length;
        result.sumSpeed = totalWeightedSpeed;
        result.totalDistance = totalDistance;
        result.totalTime = totalTime;
        result.avgSpeed = datagrams.length > 0 ? totalWeightedSpeed / datagrams.length : 0;
        result.processingTimeMs = System.currentTimeMillis() - startTime;
        result.filteredSpeedSum = filteredSpeedSum;
        result.filteredCount = filteredCount;
        result.datagrams = datagrams;

        available = true;

        return result;
    }

    /**
     * Calcula la distancia entre dos puntos usando la formula de Haversine
     * 
     * @param lat1 Latitud punto 1 (grados)
     * @param lon1 Longitud punto 1 (grados)
     * @param lat2 Latitud punto 2 (grados)
     * @param lon2 Longitud punto 2 (grados)
     * @return Distancia en kilometros
     */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convertir a radianes
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        // Formula de Haversine
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    @Override
    public boolean isAvailable(Current current) {
        return available;
    }

    @Override
    public int getWorkerId(Current current) {
        return workerId;
    }
}
