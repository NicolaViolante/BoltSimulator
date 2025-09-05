package org.uniroma2.PMCSN.model;

public class MsqSum {
    /* si tratta di una classe per memorizzare somme accumulate relative */

    public double service = 0; /*tiene traccia del tempo totale di servizio accumulato */
    public long served = 0;  /*tiene traccia di quante richieste sono state servite */

    public void reset() {
        this.service = 0;
        this.served = 0;
    }

    @Override
    public String toString() {
        return "MsqSum{service=" + service + ", served=" + served + "}";
    }

}
