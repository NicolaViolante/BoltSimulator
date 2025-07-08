package org.uniroma2.PMCSN.utils;



import org.uniroma2.PMCSN.configuration.ConfigurationManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class AnalyticalComputation {
    private static final ConfigurationManager config = new ConfigurationManager();

    public static class AnalyticalResult {
        public String name;
        public double lambda;
        public double rho;
        public double Etq;
        public double Enq;
        public double Ets;
        public double Ens;
        public double Es;

        public AnalyticalResult(double lambda, double rho, double Etq, double Enq, double Ets, double Ens, String name, double Es) {
            this.lambda = lambda;
            this.rho = rho;
            this.Etq = Etq;
            this.Enq = Enq;
            this.Ets = Ets;
            this.Ens = Ens;
            this.name = name;
            this.Es = Es;
        }
    }

    public static double factorial(int n) {
        double fact = 1;
        for (int i = 1; i <= n; i++) {
            fact *= i;
        }
        return fact;
    }

    // Method to calculate p(0)
    public static double calculateP0(int m, double rho) {
        double sum = 0.0;
        for (int i = 0; i < m; i++) {
            sum += Math.pow(m * rho, i) / factorial(i);
        }
        sum += (Math.pow(m * rho, m) / (factorial(m) * (1 - rho)));
        return 1 / sum;
    }

    // Method to calculate Pq
    public static double calculatePq(int m, double rho, double p0) {
        double numerator = Math.pow(m * rho, m);
        double denominator = factorial(m) * (1 - rho);
        return (numerator / denominator) * p0;
    }



    public static AnalyticalResult multiServer(String centerName, double lambda, double Esi, int numServers) {
        double Es = Esi / numServers;
        double rho = lambda * Es;
        double Etq, Enq, Ets, Ens;
        if (rho >= 1) {
            Etq = Double.POSITIVE_INFINITY;
            Enq = Double.POSITIVE_INFINITY;
            Ets = Double.POSITIVE_INFINITY;
            Ens = Double.POSITIVE_INFINITY;
        } else {
            double p0 = calculateP0(numServers, rho);
            double Pq = calculatePq(numServers, rho, p0);
            Etq = (Pq * Es) / (1 - rho);
            Enq = Etq * lambda;
            Ets = Etq + Esi;
            Ens = Ets * lambda;
        }
        return new AnalyticalResult(lambda, rho, Etq, Enq, Ets, Ens, centerName, Esi);
    }

    public static List<AnalyticalResult> computeAnalyticalResults(String simulationType) {
        List<AnalyticalResult> analyticalResults = new ArrayList<>();
        double pCentroSmall = config.getDouble("probabilities", "small");
        double pCentroMedium = config.getDouble("probabilities", "medium");
        double pCentroLarge = config.getDouble("probabilities", "large");

        double lambda = config.getDouble("simulation", "lambda") * (1-config.getDouble("probabilities", "exit"));

        // Centro macchine small
        analyticalResults.add(multiServer(
                "0",
                lambda*pCentroSmall,
                config.getDouble("simulation", "esi"),
                config.getInt("simulation", "serverSmall")));

        // Centro macchine medium
        analyticalResults.add(multiServer(
                "1",
                lambda*pCentroMedium,
                config.getDouble("simulation", "esi"),
                config.getInt("simulation", "serverMedium")));

        // Centro macchine large
        analyticalResults.add(multiServer(
                "2",
                lambda*pCentroLarge,
                config.getDouble("simulation", "esi"),
                config.getInt("simulation", "serverLarge")));

        writeAnalyticalResults(simulationType, analyticalResults);

        return(analyticalResults);
    }


    public static void writeAnalyticalResults(String simulationType, List<AnalyticalResult> results){
        File file = new File("csvFiles/"+simulationType+"/analyticalResults/" );
        if (!file.exists()) {
            file.mkdirs();
        }

        file = new File("csvFiles/"+simulationType+"/analyticalResults/analyticalResults.csv");
        try(FileWriter fileWriter = new FileWriter(file)) {

            String DELIMITER = "\n";
            String COMMA = ",";


            fileWriter.append("Center, E[Ts], E[Tq], E[s], E[Ns], E[Nq], ρ, λ").append(DELIMITER);
            for (AnalyticalResult result : results){

                fileWriter.append(result.name).append(COMMA)
                        .append(String.valueOf(result.Ets)).append(COMMA)
                        .append(String.valueOf(result.Etq)).append(COMMA)
                        .append(String.valueOf(result.Es)).append(COMMA)
                        .append(String.valueOf(result.Ens)).append(COMMA)
                        .append(String.valueOf(result.Enq)).append(COMMA)
                        .append(String.valueOf(result.rho)).append(COMMA)
                        .append(String.valueOf(result.lambda)).append(DELIMITER);
            }

            fileWriter.flush();
        } catch (IOException e) {
            //ignore
        }
    }




    public static void main(String[] args) {
        String simulationType = "ANALYTICAL";
        //writeAnalyticalResults(simulationType, computeAnalyticalResults(simulationType));
        writeAnalyticalResults(simulationType, computeAnalyticalResults(simulationType));
    }

}