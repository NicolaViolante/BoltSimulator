package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.DailyServerSelectorRideSharingSimple;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.DailyDistr;

import java.util.ArrayList;
import java.util.List;

public class RideSharingMultiServerNodeSimpleDaily implements Node {

    private static final int ARRIVO_ESTERNO = 0;
    private int maxServers;


    private final Rngs rng;
    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final DailyDistr distrs = new DailyDistr();

    // liste preallocare di dimensione maxServers+1 (0=arrival, 1..maxServers = servers)
    private final List<MsqSum> sum = new ArrayList<>();
    private final List<MsqServer> serversCompletion = new ArrayList<>();
    private final List<MsqEvent> event = new ArrayList<>();

    private int numberJobInSystem = 0;
    private int numberOfServersInTheCenter; // attivi nella fascia corrente

    private final double P_EXIT;
    private final int centerIndex;
    private final Sistema system;

    private final DailyServerSelectorRideSharingSimple dailyServerSelectorMultiType;

    public RideSharingMultiServerNodeSimpleDaily(Sistema system, int centerIndex, Rngs rng, DailyServerSelectorRideSharingSimple selector) {
        this.system = system;
        this.centerIndex = centerIndex;
        this.rng = rng;
        this.dailyServerSelectorMultiType = selector;

        ConfigurationManager config = new ConfigurationManager();
        this.P_EXIT = config.getDouble("probabilities", "rideExit");

        // Calcola il numero massimo di server richiesti in qualunque fascia della giornata
        maxServers = 0;
        int x = 120;
        int[] sampleTimes = {0,0+x,120+x,240+x,360+x,660+x,780+x,960+x,1140+x,1260+x};
        for (int t : sampleTimes) {
            int[] servers = selector.getServers(t);
            for (int v : servers) if (v > maxServers) maxServers = v;
        }

        // Numero iniziale di server attivi nella fascia 0
        int[] serversAt0 = selector.getServers(0);
        numberOfServersInTheCenter = serversAt0[centerIndex];

        // Pre-allocazione delle liste (0 = ARRIVAL, 1..maxServers = server)
        for (int i = 0; i <= maxServers; i++) {
            sum.add(new MsqSum());
            serversCompletion.add(new MsqServer());
            event.add(new MsqEvent());
        }

        // Primo arrivo esterno
        double arrivalTime = distrs.getNextArrivalTimeRideSharing(rng, 0.0);
        MsqEvent arr = event.getFirst();
        arr.t = arrivalTime;
        arr.x = 1;

        System.out.println("[INIT] rideNode=" + centerIndex +
                " maxServers=" + maxServers +
                " initialActive=" + numberOfServersInTheCenter);

        resetState();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= maxServers; i++) {
            MsqEvent e = event.get(i);
            if (e.x == 1 && e.t < tmin) tmin = e.t;
        }
        // e poi il primo evento interno dopo i server (se esiste)
        if (event.size() > maxServers + 1) {
            MsqEvent internal = event.get(maxServers + 1);
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
        for (int i = 0; i <= maxServers; i++) {
            MsqEvent e = event.get(i);
            if (e.x == 1 && e.t < tmin) {
                tmin = e.t;
                idx = i;
            }
        }
        // solo il primo evento interno (se presente) può competere
        if (event.size() > maxServers + 1) {
            MsqEvent internal = event.get(maxServers + 1);
            if (internal.x == 1 && internal.t < tmin) {
                tmin = internal.t;
                idx = maxServers + 1;
            }
        }
        return idx;
    }

    @Override
    public int processNextEvent(double t) {
        updateServersForCurrentTime();

        int e = peekNextEventType();
        if (e == -1) return -1; // nessun evento

        MsqEvent ev = event.get(e);
        clock.next = ev.t;
        clock.current = clock.next;

        boolean isExternal = (e == ARRIVO_ESTERNO);
        boolean isInternal = (e == maxServers + 1);

        if (isExternal || isInternal) {
            // ARRIVO
            numberJobInSystem++;

            if (isExternal) {
                // Genera prossimo arrivo esterno
                double nextArr = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, clock.current);
                MsqEvent newArr = event.getFirst();
                newArr.t = nextArr;
                newArr.x = 1;

                rng.selectStream(5);
                if (rng.random() < P_EXIT) {
                    numberJobInSystem--;
                    return -1; // uscita immediata
                }
            } else {
                // Evento interno di feedback, considerato servito
                ev.x = 0; // server idle
                ev.t = Double.POSITIVE_INFINITY;
            }

            // Assegna job a server libero
            int freeServer = findOne(); // cerca server tra quelli attivi
            if (freeServer != -1) {
                double st = distrs.getServiceTimeSimple(rng);
                MsqEvent comp = event.get(freeServer);
                comp.t = clock.current + st;
                comp.x = 1; // server busy
                serversCompletion.get(freeServer).setLastCompletionTime(comp.t);
                sum.get(freeServer).service += st;
                return freeServer;
            } else {
                // Nessun server libero → job in coda
                return -1;
            }

        } else {
            // DEPARTURE da server e
           numberJobInSystem --;
            sum.get(e).served++;

            if (numberJobInSystem >= numberOfServersInTheCenter) {
                // Job in coda → server riparte subito
                double st = distrs.getServiceTimeSimple(rng);
                MsqEvent comp = event.get(e);
                comp.t = clock.current + st;
                comp.x = 1; // server busy
                serversCompletion.get(e).setLastCompletionTime(comp.t);
                sum.get(e).service += st;
                return e;
            } else {
                // Nessun job → server idle
                MsqEvent comp = event.get(e);
                comp.x = 0;
                comp.t = Double.POSITIVE_INFINITY;
            }
        }

        return -1;
    }

    @Override
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;
        areaCollector.incNodeArea(dt * numberJobInSystem);

        int busy = (int) Math.round(getBusy());
        areaCollector.incServiceArea(dt * busy);
        int inQueue = Math.max(0, numberJobInSystem - busy);
        areaCollector.incQueueArea(dt * inQueue);
        clock.current = t;
    }

    @Override
    public void resetState() {
        numberJobInSystem = 0;
        clock.current = clock.next = 0.0;
        areaCollector.reset();

        for (MsqSum s : sum) if (s != null) s.reset();
        for (MsqServer s : serversCompletion) if (s != null) s.reset();

        // reset events: arrival programmato, server idle/inactive
        for (int i = 0; i <= maxServers; i++) {
            MsqEvent ev = event.get(i);
            if (i == ARRIVO_ESTERNO) {
                ev.t = distrs.getNextArrivalTimeRideSharing(rng, 0.0);
                ev.x = 1;
            } else {
                ev.t = Double.POSITIVE_INFINITY;
                // default: se siamo dentro il target verrà attivato in updateServersForCurrentTime,
                // ma per sicurezza settiamo idle (0) o inactive (-1) a seconda del numero iniziale
                ev.x = 0;
            }
        }
    }

    @Override
    public Area getAreaObject() { return areaCollector; }

    @Override
    public MsqSum[] getMsqSums() { return sum.toArray(new MsqSum[0]); }

    /** trova un server libero tra gli attivi (1..numberOfServersInTheCenter) */
    private int findOne() {
        for (int i = 1; i <= maxServers; i++) {
            MsqEvent s = event.get(i);
            if (s != null && s.x == 0) {  // server attivo ma idle
                return i;
            }
        }
        return -1;
    }


    private void updateServersForCurrentTime() {
        // Numero target di server attivi per questa fascia temporale
        int[] serversPerType = dailyServerSelectorMultiType.getServers(clock.current);
        int target = Math.min(serversPerType[centerIndex], maxServers);

        int busyCount = 0;

        // Conta server occupati
        for (int i = 1; i <= maxServers; i++) {
            MsqEvent s = event.get(i);
            if (s != null && s.isBusy()) {
                busyCount++;
            }
        }

        int freeToActivate = Math.max(0, target - busyCount);
        int serversUpdated = 0;

        // Aggiorna lo stato dei server preallocati
        for (int i = 1; i <= maxServers; i++) {
            MsqEvent s = event.get(i);
            if (s == null || s.isBusy()) continue;

            if (serversUpdated < freeToActivate) {
                s.x = 0; // server attivo ma idle
                s.t = Double.POSITIVE_INFINITY;
                serversUpdated++;
            } else {
                s.x = -1; // server inattivo
                s.t = Double.POSITIVE_INFINITY;
            }
        }

        int newActive = busyCount + freeToActivate;

        if (newActive != numberOfServersInTheCenter) {
            System.out.println("[DEBUG][Ride] updateServersForCurrentTime(): t=" + clock.current
                    + " oldActive=" + numberOfServersInTheCenter
                    + " newActive=" + newActive
                    + " busyCount=" + busyCount
                    + " target=" + target);
        }

        numberOfServersInTheCenter = newActive;
    }

    public double getUtilization() {
        double busyTime = areaCollector.getServiceArea();
        int servers = numberOfServersInTheCenter;
        double t = clock.current;
        return (t > 0 && servers > 0) ? busyTime / (servers * t) : 0.0;
    }

    public void resetStatistics() {
        // reset delle aree
        areaCollector.reset();

        // reset dei sum statistici
        if (!sum.isEmpty()) {
            for (MsqSum sum : sum) {
                if (sum != null) {
                    sum.reset();
                }
            }
        }
    }

    public double getBusy() {
        // restituisce numero di server effettivamente busy
        int busy = 0;
        for (int i = 1; i <= maxServers; i++) {
            MsqEvent s = event.get(i);
            if (s != null && s.isBusy()) busy++;
        }
        return busy;
    }

    /** Genera un arrivo “di feedback” appendendolo in coda (se serve) */
    public void generateArrival(double timeArrivalFromRideSharing) {
        MsqEvent arr = new MsqEvent();
        arr.t = timeArrivalFromRideSharing;
        arr.x = 1;
        event.add(arr);
    }
}