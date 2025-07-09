package org.uniroma2.PMCSN.model;

public class MsqEvent {        /* the next-event list    */
    public double t;                 /* next event time        */
    public int x;                    /* event status, 0 or 1   */
    public int postiRichiesti;
    public int capacita;
    public int capacitaRimanente;
    public int numRichiesteServite;
    public double svc;

    /*prova*/
    public double startServiceTime = -1;

    public int getNumRichiesteServite() {
        return numRichiesteServite;
    }

    public boolean isBusy() {
        return startServiceTime >= 0;
    }

    public void reset() {
        t = 0;
        svc = 0;
        startServiceTime = -1;
        x = 0;
        postiRichiesti = 0;
        capacitaRimanente = capacita;
        numRichiesteServite = 0;
    }
}
