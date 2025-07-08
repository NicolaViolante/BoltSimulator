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

import static org.uniroma2.PMCSN.model.MeanStatistics.computeMean;


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

        // Liste per replica
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
                    for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
                        Node n = localNodes.get(i);
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

            // Popoliamo le liste con i valori calcolati a fine replica per ogni nodo
            for (int i = 0; i < SIMPLE_NODES+RIDE_NODES; i++) {
                Area a = localNodes.get(i).getAreaObject();
                MsqSum[] sums = localNodes.get(i).getMsqSums();

                long jobsNow = Arrays.stream(sums).mapToLong(s -> s.served).sum();
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
            for (Node n : localNodes) {
                n.resetStatistics();
                // eventualmente resettare anche aree se necessario
            }
        }

        // 7) Costruisco MeanStatistics usando il costruttore che prende i valori medi
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

        // === STATISTICHE MEDIE CUMULATIVE ===
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

        // === COMPARISON E VERIFICA ===
        List<AnalyticalComputation.AnalyticalResult> analyticalResults =
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
        for (int i = SIMPLE_NODES; i < SIMPLE_NODES+RIDE_NODES; i++) {
            Node n = new RideSharingMultiServerNode(this, rng,centriTradizionali);
            localNodes.add(n);
        }
        return localNodes;
    }


    @Override
    public void runInfiniteSimulation() {

    }
}