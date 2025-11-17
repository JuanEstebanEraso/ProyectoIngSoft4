package com.mio.model;

public class Arc {
    private int lineId;
    private int orientation; 
    private int lineVariant; 
    private int fromStopId;
    private int toStopId;
    private int sequenceFrom;
    private int sequenceTo;

    public Arc(int lineId, int orientation, int lineVariant, int fromStopId, int toStopId,
               int sequenceFrom, int sequenceTo) {
        this.lineId = lineId;
        this.orientation = orientation;
        this.lineVariant = lineVariant;
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.sequenceFrom = sequenceFrom;
        this.sequenceTo = sequenceTo;
    }

    public int getLineId() {
        return lineId;
    }

    public int getOrientation() {
        return orientation;
    }

    public int getLineVariant() {
        return lineVariant;
    }

    public int getFromStopId() {
        return fromStopId;
    }

    public int getToStopId() {
        return toStopId;
    }

    public int getSequenceFrom() {
        return sequenceFrom;
    }

    public int getSequenceTo() {
        return sequenceTo;
    }

    public String getOrientationLabel() {
        return orientation == 0 ? "IDA" : "VUELTA";
    }

    @Override
    public String toString() {
        return String.format("Arc[Line:%d, %s, Var:%d, %d->%d (Seq:%d->%d)]",
                lineId, getOrientationLabel(), lineVariant, fromStopId, toStopId, sequenceFrom, sequenceTo);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Arc arc = (Arc) o;
        return lineId == arc.lineId &&
                orientation == arc.orientation &&
                lineVariant == arc.lineVariant &&
                fromStopId == arc.fromStopId &&
                toStopId == arc.toStopId;
    }

    @Override
    public int hashCode() {
        int result = lineId;
        result = 31 * result + orientation;
        result = 31 * result + lineVariant;
        result = 31 * result + fromStopId;
        result = 31 * result + toStopId;
        return result;
    }
}
