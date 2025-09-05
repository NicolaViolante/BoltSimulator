package org.uniroma2.PMCSN.model;


public class MeanStatistics {

    /* per riassumere una serie di simulazioni in un solo insieme di valori*/

    public String centerName;
    public double meanResponseTime;
    public double meanServiceTime;
    public double meanQueueTime;
    public double lambda;
    public double meanSystemPopulation;
    public double meanUtilization;
    public double meanQueuePopulation;

    /*viene usato a fine simulazioni per produrre le statistiche aggregate */

    public MeanStatistics(String centerName, double  meanResponseTime, double meanServiceTime, double meanQueueTime
            , double lambda, double meanSystemPopulation, double meanUtilization, double meanQueuePopulation) {
        this.centerName = centerName;
        this.meanResponseTime = meanResponseTime;
        this.meanServiceTime = meanServiceTime;
        this.meanQueueTime = meanQueueTime;
        this.lambda = lambda;
        this.meanSystemPopulation = meanSystemPopulation;
        this.meanUtilization = meanUtilization;
        this.meanQueuePopulation = meanQueuePopulation;
    }
}
