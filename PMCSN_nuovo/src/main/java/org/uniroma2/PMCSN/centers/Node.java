package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.model.Area;
import org.uniroma2.PMCSN.model.MsqServer;
import org.uniroma2.PMCSN.model.MsqSum;

import java.util.List;

public interface Node {

    double peekNextEventTime();
    int peekNextEventType();
    default int processNextEvent() {
        return processNextEvent(0.0);
    }
    int processNextEvent(double t);
    void integrateTo(double t);
    Area getAreaObject();
    MsqSum[] getMsqSums();
    void resetState();
    double getUtilization();
    MsqServer[] getServersCompletition();
}
