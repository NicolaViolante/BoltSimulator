package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.model.MeanStatistics;
import org.uniroma2.PMCSN.utils.AnalyticalComputation.AnalyticalResult;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Comparison {

    public static class ComparisonResult {
        public String name;
        public double responseTimeDiff;
        public double queueTimeDiff;
        public double serviceTimeDiff;
        public double systemPopulationDiff;
        public double queuePopulationDiff;
        public double utilizationDiff;
        public double lambdaDiff;

        public ComparisonResult(String name, double responseTimeDiff, double queueTimeDiff, double serviceTimeDiff, double systemPopulationDiff, double queuePopulationDiff, double utilizationDiff, double lambdaDiff) {
            this.name = name;
            this.responseTimeDiff = responseTimeDiff;
            this.queueTimeDiff = queueTimeDiff;
            this.serviceTimeDiff = serviceTimeDiff;
            this.systemPopulationDiff = systemPopulationDiff;
            this.queuePopulationDiff = queuePopulationDiff;
            this.utilizationDiff = utilizationDiff;
            this.lambdaDiff = lambdaDiff;
        }
    }

    public static List<ComparisonResult> compareResults(String simulationType, List<AnalyticalResult> verificationResults, List<MeanStatistics> meanStatisticsList) {
        List<ComparisonResult> comparisonResults = new ArrayList<>();

        for (MeanStatistics meanStatistics : meanStatisticsList) {
            for (AnalyticalResult result : verificationResults) {
                if (meanStatistics.centerName.contains(result.name)) {
                    double responseTimeDiff = Math.abs(result.Ets - meanStatistics.meanResponseTime);
                    double queueTimeDiff = Math.abs(result.Etq - meanStatistics.meanQueueTime);
                    double serviceTimeDiff = Math.abs(result.Es - meanStatistics.meanServiceTime);
                    double systemPopulationDiff = Math.abs(result.Ens - meanStatistics.meanSystemPopulation);
                    double queuePopulationDiff = Math.abs(result.Enq - meanStatistics.meanQueuePopulation);
                    double utilizationDiff = Math.abs(result.rho - meanStatistics.meanUtilization);
                    double lambdaDiff = Math.abs(result.lambda - meanStatistics.lambda);

                    ComparisonResult comparisonResult = new ComparisonResult(
                            result.name,
                            responseTimeDiff,
                            queueTimeDiff,
                            serviceTimeDiff,
                            systemPopulationDiff,
                            queuePopulationDiff,
                            utilizationDiff,
                            lambdaDiff
                    );

                    comparisonResults.add(comparisonResult);
                }
            }
        }
        writeResultsComparison(simulationType, comparisonResults);
        return comparisonResults;
    }

    public static void writeResultsComparison(String modelName, List<ComparisonResult> comparisonResults) {
        File file = new File("csvFiles/" + modelName + "/comparison/");
        if (!file.exists()) {
            file.mkdirs();
        }

        file = new File("csvFiles/" + modelName + "/comparison/comparison.csv");
        try (FileWriter fileWriter = new FileWriter(file)) {
            String DELIMITER = "\n";
            String COMMA = ",";

            fileWriter.append("Center, E[Ts]_Diff, E[Tq]_Diff, E[s]_Diff, E[Ns]_Diff, E[Nq]_Diff, ρ_Diff, λ_Diff").append(DELIMITER);
            for (ComparisonResult result : comparisonResults) {
                fileWriter.append(result.name).append(COMMA)
                        .append(String.valueOf(result.responseTimeDiff)).append(COMMA)
                        .append(String.valueOf(result.queueTimeDiff)).append(COMMA)
                        .append(String.valueOf(result.serviceTimeDiff)).append(COMMA)
                        .append(String.valueOf(result.systemPopulationDiff)).append(COMMA)
                        .append(String.valueOf(result.queuePopulationDiff)).append(COMMA)
                        .append(String.valueOf(result.utilizationDiff)).append(COMMA)
                        .append(String.valueOf(result.lambdaDiff)).append(DELIMITER);
            }

            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
