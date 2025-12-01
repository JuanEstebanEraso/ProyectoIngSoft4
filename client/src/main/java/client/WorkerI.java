package client;

import MIO.*;
import com.zeroc.Ice.Current;
import java.util.*;

public class WorkerI implements Worker {
    public static class PartialResultWithDatagrams extends MIO.PartialResult {
        public SpeedDatagram[] datagrams;
    }

    private final int workerId;
    private boolean available = true;

    public WorkerI(int workerId) {
        this.workerId = workerId;
    }

    private static final double EARTH_RADIUS_KM = 6371.0;
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    @Override
    public PartialResult processTask(Task task, Current current) {
        long startTime = System.currentTimeMillis();
        available = false;

        SpeedDatagram[] datagrams = task.datagrams;
        Map<Integer, List<SpeedDatagram>> datagramsByArc = new HashMap<>();
        for (SpeedDatagram dg : datagrams) {
            datagramsByArc.computeIfAbsent(dg.arcId, k -> new ArrayList<>()).add(dg);
        }

        double totalWeightedSpeed = 0;
        double totalDistance = 0;
        double totalTime = 0;
        int arcCount = 0;
        double filteredSpeedSum = 0;
        int filteredCount = 0;

        System.out.println("[Worker " + workerId + "] ---- Detalle de cálculo por arco ----");
        for (Map.Entry<Integer, List<SpeedDatagram>> entry : datagramsByArc.entrySet()) {
            List<SpeedDatagram> arcDatagrams = entry.getValue();
            if (arcDatagrams.isEmpty()) continue;

            SpeedDatagram first = arcDatagrams.get(0);
            double distanceKm = haversineDistance(first.fromLat, first.fromLon, first.toLat, first.toLon);
            if (distanceKm < 0.001) {
                System.out.println("[Worker " + workerId + "] ArcId=" + first.arcId + " descartado por distancia < 1m");
                continue;
            }

            arcDatagrams.sort(Comparator.comparingLong(dg -> dg.timestamp));
            SpeedDatagram firstDg = arcDatagrams.get(0);
            SpeedDatagram lastDg = arcDatagrams.get(arcDatagrams.size() - 1);
            double timeHours = (lastDg.timestamp - firstDg.timestamp) / (1000.0 * 3600.0);

            if (timeHours > 0.0001) {
                double arcSpeed = distanceKm / timeHours;
                double arcSpeedLimited = Math.min(Math.max(arcSpeed, 0), 120);

                totalWeightedSpeed += arcSpeedLimited * arcDatagrams.size();
                totalDistance += distanceKm * arcDatagrams.size();
                totalTime += timeHours * arcDatagrams.size();
                filteredSpeedSum += arcSpeedLimited;
                filteredCount++;

                System.out.println(String.format("[Worker %d] ArcId=%d Dist=%.3fkm Time=%.3fh Speed=%.2fkm/h (limitado=%.2fkm/h) Datagramas=%d", workerId, first.arcId, distanceKm, timeHours, arcSpeed, arcSpeedLimited, arcDatagrams.size()));
            } else {
                System.out.println(String.format("[Worker %d] ArcId=%d descartado por tiempo insuficiente (%.6fh)", workerId, first.arcId, timeHours));
            }
            arcCount++;
        }
        System.out.println("[Worker " + workerId + "] ---- Fin detalle por arco ----");

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

        System.out.println("[Worker " + workerId + "] Task " + task.taskId + " completada. " +
            "Datagramas: " + datagrams.length + ", Arcos: " + arcCount +
            ", Velocidad promedio: " + String.format("%.2f", result.avgSpeed) + " km/h");
        System.out.println(String.format("[Worker %d] Suma ponderada=%.2f, Distancia total=%.2f, Tiempo total=%.2f, Velocidad filtrada=%.2f, Count filtrado=%d", workerId, totalWeightedSpeed, totalDistance, totalTime, filteredSpeedSum, filteredCount));
        return result;
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
