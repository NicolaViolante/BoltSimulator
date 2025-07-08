package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.Distrs;

import java.util.ArrayList;
import java.util.List;

public class RideSharingMultiServerNode implements Node {

    private static final int ARRIVAL = 0;
    private final Rngs rng;


    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final Distrs distrs = new Distrs();
    private final MsqSum[] sum;
    private final List<MsqEvent> event;

    private static final List<MsqEvent> pendingArrivals = new ArrayList<>();

    private int numberJobInSystem = 0;
    private final int numberJobsProcessed = 0;
    private final int S;
    private final int Ssmall;
    private final int Smedium;
    private final int Slarge;

    private final double P_EXIT, P_FEEDBACK, P_MATCH_BUSY, P_MATCH_IDLE, TIME_WINDOW;
    private final Sistema system;
    private final ConfigurationManager config = new ConfigurationManager();
    private final List<RideSharingMultiServerNodeSimple> centriTradizionali;

    public RideSharingMultiServerNode(Sistema system, Rngs rng, List<RideSharingMultiServerNodeSimple> centriTradizionali) {

        this.system = system;
        this.rng = rng;
        this.centriTradizionali = centriTradizionali;

        P_EXIT = config.getDouble("probabilities", "rideExit");
        P_FEEDBACK = config.getDouble("probabilities", "rideFeedback");
        P_MATCH_BUSY = config.getDouble("probabilities", "rideMatchBusy");
        P_MATCH_IDLE = config.getDouble("probabilities", "rideMatchIdle");
        TIME_WINDOW = config.getDouble("simulation", "timeWindow");

        String[] srv = config.getString("simulation", "rideSimpleServers").split(",");
        Ssmall = Integer.parseInt(srv[0].trim());
        Smedium = Integer.parseInt(srv[1].trim());
        Slarge = Integer.parseInt(srv[2].trim());
        S = config.getInt("simulation", "rideServers");

        sum = new MsqSum[S + 1];
        event = new ArrayList<>(S + 1);

        for (int i = 0; i <= S; i++) {

            MsqEvent ev = new MsqEvent();

            if (i > 0) {
                if (i <= Ssmall) ev.capacita = ev.capacitaRimanente = 3;
                else if (i <= Ssmall + Smedium) ev.capacita = ev.capacitaRimanente = 4;
                else ev.capacita = ev.capacitaRimanente = 8;
                ev.numRichiesteServite = 0;
                ev.x = 0;
            } else {
                ev.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
                ev.x = 1;
            }
            sum[i] = new MsqSum();
            event.add(ev);
        }
        resetState();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= S; i++) {
            MsqEvent ev = event.get(i);
            if (ev.x == 1 && ev.t < tmin) tmin = ev.t;
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= S; i++) {
            MsqEvent ev = event.get(i);
            if (ev.x == 1 && ev.t < tmin) {
                tmin = ev.t;
                best = i;
            }
        }
        return best;
    }

    @Override
    public int processNextEvent(double t) {

        int e = peekNextEventType();
        clock.current = t;

        if (e == ARRIVAL) {
            // 1) rischedula immediatamente evento di ARRIVAL fittizio come interrupt

            numberJobInSystem++;

            MsqEvent arr = new MsqEvent();
            arr.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
            arr.x = 1;
            arr.postiRichiesti = getNumPosti();

            //impostiamo il nuovo evento di arrivo
            event.set(ARRIVAL, arr);

            rng.selectStream(2);
            double p = rng.random();
            if (p < P_EXIT) {
                numberJobInSystem--;
                return -1;
            } else if (p < P_FEEDBACK) {
                numberJobInSystem--;
                generateFeedback(arr);
                return -1;
            } else {
                return -1;
            }

            } else {
            //caso del ridesharing
            return -1;
        }
    }

//            // 2) genera batch di arrivi entro finestra
//            while (true) {
////                MsqEvent ev = new MsqEvent();
////                ev.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
////                ev.x = 1;
////                ev.postiRichiesti = getNumPosti();
////                rng.selectStream(2);
////                if (rng.random() < P_EXIT) continue;
////                rng.selectStream(3);
////                if (rng.random() < P_FEEDBACK) {
////                    generateFeedback(ev);
////                    continue;
////                }
//                pendingArrivals.add(ev);
////                numberJobInSystem++;
//                if (ev.t > clock.current + TIME_WINDOW) break;
//            }
//        } else {
//            // DEPARTURE su server e
//            MsqEvent srvEv = event.get(e);
//            sum[e].service += srvEv.svc;
//            sum[e].served += srvEv.numRichiesteServite;
//            numberJobsProcessed += srvEv.numRichiesteServite;
//            numberJobInSystem -= srvEv.numRichiesteServite;
//
//            // libera server
//            srvEv.capacitaRimanente = srvEv.capacita;
//            srvEv.numRichiesteServite = 0;
//            srvEv.x = 0;
//        }
//
//        // match non-FIFO: provo tutte le pending
//        ListIterator<MsqEvent> it = pendingArrivals.listIterator();
//        while (it.hasNext()) {
//            MsqEvent req = it.next();
//            int srv = findFreeServer(req);
//            if (srv != -1) {
//                it.remove();
////                queueJobs = pendingArrivals.size();
//            }
//        }
//
//        return e;
//    }
//
//    private int findFreeServer(MsqEvent ev) {
//        boolean matched = false;
//        int srv = -1;
//        rng.selectStream(5);
//        for (int i = 1; i <= S && !matched; i++) {
//            MsqEvent s = event.get(i);
//            if (s.x == 1 && s.capacitaRimanente >= ev.postiRichiesti && rng.random() < P_MATCH_BUSY) {
//                double svc = distrs.getServiceTimeRideSharing(rng);
//                s.t = clock.current + svc;
//                s.svc = (s.svc * s.numRichiesteServite + svc) / (s.numRichiesteServite + 1);
//                s.numRichiesteServite++;
//                s.capacitaRimanente -= ev.postiRichiesti;
//                srv = i;
//                matched = true;
//            }
//        }
//        if (!matched) {
//            for (int i = 1; i <= S && !matched; i++) {
//                MsqEvent s = event.get(i);
//                if (s.x == 0 && s.capacitaRimanente >= ev.postiRichiesti && rng.random() < P_MATCH_IDLE) {
//                    double svc = distrs.getServiceTimeRideSharing(rng);
//                    s.t = clock.current + svc;
//                    s.svc = (s.svc * s.numRichiesteServite + svc) / (s.numRichiesteServite + 1);
//                    s.numRichiesteServite++;
//                    s.capacitaRimanente -= ev.postiRichiesti;
//                    s.x = 1;
//                    srv = i;
//                    matched = true;
//                }
//            }
//        }
//        return srv;

//    }

    @Override
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;
        areaCollector.incNodeArea(dt * numberJobInSystem);
        int busy = Math.min(numberJobInSystem, S);
        areaCollector.incServiceArea(dt * busy);
        if (numberJobInSystem > busy) areaCollector.incQueueArea(dt * (numberJobInSystem - busy));
        clock.current = t;
    }

    @Override
    public Area getAreaObject() { return areaCollector; }

    @Override
    public MsqSum[] getMsqSums() { return sum; }

    @Override
    public void resetState() {
        numberJobInSystem = 0;
        clock.current = clock.next = 0.0;
        areaCollector.reset();
        pendingArrivals.clear();
        for (MsqSum s : sum) s.reset();
    }

    private int getNumPosti() {
        rng.selectStream(4);
        double r = rng.random();
        if (r < 0.4) return 1;
        if (r < 0.7) return 2;
        if (r < 0.8) return 3;
        if (r < 0.85) return 4;
        if (r < 0.9) return 5;
        if (r < 0.95) return 6;
        return 7;
    }

    public double getUtilization() {
        double busyTime = areaCollector.getServiceArea();
        int servers = sum.length - 1;
        return (clock.current > 0 && servers > 0) ? busyTime / (servers * clock.current) : 0.0;
    }

    @Override
    public MsqServer[] getServersCompletition() {
        return new MsqServer[0];
    }

    public void generateFeedback(MsqEvent event) {

        int num_posti = event.postiRichiesti;
        if(num_posti <= 3){
            centriTradizionali.getFirst().generateArrival(event.t);
           //genera evento di tipo 1
        } else if(num_posti == 4){
            centriTradizionali.get(1).generateArrival(event.t);
           //genera eventi di tipo 2
        } else {
            centriTradizionali.get(2).generateArrival(event.t);
           //genera eventi di tipo 3
       }
    }
}
