package com.mio.model;

/**
 * Mantiene el estado de un arco del grafo
 * Actualizado cada vez que un bus recorre el arco
 */
public class ArcState {
    private final int fromStopId;
    private final int toStopId;
    private double averageSpeed; // km/h
    private int traversalCount; // Cuántas veces se ha recorrido
    private double totalDistance; // km acumulados
    private double totalTime; // horas acumuladas

    public ArcState(int fromStopId, int toStopId) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.averageSpeed = 0.0;
        this.traversalCount = 0;
        this.totalDistance = 0.0;
        this.totalTime = 0.0;
    }

    /**
     * Actualiza el estado del arco con un nuevo recorrido
     * Thread-safe para procesamiento concurrente
     */
    public synchronized void updateSpeed(double distance, double timeHours) {
        if (timeHours <= 0.0001 || distance <= 0.001) {
            return; // Ignorar datos inválidos
        }

        double speed = distance / timeHours;
        // Limitar a valores razonables para bus urbano
        speed = Math.min(Math.max(speed, 0), 120);

        totalDistance += distance;
        totalTime += timeHours;
        traversalCount++;

        // Recalcular promedio
        averageSpeed = totalDistance / totalTime;
    }

    // Getters
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
