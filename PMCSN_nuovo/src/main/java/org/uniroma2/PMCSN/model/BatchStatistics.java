package org.uniroma2.PMCSN.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchStatistics extends AbstractStatistics {

    public int batchRetrievalDone = 0;
    public final int numBatches;

    // Mappa che associa ogni Index alla sua BatchMetric
    private final Map<AbstractStatistics.Index, BatchMetric> metrics = new HashMap<>();

    public BatchStatistics(String centerName, int numBatches) {
        super(centerName);
        this.numBatches = numBatches;
    }

    @Override
    public void add(Index index, List<Double> list, double value) {
        list.add(value);
        if (list.size() >= numBatches) {
            batchRetrievalDone++;
        }
    }

    public BatchMetric getMetric(String name) {
        for (BatchMetric metric : metrics.values()) {
            if (metric.getName().equals(name)) {
                return metric;
            }
        }
        return null;
    }


    public void Metric(Index index, double value) {
        BatchMetric metric = metrics.computeIfAbsent(index, i -> new BatchMetric(i.name(), new ArrayList<>()));
        metric.values.add(value);

        if (metric.values.size() >= numBatches) {
            batchRetrievalDone++;
        }
    }

    public boolean isBatchRetrievalDone() {
        return batchRetrievalDone >= numBatches;
    }
}
