package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.model.Area;
import org.uniroma2.PMCSN.model.MsqServer;
import org.uniroma2.PMCSN.model.MsqSum;

public interface Node {

    double peekNextEventTime();
    int peekNextEventType();
    int processNextEvent(double t);
    void integrateTo(double t);
    Area getAreaObject();
    MsqSum[] getMsqSums();
    void resetState();
    double getUtilization();
    void resetStatistics();
    double getBusy();
}
