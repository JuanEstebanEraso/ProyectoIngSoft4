package com.mio.model;

public class Stop {
    private int stopId;
    private int planVersionId;
    private String shortName;
    private String longName;
    private long gpsX;
    private long gpsY;
    private double decimalLong;
    private double decimalLatit;

    public Stop(int stopId, int planVersionId, String shortName, String longName,
                long gpsX, long gpsY, double decimalLong, double decimalLatit) {
        this.stopId = stopId;
        this.planVersionId = planVersionId;
        this.shortName = shortName;
        this.longName = longName;
        this.gpsX = gpsX;
        this.gpsY = gpsY;
        this.decimalLong = decimalLong;
        this.decimalLatit = decimalLatit;
    }

    public int getStopId() {
        return stopId;
    }

    public int getPlanVersionId() {
        return planVersionId;
    }

    public String getShortName() {
        return shortName;
    }

    public String getLongName() {
        return longName;
    }

    public long getGpsX() {
        return gpsX;
    }

    public long getGpsY() {
        return gpsY;
    }

    public double getDecimalLong() {
        return decimalLong;
    }

    public double getDecimalLatit() {
        return decimalLatit;
    }

    @Override
    public String toString() {
        return String.format("Stop[%d - %s]", stopId, longName);
    }
}
