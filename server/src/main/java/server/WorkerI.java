package server;

import MIO.*;
import com.zeroc.Ice.Current;
import java.util.*;

public class WorkerI implements Worker {
        public static class PartialResultWithDatagrams extends MIO.PartialResult {
            public SpeedDatagram[] datagrams;
        }

    private final int workerId;
    private boolean available = true;

    private static final double EARTH_RADIUS_KM = 6371.0;

    public WorkerI(int workerId) {
        this.workerId = workerId;
    }

    @Override
    public MIO.PartialResult processTask(Task task, Current current) {
        long startTime = System.currentTimeMillis();
        available = false;
        MIO.PartialResult result = new MIO.PartialResult();
        result.taskId = task.taskId;
        result.arcCount = 0;
        result.datagramCount = 0;
        result.sumSpeed = 0;
        result.totalDistance = 0;
        result.totalTime = 0;
        result.avgSpeed = 0;
        result.processingTimeMs = System.currentTimeMillis() - startTime;
        result.filteredSpeedSum = 0;
        result.filteredCount = 0;
        available = true;
        System.out.println("[Worker " + workerId + "] Task " + task.taskId + " completada (fallback local, sin cálculo de velocidad).");
        return result;
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

    @Override
    public boolean isAvailable(Current current) {
        return available;
    }

    @Override
    public int getWorkerId(Current current) {
        return workerId;
    }
}
