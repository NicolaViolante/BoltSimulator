package org.uniroma2.PMCSN.utils;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.SimpleDailySystem;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.controller.DailyLambdaSelectorMinutes;
import org.uniroma2.PMCSN.controller.DailyLambdaSelectorMinutesRideSharing;

import static org.uniroma2.PMCSN.libs.Distributions.*;
import static org.uniroma2.PMCSN.libs.Distributions.idfNormal;

public class DailyDistr {

    private final ConfigurationManager config = new ConfigurationManager();
    private final DailyLambdaSelectorMinutes lambdaSelector = DailyLambdaSelectorMinutes.fromSystemPropertiesOrDefaults();
    private final DailyLambdaSelectorMinutesRideSharing lambdaSelectorRideSharing = DailyLambdaSelectorMinutesRideSharing.fromSystemPropertiesOrDefaults();


    /**
     * Replica la logica di Distrs.getNextArrivalTimeSimpleCenter,
     * ma con λ dinamico secondo fasce giornaliere.
     *
     * @param r           generatore di numeri casuali
     * @param system      tipo di sistema (SimpleSystem o altro)
     * @param centerIndex indice del centro (0=small, 1=medium, 2=large)
     * @param arrival    tempo corrente (in minuti)
     * @return tempo del prossimo arrivo
     */

    public double getNextArrivalTimeSimpleCenter(Rngs r, Sistema system, int centerIndex, double arrival) {
        r.selectStream(10); // stream dedicato agli arrivi daily
        double t = arrival;

        while (true) {
            // prendo il lambda base dinamico dalla fascia corrente (arrivi / minuto)
            double lambda = lambdaSelector.getLambdaPerMinute(t);

            // applico i pesi come nel metodo originale
            if (system instanceof SimpleDailySystem) {
                switch (centerIndex) {
                    case 0 -> lambda *= config.getDouble("simulation", "p_small");
                    case 1 -> lambda *= config.getDouble("simulation", "p_medium");
                    case 2 -> lambda *= config.getDouble("simulation", "p_large");
                    default -> System.out.println("Centro inesistente!");
                }
            } else {
                switch (centerIndex) {
                    case 0 -> lambda *= config.getDouble("simulation", "p_small") * config.getDouble("simulation", "psimple");
                    case 1 -> lambda *= config.getDouble("simulation", "p_medium") * config.getDouble("simulation", "psimple");
                    case 2 -> lambda *= config.getDouble("simulation", "p_large") * config.getDouble("simulation", "psimple");
                    default -> System.out.println("Centro inesistente!");
                }
            }

            // se lambda <= 0, salto alla fascia successiva
            if (lambda <= 0.0) {
                t = lambdaSelector.getNextSwitchTimeMinutes(t);
                continue;
            }

            // campiona inter-arrivo esponenziale
            double inter = exponential(1.0 / lambda, r);
            double candidate = t + inter;

            // controllo se cade prima del prossimo cambio fascia
            double nextSwitch = lambdaSelector.getNextSwitchTimeMinutes(t);
            if (candidate < nextSwitch) {
                return candidate;
            }

            // altrimenti passo alla fascia successiva
            t = nextSwitch;
        }
    }

    //dovrebbe restituire valore gaussiana troncata tra a e b
    public double getServiceTimeSimple(Rngs r) {
        r.selectStream(11);
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

    public double getNextArrivalTimeRideSharing(Rngs r, double arrival) {
        r.selectStream(12);
        double t = arrival;

        while (true) {
            double lambda = lambdaSelectorRideSharing.getLambdaPerMinute(t);

            if (lambda <= 0.0) {
                t = lambdaSelectorRideSharing.getNextSwitchTimeMinutes(t);
                continue;
            }

            double inter = exponential(1.0 / lambda, r);
            double candidate = t + inter;

            double nextSwitch = lambdaSelectorRideSharing.getNextSwitchTimeMinutes(t);
            if (candidate < nextSwitch) {
                return candidate;
            }

            t = nextSwitch;
        }
    }

    public double getServiceTimeRideSharing(Rngs r) {
        r.selectStream(13);
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