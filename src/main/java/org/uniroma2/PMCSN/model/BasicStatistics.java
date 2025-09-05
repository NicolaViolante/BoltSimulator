package org.uniroma2.PMCSN.model;

import java.util.ArrayList;
import java.util.List;

public class BasicStatistics extends AbstractStatistics {

    List<Double> jobServed;

    public BasicStatistics(String centerName) {
        super(centerName);
        jobServed = new ArrayList<>();
    }
}