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

//        @Override
//    public void runFiniteSimulation() {
//        // 1) Parametri da config
//        final double STOP = this.STOP;
//        String baseDir = "csvFilesIntervals";
//
//        Rngs rngs = new Rngs();
//
//        double[] ETs = {0, 0, 0};
//        double[] ETq = {0, 0, 0};
//        double[] ES = {0, 0, 0};
//        double[] ENs = {0, 0, 0};
//        double[] ENq = {0, 0, 0};
//        double[] ENS = {0, 0, 0};
//        double[] lambda = {0, 0, 0};
//        double[] rho = {0, 0, 0};
//
//
//        System.out.println("=== Finite Simulation ===");
//
//        for (int rep = 1; rep <= REPLICAS; rep++) {
//
//            // a) Init RNG
//            rngs.plantSeeds(rep);
//
//            // b) Crea nodi e reset loro aree + sums
//            List<SimpleMultiServerNode> localNodes = init(rngs);
//
//            /* per la generazione dei valori da graficare*/
//            double nextReportTime = REPORTINTERVAL;
//
//            // c) Simulazione vera e propria: eventi + integrazione aree
//            double lastArrivalTime = 0.0;
//            double lastCompletionTime = 0.0;
//            while (true) {
//                // i) trova prossimo evento
//                double tmin = Double.POSITIVE_INFINITY;
//                int idxMin = -1;
//                for (int i = 0; i < NODES; i++) {
//                    double t = localNodes.get(i).peekNextEventTime();
//                    if (t < tmin) {
//                        tmin = t;
//                        idxMin = i;
//                    }
//                }
//
//                // 2) Se il prossimo report arriva prima del prossimo evento (e prima di STOP)
//                if (nextReportTime <= tmin && nextReportTime <= STOP) {
//                    // per ogni nodo, emetti un record CSV
//                    for (int i = 0; i < NODES; i++) {
//                        SimpleMultiServerNode n = localNodes.get(i);
//                        Area a = n.getAreaObject();
//                        MsqSum[] sums = n.getMsqSums();
//                        MsqServer[] serversCompletion = n.getServersCompletition();
//
//                        long numberOfJobsServed = Arrays.stream(sums).mapToLong(s -> s.served).sum();
//
//                        // calcola indicatori a intervallo nextReportTime
//                        /*double ETs = a.getNodeArea() / sumsTotalServed(sums);*/
//                        ETs[i] = a.getNodeArea() / numberOfJobsServed;
//                        /*double ETq = a.getQueueArea() / sumsTotalServed(sums);*/
//                        ETq[i] = a.getQueueArea() / numberOfJobsServed;
//                        /*double ES = a.getServiceArea() / sumsTotalServed(sums);*/
//                        ES[i] = a.getServiceArea() /numberOfJobsServed;
//
//                        ENs[i] = a.getNodeArea() / nextReportTime;
//                        ENq[i] = a.getQueueArea() / nextReportTime;
//                        ENS[i] = a.getServiceArea() / nextReportTime;
//
//                        lambda[i] = numberOfJobsServed / (nextReportTime);
//                        /* ρ = (λ * E[s]) / S  dove S = sum.length - 1 */
//                        int numServers = sums.length - 1;
//                        rho[i] = (lambda[i] * ES[i]) / numServers;
//                       /* double rho = a.getServiceArea() / (n.getNumServers() * nextReportTime);*/
//
//                        /*memorizza in una lista*/
//
//                        IntervalCSVGenerator.writeIntervalData(
//                                true,            // finite
//                                rep,             // seed (replica)
//                                i,               // centro
//                                nextReportTime,  // istante
//                                ETs[i], ENs[i], ETq[i], ENq[i],
//                                ES[i], ENS[i], rho[i],
//                                baseDir
//                        );
//                    }
//
//                    // 2b) intervalli globali
//                    writeGlobalInterval(rep, nextReportTime, localNodes, baseDir);
//
//                    nextReportTime += REPORTINTERVAL;
//                    continue;
//                }
//
//                // ii) termina se oltre STOP e non ci sono altri eventi prima di STOP
//                if (tmin > STOP) break;
//
//                // iii) integra e processa evento
//                //   - prima integri tutti i nodi fino a tmin
//                for (SimpleMultiServerNode n : localNodes) {
//                    n.integrateTo(tmin);
//                }
//
//                //   - poi processi l’evento su idxMin
//                localNodes.get(idxMin).processNextEvent(tmin);
//
//                // iv) aggiorni i due orologi di statistiche
//                if(idxMin == 0){ //si tratta di un evento di arrivo
//                    lastArrivalTime = Math.max(lastArrivalTime, tmin);
//                } else {
//                    lastCompletionTime = Math.max(lastCompletionTime, tmin);
//                }
//            }
//
//            // d) Statistiche di nodo per ogni replica
//            for (int i = 0; i < NODES; i++) {
//                SimpleMultiServerNode n = localNodes.get(i);
//                Area area = n.getAreaObject();
//                MsqSum[] sums = n.getMsqSums();
//                boolean multiSrv = true;
//                nodeStats[i].saveStats(area, sums, lastArrivalTime, lastCompletionTime, multiSrv);
//            }
//        }
//
//        // 2) Dopo tutte le repliche, estrai e stampi le medie globali
//        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
//        for (int i = 0; i < NODES; i++) {
//            System.out.printf("Node %d: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, E[NS]=%.4f, ρ=%.4f, λ=%.4f%n",
//                    i,
//                    ETs[i],
//                    ETq[i],
//                    ES[i],
//                    ENs[i],
//                    ENq[i],
//                    ENS[i],
//                    rho[i],
//                    lambda[i]
//            );
//        }
//
//        System.out.println("=== INTERVALLI DI CONFIDENZA ===");
//        for (int i = 0; i < NODES; i++) {
//            ConfidenceInterval ci = new ConfidenceInterval(
//                    nodeStats[i].meanResponseTimeList,
//                    nodeStats[i].meanQueueTimeList,
//                    nodeStats[i].meanServiceTimeList,
//                    nodeStats[i].meanSystemPopulationList,
//                    nodeStats[i].meanQueuePopulationList,
//                    nodeStats[i].meanUtilizationList,
//                    nodeStats[i].lambdaList
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
//
//
//        }
//
//        // ─── INTEGRAZIONE CON COMPARISON E VERIFICATION ───────────────────────────
//        // 1) Analitici
//        List<AnalyticalResult> analyticalResults =
//                AnalyticalComputation.computeAnalyticalResults("FINITE_SIMULATION");
//
//        // 2) Statistiche medie di simulazione
//        List<MeanStatistics> meanStatsList = new ArrayList<>();
//        for (int i = 0; i < NODES; i++) {
//            meanStatsList.add(nodeStats[i].getMeanStatistics());
//        }
//
//        // 3) Confronto analitico vs simulazione
//        List<Comparison.ComparisonResult> comparisonResults =
//                Comparison.compareResults("FINITE_SIMULATION", analyticalResults, meanStatsList);
//
//        // 4) Costruisco gli intervalli di confidenza
//        List<ConfidenceInterval> ciList = new ArrayList<>();
//        for (int i = 0; i < NODES; i++) {
//            ciList.add(new ConfidenceInterval(
//                    nodeStats[i].meanResponseTimeList,
//                    nodeStats[i].meanQueueTimeList,
//                    nodeStats[i].meanServiceTimeList,
//                    nodeStats[i].meanSystemPopulationList,
//                    nodeStats[i].meanQueuePopulationList,
//                    nodeStats[i].meanUtilizationList,
//                    nodeStats[i].lambdaList
//            ));
//        }
//
//        // 5) Verifico gli scarti nei CI
//        Verification.verifyConfidenceIntervals(
//                "FINITE_SIMULATION",
//                meanStatsList,
//                comparisonResults,
//                ciList
//        );
//        // ────────────────────────────────────────────────────────────────────────────
//
//    } // fine runFiniteSimulation

    @Override
    public void runFiniteSimulation() {
        final double STOP = this.STOP;
        String baseDir = "csvFilesIntervals";
        Rngs rngs = new Rngs();

        double[] ETs = new double[NODES];
        double[] ETq = new double[NODES];
        double[] ES = new double[NODES];
        double[] ENs = new double[NODES];
        double[] ENq = new double[NODES];
        double[] ENS = new double[NODES];
        double[] lambda = new double[NODES];
        double[] rho = new double[NODES];

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

            for (int i = 0; i < NODES; i++) {
                SimpleMultiServerNode n = localNodes.get(i);
                Area area = n.getAreaObject();
                MsqSum[] sums = n.getMsqSums();
                nodeStats[i].saveStats(area, sums, lastArrivalTime, lastCompletionTime, true);
            }
        }

        // === STATISTICHE MEDIE CUMULATIVE (USANDO MeanStatistics) ===
        List<MeanStatistics> meanStatsList = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            meanStatsList.add(nodeStats[i].getMeanStatistics());
        }

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
                    nodeStats[i].meanResponseTimeList,
                    nodeStats[i].meanQueueTimeList,
                    nodeStats[i].meanServiceTimeList,
                    nodeStats[i].meanSystemPopulationList,
                    nodeStats[i].meanQueuePopulationList,
                    nodeStats[i].meanUtilizationList,
                    nodeStats[i].lambdaList
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



    @Override
    public void runInfiniteSimulation() {
        // 1) Parametri di batch‐means
        final int k = NUMBATCHES;  // es. 64 batch
        final int b = BATCHSIZE;   // es. 256 completamenti per batch

        // 2) Preparo le liste per le batch‐means PER NODO
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

        // 3) Snapshots iniziali (per il delta)
        double[] areaNodeSnap   = new double[NODES];
        double[] areaQueueSnap  = new double[NODES];
        double[] areaServSnap   = new double[NODES];
        long[]   jobsServedSnap = new long[NODES];

        double clock          = 0.0;
        double startTimeBatch = 0.0;
        double endTimeBatch   = 0.0;
        double lastArrTime    = 0.0;
        double lastCompTime   = 0.0;

        int batchNumber     = 0;
        int jobObservations = 0;

        // 4) Inizializzo RNG e nodi
        Rngs rngs = new Rngs();
        rngs.plantSeeds(SEED);
        List<SimpleMultiServerNode> nodes = init(rngs);

        // 5) Primo snapshot (t = 0)
        for (int i = 0; i < NODES; i++) {
            Area a      = nodes.get(i).getAreaObject();
            MsqSum[] ss = nodes.get(i).getMsqSums();
            areaNodeSnap[i]   = a.getNodeArea();
            areaQueueSnap[i]  = a.getQueueArea();
            areaServSnap[i]   = a.getServiceArea();
            jobsServedSnap[i] = Arrays.stream(ss).mapToLong(s -> s.served).sum();
        }

        // 6) Ciclo evento‐driven fino a k batch
        while (batchNumber < k) {
            // trova il prossimo evento
            double tmin = Double.POSITIVE_INFINITY;
            int idxMin  = -1;
            for (int i = 0; i < NODES; i++) {
                double t = nodes.get(i).peekNextEventTime();
                if (t < tmin) {
                    tmin   = t;
                    idxMin = i;
                }
            }

            // integra e processa
            for (SimpleMultiServerNode n : nodes) n.integrateTo(tmin);
            clock = tmin;
            nodes.get(idxMin).processNextEvent(tmin);

            // aggiorno arrival/completion
            if (idxMin == 0) {
                lastArrTime = Math.max(lastArrTime, tmin);
            } else {
                lastCompTime   = Math.max(lastCompTime, tmin);
                jobObservations++;
            }

            // fine batch?
            if (jobObservations == b) {
                endTimeBatch = clock;

                // calcolo le batch‐means del batch corrente per ciascun nodo
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

                // reset statistice interne e nuovo snapshot zero
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

        // 7) Costruisco MeanStatistics usando il costruttore che prende i valori medi
        List<MeanStatistics> meanStatsList = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) {
            // calcolo medie dalle tue liste di batch‐means
            double mrt = computeMean(respTimeMeansByNode.get(i));
            double mqt = computeMean(queueTimeMeansByNode.get(i));
            double mst = computeMean(serviceTimeMeansByNode.get(i));
            double mns = computeMean(systemPopMeansByNode.get(i));
            double mnq = computeMean(queuePopMeansByNode.get(i));
            double mu  = computeMean(utilizationByNode.get(i));
            double ml  = computeMean(lambdaByNode.get(i));

            // nome del centro, ad esempio "Center0", "Center1", ...
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

        // 8) Confronto analitico vs simulazione
        List<AnalyticalResult> analyticalResults =
                AnalyticalComputation.computeAnalyticalResults("INFINITE_SIMULATION");
        List<Comparison.ComparisonResult> comparisonResults =
                Comparison.compareResults("INFINITE_SIMULATION", analyticalResults, meanStatsList);

        // 9) Calcolo gli intervalli di confidenza per ciascun nodo
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

        // 10) Verifica dei CI
        Verification.verifyConfidenceIntervals(
                "INFINITE_SIMULATION",
                meanStatsList,
                comparisonResults,
                ciList
        );

        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        for (int i = 0; i < NODES; i++) {
            System.out.printf("Node %d: E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[N]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
                    i,
                    computeMean(respTimeMeansByNode.get(i)),
                    computeMean(queueTimeMeansByNode.get(i)),
                    computeMean(serviceTimeMeansByNode.get(i)), computeMean(systemPopMeansByNode.get(i)),
                    computeMean(queuePopMeansByNode.get(i)),
                    computeMean(utilizationByNode.get(i)),
                    computeMean(lambdaByNode.get(i))
            );
        }

        System.out.println("=== INTERVALLI DI CONFIDENZA ===");
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

            System.out.printf("Node %d: ±CI E[Ts]=%.4f, E[Tq]=%.4f, E[S]=%.4f, E[Ns]=%.4f, E[Nq]=%.4f, ρ=%.4f, λ=%.4f%n",
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