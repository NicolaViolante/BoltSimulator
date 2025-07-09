package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.SimpleSystem;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;

import static org.uniroma2.PMCSN.libs.Distributions.*;

public class Distrs {

    private final ConfigurationManager config = new ConfigurationManager();

    public double getNextArrivalTimeSimpleCenter(Rngs r, Sistema system, int centerIndex, double sarrival) {
        r.selectStream(0);
        double lambda = config.getDouble("simulation","lambda");

        if(system instanceof SimpleSystem) {
            switch (centerIndex) {
                case 0 -> lambda *= config.getDouble("simulation","p_small");
                case 1 -> lambda *= config.getDouble("simulation","p_medium");
                case 2 -> lambda *= config.getDouble("simulation","p_large");
                default -> System.out.println("Centro inesistente!");
            }
        }else{
            switch (centerIndex) {
                case 0 -> lambda *= config.getDouble("simulation","p_small") * 0.7;
                case 1 -> lambda *= config.getDouble("simulation","p_medium") * 0.7;
                case 2 -> lambda *= config.getDouble("simulation","p_large") * 0.7;
                default -> System.out.println("Centro inesistente!");
            }
        }

        sarrival += exponential(1/lambda, r);
        return sarrival;
    }

    public double getNextArrivalTimeRideSharing(Rngs r, double sarrival) {
        r.selectStream(0);
        double lambda = config.getDouble("simulation","lambda") * 0.3;
        sarrival += exponential(1/lambda, r);
        return sarrival;
    }


    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTimeSimple(Rngs r) {
        r.selectStream(1);
       double esi = config.getDouble("simulation","esi");
//        double alpha, beta;
//        double a = 2;
//        double b = 60;
//
//        alpha = cdfNormal(esi, 10.0, a);
//        beta = cdfNormal(esi, 10.0, b);
//
//        double u = uniform(alpha, beta, r);
//        return idfNormal(esi, 10.0, u);
        return exponential(esi,r);
    }

    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTimeRideSharing(Rngs r) {
        r.selectStream(1);
        double esi = config.getDouble("simulation","esi");
//        double alpha, beta;
//        double a = 2;
//        double b = 60;
//
//        alpha = cdfNormal(esi, 10.0, a);
//        beta = cdfNormal(esi, 10.0, b);
//
//        double u = uniform(alpha, beta, r);
//        return idfNormal(esi, 10.0, u);
        return exponential(esi,r);
    }
}
