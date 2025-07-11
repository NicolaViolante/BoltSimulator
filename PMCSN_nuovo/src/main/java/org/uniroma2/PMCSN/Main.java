package org.uniroma2.PMCSN;

import org.uniroma2.PMCSN.controller.RideSharingSystem;
import org.uniroma2.PMCSN.controller.SimpleSystem;
import org.uniroma2.PMCSN.controller.Sistema;
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

        // 6) Sovrascrivi simulation.replicas e riscrivi il file
//        props.setProperty("simulation.replicas", Long.toString(computedReplicas));
//        try (OutputStream out = new FileOutputStream(configFile)) {
//            props.store(out, "Aggiornato simulation.replicas in base al CI (overall)");
//        }
//        System.out.println(">> simulation.replicas impostato a: " + computedReplicas);

        // 7) Scelta del tipo di sistema
        System.out.println("---- Choose type of system ----");
        System.out.println("0 - Simple ");
        System.out.println("1 - Ride Sharing ");
        int systemType = getChoice();

        Sistema system = switch (systemType) {
            case 0 -> new SimpleSystem();
            case 1 -> new RideSharingSystem();
            default -> throw new IllegalStateException("Invalid system choice: " + systemType);
        };

        // 8) Scelta del tipo di simulazione
        System.out.println("---- Choose type of simulation ----");
        System.out.println("0 - Finite horizon simulation ");
        System.out.println("1 - Infinite horizon simulation ");
        int simulationType = getChoice();

        switch (simulationType) {
            case 0 -> system.runFiniteSimulation();
            case 1 -> system.runInfiniteSimulation();
            default -> System.out.println("Invalid simulation choice!");
        }
    }

    private static int getChoice() {
        Scanner input = new Scanner(System.in);
        int choice;
        while (true) {
            System.out.print("Please, make a choice: ");
            choice = input.nextInt();
            if (choice == 0 || choice == 1) return choice;
            System.out.println("Not valid choice!");
        }
    }
}