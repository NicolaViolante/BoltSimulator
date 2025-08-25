package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.Distrs;

import java.util.ArrayList;
import java.util.List;

public class RideSharingMultiServerNodeSimple implements Node {
    private static final int ARRIVO_ESTERNO = 0;

    private final Rngs rng;
    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final Distrs distrs = new Distrs();
    private final MsqSum[] sum;
    private final MsqServer[] serversCompletion;
    private final List<MsqEvent> eventList;
    private int numberJobInSystem = 0;
    private final double P_EXIT;
    private final int centerIndex;
    private final Sistema system;
    private final int serverCount;

    public RideSharingMultiServerNodeSimple(Sistema system, int centerIndex, Rngs rng) {
        this.rng = rng;
        this.centerIndex = centerIndex;
        this.system = system;

        ConfigurationManager config = new ConfigurationManager();
        this.P_EXIT = config.getDouble("probabilities", "exit");

        // leggo quanti server ha questo centro
        String[] srv = config.getString("simulation", "servers").split(",");
        serverCount = Integer.parseInt(srv[centerIndex].trim());

        // array per stats [0]=per gli arrivi, [1..S]=per i server
        sum = new MsqSum[serverCount + 1];
        serversCompletion = new MsqServer[serverCount + 1];
        eventList = new ArrayList<>(serverCount + 2);

        // inizializzo sum, serverCompletion e lista eventi
        for (int i = 0; i <= serverCount; i++) {
            sum[i] = new MsqSum();
            serversCompletion[i] = new MsqServer();
            eventList.add(new MsqEvent());  // placeholder per arrivo esterno e per ogni server
        }

        // primo arrivo esterno
        double firstArr = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, 0.0);
        MsqEvent e0 = eventList.getFirst();
        e0.t = firstArr;
        e0.x = 1;

        resetState();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        // controlla arrivo esterno e completions server
        for (int i = 0; i <= serverCount; i++) {
            MsqEvent e = eventList.get(i);
            if (e.x == 1 && e.t < tmin) {
                tmin = e.t;
            }
        }
        // e poi il primo evento interno dopo i server (se esiste)
        if (eventList.size() > serverCount + 1) {
            MsqEvent internal = eventList.get(serverCount + 1);
            if (internal.x == 1 && internal.t < tmin) {
                tmin = internal.t;
            }
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        double tmin = Double.POSITIVE_INFINITY;
        int idx = -1;
        // 0 = esterno, 1..S = server completion
        for (int i = 0; i <= serverCount; i++) {
            MsqEvent e = eventList.get(i);
            if (e.x == 1 && e.t < tmin) {
                tmin = e.t;
                idx = i;
            }
        }
        // solo il primo evento interno (se presente) può competere
        if (eventList.size() > serverCount + 1) {
            MsqEvent internal = eventList.get(serverCount + 1);
            if (internal.x == 1 && internal.t < tmin) {
                tmin = internal.t;
                idx = serverCount + 1;
            }
        }
        return idx;
    }

    @Override
    public int processNextEvent(double t) {
        int eIdx = peekNextEventType();
        MsqEvent ev = eventList.get(eIdx);
        clock.next = ev.t;
        clock.current = clock.next;

        // ARRIVO (esterno = indice 0, interno = primo dopo i server)
        boolean isExternal = (eIdx == ARRIVO_ESTERNO);
        boolean isInternal = (eIdx == serverCount + 1);

        if (isExternal || isInternal) {
            numberJobInSystem++;
            // se esterno, ne genero subito uno nuovo
            if (isExternal) {
                double nextArr = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, clock.current);
                MsqEvent newArr = new MsqEvent();
                newArr.t = nextArr;
                newArr.x = 1;
                eventList.set(ARRIVO_ESTERNO, newArr);

                rng.selectStream(4);
                if (rng.random() < P_EXIT) {
                    numberJobInSystem--;
                    return -1;
                }
            } else {
                // se era interno, lo rimuovo: l'ArrayList si ridimensiona e sposta all'indietro
                eventList.remove(eIdx);
            }

            int freeSrv = findFreeServer();
            if (freeSrv != -1) {
                double st = distrs.getServiceTimeSimple(rng);
                MsqEvent comp = eventList.get(freeSrv);
                comp.t = clock.current + st;
                comp.x = 1;
                serversCompletion[freeSrv].setLastCompletionTime(comp.t);
                sum[freeSrv].service += st;
                return freeSrv;
            }
        } else {
            // DEPARTURE da server eIdx
            numberJobInSystem--;
            sum[eIdx].served++;
            // se coda non vuota, rimetto in servizio
            if (numberJobInSystem >= serverCount) {
                double st = distrs.getServiceTimeSimple(rng);
                MsqEvent comp = eventList.get(eIdx);
                comp.t = clock.current + st;
                comp.x = 1;
                serversCompletion[eIdx].setLastCompletionTime(comp.t);
                sum[eIdx].service += st;
                return eIdx;
            } else {
                eventList.get(eIdx).x = 0;  // server ora libero
            }
        }
        return -1;
    }

    private int findFreeServer() {
        for (int i = 1; i <= serverCount; i++) {
            if (eventList.get(i).x == 0) return i;
        }
        return -1;
    }

    @Override
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;
        areaCollector.incNodeArea(dt * numberJobInSystem);
        int busy = Math.min(numberJobInSystem, serverCount);
        areaCollector.incServiceArea(dt * busy);
        if (numberJobInSystem > busy) {
            areaCollector.incQueueArea(dt * (numberJobInSystem - busy));
        }
        clock.current = t;
    }

    @Override public Area getAreaObject()   { return areaCollector; }
    @Override public MsqSum[] getMsqSums()  { return sum; }

    @Override
    public void resetStatistics() {
        // reset delle aree
        areaCollector.reset();

        // reset dei sum statistici
        if (sum != null) {
            for (MsqSum sum : sum) {
                if (sum != null) {
                    sum.reset();
                }
            }
        }
    }

    @Override
    public double getBusy() {
        return 0.0;
    }

    @Override
    public void resetState() {
        numberJobInSystem = 0;
        clock.current = clock.next = 0.0;
        areaCollector.reset();
        sum[0].reset();
        for (int i = 1; i <= serverCount; i++) sum[i].reset();
        for (MsqServer s : serversCompletion) s.reset();
        // non tocco eventList, mantengo i placeholder
    }

    @Override
    public double getUtilization() {
        double busyTime = areaCollector.getServiceArea();
        double t = clock.current;
        return (t > 0) ? busyTime / (serverCount * t) : 0.0;
    }

    /** Genera un arrivo “di feedback” appendendolo in coda */
    public void generateArrival(double timeArrivalFromRideSharing) {
        MsqEvent arr = new MsqEvent();
        arr.t = timeArrivalFromRideSharing;
        arr.x = 1;
        eventList.add(arr);
        // ArrayList.remove(i) sposterà tutti gli elementi successivi verso indici più bassi
    }
}