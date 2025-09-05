package org.uniroma2.PMCSN.model;


import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.libs.Rvms;

import java.util.List;

public class ConfidenceInterval {
    public double responseTimeCI;
    public double queueTimeCI;
    public double serviceTimeCI;
    public double systemPopulationCI;
    public double queuePopulationCI;
    public double utilizationCI;
    public double lambdaCI;

    public ConfidenceInterval(List<Double> meanResponseTimeList, List<Double> meanQueueTimeList, List<Double> meanServiceTimeList,
                              List<Double> meanSystemPopulationList, List<Double> meanQueuePopulationList,
                              List<Double> meanUtilizationList, List<Double> lambdaList) {
        this.responseTimeCI = computeConfidenceInterval(meanResponseTimeList);
        this.queueTimeCI = computeConfidenceInterval(meanQueueTimeList);
        this.serviceTimeCI = computeConfidenceInterval(meanServiceTimeList);
        this.systemPopulationCI = computeConfidenceInterval(meanSystemPopulationList);
        this.queuePopulationCI = computeConfidenceInterval(meanQueuePopulationList);
        this.utilizationCI = computeConfidenceInterval(meanUtilizationList);
        this.lambdaCI = computeConfidenceInterval(lambdaList);
    }

    public double getResponseTimeCI() {
        return responseTimeCI;
    }

    public double getQueueTimeCI() {
        return queueTimeCI;
    }

    public double getServiceTimeCI() {
        return serviceTimeCI;
    }

    public double getSystemPopulationCI() {
        return systemPopulationCI;
    }

    public double getQueuePopulationCI() {
        return queuePopulationCI;
    }

    public double getUtilizationCI() {
        return utilizationCI;
    }

    public double getLambdaCI() {
        return lambdaCI;
    }


    public static double computeConfidenceInterval(List<Double> values) {
        long n = 0; /* counts data points */
        double sum = 0.0;
        double mean = 0.0;
        double u, t, w = 0.0;
        double diff;

        Rvms rvms = new Rvms();

        for (Double data : values) {
            n++; /* and standard deviation */
            diff = data - mean;
            sum += diff * diff * (n - 1.0) / n;
            mean += diff / n;
        }

        double stddev = Math.sqrt(sum / n);

        ConfigurationManager configurationManager = new ConfigurationManager();
        double levelOfConfidence = configurationManager.getDouble("general", "levelOfConfidence");
        if (n > 1) {
            u = 1.0 - 0.5 * (1.0 - levelOfConfidence); /* interval parameter */
            t = rvms.idfStudent(n - 1, u); /* critical value of t */
            w = t * stddev / Math.sqrt(n - 1); /* interval half width */
        } else {
            System.out.print("ERROR - insufficient data to compute confidence interval\n");
        }
        return w;
    }
}
