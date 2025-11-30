package com.mio.model;

/**
 * Historial de posiciones de un bus
 * Guarda tanto posiciones en paradas como fuera de paradas
 */
public class BusHistory {
    // Última posición conocida (puede ser en parada o no)
    public Integer lastStopId; // null si no estaba en parada
    public double lastLat;
    public double lastLon;
    public long lastTimestamp;

    public BusHistory(Integer stopId, double lat, double lon, long timestamp) {
        this.lastStopId = stopId;
        this.lastLat = lat;
        this.lastLon = lon;
        this.lastTimestamp = timestamp;
    }

    /**
     * Actualiza la posición del bus
     */
    public void update(Integer stopId, double lat, double lon, long timestamp) {
        this.lastStopId = stopId;
        this.lastLat = lat;
        this.lastLon = lon;
        this.lastTimestamp = timestamp;
    }
}
