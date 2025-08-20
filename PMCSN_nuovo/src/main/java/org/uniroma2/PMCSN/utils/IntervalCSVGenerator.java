package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.centers.SimpleMultiServerNode;
import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.model.Area;
import org.uniroma2.PMCSN.model.MsqSum;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Scrive riepiloghi a intervalli per ogni nodo.
 * I file vengono creati in:
 *    {baseDir}/{finite|infinite}_interval_center{idx}.csv
 */
public class IntervalCSVGenerator {

    /**
     * Crea la directory se non esiste.
     */


    private static void ensureDirectory(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Metodo per creare il file e restituire se è stato creato adesso.
     */
    public static boolean ensureFile(String path) {
        File f = new File(path);
        if (!f.exists()) {
            try {
                f.getParentFile().mkdirs();
                f.createNewFile();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Scrive su CSV i dati a intervallo di un singolo nodo (finite o infinite).
     *
     * @param isFinite    true=finite, false=infinite
     * @param seed        indice di replica (o batch)
     * @param centerIndex indice del nodo
     * @param time        orario di reporting
     * @param eTs         E[Ts]
     * @param eNs         E[Ns]
     * @param eTq         E[Tq]
     * @param eNq         E[Nq]
     * @param eS          E[s]
     * @param eNS         E[NS]
     * @param rho         ρ
     * @param baseDir     cartella base (es. "csvFilesIntervals")
     */
    public static void writeIntervalData(
            boolean isFinite,
            long seed,
            int centerIndex,
            double time,
            double eTs,
            double eNs,
            double eTq,
            double eNq,
            double eS,
            double eNS,
            double rho,
            String baseDir
    ) {
        String suffix = isFinite ? "finite_interval" : "infinite_interval";
        String dir = baseDir + "/" + suffix;
        ensureDirectory(dir);

        String fileName = dir + "/center" + centerIndex + ".csv";
        boolean newFile = ensureFile(fileName);

        try (FileWriter fw = new FileWriter(fileName, true)) {
            if (newFile) {
                // header
                fw.append("Seed,Center,Time,ETs,ENs,ETq,ENq,ES,ENS,Rho\n");
            }
            fw.append(String.join(",",
                    String.valueOf(seed),
                    String.valueOf(centerIndex),
                    String.format(Locale.US, "%.2f", time),
                    String.format(Locale.US, "%.5f", eTs),
                    String.format(Locale.US, "%.5f", eNs),
                    String.format(Locale.US, "%.5f", eTq),
                    String.format(Locale.US, "%.5f", eNq),
                    String.format(Locale.US, "%.5f", eS),
                    String.format(Locale.US, "%.5f", eNS),
                    String.format(Locale.US, "%.5f", rho)
            ));
            fw.append("\n");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public static void writeGlobalInterval(
            long rep,
            double reportTime,
            List<SimpleMultiServerNode> localNodes,
            String baseDir
    ) {

        ConfigurationManager config = new ConfigurationManager();

        double cumArea = 0.0;
        double cumAreaQ = 0.0;
        double cumServiceArea = 0.0;
        double sumRho = 0.0;
        long cumJobs = 0L;

        for (SimpleMultiServerNode n : localNodes) {
            Area a = n.getAreaObject();
            MsqSum[] sums = n.getMsqSums();
            cumArea += a.getNodeArea();
            cumAreaQ += a.getQueueArea();
            cumServiceArea += a.getServiceArea();
            cumJobs += Arrays.stream(sums).mapToLong(s -> s.served).sum();
            sumRho += n.getUtilization();
        }

        double ETs = cumJobs > 0 ? cumArea / cumJobs : 0.0;
        double ENs = cumArea / reportTime;
        double ETq = cumJobs > 0 ? cumAreaQ / cumJobs : 0.0;
        double ENq = cumAreaQ / reportTime;
        double ES = cumJobs > 0 ? cumServiceArea / cumJobs : 0.0;
        double ENS = cumServiceArea / reportTime;
        double rho = sumRho / config.getInt("simulation", "nodes");;

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

}
