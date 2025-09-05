package org.uniroma2.PMCSN.model;


public class MsqEvent implements Comparable<MsqEvent> {
    public double t;
    public int x;
    public int postiRichiesti;
    public int capacita;
    public int capacitaRimanente;
    public int numRichiesteServite;
    public double svc;

    @Override
    public int compareTo(MsqEvent other) {
        return Double.compare(this.t, other.t);
    }

    public int getNumRichiesteServite() {
        return numRichiesteServite;
    }

    public boolean isBusy() {
        return this.x==1? true: false;
    }
}
