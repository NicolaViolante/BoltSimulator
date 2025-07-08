package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.centers.Node;
import org.uniroma2.PMCSN.centers.RideSharingMultiServerNode;
import org.uniroma2.PMCSN.centers.RideSharingMultiServerNodeSimple;
import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.AnalyticalComputation;
import org.uniroma2.PMCSN.utils.Comparison;
import org.uniroma2.PMCSN.utils.IntervalCSVGenerator;
import org.uniroma2.PMCSN.utils.Verification;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


public class RideSharingSystem implements Sistema {

    /*Case Finite*/
    private final int SIMPLE_NODES;
    private final int RIDE_NODES;
    private final int REPLICAS;
    private final double STOP;
    private final double REPORTINTERVAL;

    /*Case Infinite*/
    private final int BATCHSIZE;
    private final int NUMBATCHES;
    private final int SEED;


    // Ora teniamo le statistiche per ogni nodo
    private final BasicStatistics[] nodeStats;
    private BatchStatistics batchStatistics;

    public RideSharingSystem() {

        // legge tutto da config.properties
        ConfigurationManager config = new ConfigurationManager();
        this.SIMPLE_NODES = config.getInt("simulation", "nodes");
        this.RIDE_NODES = config.getInt("simulation", "rideNodes");
        this.REPLICAS = config.getInt("simulation", "replicas");
        this.STOP = config.getDouble("simulation", "stop");
        this.REPORTINTERVAL = new ConfigurationManager()
                .getDouble("simulation", "reportInterval");
        this.BATCHSIZE = config.getInt("simulation", "batchSize");
        this.NUMBATCHES = config.getInt("simulation", "batchSize");
        this.SEED = config.getInt("simulation", "seed");

        // 2) inizializza BasicStatistics per ogni nodo
        nodeStats = new BasicStatistics[SIMPLE_NODES+RIDE_NODES];
        for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
            nodeStats[i] = new BasicStatistics("Node" + i);
        }
    }

    @Override
    public void runFiniteSimulation() {

        final double STOP = this.STOP;
        String baseDir = "csvFilesIntervals";
        Rngs rngs = new Rngs();

        double[] ETs = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ETq = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ES = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENs = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENq = new double[SIMPLE_NODES+RIDE_NODES];
        double[] ENS = new double[SIMPLE_NODES+RIDE_NODES];
        double[] lambda = new double[SIMPLE_NODES+RIDE_NODES];
        double[] rho = new double[SIMPLE_NODES+RIDE_NODES];

        System.out.println("=== Finite Simulation ===");

        for (int rep = 1; rep <= REPLICAS; rep++) {
            rngs.plantSeeds(rep);
            List<Node> localNodes = init(rngs);
            double nextReportTime = REPORTINTERVAL;
            double lastArrivalTime = 0.0;
            double lastCompletionTime = 0.0;

            while (true) {
                double tmin = Double.POSITIVE_INFINITY;
                int idxMin = -1;
                for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
                    double t = localNodes.get(i).peekNextEventTime();
                    if (t < tmin) {
                        tmin = t;
                        idxMin = i;
                    }
                }

                if (nextReportTime <= tmin && nextReportTime <= STOP) {
                    for (int i = 0; i < SIMPLE_NODES; i++) {
                        RideSharingMultiServerNodeSimple n = (RideSharingMultiServerNodeSimple) localNodes.get(i);

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

                    for (int i = SIMPLE_NODES; i < SIMPLE_NODES+RIDE_NODES; i++) {
                        RideSharingMultiServerNode n = (RideSharingMultiServerNode) localNodes.get(i);
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

                for (Node n : localNodes) {
                    n.integrateTo(tmin);
                }

                localNodes.get(idxMin).processNextEvent(tmin);

                if (idxMin == 0) {
                    lastArrivalTime = Math.max(lastArrivalTime, tmin);
                } else {
                    lastCompletionTime = Math.max(lastCompletionTime, tmin);
                }
            }

            for (int i = 0; i < SIMPLE_NODES; i++) {
                RideSharingMultiServerNodeSimple n = (RideSharingMultiServerNodeSimple) localNodes.get(i);
                Area area = n.getAreaObject();
                MsqSum[] sums = n.getMsqSums();
                nodeStats[i].saveStats(area, sums, lastArrivalTime, lastCompletionTime, true);
            }

//            for (int i = SIMPLE_NODES; i < SIMPLE_NODES+RIDE_NODES; i++) {
//                RideSharingMultiServerNode n = (RideSharingMultiServerNode) localNodes.get(i);
//                Area area = n.getAreaObject();
//                MsqSum[] sums = n.getMsqSums();
//                nodeStats[i].saveStats(area, sums, lastArrivalTime, lastCompletionTime, true);
//            }

        }

        // === STATISTICHE MEDIE CUMULATIVE (USANDO MeanStatistics) ===
        List<MeanStatistics> meanStatsListSimple = new ArrayList<>();
        for (int i = 0; i < SIMPLE_NODES; i++) { //aggiungi rn
            meanStatsListSimple.add(nodeStats[i].getMeanStatistics());
        }

        // === STATISTICHE MEDIE CUMULATIVE (USANDO MeanStatistics) ===
//        List<MeanStatistics> meanStatsListRide = new ArrayList<>();
//        for (int i = SIMPLE_NODES; i < SIMPLE_NODES + RIDE_NODES; i++) { //aggiungi rn
//            meanStatsListRide.add(nodeStats[i].getMeanStatistics());
//        }

        System.out.println("=== STATISTICHE MEDIE CUMULATIVE ===");
        for (int i = 0; i < SIMPLE_NODES; i++) {//aggiungi rn
            MeanStatistics ms = meanStatsListSimple.get(i);
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
        for (int i = 0; i < SIMPLE_NODES; i++) {//aggiungi rn
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
        List<AnalyticalComputation.AnalyticalResult> analyticalResults =
                AnalyticalComputation.computeAnalyticalResults("FINITE_SIMULATION");

        List<Comparison.ComparisonResult> comparisonResults =
                Comparison.compareResults("FINITE_SIMULATION", analyticalResults, meanStatsListSimple);

        Verification.verifyConfidenceIntervals(
                "FINITE_SIMULATION",
                meanStatsListSimple,
                comparisonResults,
                ciList
        );
    }

    // utilitario per sommare tutti i served in un array di MsqSum
    private long sumsTotalServed(MsqSum[] sums) {
        long tot = 0;
        for (MsqSum s : sums) tot += s.served;
        return tot;
    }

    private void writeGlobalInterval(
            int rep,
            double reportTime,
            List<Node> localNodes,
            String baseDir
    ) {
        double cumArea = 0.0;
        double cumAreaQ = 0.0;
        double cumServiceArea = 0.0;
        double sumRho = 0.0;
        long cumJobs = 0L;

        for (Node n : localNodes) {
            Area a = n.getAreaObject();
            MsqSum[] sums = n.getMsqSums();
            cumArea += a.getNodeArea();
            cumAreaQ += a.getQueueArea();
            cumServiceArea += a.getServiceArea();
            cumJobs += sumsTotalServed(sums);
            sumRho += n.getUtilization();
        }

        double ETs = cumJobs > 0 ? cumArea / cumJobs : 0.0;
        double ENs = cumArea / reportTime;
        double ETq = cumJobs > 0 ? cumAreaQ / cumJobs : 0.0;
        double ENq = cumAreaQ / reportTime;
        double ES = cumJobs > 0 ? cumServiceArea / cumJobs : 0.0;
        double ENS = cumServiceArea / reportTime;
        double rho = sumRho / (SIMPLE_NODES + RIDE_NODES);//aggiungi rn

        // prepara file global.csv in baseDir/finite_interval
        String dir = baseDir + "/finite_interval";
        String file = dir + "/global.csv";
        boolean isNew = IntervalCSVGenerator.ensureFile(file);
        try (FileWriter fw = new FileWriter(file, true)) {
            if (isNew) {
                fw.append("Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho\n");
            }
            fw.append(String.join(",",
                    String.valueOf(rep),
                    "-1",
                    String.format(Locale.US, "%.2f", reportTime),
                    String.format(Locale.US, "%.5f", ETs),
                    String.format(Locale.US, "%.5f", ENs),
                    String.format(Locale.US, "%.5f", ETq),
                    String.format(Locale.US, "%.5f", ENq),
                    String.format(Locale.US, "%.5f", ES),
                    String.format(Locale.US, "%.5f", ENS),
                    String.format(Locale.US, "%.5f", rho)
            ));
            fw.append("\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private List<Node> init(Rngs rng) {
        List<Node> localNodes = new ArrayList<>();
        List<RideSharingMultiServerNodeSimple> centriTradizionali = new ArrayList<>();
        for (int i = 0; i < SIMPLE_NODES; i++) {
            RideSharingMultiServerNodeSimple n = new RideSharingMultiServerNodeSimple(this, i, rng);
            localNodes.add(n);
            centriTradizionali.add(n);
        }
        for (int i = 0; i < RIDE_NODES; i++) {
            Node n = new RideSharingMultiServerNode(this, rng,centriTradizionali);
            localNodes.add(n);
        }
        return localNodes;
    }


    @Override
    public void runInfiniteSimulation() {

    }
}