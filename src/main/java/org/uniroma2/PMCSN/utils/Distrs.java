package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.SimpleSystem;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;

import static org.uniroma2.PMCSN.libs.Distributions.*;

public class Distrs {

    private final ConfigurationManager config = new ConfigurationManager();

    public double getNextArrivalTimeSimpleCenter(Rngs r, Sistema system, int centerIndex, double sarrival) {
        r.selectStream(1);
        double lambda = config.getDouble("simulation","lambdasimple");

        if(system instanceof SimpleSystem) {
            switch (centerIndex) {
                case 0 -> lambda *= config.getDouble("simulation","p_small");
                case 1 -> lambda *= config.getDouble("simulation","p_medium");
                case 2 -> lambda *= config.getDouble("simulation","p_large");
                default -> System.out.println("Centro inesistente!");
            }
        }else{
            switch (centerIndex) {
                case 0 -> lambda *= config.getDouble("simulation","p_small") * config.getDouble("simulation","psimple") ;
                case 1 -> lambda *= config.getDouble("simulation","p_medium") * config.getDouble("simulation","psimple") ;
                case 2 -> lambda *= config.getDouble("simulation","p_large") * config.getDouble("simulation","psimple") ;
                default -> System.out.println("Centro inesistente!");
            }
        }

        sarrival += exponential(1/lambda, r);
        return sarrival;
    }

    public double getNextArrivalTimeRideSharing(Rngs r, double sarrival) {
        r.selectStream(2);
        double lambda = config.getDouble("simulation","lambdaride") * config.getDouble("simulation","pride") ;
        sarrival += exponential(1/lambda, r);
        return sarrival;
    }


    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTimeSimple(Rngs r) {
        r.selectStream(3);
        double esi = config.getDouble("simulation","esi");
        double alpha, beta;
        double a = 2;
        double b = 30;

        alpha = cdfNormal(esi, 4, a);
        beta = 1 - cdfNormal(esi, 4, b);

        double u = uniform(alpha, 1 - beta, r);
        return idfNormal(esi, 4, u);
//        return exponential(esi,r);
    }

    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTimeRideSharing(Rngs r) {
        r.selectStream(4);
        double esi = config.getDouble("simulation","esi");
        double alpha, beta;
        double a = 2;
        double b = 30;

        alpha = cdfNormal(esi, 4, a);
        beta = 1 - cdfNormal(esi, 4, b);

        double u = uniform(alpha, 1 - beta, r);
        return idfNormal(esi, 4, u);
    }
}
