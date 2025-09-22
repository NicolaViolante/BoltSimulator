//package org.uniroma2.PMCSN.controller;
//
//import org.uniroma2.PMCSN.centers.*;
//import org.uniroma2.PMCSN.configuration.ConfigurationManager;
//import org.uniroma2.PMCSN.libs.Rngs;
//import org.uniroma2.PMCSN.model.*;
//import org.uniroma2.PMCSN.utils.AnalyticalComputation;
//import org.uniroma2.PMCSN.utils.Comparison;
//import org.uniroma2.PMCSN.utils.IntervalCSVGenerator;
//import org.uniroma2.PMCSN.utils.Verification;
//
//import java.io.FileWriter;
//import java.io.IOException;
//import java.io.PrintWriter;
//import java.util.*;
//import java.util.Locale;
//import java.io.File;
//
//public class RideSharingDailySystem implements Sistema {
//
//    /*Case Finite*/
//    private final int SIMPLE_NODES;
//    private final int RIDE_NODES;
//    private final int REPLICAS;
//    private final double REPORTINTERVAL;
//    private final long SEED;
//
//    ConfigurationManager config = new ConfigurationManager();
//
//    public RideSharingDailySystem() {
//        this.SIMPLE_NODES = config.getInt("simulation", "nodes");
//        this.RIDE_NODES = config.getInt("simulation", "rideNodes");
//        this.REPLICAS = config.getInt("simulation", "replicas");
//        this.REPORTINTERVAL = new ConfigurationManager()
//                .getDouble("simulation", "reportInterval");
//        this.SEED = config.getInt("simulation", "seed");
//    }
//
//    @Override
//    public void runFiniteSimulation() {
//        final double STOP = 1440; //min
//        String baseDir = "csvFilesIntervals";
//
//        Rngs rngs = new Rngs();
//
//        List<List<Long>> jobsProcessedByNode = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        for (int i = 0; i < SIMPLE_NODES + RIDE_NODES; i++) {
//            jobsProcessedByNode.add(new ArrayList<>());
//        }
//
//        double[] ETs = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] ETq = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] ES = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] ENs = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] ENq = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] ENS = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] lambda = new double[SIMPLE_NODES+RIDE_NODES];
//        double[] rho = new double[SIMPLE_NODES+RIDE_NODES];
//
//        // Liste per replica (per le medie cumulative a replica)
//        List<List<Double>> respTimeMeansByNode     = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> queueTimeMeansByNode    = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> serviceTimeMeansByNode  = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> systemPopMeansByNode    = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> queuePopMeansByNode     = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> utilizationByNode       = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        List<List<Double>> lambdaByNode            = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//
//        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
//            respTimeMeansByNode   .add(new ArrayList<>());
//            queueTimeMeansByNode  .add(new ArrayList<>());
//            serviceTimeMeansByNode.add(new ArrayList<>());
//            systemPopMeansByNode  .add(new ArrayList<>());
//            queuePopMeansByNode   .add(new ArrayList<>());
//            utilizationByNode     .add(new ArrayList<>());
//            lambdaByNode          .add(new ArrayList<>());
//        }
//
//        System.out.println("=== Finite Simulation ===");
//        rngs.plantSeeds(SEED);
//
//        final int totalNodes = SIMPLE_NODES + RIDE_NODES;
//
//        for (int rep = 1; rep <= REPLICAS; rep++) {
//
//            long seedForRep = rngs.getSeed();
//
//            List<Node> localNodes = init(rngs);
//
//            // inizializza file CSV t=0
//            for (int i = 0; i < totalNodes; i++) {
//                IntervalCSVGenerator.writeIntervalData(
//                        true, seedForRep, i, 0,
//                        0, 0, 0, 0,
//                        0, 0, 0,
//                        baseDir
//                );
//            }
//
//            // PREV cumulativi per intervalli (usati per delta)
//            double[] prevNodeArea = new double[totalNodes];
//            double[] prevQueueArea = new double[totalNodes];
//            double[] prevServiceArea = new double[totalNodes];
//            double[] prevActiveServerArea = new double[totalNodes];
//            long[] prevServed = new long[totalNodes];
//
//            Arrays.fill(prevNodeArea, 0.0);
//            Arrays.fill(prevQueueArea, 0.0);
//            Arrays.fill(prevServiceArea, 0.0);
//            Arrays.fill(prevActiveServerArea, 0.0);
//            Arrays.fill(prevServed, 0L);
//
//            double nextReportTime = REPORTINTERVAL;
//            double lastArrivalTime = 0.0;
//            double lastCompletionTime = 0.0;
//
//            while (true) {
//                double tmin = Double.POSITIVE_INFINITY;
//                int idxMin = -1;
//                for (int i = 0; i < totalNodes; i++) {
//                    double t = localNodes.get(i).peekNextEventTime();
//                    if (t < tmin) {
//                        tmin = t;
//                        idxMin = i;
//                    }
//                }
//
//                // --- reporting: INTEGRA TUTTI i nodi fino a nextReportTime PRIMA di leggere aree ---
//                if (nextReportTime <= tmin && nextReportTime <= STOP) {
//                    double reportTime = nextReportTime;
//
//                    // integra ogni nodo fino al bordo dell'intervallo
//                    for (int i = 0; i < totalNodes; i++) {
//                        localNodes.get(i).integrateTo(reportTime);
//                    }
//
//                    // Calcola per ogni nodo i delta e scrive CSV
//                    for (int i = 0; i < totalNodes; i++) {
//                        Node n = localNodes.get(i);
//                        Area a = n.getAreaObject();
//                        MsqSum[] sums = n.getMsqSums();
//
//                        long servedNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
//                        double nodeAreaNow = a.getNodeArea();
//                        double queueAreaNow = a.getQueueArea();
//                        double serviceAreaNow = a.getServiceArea();
//                        double activeServerAreaNow = a.getActiveServerArea();
//
//                        long deltaServed = servedNow - prevServed[i];
//                        double deltaNodeArea = nodeAreaNow - prevNodeArea[i];
//                        double deltaQueueArea = queueAreaNow - prevQueueArea[i];
//                        double deltaServiceArea = serviceAreaNow - prevServiceArea[i];
//                        double deltaActiveServers = activeServerAreaNow - prevActiveServerArea[i];
//
//                        // Sanity-check: non dovrebbero essere negativi. Log & clamp se succede.
//                        final double EPS = 1e-9;
//                        if (deltaServed < 0 || deltaNodeArea < -EPS || deltaQueueArea < -EPS || deltaServiceArea < -EPS || deltaActiveServers < -EPS) {
//                            System.err.printf("[WARN] Negative delta at rep=%d t=%.2f node=%d -> dServed=%d dNode=%.6f dQ=%.6f dS=%.6f dAS=%.6f%n",
//                                    rep, reportTime, i, deltaServed, deltaNodeArea, deltaQueueArea, deltaServiceArea, deltaActiveServers);
//                            // clamp to zero to avoid NaN/Infinity downstream
//                            deltaServed = Math.max(0L, deltaServed);
//                            deltaNodeArea = Math.max(0.0, deltaNodeArea);
//                            deltaQueueArea = Math.max(0.0, deltaQueueArea);
//                            deltaServiceArea = Math.max(0.0, deltaServiceArea);
//                        }
//
//                        // metriche per intervallo (uso deltaServed come denominatore)
//                        double respTimeInterval = (deltaServed > 0) ? deltaNodeArea / deltaServed : 0.0;
//                        double waitTimeInterval = (deltaServed > 0) ? deltaQueueArea / deltaServed : 0.0;
//                        double serviceTimeInterval = (deltaServed > 0) ? deltaServiceArea / deltaServed : 0.0;
//
//                        double ENInterval = deltaNodeArea / REPORTINTERVAL;
//                        double ENqInterval = deltaQueueArea / REPORTINTERVAL;
//                        double ENsInterval = deltaServiceArea / REPORTINTERVAL;
//
//                        double lambdaInterval = deltaServed / REPORTINTERVAL;
//
//                        int numServers = Math.max(sums.length - 1, 1);
//                        double rhoInterval = 0.0;
//                        if (deltaServed > 0) {
//                            rhoInterval = (lambdaInterval * serviceTimeInterval) / numServers;
//                            rhoInterval = Math.max(0.0, Math.min(1.0, rhoInterval));
//                        }
//
//                        // scrive CSV per questo nodo e intervallo
//                        IntervalCSVGenerator.writeIntervalData(
//                                true, seedForRep, i, reportTime,
//                                respTimeInterval, ENInterval, waitTimeInterval, ENqInterval,
//                                serviceTimeInterval, ENsInterval, rhoInterval,
//                                baseDir
//                        );
//
//                        // aggiorna prev per il prossimo intervallo
//                        prevServed[i] = servedNow;
//                        prevNodeArea[i] = nodeAreaNow;
//                        prevQueueArea[i] = queueAreaNow;
//                        prevServiceArea[i] = serviceAreaNow;
//                        prevActiveServerArea[i] = activeServerAreaNow;
//                    }
//
//                    nextReportTime += REPORTINTERVAL;
//                    continue;
//                }
//
//                if (tmin > STOP) break;
//
//                // integra fino al prossimo evento e processa
//                for (Node n : localNodes) {
//                    n.integrateTo(tmin);
//                }
//
//                localNodes.get(idxMin).processNextEvent(tmin);
//
//                if (idxMin == 0) {
//                    lastArrivalTime = Math.max(lastArrivalTime, tmin);
//                } else {
//                    lastCompletionTime = Math.max(lastCompletionTime, tmin);
//                }
//            } // end while events
//
//            // --- fine replica: calcoli cumulativi per la replica (come prima) ---
//            for (int i = 0; i < totalNodes; i++) {
//                Area a = localNodes.get(i).getAreaObject();
//                MsqSum[] sums = localNodes.get(i).getMsqSums();
//
//                long jobsNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
//                jobsProcessedByNode.get(i).add(jobsNow);
//
//                int numServers = Math.max(sums.length - 1, 1);
//
//                if (jobsNow > 0) {
//                    double ETsReplica = a.getNodeArea() / jobsNow;
//                    double ETqReplica = a.getQueueArea() / jobsNow;
//                    double ESReplica  = a.getServiceArea() / jobsNow;
//
//                    double ENsReplica = a.getNodeArea() / STOP;
//                    double ENqReplica = a.getQueueArea() / STOP;
//
//                    double lambdaReplica = jobsNow / STOP;
//                    double rhoReplica;
//
//                    if (localNodes.get(i) instanceof RideSharingMultiServerNodeDaily) {
//                        int busyServers = ((RideSharingMultiServerNodeDaily) localNodes.get(i)).getNumBusyServers();
//                        rhoReplica = (double) busyServers / numServers;
//                    } else {
//                        rhoReplica = lambdaReplica * ESReplica / numServers;
//                    }
//
//                    rhoReplica = Math.min(rhoReplica, 1.0);
//
//                    respTimeMeansByNode.get(i).add(ETsReplica);
//                    queueTimeMeansByNode.get(i).add(ETqReplica);
//                    serviceTimeMeansByNode.get(i).add(ESReplica);
//                    systemPopMeansByNode.get(i).add(ENsReplica);
//                    queuePopMeansByNode.get(i).add(ENqReplica);
//                    utilizationByNode.get(i).add(rhoReplica);
//                    lambdaByNode.get(i).add(lambdaReplica);
//                }
//            }
//
//            // Reset delle statistiche interne per i nodi per la prossima replica
//            for (Node n : localNodes) {
//                n.resetStatistics();
//            }
//        } // end replicas
//
//        // 7) Costruisco MeanStatistics usando il costruttore che prende i valori medi
//        List<MeanStatistics> meanStatsList = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
//        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
//            double mrt = computeMean(respTimeMeansByNode.get(i));
//            double mst = computeMean(serviceTimeMeansByNode.get(i));
//            double mqt = computeMean(queueTimeMeansByNode.get(i));
//            double ml  = computeMean(lambdaByNode.get(i));
//            double mns = computeMean(systemPopMeansByNode.get(i));
//            double mu  = computeMean(utilizationByNode.get(i));
//            double mnq = computeMean(queuePopMeansByNode.get(i));
//
//            String centerName = "Center" + i;
//
//            meanStatsList.add(new MeanStatistics(
//                    centerName,
//                    mrt,
//                    mst,
//                    mqt,
//                    ml,
//                    mns,
//                    mu,
//                    mnq
//            ));
//        }
//
//        // === STATISTICHE MEDIE CUMULATIVE ===
//        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
//        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
//            MeanStatistics ms = meanStatsList.get(i);
//            System.out.printf("Node %d: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
//                    i,
//                    ms.meanResponseTime,
//                    ms.meanQueueTime,
//                    ms.meanServiceTime,
//                    ms.meanSystemPopulation,
//                    ms.meanQueuePopulation,
//                    ms.meanUtilization,
//                    ms.lambda
//            );
//        }
//
//        // === INTERVALLI DI CONFIDENZA ===
//        System.out.println("=== INTERVALLI DI CONFIDENZA ===");
//        List<ConfidenceInterval> ciList = new ArrayList<>();
//        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
//            ConfidenceInterval ci = new ConfidenceInterval(
//                    respTimeMeansByNode.get(i),
//                    queueTimeMeansByNode.get(i),
//                    serviceTimeMeansByNode.get(i),
//                    systemPopMeansByNode.get(i),
//                    queuePopMeansByNode.get(i),
//                    utilizationByNode.get(i),
//                    lambdaByNode.get(i)
//            );
//            ciList.add(ci);
//
//            System.out.printf("Node %d: ±CI E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
//                    i,
//                    ci.getResponseTimeCI(),
//                    ci.getQueueTimeCI(),
//                    ci.getServiceTimeCI(),
//                    ci.getSystemPopulationCI(),
//                    ci.getQueuePopulationCI(),
//                    ci.getUtilizationCI(),
//                    ci.getLambdaCI()
//            );
//
//        }
//
//        // === NUMERO MEDIO DI JOB PROCESSATI ===
//        System.out.println("=== NUMERO MEDIO DI JOB PROCESSATI ===");
//        double totalAvgJobsProcessed = 0.0;
//        for (int j = 0; j < SIMPLE_NODES + RIDE_NODES; j++) {
//            List<Long> jobsList = jobsProcessedByNode.get(j);
//            double avgJobs = jobsList.stream().mapToLong(Long::longValue).average().orElse(0.0);
//            totalAvgJobsProcessed += Math.floor(avgJobs);  // tronca la media per difetto
//        }
//        System.out.printf("Media totale jobs processati (approssimazione per difetto): %.0f%n", totalAvgJobsProcessed);
//    }
//
//
//    // helper locali
//    private double computeMean(List<Double> list) {
//        // media dei valori in list ignorando NaN; se vuota -> 0.0
//        if (list == null || list.isEmpty()) return 0.0;
//        double sum = 0.0;
//        int cnt = 0;
//        for (Double v : list) {
//            if (v == null || Double.isNaN(v)) continue;
//            sum += v;
//            cnt++;
//        }
//        return (cnt > 0) ? (sum / cnt) : 0.0;
//    }
//
//
//    private List<Node> init(Rngs rng) {
//        List<Node> localNodes = new ArrayList<>();
//        List<RideSharingMultiServerNodeSimpleDaily> centriTradizionali = new ArrayList<>();
//        ConfigurationManager config = new ConfigurationManager();
//        DailyServerSelectorRideSharingSimple selectorRideSimple = new DailyServerSelectorRideSharingSimple(config);
//        DailyServerSelectorRideSharing selectorRide = new DailyServerSelectorRideSharing(config);
//
//        // Nodi “tradizionali” (SIMPLE_NODES)
//        for (int i = 0; i < SIMPLE_NODES; i++) {
//            RideSharingMultiServerNodeSimpleDaily n = new RideSharingMultiServerNodeSimpleDaily(this, i, rng, selectorRideSimple);
//            localNodes.add(n);
//            centriTradizionali.add(n);
//        }
//
//        // Nodi RideSharing “avanzati” (RIDE_NODES)
//        for (int i = SIMPLE_NODES; i < SIMPLE_NODES + RIDE_NODES; i++) {
//            Node n = new RideSharingMultiServerNodeDaily(this, rng, centriTradizionali, selectorRide);
//            localNodes.add(n);
//        }
//
//        return localNodes;
//    }
//
//    @Override
//    public void runInfiniteSimulation(){
//    }
//
//}


package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.centers.*;
import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.IntervalCSVGenerator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.Locale;
import java.io.File;

public class RideSharingDailySystem implements Sistema {

    /*Case Finite*/
    private final int SIMPLE_NODES;
    private final int RIDE_NODES;
    private final int REPLICAS;
    private final double REPORTINTERVAL;
    private final long SEED;

    ConfigurationManager config = new ConfigurationManager();

    public RideSharingDailySystem() {
        this.SIMPLE_NODES = config.getInt("simulation", "nodes");
        this.RIDE_NODES = config.getInt("simulation", "rideNodes");
        this.REPLICAS = config.getInt("simulation", "replicas");
        this.REPORTINTERVAL = new ConfigurationManager()
                .getDouble("simulation", "reportInterval");
        this.SEED = config.getInt("simulation", "seed");
    }

    @Override
    public void runFiniteSimulation() {
        final double STOP = 1440; //min
        String baseDir = "csvFilesIntervals";

        Rngs rngs = new Rngs();

        List<List<Long>> jobsProcessedByNode = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        for (int i = 0; i < SIMPLE_NODES + RIDE_NODES; i++) {
            jobsProcessedByNode.add(new ArrayList<>());
        }

        double[] ETs = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ETq = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ES = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENs = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENq = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENS = new double[SIMPLE_NODES+RIDE_NODES];
        double[] lambda = new double[SIMPLE_NODES+RIDE_NODES];
        double[] rho = new double[SIMPLE_NODES+RIDE_NODES];

        // Liste per replica (per le medie cumulative a replica)
        List<List<Double>> respTimeMeansByNode     = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> queueTimeMeansByNode    = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> serviceTimeMeansByNode  = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> systemPopMeansByNode    = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> queuePopMeansByNode     = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> utilizationByNode       = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        List<List<Double>> lambdaByNode            = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);

        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
            respTimeMeansByNode   .add(new ArrayList<>());
            queueTimeMeansByNode  .add(new ArrayList<>());
            serviceTimeMeansByNode.add(new ArrayList<>());
            systemPopMeansByNode  .add(new ArrayList<>());
            queuePopMeansByNode   .add(new ArrayList<>());
            utilizationByNode     .add(new ArrayList<>());
            lambdaByNode          .add(new ArrayList<>());
        }

        // --- NEW: per-interval structures (inspired by SimpleDailySystem)
        final int totalNodes = SIMPLE_NODES + RIDE_NODES;
        int numReports = (int) Math.ceil(STOP / REPORTINTERVAL) + 1;

        List<List<List<Double>>> respTimeByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> queueTimeByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> serviceTimeByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> sysPopByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> queuePopByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> utilByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> lambdaByNodeTime = new ArrayList<>(totalNodes);
        List<List<List<Double>>> servicePopByNodeTime = new ArrayList<>(totalNodes);

        for (int i = 0; i < totalNodes; i++) {
            respTimeByNodeTime.add(makeEmptyPerTime(numReports));
            queueTimeByNodeTime.add(makeEmptyPerTime(numReports));
            serviceTimeByNodeTime.add(makeEmptyPerTime(numReports));
            sysPopByNodeTime.add(makeEmptyPerTime(numReports));
            queuePopByNodeTime.add(makeEmptyPerTime(numReports));
            utilByNodeTime.add(makeEmptyPerTime(numReports));
            lambdaByNodeTime.add(makeEmptyPerTime(numReports));
            servicePopByNodeTime.add(makeEmptyPerTime(numReports));
        }

        // global per-interval across-replicas
        List<List<Double>> globalRespTimeByTime = makeEmptyPerTime(numReports);
        List<List<Double>> globalSysPopByTime  = makeEmptyPerTime(numReports);
        List<List<Double>> globalQueueTimeByTime = makeEmptyPerTime(numReports);
        List<List<Double>> globalQueuePopByTime  = makeEmptyPerTime(numReports);
        List<List<Double>> globalServiceTimeByTime = makeEmptyPerTime(numReports);
        List<List<Double>> globalServicePopByTime = makeEmptyPerTime(numReports);
        List<List<Double>> globalRhoByTime = makeEmptyPerTime(numReports);

        System.out.println("=== Finite Simulation ===");
        rngs.plantSeeds(SEED);

        // prepare per_interval global file path for reuse
        String dirPerInterval = baseDir + "/per_interval";
        String fileGlobal = dirPerInterval + "/global.csv";
        new File(dirPerInterval).mkdirs();
        final String CSV_HEADER = "Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho";

        for (int rep = 1; rep <= REPLICAS; rep++) {

            long seedForRep = rngs.getSeed();

            List<Node> localNodes = init(rngs);

            // inizializza file CSV t=0 (original behavior)
            for (int i = 0; i < totalNodes; i++) {
                IntervalCSVGenerator.writeIntervalData(
                        true, seedForRep, i, 0,
                        0, 0, 0, 0,
                        0, 0, 0,
                        baseDir
                );
            }

            // --- NEW: create per-node per-interval files and write header + t=0 row (replica-wise per_interval files)
            for (int i = 0; i < totalNodes; i++) {
                String fileNode = dirPerInterval + "/node_" + i + ".csv";
                IntervalCSVGenerator.ensureFile(fileNode);
                String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                        seedForRep, i, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                appendCsvLine(fileNode, CSV_HEADER, line);
            }

            // global initial
            IntervalCSVGenerator.ensureFile(fileGlobal);
            String lineG0 = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    seedForRep, -1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            appendCsvLine(fileGlobal, CSV_HEADER, lineG0);

            // PREV cumulativi per intervalli (usati per delta)
            double[] prevNodeArea = new double[totalNodes];
            double[] prevQueueArea = new double[totalNodes];
            double[] prevServiceArea = new double[totalNodes];
            double[] prevActiveServerArea = new double[totalNodes];
            long[] prevServed = new long[totalNodes];

            Arrays.fill(prevNodeArea, 0.0);
            Arrays.fill(prevQueueArea, 0.0);
            Arrays.fill(prevServiceArea, 0.0);
            Arrays.fill(prevActiveServerArea, 0.0);
            Arrays.fill(prevServed, 0L);

            double nextReportTime = REPORTINTERVAL;
            double lastArrivalTime = 0.0;
            double lastCompletionTime = 0.0;

            while (true) {
                double tmin = Double.POSITIVE_INFINITY;
                int idxMin = -1;
                for (int i = 0; i < totalNodes; i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        idxMin = i;
                    }
                }

                // --- reporting: INTEGRA TUTTI i nodi fino a nextReportTime PRIMA di leggere aree ---
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    double reportTime = nextReportTime;
                    int reportIndex = (int) (reportTime / REPORTINTERVAL);

                    // integra ogni nodo fino al bordo dell'intervallo
                    for (int i = 0; i < totalNodes; i++) {
                        localNodes.get(i).integrateTo(reportTime);
                    }

                    // Calcola per ogni nodo i delta e scrive CSV
                    // --- NEW: accumulate global deltas for global per-interval file ---
                    double cumDeltaNodeArea = 0.0;
                    double cumDeltaQueueArea = 0.0;
                    double cumDeltaServiceArea = 0.0;
                    long cumDeltaServed = 0L;
                    double sumRho = 0.0;

                    for (int i = 0; i < totalNodes; i++) {
                        Node n = localNodes.get(i);
                        Area a = n.getAreaObject();
                        MsqSum[] sums = n.getMsqSums();

                        long servedNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
                        double nodeAreaNow = a.getNodeArea();
                        double queueAreaNow = a.getQueueArea();
                        double serviceAreaNow = a.getServiceArea();
                        double activeServerAreaNow = a.getActiveServerArea();

                        long deltaServed = servedNow - prevServed[i];
                        double deltaNodeArea = nodeAreaNow - prevNodeArea[i];
                        double deltaQueueArea = queueAreaNow - prevQueueArea[i];
                        double deltaServiceArea = serviceAreaNow - prevServiceArea[i];
                        double deltaActiveServers = activeServerAreaNow - prevActiveServerArea[i];

                        // Sanity-check: non dovrebbero essere negativi. Log & clamp se succede.
                        final double EPS = 1e-9;
                        if (deltaServed < 0 || deltaNodeArea < -EPS || deltaQueueArea < -EPS || deltaServiceArea < -EPS || deltaActiveServers < -EPS) {
                            System.err.printf("[WARN] Negative delta at rep=%d t=%.2f node=%d -> dServed=%d dNode=%.6f dQ=%.6f dS=%.6f dAS=%.6f%n",
                                    rep, reportTime-120, i, deltaServed, deltaNodeArea, deltaQueueArea, deltaServiceArea, deltaActiveServers);
                            // clamp to zero to avoid NaN/Infinity downstream
                            deltaServed = Math.max(0L, deltaServed);
                            deltaNodeArea = Math.max(0.0, deltaNodeArea);
                            deltaQueueArea = Math.max(0.0, deltaQueueArea);
                            deltaServiceArea = Math.max(0.0, deltaServiceArea);
                        }

                        // metriche per intervallo (uso deltaServed come denominatore)
                        double respTimeInterval = (deltaServed > 0) ? deltaNodeArea / deltaServed : 0.0;
                        double waitTimeInterval = (deltaServed > 0) ? deltaQueueArea / deltaServed : 0.0;
                        double serviceTimeInterval = (deltaServed > 0) ? deltaServiceArea / deltaServed : 0.0;

                        double ENInterval = deltaNodeArea / REPORTINTERVAL;
                        double ENqInterval = deltaQueueArea / REPORTINTERVAL;
                        double ENsInterval = deltaServiceArea / REPORTINTERVAL;

                        double lambdaInterval = deltaServed / REPORTINTERVAL;

                        int numServers = Math.max(sums.length - 1, 1);
                        double rhoInterval = 0.0;
                        if (deltaServed > 0) {
                            rhoInterval = (lambdaInterval * serviceTimeInterval) / numServers;
                            rhoInterval = Math.max(0.0, Math.min(1.0, rhoInterval));
                        }

                        // scrive CSV per questo nodo e intervallo (comportamento originale)
                        IntervalCSVGenerator.writeIntervalData(
                                true, seedForRep, i, reportTime-120,
                                respTimeInterval, ENInterval, waitTimeInterval, ENqInterval,
                                serviceTimeInterval, ENsInterval, rhoInterval,
                                baseDir
                        );

                        // --- NEW: append per-node replica-wise per_interval file & collect into per-time structures ---
                        String file = dirPerInterval + "/node_" + i + ".csv";
                        String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                                seedForRep, i, reportTime-120,
                                respTimeInterval,
                                ENInterval,
                                waitTimeInterval,
                                ENqInterval,
                                serviceTimeInterval,
                                ENsInterval,
                                rhoInterval);
                        appendCsvLine(file, CSV_HEADER, line);

                        respTimeByNodeTime.get(i).get(reportIndex).add(respTimeInterval);
                        queueTimeByNodeTime.get(i).get(reportIndex).add(waitTimeInterval);
                        serviceTimeByNodeTime.get(i).get(reportIndex).add(serviceTimeInterval);
                        sysPopByNodeTime.get(i).get(reportIndex).add(ENInterval);
                        queuePopByNodeTime.get(i).get(reportIndex).add(ENqInterval);
                        utilByNodeTime.get(i).get(reportIndex).add(rhoInterval);
                        lambdaByNodeTime.get(i).get(reportIndex).add(lambdaInterval);
                        servicePopByNodeTime.get(i).get(reportIndex).add(ENsInterval);

                        // aggiorna prev per il prossimo intervallo
                        prevServed[i] = servedNow;
                        prevNodeArea[i] = nodeAreaNow;
                        prevQueueArea[i] = queueAreaNow;
                        prevServiceArea[i] = serviceAreaNow;
                        prevActiveServerArea[i] = activeServerAreaNow;

                        // update global accumulators
                        cumDeltaNodeArea += deltaNodeArea;
                        cumDeltaQueueArea += deltaQueueArea;
                        cumDeltaServiceArea += deltaServiceArea;
                        cumDeltaServed += deltaServed;
                        sumRho += rhoInterval;
                    }

                    // scrivo file globale per l'intervallo (per replica) includendo Time e Center=-1
                    double globalETs = (cumDeltaServed > 0) ? cumDeltaNodeArea / cumDeltaServed : 0.0;
                    double globalEN = cumDeltaNodeArea / REPORTINTERVAL;
                    double globalETq = (cumDeltaServed > 0) ? cumDeltaQueueArea / cumDeltaServed : 0.0;
                    double globalENq = cumDeltaQueueArea / REPORTINTERVAL;
                    double globalES  = (cumDeltaServed > 0) ? cumDeltaServiceArea / cumDeltaServed : 0.0;
                    double globalENS = cumDeltaServiceArea / REPORTINTERVAL;

                    double globalRho = (totalNodes > 0) ? (sumRho / (double) totalNodes) : 0.0;

                    String lineG = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                            seedForRep, -1, reportTime-120,
                            globalETs, globalEN, globalETq, globalENq, globalES, globalENS, globalRho);
                    appendCsvLine(fileGlobal, CSV_HEADER, lineG);

                    // Salva i valori globali di questa replica per l'intervallo
                    globalRespTimeByTime.get(reportIndex).add(globalETs);
                    globalSysPopByTime.get(reportIndex).add(globalEN);
                    globalQueueTimeByTime.get(reportIndex).add(globalETq);
                    globalQueuePopByTime.get(reportIndex).add(globalENq);
                    globalServiceTimeByTime.get(reportIndex).add(globalES);
                    globalServicePopByTime.get(reportIndex).add(globalENS);
                    globalRhoByTime.get(reportIndex).add(globalRho);

                    nextReportTime += REPORTINTERVAL;
                    continue;
                }

                if (tmin > STOP) break;

                // integra fino al prossimo evento e processa
                for (Node n : localNodes) {
                    n.integrateTo(tmin);
                }

                localNodes.get(idxMin).processNextEvent(tmin);

                if (idxMin == 0) {
                    lastArrivalTime = Math.max(lastArrivalTime, tmin);
                } else {
                    lastCompletionTime = Math.max(lastCompletionTime, tmin);
                }
            } // end while events

            // --- fine replica: calcoli cumulativi per la replica (come prima) ---
            for (int i = 0; i < totalNodes; i++) {
                Area a = localNodes.get(i).getAreaObject();
                MsqSum[] sums = localNodes.get(i).getMsqSums();

                long jobsNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
                jobsProcessedByNode.get(i).add(jobsNow);

                int numServers = Math.max(sums.length - 1, 1);

                if (jobsNow > 0) {
                    double ETsReplica = a.getNodeArea() / jobsNow;
                    double ETqReplica = a.getQueueArea() / jobsNow;
                    double ESReplica  = a.getServiceArea() / jobsNow;

                    double ENsReplica = a.getNodeArea() / STOP;
                    double ENqReplica = a.getQueueArea() / STOP;

                    double lambdaReplica = jobsNow / STOP;
                    double rhoReplica;

                    if (localNodes.get(i) instanceof RideSharingMultiServerNodeDaily) {
                        int busyServers = ((RideSharingMultiServerNodeDaily) localNodes.get(i)).getNumBusyServers();
                        rhoReplica = (double) busyServers / numServers;
                    } else {
                        rhoReplica = lambdaReplica * ESReplica / numServers;
                    }

                    rhoReplica = Math.min(rhoReplica, 1.0);

                    respTimeMeansByNode.get(i).add(ETsReplica);
                    queueTimeMeansByNode.get(i).add(ETqReplica);
                    serviceTimeMeansByNode.get(i).add(ESReplica);
                    systemPopMeansByNode.get(i).add(ENsReplica);
                    queuePopMeansByNode.get(i).add(ENqReplica);
                    utilizationByNode.get(i).add(rhoReplica);
                    lambdaByNode.get(i).add(lambdaReplica);
                }
            }

            // Reset delle statistiche interne per i nodi per la prossima replica
            for (Node n : localNodes) {
                n.resetStatistics();
            }
        } // end replicas

        //=== Dopo tutte le repliche: calcolo le medie per ogni nodo e per ogni reportIndex e scrivo CSV per il plotting ===
        String dirMean = baseDir + "/interval_mean";
        new File(dirMean).mkdirs();

        for (int i = 0; i < totalNodes; i++) {
            String file = dirMean + "/node_" + i + "_interval_mean.csv";
            String header = "Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho";
            IntervalCSVGenerator.ensureFile(file);
            try {
                appendCsvLineIfFileEmpty(file, header);
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (int r = 0; r < numReports; r++) {
                double reportTime = r * REPORTINTERVAL;
                double meanETs = mean(respTimeByNodeTime.get(i).get(r));
                double meanETq = mean(queueTimeByNodeTime.get(i).get(r));
                double meanES = mean(serviceTimeByNodeTime.get(i).get(r));
                double meanENs = mean(sysPopByNodeTime.get(i).get(r));
                double meanENq = mean(queuePopByNodeTime.get(i).get(r));
                double meanLambda = mean(lambdaByNodeTime.get(i).get(r));
                double meanRho = mean(utilByNodeTime.get(i).get(r));
                double meanENS = mean(servicePopByNodeTime.get(i).get(r));

                String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                        0, // seed=0 for aggregated mean file (no single seed)
                        i,
                        reportTime-120,
                        meanETs,
                        meanENs,
                        meanETq,
                        meanENq,
                        meanES,
                        meanENS,
                        meanRho);
                appendCsvLine(file, header, line);
            }
        }

        // scrivo file globale di medie per-interval: media sui seed dei valori globali per-interval
        String fileG = dirMean + "/global_interval_mean.csv";
        IntervalCSVGenerator.ensureFile(fileG);
        appendCsvLineIfFileEmpty(fileG, CSV_HEADER);
        for (int r = 0; r < numReports; r++) {
            double reportTime = r * REPORTINTERVAL;

            double meanETsG = mean(globalRespTimeByTime.get(r));    // media across-replicas dei global ETs per intervallo r
            double meanENsG = mean(globalSysPopByTime.get(r));
            double meanETqG = mean(globalQueueTimeByTime.get(r));
            double meanENqG = mean(globalQueuePopByTime.get(r));
            double meanESG  = mean(globalServiceTimeByTime.get(r));
            double meanENSG = mean(globalServicePopByTime.get(r));
            double meanRhoG = mean(globalRhoByTime.get(r));

            String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    0,
                    -1,
                    reportTime-120,
                    meanETsG,
                    meanENsG,
                    meanETqG,
                    meanENqG,
                    meanESG,
                    meanENSG,
                    meanRhoG);
            appendCsvLine(fileG, CSV_HEADER, line);
        }

        // === STATISTICHE MEDIE CUMULATIVE ===
        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        List<MeanStatistics> meanStatsList = new ArrayList<>(SIMPLE_NODES+RIDE_NODES);
        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
            double mrt = computeMean(respTimeMeansByNode.get(i));
            double mst = computeMean(serviceTimeMeansByNode.get(i));
            double mqt = computeMean(queueTimeMeansByNode.get(i));
            double ml  = computeMean(lambdaByNode.get(i));
            double mns = computeMean(systemPopMeansByNode.get(i));
            double mu  = computeMean(utilizationByNode.get(i));
            double mnq = computeMean(queuePopMeansByNode.get(i));

            String centerName = "Center" + i;

            meanStatsList.add(new MeanStatistics(
                    centerName,
                    mrt,
                    mst,
                    mqt,
                    ml,
                    mns,
                    mu,
                    mnq
            ));
        }

        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
            MeanStatistics ms = meanStatsList.get(i);
            System.out.printf("Node %d: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
                    i,
                    ms.meanResponseTime,
                    ms.meanQueueTime,
                    ms.meanServiceTime,
                    ms.meanSystemPopulation,
                    ms.meanQueuePopulation,
                    ms.meanUtilization,
                    ms.lambda
            );
        }

        // === INTERVALLI DI CONFIDENZA ===
        System.out.println("=== INTERVALLI DI CONFIDENZA ===");
        List<ConfidenceInterval> ciList = new ArrayList<>();
        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
            ConfidenceInterval ci = new ConfidenceInterval(
                    respTimeMeansByNode.get(i),
                    queueTimeMeansByNode.get(i),
                    serviceTimeMeansByNode.get(i),
                    systemPopMeansByNode.get(i),
                    queuePopMeansByNode.get(i),
                    utilizationByNode.get(i),
                    lambdaByNode.get(i)
            );
            ciList.add(ci);

            System.out.printf("Node %d: ±CI E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
                    i,
                    ci.getResponseTimeCI(),
                    ci.getQueueTimeCI(),
                    ci.getServiceTimeCI(),
                    ci.getSystemPopulationCI(),
                    ci.getQueuePopulationCI(),
                    ci.getUtilizationCI(),
                    ci.getLambdaCI()
            );

        }

        // === NUMERO MEDIO DI JOB PROCESSATI ===
        System.out.println("=== NUMERO MEDIO DI JOB PROCESSATI ===");
        double totalAvgJobsProcessed = 0.0;
        for (int j = 0; j < SIMPLE_NODES + RIDE_NODES; j++) {
            List<Long> jobsList = jobsProcessedByNode.get(j);
            double avgJobs = jobsList.stream().mapToLong(Long::longValue).average().orElse(0.0);
            totalAvgJobsProcessed += Math.floor(avgJobs);  // tronca la media per difetto
        }
        System.out.printf("Media totale jobs processati (approssimazione per difetto): %.0f%n", totalAvgJobsProcessed);
    }


    // helper locali
    private double computeMean(List<Double> list) {
        // media dei valori in list ignorando NaN; se vuota -> 0.0
        if (list == null || list.isEmpty()) return 0.0;
        double sum = 0.0;
        int cnt = 0;
        for (Double v : list) {
            if (v == null || Double.isNaN(v)) continue;
            sum += v;
            cnt++;
        }
        return (cnt > 0) ? (sum / cnt) : 0.0;
    }

    // --- NEW helpers copied/adapted from SimpleDailySystem ---
    private static List<List<Double>> makeEmptyPerTime(int numReports) {
        List<List<Double>> perTime = new ArrayList<>(numReports);
        for (int r = 0; r < numReports; r++) perTime.add(new ArrayList<>());
        return perTime;
    }

    private static double mean(List<Double> list) {
        if (list == null || list.isEmpty()) return 0.0;
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    /** Append a CSV line, writing header only if file is empty. */
    private void appendCsvLine(String filepath, String header, String line) {
        File f = new File(filepath);
        try {
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            boolean needHeader = ( !f.exists() || f.length() == 0 );
            try (FileWriter fw = new FileWriter(f, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                if (needHeader) pw.println(header);
                pw.println(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Write header only if file empty (helper used when header may already exist). */
    private void appendCsvLineIfFileEmpty(String filepath, String header) {
        File f = new File(filepath);
        try {
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            if (!f.exists() || f.length() == 0) {
                try (FileWriter fw = new FileWriter(f, true);
                     PrintWriter pw = new PrintWriter(fw)) {
                    pw.println(header);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private List<Node> init(Rngs rng) {
        List<Node> localNodes = new ArrayList<>();
        List<RideSharingMultiServerNodeSimpleDaily> centriTradizionali = new ArrayList<>();
        ConfigurationManager config = new ConfigurationManager();
        DailyServerSelectorRideSharingSimple selectorRideSimple = new DailyServerSelectorRideSharingSimple(config);
        DailyServerSelectorRideSharing selectorRide = new DailyServerSelectorRideSharing(config);

        // Nodi “tradizionali” (SIMPLE_NODES)
        for (int i = 0; i < SIMPLE_NODES; i++) {
            RideSharingMultiServerNodeSimpleDaily n = new RideSharingMultiServerNodeSimpleDaily(this, i, rng, selectorRideSimple);
            localNodes.add(n);
            centriTradizionali.add(n);
        }

        // Nodi RideSharing “avanzati” (RIDE_NODES)
        for (int i = SIMPLE_NODES; i < SIMPLE_NODES + RIDE_NODES; i++) {
            Node n = new RideSharingMultiServerNodeDaily(this, rng, centriTradizionali, selectorRide);
            localNodes.add(n);
        }

        return localNodes;
    }

    @Override
    public void runInfiniteSimulation(){
    }

}