package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.controller.*;
import org.uniroma2.PMCSN.utils.SampleSizeEstimator;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Properties;
import java.util.Scanner;

public class Main {
    private static final String CONFIG_NAME = "config.properties";

    public static void main(String[] args) throws IOException, URISyntaxException {
        // 1) Trova config.properties sotto resources
        ClassLoader loader = Main.class.getClassLoader();
        URL resourceUrl = loader.getResource(CONFIG_NAME);
        if (resourceUrl == null) {
            System.err.println("Non ho trovato " + CONFIG_NAME + " in resources/");
            return;
        }
        File configFile = new File(resourceUrl.toURI());

        // 2) Carica le proprietà
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(configFile)) {
            props.load(in);
        }

        // 3) Leggi i parametri per l'intervallo di confidenza
        double confidenceLevel = Double.parseDouble(
                props.getProperty("general.levelOfConfidence", "0.95")
        );
        double wProportion = Double.parseDouble(
                props.getProperty("simulation.relativeHalfWidth", "0.1")
        );

        // 4) STIME PRELIMINARI delle dev. standard, ricavabili da un pilot run
        double preStdTs     = Double.parseDouble(props.getProperty("pilot.std.Ts",     "1.0"));
        double preStdTq     = Double.parseDouble(props.getProperty("pilot.std.Tq",     "1.0"));
        double preStds      = Double.parseDouble(props.getProperty("pilot.std.s",      "1.0"));
        double preStdNs     = Double.parseDouble(props.getProperty("pilot.std.Ns",     "1.0"));
        double preStdNq     = Double.parseDouble(props.getProperty("pilot.std.Nq",     "1.0"));
        double preStdRho    = Double.parseDouble(props.getProperty("pilot.std.rho",    "1.0"));
        double preStdLambda = Double.parseDouble(props.getProperty("pilot.std.lambda", "1.0"));

        // 5) Calcola n overall che soddisfa tutte le stime
        SampleSizeEstimator estimator = new SampleSizeEstimator();
        estimator.estimateOverallSampleSize(
                wProportion,
                confidenceLevel,
                preStdTs, preStdTq, preStds,
                preStdNs, preStdNq, preStdRho, preStdLambda
        );

        Scanner input = new Scanner(System.in);
        try {
            // 7) Scelta del tipo di sistema
            System.out.println("---- Choose type of system ----");
            System.out.println("0 - Simple ");
            System.out.println("1 - Ride Sharing ");
            System.out.println("2 - Simple Daily ");
            System.out.println("3 - Ride Sharing Daily ");
            int systemType = getChoiceInRange(3, input);

            Sistema system = switch (systemType) {
                case 0 -> new SimpleSystem();
                case 1 -> new RideSharingSystem();
                case 2 -> new SimpleDailySystem();
                case 3 -> new RideSharingDailySystem();
                default -> throw new IllegalStateException("Invalid system choice: " + systemType);
            };

            // Se l'utente ha scelto un sistema "Daily" (2 o 3) esegui subito l'orizzonte finito
            if (systemType == 2 || systemType == 3) {
                System.out.println("Starting finite-horizon (24h) simulation for daily system...");
                system.runFiniteSimulation();
                return; // termina il main dopo la simulazione
            }

            // Per i sistemi non-daily chiediamo all'utente quale tipo di simulazione eseguire
            System.out.println("---- Choose type of simulation ----");
            System.out.println("0 - Finite horizon simulation ");
            System.out.println("1 - Infinite horizon simulation ");
            int simulationType = getChoiceInRange(1, input);

            switch (simulationType) {
                case 0 -> system.runFiniteSimulation();
                case 1 -> system.runInfiniteSimulation();
                default -> System.out.println("Invalid simulation choice!");
            }
        } finally {
            input.close();
        }
    }

    /**
     * Legge un intero da Scanner e lo valida che sia compreso nell'intervallo [min,max].
     * Ripete il prompt fino a inserimento valido; gestisce ingresso non numerico.
     */
    private static int getChoiceInRange(int max, Scanner input) {
        while (true) {
            System.out.printf("Please, make a choice (%d-%d): ", 0, max);
            String line = input.nextLine().trim();
            try {
                int val = Integer.parseInt(line);
                if (val >= 0 && val <= max) return val;
            } catch (NumberFormatException ignored) { }
            System.out.println("Not valid choice! Enter an integer between " + 0 + " and " + max + ".");
        }
    }
}
