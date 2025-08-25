package org.uniroma2.PMCSN.model;

import java.util.ArrayList;
import java.util.List;

public abstract class AbstractStatistics {

    /*lo utilizziamo per definire tutte le possibili caratteristiche*/
    public enum Index {
        ServiceTime,
        QueueTime,
        Lambda,
        SystemPopulation,
        Utilization,
        QueuePopulation,
        ResponseTime
    }

    /*liste per le statistiche */

    public List<Double> meanServiceTimeList = new ArrayList<>();
    public List<Double> meanQueueTimeList = new ArrayList<>();
    public List<Double> lambdaList = new ArrayList<>();
    public List<Double> meanSystemPopulationList = new ArrayList<>();
    public List<Double> meanUtilizationList = new ArrayList<>();
    public List<Double> meanQueuePopulationList = new ArrayList<>();
    public List<Double> meanResponseTimeList = new ArrayList<>();

    /*su ogni lista vengono accumulati tutti i valori medi*/
    private final String centerName;

    public AbstractStatistics(String centerName) {
        this.centerName = centerName;
    } /* statistiche per centro */

    public String getCenterName() {
        return centerName;
    }

}
