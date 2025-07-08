package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.model.BatchMetric;
import org.uniroma2.PMCSN.model.BatchStatistics;

import java.util.List;


public class ModelVerificationBatchMeans {

    public static void runModelVerificationWithBatchMeansMethod() throws Exception {
//        ConfigurationManager config = new ConfigurationManager();
//        int batchSize = config.getInt("simulation", "batchSize");
//        int numBatches = config.getInt("simulation", "numBatches");
//        int warmupThreshold = (int) ((batchSize * numBatches) * config.getDouble("simulation", "warmupPercentage"));
//
//        BatchSimulationRunner batchRunner = new BatchSimulationRunner(batchSize, numBatches, warmupThreshold);
//        List<BatchStatistics> batchStatisticsList = batchRunner.runBatchSimulation(true, false);
//
//        for (BatchStatistics batchStatistics : batchStatisticsList) {
//            List<BatchMetric> allBatchMetrics = List.of(
//                    new BatchMetric("E[Ts]", batchStatistics.meanResponseTimeList),
//                    new BatchMetric("E[Tq]", batchStatistics.meanQueueTimeList),
//                    new BatchMetric("E[s]", batchStatistics.meanServiceTimeList),
//                    new BatchMetric("E[Ns]", batchStatistics.meanSystemPopulationList),
//                    new BatchMetric("E[Nq]", batchStatistics.meanQueuePopulationList),
//                    new BatchMetric("ρ", batchStatistics.meanUtilizationList),
//                    new BatchMetric("λ", batchStatistics.lambdaList)
//            );
//
//            for (BatchMetric batchMetric : allBatchMetrics) {
//                double acfValue = Math.abs(acf(batchMetric.values));
//                batchMetric.setAcfValue(acfValue);
//            }
//
//            printBatchStatisticsResult(batchStatistics.getCenterName(), allBatchMetrics, batchSize, numBatches);
//        }
    }

    public static double acf(List<Double> data) {
        int k = data.size();
        if (k <= 1) return 0.0; // Non ha senso calcolare ACF con meno di 2 valori

        double mean = data.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double numerator = 0.0;
        double denominator = 0.0;

        for (int j = 0; j < k - 1; j++) {
            numerator += (data.get(j) - mean) * (data.get(j + 1) - mean);
        }
        for (int j = 0; j < k; j++) {
            denominator += Math.pow(data.get(j) - mean, 2);
        }

        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }
}
