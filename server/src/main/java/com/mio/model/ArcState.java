package com.mio.model;

public class ArcState {
    private final int fromStopId;
    private final int toStopId;
    private double averageSpeed;
    private int traversalCount;
    private double totalDistance;
    private double totalTime;

    public ArcState(int fromStopId, int toStopId) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.averageSpeed = 0.0;
        this.traversalCount = 0;
        this.totalDistance = 0.0;
        this.totalTime = 0.0;
    }

    public synchronized void updateSpeed(double distance, double timeHours) {
        if (timeHours <= 0.0001 || distance <= 0.001) {
            return;
        }

        double speed = distance / timeHours;
        speed = Math.min(Math.max(speed, 0), 120);

        totalDistance += distance;
        totalTime += timeHours;
        traversalCount++;

        averageSpeed = totalDistance / totalTime;
    }

    public int getFromStopId() {
        return fromStopId;
    }

    public int getToStopId() {
        return toStopId;
    }

    public double getAverageSpeed() {
        return averageSpeed;
    }

    public int getTraversalCount() {
        return traversalCount;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public double getTotalTime() {
        return totalTime;
    }

    @Override
    public String toString() {
        return String.format("Arc[%d→%d]: %.2f km/h (%d recorridos, %.2f km, %.4f h)",
                fromStopId, toStopId, averageSpeed, traversalCount, totalDistance, totalTime);
    }
}
