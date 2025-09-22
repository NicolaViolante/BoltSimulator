package org.uniroma2.PMCSN.model;

public class Area {
    private double nodeArea = 0.0;
    private double queueArea = 0.0;
    private double serviceArea = 0.0;

    private double activeServerArea = 0.0;

    public double getNodeArea() {
        return nodeArea;
    }

    public double getActiveServerArea() { return activeServerArea; }

    public void incNodeArea(double area) {
        nodeArea += area;
    }

    public double getQueueArea() {
        return queueArea;
    }

    public void incQueueArea(double area) {
        queueArea += area;
    }

    public double getServiceArea() {
        return serviceArea;
    }

    public void incServiceArea(double area) {
        serviceArea += area;
    }

    public void reset() {
        nodeArea = 0.0;
        queueArea = 0.0;
        serviceArea = 0.0;
    }

    // <<< IMPLEMENTAZIONE DI SERVER AREA >>>
    public void incActiveServerArea(double v) {
        activeServerArea += v;
    }
}
