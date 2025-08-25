package org.uniroma2.PMCSN.model;

public class MsqServer {

    private double lastCompletionTime;

    public void setLastCompletionTime(double lastCompletionTime) {
        this.lastCompletionTime = lastCompletionTime;
    }

    public MsqServer() {
        this.lastCompletionTime = 0;
    }

    public void reset(){
        this.lastCompletionTime = 0;
    }
}
