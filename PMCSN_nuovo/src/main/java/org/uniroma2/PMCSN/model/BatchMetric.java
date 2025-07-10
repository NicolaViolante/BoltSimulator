package org.uniroma2.PMCSN.model;

import java.util.List;

public class BatchMetric {

    private final String name;
    public final List<Double> values;
    private double acfValue;

    public BatchMetric(String name, List<Double> values) {
        this.name = name;
        this.values = values;
    }

    public String getName() {
        return name;
    }
}
