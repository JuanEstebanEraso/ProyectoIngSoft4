package com.mio.model;

public class BusHistory {
    public Integer lastStopId;
    public double lastLat;
    public double lastLon;
    public long lastTimestamp;

    public BusHistory(Integer stopId, double lat, double lon, long timestamp) {
        this.lastStopId = stopId;
        this.lastLat = lat;
        this.lastLon = lon;
        this.lastTimestamp = timestamp;
    }

    public void update(Integer stopId, double lat, double lon, long timestamp) {
        this.lastStopId = stopId;
        this.lastLat = lat;
        this.lastLon = lon;
        this.lastTimestamp = timestamp;
    }
}
