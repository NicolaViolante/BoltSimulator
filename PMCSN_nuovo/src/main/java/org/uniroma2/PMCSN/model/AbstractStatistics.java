package org.uniroma2.PMCSN.model;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public abstract class AbstractStatistics {

    private final Logger logger = Logger.getLogger(AbstractStatistics.class.getName());

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

    MeanStatistics meanStatistics = null;
    private final String centerName;

    public AbstractStatistics(String centerName) {
        this.centerName = centerName;
    } /* statistiche per centro */

    public String getCenterName() {
        return centerName;
    }

    /*lazy initialization */
    public MeanStatistics getMeanStatistics() {
        if(meanStatistics == null){
            meanStatistics = new MeanStatistics(this);
        }
        return meanStatistics;
    }

    public void saveStats(Area area, MsqSum[] sum, double lastArrivalTime, double lastCompletionTime, boolean isMultiServer) {
        saveStats(area, sum, lastArrivalTime, lastCompletionTime, isMultiServer, 0);
    }


    public void saveStats(Area area, MsqSum[] sum, double lastArrivalTime, double lastCompletionTime, boolean isMultiServer, double currentBatchStartTime) {
        /* calcolo del numero totale di job serviti */
        long numberOfJobsServed = Arrays.stream(sum).mapToLong(s -> s.served).sum();

        /* λ = lavori serviti / intervallo di tempo */
        double lambda = numberOfJobsServed / (lastArrivalTime - currentBatchStartTime);
        add(Index.Lambda, lambdaList, lambda);

        /* E[Ns] */
        double meanSystemPopulation = area.getNodeArea() / (lastCompletionTime - currentBatchStartTime);
        add(Index.SystemPopulation, meanSystemPopulationList, meanSystemPopulation);

        /* E[Ts] */
        double meanResponseTime = area.getNodeArea() / numberOfJobsServed;
        add(Index.ResponseTime, meanResponseTimeList, meanResponseTime);

        /* E[Nq] */
        double meanQueuePopulation = area.getQueueArea() / (lastCompletionTime - currentBatchStartTime);
        add(Index.QueuePopulation, meanQueuePopulationList, meanQueuePopulation);

        /* E[Tq] */
        double meanQueueTime = area.getQueueArea() / numberOfJobsServed;
        add(Index.QueueTime, meanQueueTimeList, meanQueueTime);

        double meanServiceTime;
        double utilization;

        if (isMultiServer) {
            /* E[s]: media dei serviceTime per server */
            meanServiceTime = Arrays.stream(sum)
                    .filter(s -> s.served > 0)
                    .mapToDouble(s -> s.service / s.served)
                    .average()
                    .orElseThrow();

            /* ρ = (λ * E[s]) / S  dove S = sum.length - 1 */
            int numServers = sum.length - 1;
            utilization = (lambda * meanServiceTime) / numServers;

        } else {
            /* Single-server case */
            meanServiceTime = sum[0].service / sum[0].served;
            utilization = area.getServiceArea() / (lastCompletionTime - currentBatchStartTime);
        }

        add(Index.Utilization, meanUtilizationList, utilization);
        add(Index.ServiceTime, meanServiceTimeList, meanServiceTime);
    }


    abstract void add(Index index, List<Double> list, double value);

    public void writeStats(String simulationType, long seed) throws IOException {
        File parent = Path.of("csvFiles", simulationType, String.valueOf(seed), "results").toFile();
        if (!parent.exists()) {
            if (!parent.mkdirs()) {
                logger.severe("Failed to create directory: " + parent.getPath());
                System.exit(1);
            }
        }
        File file = new File(parent, centerName + ".csv");
        try (FileWriter fileWriter = new FileWriter(file)) {
            String DELIMITER = "\n";
            String COMMA = ",";
            int run;
            String name = simulationType.contains("BATCH") ? "#Batch" : "#Run";
            fileWriter.append(name).append(", E[Ts], E[Tq], E[s], E[Ns], E[Nq], ρ, λ").append(DELIMITER);
            for (run = 0; run < meanResponseTimeList.size(); run++) {
                writeRunValuesRow(fileWriter, run, COMMA, DELIMITER);
            }
            fileWriter.flush();
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    /* per ogni run specifico */
    private void writeRunValuesRow(FileWriter fileWriter, int run, String COMMA, String DELIMITER) throws IOException {
        fileWriter.append(String.valueOf(run + 1)).append(COMMA)
                .append(String.valueOf(meanResponseTimeList.get(run))).append(COMMA)
                .append(String.valueOf(meanQueueTimeList.get(run))).append(COMMA)
                .append(String.valueOf(meanServiceTimeList.get(run))).append(COMMA)
                .append(String.valueOf(meanSystemPopulationList.get(run))).append(COMMA)
                .append(String.valueOf(meanQueuePopulationList.get(run))).append(COMMA)
                .append(String.valueOf(meanUtilizationList.get(run))).append(COMMA)
                .append(String.valueOf(lambdaList.get(run))).append(DELIMITER);
    }
}
