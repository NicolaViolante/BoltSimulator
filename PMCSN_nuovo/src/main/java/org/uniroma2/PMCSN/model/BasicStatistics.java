package org.uniroma2.PMCSN.model;

import java.util.ArrayList;
import java.util.List;

public class BasicStatistics extends AbstractStatistics {

    List<Double> jobServed;
    private final List<Double> busyTimeList = new ArrayList<>();

    public BasicStatistics(String centerName) {
        super(centerName);
        jobServed = new ArrayList<>();
    }

    @Override
    void add(Index index, List<Double> list, double value) {
        list.add(value);
    }

    public List<Double> getJobServed() {
        return jobServed;
    }

    public void addJobServed(double value) {
        jobServed.add(value);
    }

    public void addBusyTime(double value) {
        busyTimeList.add(value);
    }

    public double getMeanBusyTime() {
        return busyTimeList.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
}