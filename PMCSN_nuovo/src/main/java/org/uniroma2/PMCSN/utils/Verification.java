package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.model.MeanStatistics;
import org.uniroma2.PMCSN.model.ConfidenceInterval;



import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class Verification {
    public static class VerificationResult {
        public String name;
        public Comparison.ComparisonResult comparisonResult;
        public ConfidenceInterval confidenceIntervals;
        public MeanStatistics meanStatistics;

        public VerificationResult(String name, Comparison.ComparisonResult comparisonResult, ConfidenceInterval confidenceIntervals, MeanStatistics meanStatistics) {
            this.name = name;
            this.comparisonResult = comparisonResult;
            this.confidenceIntervals = confidenceIntervals;
            this.meanStatistics = meanStatistics;
        }

        public boolean isWithinInterval(double diff, double ci) {
            return diff <= ci;
        }
    }

    public static void verifyConfidenceIntervals(String simulationType, List<MeanStatistics> meanStatisticsList, List<Comparison.ComparisonResult> comparisonResultList, List<ConfidenceInterval> confidenceIntervalsList) {
        List<VerificationResult> verificationResults = new ArrayList<>();

        if (comparisonResultList.size() != confidenceIntervalsList.size() || comparisonResultList.size() != meanStatisticsList.size()) {
            System.out.println("Mismatch in the size of comparison results, confidence intervals, and mean statistics lists");
            return;
        }

        for (int i = 0; i < comparisonResultList.size(); i++) {
            Comparison.ComparisonResult comparisonResult = comparisonResultList.get(i);
            ConfidenceInterval confidenceIntervals = confidenceIntervalsList.get(i);
            MeanStatistics meanStatistics = meanStatisticsList.get(i);

            verificationResults.add(new VerificationResult(comparisonResult.name, comparisonResult, confidenceIntervals, meanStatistics));
        }

        writeVerificationResults(simulationType, verificationResults);
    }

    public static void writeVerificationResults(String modelName, List<VerificationResult> verificationResults) {
        File file = new File("csvFiles/" + modelName + "/verification/");
        if (!file.exists()) {
            file.mkdirs();
        }

        file = new File("csvFiles/" + modelName + "/verification/verification.csv");
        try (FileWriter fileWriter = new FileWriter(file)) {
            String DELIMITER = "\n";
            String COMMA = ",";

            fileWriter.append("Center, E[Ts]_Diff, E[Ts]_CI, E[Ts]_Within, E[Tq]_Diff, E[Tq]_CI, E[Tq]_Within, E[s]_Diff, E[s]_CI, E[s]_Within, E[Ns]_Diff, E[Ns]_CI, E[Ns]_Within, E[Nq]_Diff, E[Nq]_CI, E[Nq]_Within, ρ_Diff, ρ_CI, ρ_Within, λ_Diff, λ_CI, λ_Within").append(DELIMITER);
            for (VerificationResult verificationResult : verificationResults) {
                fileWriter.append(verificationResult.name).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.responseTimeDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getResponseTimeCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.responseTimeDiff, verificationResult.confidenceIntervals.getResponseTimeCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.queueTimeDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getQueueTimeCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.queueTimeDiff, verificationResult.confidenceIntervals.getQueueTimeCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.serviceTimeDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getServiceTimeCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.serviceTimeDiff, verificationResult.confidenceIntervals.getServiceTimeCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.systemPopulationDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getSystemPopulationCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.systemPopulationDiff, verificationResult.confidenceIntervals.getSystemPopulationCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.queuePopulationDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getQueuePopulationCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.queuePopulationDiff, verificationResult.confidenceIntervals.getQueuePopulationCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.utilizationDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getUtilizationCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.utilizationDiff, verificationResult.confidenceIntervals.getUtilizationCI()))).append(COMMA)
                        .append(String.valueOf(verificationResult.comparisonResult.lambdaDiff)).append(COMMA)
                        .append(String.valueOf(verificationResult.confidenceIntervals.getLambdaCI())).append(COMMA)
                        .append(String.valueOf(verificationResult.isWithinInterval(verificationResult.comparisonResult.lambdaDiff, verificationResult.confidenceIntervals.getLambdaCI()))).append(DELIMITER);
            }

            fileWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
