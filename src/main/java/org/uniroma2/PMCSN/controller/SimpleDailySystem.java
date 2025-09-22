package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.centers.SimpleMultiServerNodeDaily;
import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.AnalyticalComputation;
import org.uniroma2.PMCSN.utils.AnalyticalComputation.AnalyticalResult;
import org.uniroma2.PMCSN.utils.Comparison;
import org.uniroma2.PMCSN.utils.IntervalCSVGenerator;
import org.uniroma2.PMCSN.utils.Verification;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SimpleDailySystem implements Sistema {

    private final int NODES;
    private final int REPLICAS;
    private final double REPORTINTERVAL;
    private final long SEED;

    ConfigurationManager config = new ConfigurationManager();

    public SimpleDailySystem() {
        this.NODES = config.getInt("simulation", "nodes");
        this.REPLICAS = config.getInt("simulation", "replicas");
        this.REPORTINTERVAL = new ConfigurationManager().getDouble("simulation", "reportInterval");
        this.SEED = (long) config.getDouble("simulation", "seed");
    }

    @Override
    public void runFiniteSimulation() {
        final double STOP = 1440.0;
        String baseDir = "csvFilesIntervalsDailySimpleSystem";

        Rngs rngs = new Rngs();

        // per replica conteggio jobs processati (cumulativo alla fine di ogni replica)
        List<List<Long>> jobsProcessedByNode = new ArrayList<>(NODES);
        for (int i = 0; i < NODES; i++) jobsProcessedByNode.add(new ArrayList<>());

        // Numero di report steps (incluso t=0)
        int numReports = (int) Math.ceil(STOP / REPORTINTERVAL) + 1;

        // Strutture per raccogliere per-interval (reportIndex) i valori di tutte le repliche:
        List<List<List<Double>>> respTimeByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> queueTimeByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> serviceTimeByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> sysPopByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> queuePopByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> utilByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> lambdaByNodeTime = new ArrayList<>(NODES);
        List<List<List<Double>>> servicePopByNodeTime = new ArrayList<>(NODES); // ENS per-interval

        for (int i = 0; i < NODES; i++) {
            respTimeByNodeTime.add(makeEmptyPerTime(numReports));
            queueTimeByNodeTime.add(makeEmptyPerTime(numReports));
            serviceTimeByNodeTime.add(makeEmptyPerTime(numReports));
            sysPopByNodeTime.add(makeEmptyPerTime(numReports));
            queuePopByNodeTime.add(makeEmptyPerTime(numReports));
            utilByNodeTime.add(makeEmptyPerTime(numReports));
            lambdaByNodeTime.add(makeEmptyPerTime(numReports));
            servicePopByNodeTime.add(makeEmptyPerTime(numReports));
        }

        // Nuove strutture per raccogliere i valori globali per-interval across-replicas
        List<List<Double>> globalRespTimeByTime = makeEmptyPerTime(numReports);   // ETs
        List<List<Double>> globalSysPopByTime  = makeEmptyPerTime(numReports);   // ENs
        List<List<Double>> globalQueueTimeByTime = makeEmptyPerTime(numReports); // ETq
        List<List<Double>> globalQueuePopByTime  = makeEmptyPerTime(numReports); // ENq
        List<List<Double>> globalServiceTimeByTime = makeEmptyPerTime(numReports);// ES
        List<List<Double>> globalServicePopByTime = makeEmptyPerTime(numReports); // ENS
        List<List<Double>> globalRhoByTime = makeEmptyPerTime(numReports);       // Rho

        // Liste aggregate su repliche per le statistiche finali cumulative (come prima)
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

        System.out.println("=== Finite Simulation (daily nodes, per-interval aggregation) ===");
        rngs.plantSeeds(SEED);

        // prepare per_interval global file path for reuse
        String dirPerInterval = baseDir + "/per_interval";
        String fileGlobal = dirPerInterval + "/global.csv";

        // ensure directory exists
        new File(dirPerInterval).mkdirs();
        // header string exactly as requested
        final String CSV_HEADER = "Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho";

        for (int rep = 1; rep <= REPLICAS; rep++) {
            long seedForRep = rngs.getSeed();
            double nextReportTime = REPORTINTERVAL;

            // inizializza nodi
            List<SimpleMultiServerNodeDaily> localNodes = init(rngs);

            // prev cumulativi usati per calcolare delta per intervallo
            double[] prevNodeArea = new double[NODES];
            double[] prevQueueArea = new double[NODES];
            double[] prevServiceArea = new double[NODES];
            double[] prevActiveServerArea = new double[NODES]; // mantenuto ma non usato per rho (compatibilità)
            long[] prevServed = new long[NODES];

            // scrivo header/iniziali per ciascun nodo (t=0) — file per-replica/interval (righe iniziali con 0)
            for (int i = 0; i < NODES; i++) {
                String fileNode = dirPerInterval + "/node_" + i + ".csv";
                IntervalCSVGenerator.ensureFile(fileNode); // keep existing behavior if it also creates dirs
                String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                        seedForRep, i, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
                appendCsvLine(fileNode, CSV_HEADER, line);
            }

            // scrivo il globale iniziale (t=0) con Center = -1
            IntervalCSVGenerator.ensureFile(fileGlobal);
            String lineG0 = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                    seedForRep, -1, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
            appendCsvLine(fileGlobal, CSV_HEADER, lineG0);

            // main event loop per replica
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

                // reporting per-INTERVAL (delta rispetto a prev*)
                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    int reportIndex = (int) (nextReportTime / REPORTINTERVAL); // 1..numReports-1
                    double reportTime = nextReportTime; // time to write in CSV

                    // accumulate global deltas
                    double cumDeltaNodeArea = 0.0;
                    double cumDeltaQueueArea = 0.0;
                    double cumDeltaServiceArea = 0.0;
                    long cumDeltaServed = 0L;
                    double sumRho = 0.0;

                    for (int i = 0; i < NODES; i++) {
                        Area a = localNodes.get(i).getAreaObject();
                        MsqSum[] sums = localNodes.get(i).getMsqSums();

                        long servedNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
                        double nodeAreaNow = a.getNodeArea();
                        double queueAreaNow = a.getQueueArea();
                        double serviceAreaNow = a.getServiceArea();
                        double activeServerAreaNow = a.getActiveServerArea();

                        long deltaServed = servedNow - prevServed[i];
                        double deltaNodeArea = nodeAreaNow - prevNodeArea[i];
                        double deltaQueueArea = queueAreaNow - prevQueueArea[i];
                        double deltaServiceArea = serviceAreaNow - prevServiceArea[i];

                        // ETs, ETq, ES sull'intervallo (se no jobs nell'intervallo -> 0)
                        double ETsInterval = (deltaServed > 0) ? deltaNodeArea / (double) deltaServed : 0.0;
                        double ETqInterval = (deltaServed > 0) ? deltaQueueArea / (double) deltaServed : 0.0;
                        double ESInterval  = (deltaServed > 0) ? deltaServiceArea / (double) deltaServed : 0.0;

                        // ENs, ENq, ENS (medie sull'intervallo)
                        double ENInterval = deltaNodeArea / REPORTINTERVAL;
                        double ENqInterval = deltaQueueArea / REPORTINTERVAL;
                        double ENSInterval = deltaServiceArea / REPORTINTERVAL;
                        double lambdaInterval = deltaServed / REPORTINTERVAL;

                        // === ρ: CALCOLO IDENTICO A SimpleSystem ===
                        // numServers ricavato da MsqSum[] (come in SimpleSystem: sums.length - 1)
                        int numServers = Math.max(sums.length - 1, 1);
                        double rhoInterval = 0.0;
                        if (deltaServed > 0) {
                            // evita divisione per zero; se ESInterval==0 -> rhoInterval sarà 0 automaticamente
                            rhoInterval = (lambdaInterval * ESInterval) / (double) numServers;
                        } else {
                            rhoInterval = 0.0;
                        }

                        // append to per-node csv (one row per replica per interval) including Time and Center
                        String file = dirPerInterval + "/node_" + i + ".csv";
                        String line = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                                seedForRep, i, reportTime,
                                ETsInterval,
                                ENInterval,
                                ETqInterval,
                                ENqInterval,
                                ESInterval,
                                ENSInterval,
                                rhoInterval);
                        appendCsvLine(file, CSV_HEADER, line);

                        // add to the structures used to compute per-interval means across replicas
                        respTimeByNodeTime.get(i).get(reportIndex).add(ETsInterval);
                        queueTimeByNodeTime.get(i).get(reportIndex).add(ETqInterval);
                        serviceTimeByNodeTime.get(i).get(reportIndex).add(ESInterval);
                        sysPopByNodeTime.get(i).get(reportIndex).add(ENInterval);
                        queuePopByNodeTime.get(i).get(reportIndex).add(ENqInterval);
                        utilByNodeTime.get(i).get(reportIndex).add(rhoInterval);
                        lambdaByNodeTime.get(i).get(reportIndex).add(lambdaInterval);
                        servicePopByNodeTime.get(i).get(reportIndex).add(ENSInterval);

                        // update prev cumulativi per il prossimo intervallo
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
                    double globalETs = (cumDeltaServed > 0) ? cumDeltaNodeArea / (double) cumDeltaServed : 0.0;
                    double globalEN = cumDeltaNodeArea / REPORTINTERVAL;
                    double globalETq = (cumDeltaServed > 0) ? cumDeltaQueueArea / (double) cumDeltaServed : 0.0;
                    double globalENq = cumDeltaQueueArea / REPORTINTERVAL;
                    double globalES  = (cumDeltaServed > 0) ? cumDeltaServiceArea / (double) cumDeltaServed : 0.0;
                    double globalENS = cumDeltaServiceArea / REPORTINTERVAL;

                    // globalRho: uso la media aritmetica delle rho dei nodi (coerente con comportamento precedente)
                    double globalRho = (NODES > 0) ? (sumRho / (double) NODES) : 0.0;

                    String lineG = String.format(Locale.US, "%d,%d,%.2f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f",
                            seedForRep, -1, reportTime,
                            globalETs, globalEN, globalETq, globalENq, globalES, globalENS, globalRho);
                    appendCsvLine(fileGlobal, CSV_HEADER, lineG);

                    // Salva i valori globali di questa replica per l'intervallo, così da poter poi mediare sui seed
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

                // fine replica
                if (tmin > STOP) break;

                // integra tutti i nodi fino a tmin
                for (SimpleMultiServerNodeDaily n : localNodes) n.integrateTo(tmin);

                // processa evento minimo
                localNodes.get(idxMin).processNextEvent(tmin);
            } // end while replica

            // alla fine della replica: calcolo e salvo le metriche cumulative finali per il set di medie "replica-wise"
            for (int i = 0; i < NODES; i++) {
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
                    double ENSReplica = a.getServiceArea() / STOP;

                    double lambdaReplica = (double) jobsNow / STOP;
                    double rhoReplica = (lambdaReplica * ESReplica) / (double) numServers;

                    respTimeMeansByNode.get(i).add(ETsReplica);
                    queueTimeMeansByNode.get(i).add(ETqReplica);
                    serviceTimeMeansByNode.get(i).add(ESReplica);
                    systemPopMeansByNode.get(i).add(ENsReplica);
                    queuePopMeansByNode.get(i).add(ENqReplica);
                    utilizationByNode.get(i).add(rhoReplica);
                    lambdaByNode.get(i).add(lambdaReplica);
                } else {
                    respTimeMeansByNode.get(i).add(0.0);
                    queueTimeMeansByNode.get(i).add(0.0);
                    serviceTimeMeansByNode.get(i).add(0.0);
                    systemPopMeansByNode.get(i).add(0.0);
                    queuePopMeansByNode.get(i).add(0.0);
                    utilizationByNode.get(i).add(0.0);
                    lambdaByNode.get(i).add(0.0);
                }

                // reset delle statistiche interne per i nodi per la prossima replica
                localNodes.get(i).resetStatistics();
            }
        } // end replicas

        //=== Dopo tutte le repliche: calcolo le medie per ogni nodo e per ogni reportIndex e scrivo CSV per il plotting ===
        String dirMean = baseDir + "/interval_mean";
        new File(dirMean).mkdirs();

        for (int i = 0; i < NODES; i++) {
            String file = dirMean + "/node_" + i + "_interval_mean.csv";
            String header = "Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho";
            IntervalCSVGenerator.ensureFile(file);
            try {
                // write header only if file empty
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
                        reportTime,
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
                    reportTime,
                    meanETsG,
                    meanENsG,
                    meanETqG,
                    meanENqG,
                    meanESG,
                    meanENSG,
                    meanRhoG);
            appendCsvLine(fileG, CSV_HEADER, line);
        }

        // === (resto del codice: meanStatsList, CI, printing, Comparison & Verification) ===
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
            meanStatsList.add(new MeanStatistics(centerName, mrt, mst, mqt, ml, mns, mu, mnq));
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

        System.out.println("=== INTERVALLI DI CONFIDENZA (cumulative metrics) ===");
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

        System.out.println("=== NUMERO MEDIO DI JOB PROCESSATI ===");
        double totalAvgJobsProcessed = 0.0;
        for (int j = 0; j < NODES; j++) {
            List<Long> jobsList = jobsProcessedByNode.get(j);
            double avgJobs = jobsList.stream().mapToLong(Long::longValue).average().orElse(0.0);
            totalAvgJobsProcessed += Math.floor(avgJobs);
        }
        System.out.printf("Media totale jobs processati (approssimazione per difetto): %.0f%n", totalAvgJobsProcessed);

        List<AnalyticalResult> analyticalResults = AnalyticalComputation.computeAnalyticalResults("FINITE_SIMULATION");
        List<Comparison.ComparisonResult> comparisonResults = Comparison.compareResults("FINITE_SIMULATION", analyticalResults, meanStatsList);
        Verification.verifyConfidenceIntervals("FINITE_SIMULATION", meanStatsList, comparisonResults, ciList);
    } // end runFiniteSimulation

    @Override
    public void runInfiniteSimulation() {
        // not implemented here
    }

    // --- helper: crea struttura vuota per ogni reportIndex ---
    private static List<List<Double>> makeEmptyPerTime(int numReports) {
        List<List<Double>> perTime = new ArrayList<>(numReports);
        for (int r = 0; r < numReports; r++) perTime.add(new ArrayList<>());
        return perTime;
    }

    private List<SimpleMultiServerNodeDaily> init(Rngs rng) {
        List<SimpleMultiServerNodeDaily> localNodes = new ArrayList<>();
        for (int i = 0; i < NODES; i++) {
            DailyServerSelectorMultiType selector = new DailyServerSelectorMultiType(
                    config.getInt("simulation", "smallverylow"),
                    config.getInt("simulation", "mediumverylow"),
                    config.getInt("simulation", "largeverylow"),
                    config.getInt("simulation", "smalllow"),
                    config.getInt("simulation", "mediumlow"),
                    config.getInt("simulation", "largelow"),
                    config.getInt("simulation", "smallmedium"),
                    config.getInt("simulation", "mediummedium"),
                    config.getInt("simulation", "largemedium"),
                    config.getInt("simulation", "smallhigh"),
                    config.getInt("simulation", "mediumhigh"),
                    config.getInt("simulation", "largehigh"),
                    config.getInt("simulation", "smallveryhigh"),
                    config.getInt("simulation", "mediumveryhigh"),
                    config.getInt("simulation", "largeveryhigh")
            );
            SimpleMultiServerNodeDaily n = new SimpleMultiServerNodeDaily(this, i, rng, selector);
            localNodes.add(n);
        }
        return localNodes;
    }

    // helper locali
    private double computeMean(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
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

}
