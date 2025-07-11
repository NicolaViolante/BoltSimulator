package org.uniroma2.PMCSN.model;

import java.util.PriorityQueue;

public class MsqEvent implements Comparable<MsqEvent> {        /* the next-event list    */
    public double t;                 /* next event time        */
    public int x;                    /* event status, 0 or 1   */
    public int postiRichiesti;
    public int capacita;
    public int capacitaRimanente;
    public int numRichiesteServite;
    public double svc;

    public int retryCount = 0;  // numero tentativi di matching fatti
    public double startServiceTime = -1;

    /*prova*/
    public PriorityQueue<MsqEvent> richiesteInServizio = new PriorityQueue<>();
    /*richieste effettive che il server sta servendo*/

    public void reset() {
        t = 0;
        svc = 0;
        startServiceTime = -1;
        x = 0;
        postiRichiesti = 0;
        capacitaRimanente = capacita;
        numRichiesteServite = 0;

        /*prova*/
        retryCount = 0;
        richiesteInServizio.clear();
    }

    public int getNumRichiesteInServizio() {
        return richiesteInServizio.size();
    }

    @Override
    public int compareTo(MsqEvent other) {
        return Double.compare(this.t, other.t);
    }

    public int getNumRichiesteServite() {
        return numRichiesteServite;
    }

    public boolean isBusy() {
        return this.startServiceTime != -1;
    }
}
