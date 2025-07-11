package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.centers.SimpleMultiServerNode;
import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.AnalyticalComputation;
import org.uniroma2.PMCSN.utils.AnalyticalComputation.AnalyticalResult;
import org.uniroma2.PMCSN.utils.Comparison;
import org.uniroma2.PMCSN.utils.IntervalCSVGenerator;
import org.uniroma2.PMCSN.utils.Verification;


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.uniroma2.PMCSN.utils.IntervalCSVGenerator.writeGlobalInterval;

public class SimpleSystem implements Sistema {

    /*Case Finite*/
    private final int NODES;
    private final int REPLICAS;
    private final double STOP;
    private final double REPORTINTERVAL;
    private final int rngStreamIndex ;
    private final int SEED;

    /*Case Infinite*/
    private final int BATCHSIZE;
    private final int NUMBATCHES;


    // Ora teniamo le statistiche per ogni nodo
    private final BasicStatistics[] nodeStats;
    private BatchStatistics batchStatistics;
    ConfigurationManager config = new ConfigurationManager();

    public SimpleSystem() {

        // legge tutto da config.properties
        this.NODES = config.getInt("simulation", "nodes");
        this.REPLICAS = config.getInt("simulation", "replicas");
        this.STOP = config.getDouble("simulation", "stop");
        this.REPORTINTERVAL = new ConfigurationManager()
                .getDouble("simulation", "reportInterval");
        this.BATCHSIZE = config.getInt("simulation", "batchSize");
        this.NUMBATCHES = config.getInt("simulation", "batchSize");
        this.SEED = config.getInt("simulation", "seed");
        this.rngStreamIndex = config.getInt("general", "seedStreamIndex");

        // 2) inizializza BasicStatistics per ogni nodo
        nodeStats = new BasicStatistics[NODES];
        for (int i = 0; i < NODES; i++) {
            nodeStats[i] = new BasicStatistics("Node" + i);
        }
    }

    @Override
    public void runFiniteSimulation() {
        final double STOP = this.STOP;
        String baseDir = "csvFilesIntervals";
        Rngs rngs = new Rngs();

        List<List<Long>> jobsProcessedByNode = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
            jobsProcessedByNode.add(new ArrayList<>());
        }

        double[] ETs = new double[NODES];
        double[] ETq = new double[NODES];
        double[] ES = new double[NODES];
        double[] ENs = new double[NODES];
        double[] ENq = new double[NODES];
        double[] ENS = new double[NODES];
        double[] lambda = new double[NODES];
        double[] rho = new double[NODES];

        // Liste per replica
        List<List<Double>> respTimeMeansByNode     = new ArrayList<>(NODES);
        List<List<Double>> queueTimeMeansByNode    = new ArrayList<>(NODES);
        List<List<Double>> serviceTimeMeansByNode  = new ArrayList<>(NODES);
        List<List<Double>> systemPopMeansByNode    = new ArrayList<>(NODES);
        List<List<Double>> queuePopMeansByNode     = new ArrayList<>(NODES);
        List<List<Double>> utilizationByNode       = new ArrayList<>(NODES);
        List<List<Double>> lambdaByNode            = new ArrayList<>(NODES);

        for (int i = 0; i < NODES; i++) {
            respTimeMeansByNode   .add(new ArrayList<>());
            queueTimeMeansByNode  .add(new ArrayList<>());
            serviceTimeMeansByNode.add(new ArrayList<>());
            systemPopMeansByNode  .add(new ArrayList<>());
            queuePopMeansByNode   .add(new ArrayList<>());
            utilizationByNode     .add(new ArrayList<>());
            lambdaByNode          .add(new ArrayList<>());
        }

        System.out.println("=== Finite Simulation ===");

        for (int rep = 1; rep <= REPLICAS; rep++) {
            rngs.plantSeeds(rep);
            List<SimpleMultiServerNode> localNodes = init(rngs);
            double nextReportTime = REPORTINTERVAL;
            double lastArrivalTime = 0.0;
            double lastCompletionTime = 0.0;

            while (true) {
                double tmin = Double.POSITIVE_INFINITY;
                int idxMin = -1;
                for (int i = 0; i < NODES; i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        idxMin = i;
                    }
                }

                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    for (int i = 0; i < NODES; i++) {
                        SimpleMultiServerNode n = localNodes.get(i);
                        Area a = n.getAreaObject();
                        MsqSum[] sums = n.getMsqSums();

                        long served = Arrays.stream(sums).mapToLong(s -> s.served).sum();

                        ETs[i] = a.getNodeArea() / served;
                        ETq[i] = a.getQueueArea() / served;
                        ES[i] = a.getServiceArea() / served;

                        ENs[i] = a.getNodeArea() / nextReportTime;
                        ENq[i] = a.getQueueArea() / nextReportTime;
                        ENS[i] = a.getServiceArea() / nextReportTime;

                        lambda[i] = served / nextReportTime;
                        int numServers = sums.length - 1;
                        rho[i] = (lambda[i] * ES[i]) / numServers;

                        IntervalCSVGenerator.writeIntervalData(
                                true, rep, i, nextReportTime,
                                ETs[i], ENs[i], ETq[i], ENq[i],
                                ES[i], ENS[i], rho[i],
                                baseDir
                        );
                    }

                    writeGlobalInterval(rep, nextReportTime, localNodes, baseDir);
                    nextReportTime += REPORTINTERVAL;
                    continue;
                }

                if (tmin > STOP) break;

                for (SimpleMultiServerNode n : localNodes) {
                    n.integrateTo(tmin);
                }

                localNodes.get(idxMin).processNextEvent(tmin);

                if (idxMin == 0) {
                    lastArrivalTime = Math.max(lastArrivalTime, tmin);
                } else {
                    lastCompletionTime = Math.max(lastCompletionTime, tmin);
                }
            }

            // Popoliamo le liste con i valori calcolati a fine replica per ogni nodo
            for (int i = 0; i < NODES; i++) {
                Area a = localNodes.get(i).getAreaObject();
                MsqSum[] sums = localNodes.get(i).getMsqSums();

                long jobsNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
                jobsProcessedByNode.get(i).add(jobsNow);

                int numServers = sums.length - 1;

                if (jobsNow > 0) {
                    double ETsReplica = a.getNodeArea() / jobsNow;
                    double ETqReplica = a.getQueueArea() / jobsNow;
                    double ESReplica  = a.getServiceArea() / jobsNow;

                    double ENsReplica = a.getNodeArea() / STOP;
                    double ENqReplica = a.getQueueArea() / STOP;
                    double ENSReplica = a.getServiceArea() / STOP;

                    double lambdaReplica = jobsNow / STOP;
                    double rhoReplica = (lambdaReplica * ESReplica) / numServers;

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
            for (SimpleMultiServerNode n : localNodes) {
                n.resetStatistics();
                // eventualmente resettare anche aree se necessario
            }
        }

        // 7) Costruisco MeanStatistics usando il costruttore che prende i valori medi
        List<MeanStatistics> meanStatsList = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
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

        // === STATISTICHE MEDIE CUMULATIVE ===
        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        for (int i = 0; i < NODES; i++) {
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
        for (int i = 0; i < NODES; i++) {
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
        for (int j = 0; j < NODES; j++) {
            List<Long> jobsList = jobsProcessedByNode.get(j);
            double avgJobs = jobsList.stream().mapToLong(Long::longValue).average().orElse(0.0);
            totalAvgJobsProcessed += Math.floor(avgJobs);  // tronca la media per difetto
        }
        System.out.printf("Media totale jobs processati (approssimazione per difetto): %.0f%n", totalAvgJobsProcessed);

        // === COMPARISON E VERIFICA ===
        List<AnalyticalResult> analyticalResults =
                AnalyticalComputation.computeAnalyticalResults("FINITE_SIMULATION");

        List<Comparison.ComparisonResult> comparisonResults =
                Comparison.compareResults("FINITE_SIMULATION", analyticalResults, meanStatsList);

        Verification.verifyConfidenceIntervals(
                "FINITE_SIMULATION",
                meanStatsList,
                comparisonResults,
                ciList
        );
    }
//    @Override
//    public void runInfiniteSimulation() {
//        // 1) Parametri di batch‐means
//        // es. 64 batch
//        // es. 256 completamenti per batch
//
//        // 2) Preparo le liste per le batch‐means PER NODO
//        List<List<Double>> respTimeMeansByNode     = new ArrayList<>(NODES);
//        List<List<Double>> queueTimeMeansByNode    = new ArrayList<>(NODES);
//        List<List<Double>> serviceTimeMeansByNode  = new ArrayList<>(NODES);
//        List<List<Double>> systemPopMeansByNode    = new ArrayList<>(NODES);
//        List<List<Double>> queuePopMeansByNode     = new ArrayList<>(NODES);
//        List<List<Double>> utilizationByNode       = new ArrayList<>(NODES);
//        List<List<Double>> lambdaByNode            = new ArrayList<>(NODES);
//
//        for (int i = 0; i < NODES; i++) {
//            respTimeMeansByNode   .add(new ArrayList<>());
//            queueTimeMeansByNode  .add(new ArrayList<>());
//            serviceTimeMeansByNode.add(new ArrayList<>());
//            systemPopMeansByNode  .add(new ArrayList<>());
//            queuePopMeansByNode   .add(new ArrayList<>());
//            utilizationByNode     .add(new ArrayList<>());
//            lambdaByNode          .add(new ArrayList<>());
//        }
//
//        // 3) Snapshots iniziali (per il delta)
//        double[] areaNodeSnap   = new double[NODES];
//        double[] areaQueueSnap  = new double[NODES];
//        double[] areaServSnap   = new double[NODES];
//        long[]   jobsServedSnap = new long[NODES];
//
//        double clock;
//        double startTimeBatch = 0.0;
//        double endTimeBatch;
//        double lastArrTime    = 0.0;
//        double lastCompTime   = 0.0;
//
//        int batchNumber     = 0;
//        int jobObservations = 0;
//
//        // 4) Inizializzo RNG e nodi
//        Rngs rngs = new Rngs();
//        rngs.plantSeeds(SEED);
//        List<SimpleMultiServerNode> nodes = init(rngs);
//
//        // 5) Primo snapshot (t = 0)
//        for (int i = 0; i < NODES; i++) {
//            Area a      = nodes.get(i).getAreaObject();
//            MsqSum[] ss = nodes.get(i).getMsqSums();
//            areaNodeSnap[i]   = a.getNodeArea();
//            areaQueueSnap[i]  = a.getQueueArea();
//            areaServSnap[i]   = a.getServiceArea();
//            jobsServedSnap[i] = Arrays.stream(ss).mapToLong(s -> s.served).sum();
//        }
//
//        // 6) Ciclo evento‐driven fino a k batch
//        while (batchNumber < NUMBATCHES) {
//            // trova il prossimo evento
//            double tmin = Double.POSITIVE_INFINITY;
//            int idxMin  = -1;
//            for (int i = 0; i < NODES; i++) {
//                double t = nodes.get(i).peekNextEventTime();
//                if (t < tmin) {
//                    tmin   = t;
//                    idxMin = i;
//                }
//            }
//
//            // integra e processa
//            for (SimpleMultiServerNode n : nodes) n.integrateTo(tmin);
//            clock = tmin;
//            nodes.get(idxMin).processNextEvent(tmin);
//
//            // aggiorno arrival/completion
//            if (idxMin == 0) {
//                lastArrTime = Math.max(lastArrTime, tmin);
//            } else {
//                lastCompTime   = Math.max(lastCompTime, tmin);
//                jobObservations++;
//            }
//
//            // fine batch?
//            if (jobObservations == BATCHSIZE) {
//                endTimeBatch = clock;
//
//                // calcolo le batch‐means del batch corrente per ciascun nodo
//                for (int i = 0; i < NODES; i++) {
//                    Area    a       = nodes.get(i).getAreaObject();
//                    MsqSum[] ss     = nodes.get(i).getMsqSums();
//                    long    jobsNow = Arrays.stream(ss).mapToLong(s -> s.served).sum();
//
//                    long   deltaJobs      = jobsNow - jobsServedSnap[i];
//                    double batchTime      = endTimeBatch - startTimeBatch;
//                    double deltaNodeArea  = a.getNodeArea()   - areaNodeSnap[i];
//                    double deltaQueueArea = a.getQueueArea()  - areaQueueSnap[i];
//                    double deltaServArea  = a.getServiceArea() - areaServSnap[i];
//                    int    numServers     = ss.length - 1;
//
//                    if (deltaJobs > 0 && batchTime > 0) {
//                        double ETs    = deltaNodeArea  / deltaJobs;
//                        double ETq    = deltaQueueArea / deltaJobs;
//                        double ES     = deltaServArea  / deltaJobs;
//                        double ENs    = deltaNodeArea  / batchTime;
//                        double ENq    = deltaQueueArea / batchTime;
//                        double ENS    = deltaServArea  / batchTime;
//                        double lambda = deltaJobs      / batchTime;
//                        double rho    = (lambda * ES)  / numServers;
//
//                        respTimeMeansByNode   .get(i).add(ETs);
//                        queueTimeMeansByNode  .get(i).add(ETq);
//                        serviceTimeMeansByNode.get(i).add(ES);
//                        systemPopMeansByNode  .get(i).add(ENs);
//                        queuePopMeansByNode   .get(i).add(ENq);
//                        utilizationByNode     .get(i).add(rho);
//                        lambdaByNode          .get(i).add(lambda);
//                    }
//                }
//
//                // reset statistice interne e nuovo snapshot zero
//                for (SimpleMultiServerNode n : nodes) n.resetStatistics();
//                for (int i = 0; i < NODES; i++) {
//                    areaNodeSnap[i]   = 0.0;
//                    areaQueueSnap[i]  = 0.0;
//                    areaServSnap[i]   = 0.0;
//                    jobsServedSnap[i] = 0L;
//                }
//                startTimeBatch = endTimeBatch;
//
//                batchNumber++;
//                jobObservations = 0;
//            }
//        }
//
//        // 7) Costruisco MeanStatistics usando il costruttore che prende i valori medi
//        List<MeanStatistics> meanStatsList = new ArrayList<>(NODES);
//        for (int i = 0; i < NODES; i++) {
//            // calcolo medie dalle tue liste di batch‐means
//            double mrt = computeMean(respTimeMeansByNode.get(i));
//            double mqt = computeMean(queueTimeMeansByNode.get(i));
//            double mst = computeMean(serviceTimeMeansByNode.get(i));
//            double mns = computeMean(systemPopMeansByNode.get(i));
//            double mnq = computeMean(queuePopMeansByNode.get(i));
//            double mu  = computeMean(utilizationByNode.get(i));
//            double ml  = computeMean(lambdaByNode.get(i));
//
//            // nome del centro, ad esempio "Center0", "Center1", ...
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
//        // 8) Confronto analitico vs simulazione
//        List<AnalyticalResult> analyticalResults =
//                AnalyticalComputation.computeAnalyticalResults("INFINITE_SIMULATION");
//        List<Comparison.ComparisonResult> comparisonResults =
//                Comparison.compareResults("INFINITE_SIMULATION", analyticalResults, meanStatsList);
//
//        // 9) Calcolo gli intervalli di confidenza per ciascun nodo
//        List<ConfidenceInterval> ciList = new ArrayList<>(NODES);
//        for (int i = 0; i < NODES; i++) {
//            ciList.add(new ConfidenceInterval(
//                    respTimeMeansByNode.get(i),
//                    queueTimeMeansByNode.get(i),
//                    serviceTimeMeansByNode.get(i),
//                    systemPopMeansByNode.get(i),
//                    queuePopMeansByNode.get(i),
//                    utilizationByNode.get(i),
//                    lambdaByNode.get(i)
//            ));
//        }
//
//        // 10) Verifica dei CI
//        Verification.verifyConfidenceIntervals(
//                "INFINITE_SIMULATION",
//                meanStatsList,
//                comparisonResults,
//                ciList
//        );
//
//        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
//        for (int i = 0; i < NODES; i++) {
//            System.out.printf("Node %d: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
//                    i,
//                    computeMean(respTimeMeansByNode.get(i)),
//                    computeMean(queueTimeMeansByNode.get(i)),
//                    computeMean(serviceTimeMeansByNode.get(i)), computeMean(systemPopMeansByNode.get(i)),
//                    computeMean(queuePopMeansByNode.get(i)),
//                    computeMean(utilizationByNode.get(i)),
//                    computeMean(lambdaByNode.get(i))
//            );
//        }
//
//        System.out.println("=== INTERVALLI DI CONFIDENZA ===");
//        for (int i = 0; i < NODES; i++) {
//            ConfidenceInterval ci = new ConfidenceInterval(
//                    respTimeMeansByNode.get(i),
//                    queueTimeMeansByNode.get(i),
//                    serviceTimeMeansByNode.get(i),
//                    systemPopMeansByNode.get(i),
//                    queuePopMeansByNode.get(i),
//                    utilizationByNode.get(i),
//                    lambdaByNode.get(i)
//            );
//
//            System.out.printf("Node %d: ±CI E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[Ns]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
//                    i,
//                    ci.getResponseTimeCI(),
//                    ci.getQueueTimeCI(),
//                    ci.getServiceTimeCI(),
//                    ci.getSystemPopulationCI(),
//                    ci.getQueuePopulationCI(),
//                    ci.getUtilizationCI(),
//                    ci.getLambdaCI()
//            );
//        }
//
//    }

    private static final String RESET         = "\u001B[0m";
    private static final String BRIGHT_GREEN  = "\u001B[92m";
    private static final String BRIGHT_YELLOW = "\u001B[93m";
    private static final String BRIGHT_RED    = "\u001B[91m";

    @Override
    public void runInfiniteSimulation() {
        List<List<Double>> respTimeMeansByNode     = new ArrayList<>(NODES);
        List<List<Double>> queueTimeMeansByNode    = new ArrayList<>(NODES);
        List<List<Double>> serviceTimeMeansByNode  = new ArrayList<>(NODES);
        List<List<Double>> systemPopMeansByNode    = new ArrayList<>(NODES);
        List<List<Double>> queuePopMeansByNode     = new ArrayList<>(NODES);
        List<List<Double>> utilizationByNode       = new ArrayList<>(NODES);
        List<List<Double>> lambdaByNode            = new ArrayList<>(NODES);

        for (int i = 0; i < NODES; i++) {
            respTimeMeansByNode   .add(new ArrayList<>());
            queueTimeMeansByNode  .add(new ArrayList<>());
            serviceTimeMeansByNode.add(new ArrayList<>());
            systemPopMeansByNode  .add(new ArrayList<>());
            queuePopMeansByNode   .add(new ArrayList<>());
            utilizationByNode     .add(new ArrayList<>());
            lambdaByNode          .add(new ArrayList<>());
        }

        double[] areaNodeSnap   = new double[NODES];
        double[] areaQueueSnap  = new double[NODES];
        double[] areaServSnap   = new double[NODES];
        long[]   jobsServedSnap = new long[NODES];

        double clock;
        double startTimeBatch = 0.0;
        double endTimeBatch;
        double lastArrTime    = 0.0;
        double lastCompTime   = 0.0;

        int batchNumber     = 0;
        int jobObservations = 0;

        Rngs rngs = new Rngs();
        rngs.plantSeeds(SEED);
        List<SimpleMultiServerNode> nodes = init(rngs);

        for (int i = 0; i < NODES; i++) {
            Area a      = nodes.get(i).getAreaObject();
            MsqSum[] ss = nodes.get(i).getMsqSums();
            areaNodeSnap[i]   = a.getNodeArea();
            areaQueueSnap[i]  = a.getQueueArea();
            areaServSnap[i]   = a.getServiceArea();
            jobsServedSnap[i] = Arrays.stream(ss).mapToLong(s -> s.served).sum();
        }

        while (batchNumber < NUMBATCHES) {
            double tmin = Double.POSITIVE_INFINITY;
            int idxMin  = -1;
            for (int i = 0; i < NODES; i++) {
                double t = nodes.get(i).peekNextEventTime();
                if (t < tmin) {
                    tmin   = t;
                    idxMin = i;
                }
            }

            for (SimpleMultiServerNode n : nodes) n.integrateTo(tmin);
            clock = tmin;
            nodes.get(idxMin).processNextEvent(tmin);

            if (idxMin == 0) {
                lastArrTime = Math.max(lastArrTime, tmin);
            } else {
                lastCompTime   = Math.max(lastCompTime, tmin);
                jobObservations++;
            }

            if (jobObservations == BATCHSIZE) {
                endTimeBatch = clock;

                for (int i = 0; i < NODES; i++) {
                    Area    a       = nodes.get(i).getAreaObject();
                    MsqSum[] ss     = nodes.get(i).getMsqSums();
                    long    jobsNow = Arrays.stream(ss).mapToLong(s -> s.served).sum();

                    long   deltaJobs      = jobsNow - jobsServedSnap[i];
                    double batchTime      = endTimeBatch - startTimeBatch;
                    double deltaNodeArea  = a.getNodeArea()   - areaNodeSnap[i];
                    double deltaQueueArea = a.getQueueArea()  - areaQueueSnap[i];
                    double deltaServArea  = a.getServiceArea() - areaServSnap[i];
                    int    numServers     = ss.length - 1;

                    if (deltaJobs > 0 && batchTime > 0) {
                        double ETs    = deltaNodeArea  / deltaJobs;
                        double ETq    = deltaQueueArea / deltaJobs;
                        double ES     = deltaServArea  / deltaJobs;
                        double ENs    = deltaNodeArea  / batchTime;
                        double ENq    = deltaQueueArea / batchTime;
                        double ENS    = deltaServArea  / batchTime;
                        double lambda = deltaJobs      / batchTime;
                        double rho    = (lambda * ES)  / numServers;

                        respTimeMeansByNode   .get(i).add(ETs);
                        queueTimeMeansByNode  .get(i).add(ETq);
                        serviceTimeMeansByNode.get(i).add(ES);
                        systemPopMeansByNode  .get(i).add(ENs);
                        queuePopMeansByNode   .get(i).add(ENq);
                        utilizationByNode     .get(i).add(rho);
                        lambdaByNode          .get(i).add(lambda);
                    }
                }

                for (SimpleMultiServerNode n : nodes) n.resetStatistics();
                for (int i = 0; i < NODES; i++) {
                    areaNodeSnap[i]   = 0.0;
                    areaQueueSnap[i]  = 0.0;
                    areaServSnap[i]   = 0.0;
                    jobsServedSnap[i] = 0L;
                }
                startTimeBatch = endTimeBatch;

                batchNumber++;
                jobObservations = 0;
            }
        }

        List<MeanStatistics> meanStatsList = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
            meanStatsList.add(new MeanStatistics(
                    "Center" + i,
                    computeMean(respTimeMeansByNode.get(i)),
                    computeMean(serviceTimeMeansByNode.get(i)),
                    computeMean(queueTimeMeansByNode.get(i)),
                    computeMean(lambdaByNode.get(i)),
                    computeMean(systemPopMeansByNode.get(i)),
                    computeMean(utilizationByNode.get(i)),
                    computeMean(queuePopMeansByNode.get(i))
            ));
        }

        List<AnalyticalResult> analyticalResults =
                AnalyticalComputation.computeAnalyticalResults("INFINITE_SIMULATION");
        List<Comparison.ComparisonResult> comparisonResults =
                Comparison.compareResults("INFINITE_SIMULATION", analyticalResults, meanStatsList);

        List<ConfidenceInterval> ciList = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
            ciList.add(new ConfidenceInterval(
                    respTimeMeansByNode.get(i),
                    queueTimeMeansByNode.get(i),
                    serviceTimeMeansByNode.get(i),
                    systemPopMeansByNode.get(i),
                    queuePopMeansByNode.get(i),
                    utilizationByNode.get(i),
                    lambdaByNode.get(i)
            ));
        }

        List<Verification.VerificationResult> verificationResults =
                Verification.verifyConfidenceIntervals(
                        "INFINITE_SIMULATION",
                        meanStatsList,
                        comparisonResults,
                        ciList
                );

        System.out.println("=== RISULTATI DI VERIFICA ===");
        for (Verification.VerificationResult result : verificationResults) {
            printVerificationResult(result);
        }

        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        for (MeanStatistics ms : meanStatsList) {
            System.out.printf(
                    "%s: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
                    ms.centerName,
                    ms.meanResponseTime,
                    ms.meanQueueTime,
                    ms.meanServiceTime,
                    ms.meanSystemPopulation,
                    ms.meanQueuePopulation,
                    ms.meanUtilization,
                    ms.lambda
            );
        }


    }

    private static void printVerificationResult(Verification.VerificationResult result) {
        String within  = BRIGHT_GREEN + "within" + RESET;
        String outside = BRIGHT_RED   + "outside" + RESET;

        String rtColor = getColor(result.comparisonResult.responseTimeDiff);
        String rtInOut = result.isWithinInterval(
                result.comparisonResult.responseTimeDiff,
                result.confidenceIntervals.getResponseTimeCI()
        ) ? within : outside;
        System.out.printf("%s\nE[Ts]: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.centerName,
                result.meanStatistics.meanResponseTime,
                rtColor, result.comparisonResult.responseTimeDiff, RESET,
                rtInOut,
                result.confidenceIntervals.getResponseTimeCI()
        );

        String qtColor = getColor(result.comparisonResult.queueTimeDiff);
        String qtInOut = result.isWithinInterval(
                result.comparisonResult.queueTimeDiff,
                result.confidenceIntervals.getQueueTimeCI()
        ) ? within : outside;
        System.out.printf("E[Tq]: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.meanQueueTime,
                qtColor, result.comparisonResult.queueTimeDiff, RESET,
                qtInOut,
                result.confidenceIntervals.getQueueTimeCI()
        );

        String stColor = getColor(result.comparisonResult.serviceTimeDiff);
        String stInOut = result.isWithinInterval(
                result.comparisonResult.serviceTimeDiff,
                result.confidenceIntervals.getServiceTimeCI()
        ) ? within : outside;
        System.out.printf("E[Si]: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.meanServiceTime,
                stColor, result.comparisonResult.serviceTimeDiff, RESET,
                stInOut,
                result.confidenceIntervals.getServiceTimeCI()
        );

        String npColor = getColor(result.comparisonResult.systemPopulationDiff);
        String npInOut = result.isWithinInterval(
                result.comparisonResult.systemPopulationDiff,
                result.confidenceIntervals.getSystemPopulationCI()
        ) ? within : outside;
        System.out.printf("E[Ns]: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.meanSystemPopulation,
                npColor, result.comparisonResult.systemPopulationDiff, RESET,
                npInOut,
                result.confidenceIntervals.getSystemPopulationCI()
        );

        String nqColor = getColor(result.comparisonResult.queuePopulationDiff);
        String nqInOut = result.isWithinInterval(
                result.comparisonResult.queuePopulationDiff,
                result.confidenceIntervals.getQueuePopulationCI()
        ) ? within : outside;
        System.out.printf("E[Nq]: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.meanQueuePopulation,
                nqColor, result.comparisonResult.queuePopulationDiff, RESET,
                nqInOut,
                result.confidenceIntervals.getQueuePopulationCI()
        );

        String uColor = getColor(result.comparisonResult.utilizationDiff);
        String uInOut = result.isWithinInterval(
                result.comparisonResult.utilizationDiff,
                result.confidenceIntervals.getUtilizationCI()
        ) ? within : outside;
        System.out.printf("ρ: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.meanUtilization,
                uColor, result.comparisonResult.utilizationDiff, RESET,
                uInOut,
                result.confidenceIntervals.getUtilizationCI()
        );

        String lColor = getColor(result.comparisonResult.lambdaDiff);
        String lInOut = result.isWithinInterval(
                result.comparisonResult.lambdaDiff,
                result.confidenceIntervals.getLambdaCI()
        ) ? within : outside;
        System.out.printf("λ: mean %.4f, diff %s%.4f%s is %s ±%.4f%n",
                result.meanStatistics.lambda,
                lColor, result.comparisonResult.lambdaDiff, RESET,
                lInOut,
                result.confidenceIntervals.getLambdaCI()
        );
    }

    private static String getColor(double value) {
        if (value < 0.5) {
            return BRIGHT_GREEN;
        } else if (value < 1.0) {
            return BRIGHT_YELLOW;
        } else {
            return BRIGHT_RED;
        }
    }

    // helper locale per calcolare la media
    private double computeMean(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
    }

    private List<SimpleMultiServerNode> init(Rngs rng) {
        List<SimpleMultiServerNode> localNodes = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            SimpleMultiServerNode n = new SimpleMultiServerNode(this, i, rng);
            localNodes.add(n);
        }
        return localNodes;
    }

    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}