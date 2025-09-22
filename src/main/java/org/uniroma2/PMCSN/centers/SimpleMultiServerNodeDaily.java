package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.DailyServerSelectorMultiType;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.DailyDistr;

import java.util.ArrayList;
import java.util.List;

public class SimpleMultiServerNodeDaily implements Node {

    private static final int ARRIVAL = 0;

    private final Rngs rng;
    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final DailyDistr distrs = new DailyDistr();

    // manteniamo liste di dimensione fissa = maxServers+1 (0=arrival, 1..maxServers = servers)
    private final List<MsqSum> sum = new ArrayList<>();
    private final List<MsqServer> serversCompletition = new ArrayList<>();
    private final List<MsqEvent> event = new ArrayList<>();

    private int numberJobInSystem = 0;
    private double arrivalTime;
    private int numberOfServersInTheCenter; // attivi in questa fascia

    private final double P_EXIT;
    private final int centerIndex;
    private final Sistema system;


    private final DailyServerSelectorMultiType dailyServerSelectorMultiType;

    public SimpleMultiServerNodeDaily(Sistema system, int centerIndex, Rngs rng, DailyServerSelectorMultiType selector) {
        this.system = system;
        this.centerIndex = centerIndex;
        this.rng = rng;
        this.dailyServerSelectorMultiType = selector;

        ConfigurationManager config = new ConfigurationManager();
        P_EXIT = config.getDouble("probabilities", "exit");

        // calcola il numero massimo di server che potrebbe servire questo tipo su tutta la giornata
        // (campioniamo le fasce note del selector: 0,180,360,660,840,1140,1260)
        int maxServers = 0;
        int x = 120;
        int[][] samples = {
                selector.getServers(0),

                selector.getServers(0+x),
                selector.getServers(120+x),
                selector.getServers(240+x),
                selector.getServers(360+x),
                selector.getServers(660+x),
                selector.getServers(780+x),
                selector.getServers(960+x),
                selector.getServers(1140+x),
                selector.getServers(1260+x)
        };
        for (int[] s : samples) {
            for (int v : s) if (v > maxServers) maxServers = v;
        }

        // numero iniziale di server attivi per la fascia 0
        int[] serversPerTypeAt0 = dailyServerSelectorMultiType.getServers(0);
        numberOfServersInTheCenter = serversPerTypeAt0[centerIndex];

        // inizializza liste con dimensione fissa maxServers+1 (indice 0 per arrival)
        int initialSize = maxServers + 1;
        for (int i = 0; i < initialSize; i++) {
            sum.add(new MsqSum());
            serversCompletition.add(new MsqServer());
            event.add(new MsqEvent());
        }

        // inizializza evento ARRIVAL (index 0)
        arrivalTime = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, 0.0);
        MsqEvent arr = event.getFirst();
        arr.t = arrivalTime;
        arr.x = 1;

        System.out.println("[INIT] node=" + centerIndex + " maxServers=" + maxServers + " initialActive=" + numberOfServersInTheCenter);

        resetState();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (MsqEvent e : event) {
            if (e.x == 1 && e.t < tmin) tmin = e.t;
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        double tmin = Double.POSITIVE_INFINITY;
        int idx = -1;
        for (int i = 0; i < event.size(); i++) {
            MsqEvent e = event.get(i);
            if (e.x == 1 && e.t < tmin) {
                tmin = e.t;
                idx = i;
            }
        }
        return idx;
    }

    @Override
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        //if (e == -1) return -1;

        MsqEvent ev = event.get(e);
        clock.next = ev.t;
        clock.current = clock.next;

        System.out.println("[DEBUG] processNextEvent(): t=" + clock.current + " nextEventIndex=" + e
                + " activeServers=" + numberOfServersInTheCenter + " jobs=" + numberJobInSystem);

        // aggiorna il numero di server attivi (non cambia la struttura delle liste)
        updateServersForCurrentTime();

        if (e == ARRIVAL) {
            // ARRIVAL
            numberJobInSystem++;
            System.out.println("[DEBUG] ARRIVAL at t=" + clock.current + " jobs->" + numberJobInSystem);

            // schedule next arrival
            MsqEvent arr = event.getFirst();
            arr.t = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, clock.current);
            arr.x = 1;

            rng.selectStream(5);
            double rnd = rng.random();
            if (rnd < P_EXIT) {
                // job exits immediately
                numberJobInSystem--;
                System.out.println("[DEBUG] ARRIVAL: job exits immediately (rnd=" + rnd + ")");
                return -1;
            }

            // prova ad assegnare a un server attivo
            int serverIndex = findOne();
            if (serverIndex != -1) {
                double serviceTimeSimple = distrs.getServiceTimeSimple(rng);
                MsqEvent sEvent = event.get(serverIndex);
                sEvent.t = clock.current + serviceTimeSimple;
                sEvent.x = 1;
                serversCompletition.get(serverIndex).setLastCompletionTime(sEvent.t);
                sum.get(serverIndex).service += serviceTimeSimple;
                System.out.println("[DEBUG] ARRIVAL -> assegnato server=" + serverIndex + " svc=" + serviceTimeSimple);
                return serverIndex;
            } else {
                // nessun server libero: il job resta in coda (nessuna modifica ad eventi server)
                System.out.println("[DEBUG] ARRIVAL: nessun server libero, job in coda (jobs=" + numberJobInSystem + ")");
                return -1;
            }
        } else {
            // DEPARTURE per server e (indipendentemente da activeServers)
            System.out.println("[DEBUG] DEPARTURE on server=" + e + " at t=" + clock.current);
            numberJobInSystem--;
            sum.get(e).served++;

            // se ci sono ancora job in coda *che possono essere serviti da server attivi*, schedula nuovo completamento
            // Nota: il criterio usa numberOfServersInTheCenter come "capacitá attiva" per il dispatch
            if (numberJobInSystem >= numberOfServersInTheCenter) {
                double serviceTimeSimple = distrs.getServiceTimeSimple(rng);
                MsqEvent sEvent = event.get(e);
                sEvent.t = clock.current + serviceTimeSimple;
                sEvent.x = 1;
                serversCompletition.get(e).setLastCompletionTime(sEvent.t);
                sum.get(e).service += serviceTimeSimple;
                System.out.println("[DEBUG] DEPARTURE -> server " + e + " ri-servirà (jobs left=" + numberJobInSystem + ")");
                return e;
            } else {
                // server diventa libero
                event.get(e).x = 0;
                System.out.println("[DEBUG] DEPARTURE -> server " + e + " libero (jobs left=" + numberJobInSystem + ")");
                return e;
            }
        }
    }

    @Override
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;

        int busy = Math.min(numberJobInSystem, numberOfServersInTheCenter);
        int queueJobs = numberJobInSystem - busy;

        areaCollector.incNodeArea(dt * numberJobInSystem);
        areaCollector.incServiceArea(dt * busy);
        areaCollector.incQueueArea(dt * queueJobs); // già presente

        // salva anche "serverArea" cumulata per media intervallo
        areaCollector.incActiveServerArea(dt * numberOfServersInTheCenter);

        clock.current = t;
    }

    @Override
    public void resetState() {
        numberJobInSystem = 0;
        clock.current = 0.0;
        clock.next = 0.0;
        arrivalTime = 0.0;

        areaCollector.reset(); // reset include activeServerArea
        sum.forEach(MsqSum::reset);
        serversCompletition.forEach(MsqServer::reset);

        // Assicuriamoci che l'evento ARRIVAL sia impostato correttamente
        event.getFirst().x = 1;
    }

    @Override
    public Area getAreaObject() { return areaCollector; }

    @Override
    public MsqSum[] getMsqSums() { return sum.toArray(new MsqSum[0]); }

    /** trova un server libero tra gli attivi (1..numberOfServersInTheCenter) */
    private int findOne() {
        for (int i = 1; i <= numberOfServersInTheCenter && i < event.size(); i++) {
            if (event.get(i).x == 0) {
                System.out.println("[DEBUG] findOne(): trovato server libero index=" + i);
                return i;
            }
        }
        System.out.println("[DEBUG] findOne(): nessun server libero, jobs=" + numberJobInSystem);
        return -1;
    }

    /** aggiorna solo la count di server attivi; non tocca la struttura delle liste */
    private void updateServersForCurrentTime() {
        int[] serversPerType = dailyServerSelectorMultiType.getServers(clock.current);
        int newNumberOfServers = serversPerType[centerIndex];

        if (newNumberOfServers != numberOfServersInTheCenter) {
            System.out.println("[DEBUG] updateServersForCurrentTime(): t=" + clock.current
                    + " oldActive=" + numberOfServersInTheCenter
                    + " newActive=" + newNumberOfServers);
        }
        numberOfServersInTheCenter = Math.min(newNumberOfServers, event.size()-1); // non superare la capacity preallocata
    }

    public double getUtilization() {
        double busyTime = areaCollector.getServiceArea();
        int servers = numberOfServersInTheCenter;
        double t = clock.current;
        return (t > 0 && servers > 0) ? busyTime / (servers * t) : 0.0;
    }

    public void resetStatistics() {
        areaCollector.reset();
        sum.forEach(MsqSum::reset);
    }

    @Override
    public double getBusy() { return 0; }
}
