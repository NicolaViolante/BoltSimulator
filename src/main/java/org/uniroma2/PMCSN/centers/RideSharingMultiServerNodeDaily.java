package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.DailyServerSelectorRideSharing;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.DailyDistr;

import java.util.*;

public class RideSharingMultiServerNodeDaily implements Node {

    private static final int ARRIVAL = 0;

    private int numberOfServersInTheCenter;
    private final Rngs rng;
    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final DailyDistr distrs = new DailyDistr();

    // liste preallocare di dimensione maxServers+1 (0=arrival, 1..maxServers = servers)
    private final MsqSum[] sum;
    private final List<MsqServer> serversCompletion = new ArrayList<>();
    private final List<MsqEvent> event = new ArrayList<>();

    private final Deque<MsqEvent> pendingArrivals = new ArrayDeque<>();

    private int numberJobInSystem = 0;

    // massimali per tipo (preallocazione)
    private final int maxSmall;
    private final int maxMedium;
    private final int maxLarge;
    private final int totalMax; // = maxSmall+maxMedium+maxLarge

    // mapping esplicito indice -> tipo
    private final int smallStart;
    private final int smallEnd;
    private final int mediumStart;
    private final int mediumEnd;
    private final int largeStart;
    private final int largeEnd;

    // target (numero massimo consentito di veicoli attivi per tipo nella fascia corrente)
    private int targetSmall;
    private int targetMedium;
    private int targetLarge;

    private final double P_EXIT;
    private final double P_MATCH_BUSY;
    private final double TIME_WINDOW;

    private final List<RideSharingMultiServerNodeSimpleDaily> centriTradizionali;

    // selector per fasce giornaliere
    private final DailyServerSelectorRideSharing selector;

    // finestra di matching
    private double nextMatchTime = Double.POSITIVE_INFINITY;

    /**
     * Costruttore: riceve il selector che definisce il numero target per tipo in ogni fascia.
     */
    public RideSharingMultiServerNodeDaily(Sistema system,
                                           Rngs rng,
                                           List<RideSharingMultiServerNodeSimpleDaily> centriTradizionali,
                                           DailyServerSelectorRideSharing selector) {
        this.rng = rng;
        this.centriTradizionali = centriTradizionali;
        this.selector = selector;

        ConfigurationManager config = new ConfigurationManager();
        P_EXIT = config.getDouble("probabilities", "rideExit");
        P_MATCH_BUSY = config.getDouble("probabilities", "rideMatchBusy");
        TIME_WINDOW = config.getDouble("simulation", "timeWindow");

        int x = 120;
        // campioniamo il selector nelle fasce note per calcolare i massimali
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

        int ms = 0, mm = 0, ml = 0;
        for (int[] s : samples) {
            ms = Math.max(ms, s[0]);
            mm = Math.max(mm, s[1]);
            ml = Math.max(ml, s[2]);
        }
        this.maxSmall = ms;
        this.maxMedium = mm;
        this.maxLarge = ml;
        this.totalMax = maxSmall + maxMedium + maxLarge;

        // mappatura esplicita indici per tipo
        this.smallStart = 1;
        this.smallEnd = smallStart + maxSmall - 1;
        this.mediumStart = smallEnd + 1;
        this.mediumEnd = mediumStart + maxMedium - 1;
        this.largeStart = mediumEnd + 1;
        this.largeEnd = largeStart + maxLarge - 1;

        sum = new MsqSum[totalMax+1];

        // preallocazione liste (0 = ARRIVAL; 1..totalMax = server slots)
        for (int i = 0; i <= totalMax; i++) {
            sum[i] = new MsqSum();
            serversCompletion.add(new MsqServer());
            event.add(new MsqEvent());
        }

        // inizializziamo gli slot: 0 = arrival; 1..totalMax = veicoli
        for (int i = 0; i <= totalMax; i++) {
            MsqEvent ev = new MsqEvent();
            if (i == 0) {
                // arrival placeholder: scheduliamo il primo arrivo (usando DailyDistr)
                ev.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
                ev.x = 1;
                ev.x = 1;
                ev.postiRichiesti = 0;
            } else {
                // assegniamo capacità in funzione della mappatura indice->tipo
                if (i >= smallStart && i <= smallEnd) {
                    ev.capacita = ev.capacitaRimanente = 3;
                } else if (i >= mediumStart && i <= mediumEnd) {
                    ev.capacita = ev.capacitaRimanente = 4; // medium
                } else if (i >= largeStart && i <= largeEnd) {
                    ev.capacita = ev.capacitaRimanente = 8; // large
                } else {
                    ev.capacita = ev.capacitaRimanente = 0;
                }
                ev.numRichiesteServite = 0;
                ev.x = -1; // per default: inattivo
                ev.t = Double.POSITIVE_INFINITY;
            }
            event.set(i, ev);
        }

        // targets iniziali per t=0 (in base al selector)
        int[] init = selector.getServers(0);
        this.targetSmall = Math.min(init[0], maxSmall);
        this.targetMedium = Math.min(init[1], maxMedium);
        this.targetLarge = Math.min(init[2], maxLarge);

        // numero attuale di server (conteggio attivi) - usato solo come contatore/utile informazione,
        // la scansione degli eventi deve invece usare sempre totalMax o le mappe esplicite.
        this.numberOfServersInTheCenter = targetSmall + targetMedium + targetLarge;

        // reset dello stato (clock, aree, code)
        resetState();

        // attiva i server corrispondenti al target iniziale (se non busy)
        updateServersForCurrentTime();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < totalMax+1; i++) {
            MsqEvent ev = event.get(i);
            if (ev.x == 1 && ev.t < tmin) tmin = ev.t;
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i < totalMax+1; i++) {
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
        // aggiorna i server attivi/liberi secondo la fascia corrente
        updateServersForCurrentTime();

        // trova il prossimo evento
        int e = peekNextEventType();
        if (e == -1) return -1;

        // aggiorna il clock
        MsqEvent ev = event.get(e);
        clock.next = ev.t;
        clock.current = clock.next;

        if (e == ARRIVAL) {
            // ARRIVO ESTERNO
            numberJobInSystem++;

            // schedula il prossimo arrivo
            MsqEvent newArr = new MsqEvent();
            newArr.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
            newArr.x = 1;
            newArr.postiRichiesti = getNumPosti();
            event.set(ARRIVAL, newArr);

            // probabilità di uscita
            rng.selectStream(6);
            if (rng.random() < P_EXIT) {
                numberJobInSystem--;
                return -1;
            }

            // aggiungi in coda (salviamo l'evento per il matching)
            pendingArrivals.add(newArr);

            // imposta finestra di batch matching se necessario
            if (Double.isInfinite(nextMatchTime)) {
                nextMatchTime = clock.current + TIME_WINDOW;
            }

        } else {
            // DEPARTURE da un server
            MsqEvent sEvent = event.get(e);

            // aggiorna le statistiche
            int numServed = sEvent.numRichiesteServite;
            numberJobInSystem -= numServed;
            sum[e].served += numServed;
            sum[e].service += sEvent.svc;

            // reset server: diventa idle se dentro target
            if (e <= totalMax && sEvent.x == 1) {
                sEvent.x = 0;  // idle
                sEvent.capacitaRimanente = sEvent.capacita;
                sEvent.numRichiesteServite = 0;
                sEvent.postiRichiesti = 0;
                sEvent.svc = 0;
                sEvent.t = Double.POSITIVE_INFINITY;
            }

            return e;
        }

        // batch matching se scaduta la finestra
        if (clock.current >= nextMatchTime) {
            while (true) {
                int matched = findOne();
                if (matched == 0) {
                    if (!pendingArrivals.isEmpty()) {
                        // feedback verso centri tradizionali
                        numberJobInSystem--;
                        MsqEvent toFb = pendingArrivals.getFirst();
                        generateFeedback(toFb);
                        pendingArrivals.removeFirst();
                        continue; // riprova con eventuali altri server liberi
                    }
                    break;
                }
            }
            nextMatchTime = Double.POSITIVE_INFINITY;
        }

        return -1;
    }

    @Override
    public void integrateTo(double t) {
        if (t <= clock.current) return;
        double dt = t - clock.current;

        // node area = passenger-seconds (numero in sistema * dt)
        areaCollector.incNodeArea(dt * numberJobInSystem);

        // service area = passenger-seconds in servizio
        int passengersInService = getPassengersInService();
        areaCollector.incServiceArea(dt * passengersInService);

        // active server area = server-seconds occupati (per utilisation calcs)
        int busyServers = getNumBusyServers();
        areaCollector.incActiveServerArea(dt * busyServers);

        int inQueue = Math.max(0, numberJobInSystem - passengersInService);
        areaCollector.incQueueArea(dt * inQueue);

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
        nextMatchTime = Double.POSITIVE_INFINITY;
        for (MsqSum s : sum) if (s != null) s.reset();
        // NOTA: non resettiamo l'array event qui (capacità e mapping rimangono)
    }

    private int getNumPosti() {
        rng.selectStream(8);
        double r = rng.random();
        if (r < 0.4) return 1;
        if (r < 0.7) return 2;
        if (r < 0.9) return 3;
        return 4;
    }

    public int findOne() {
        if (pendingArrivals.isEmpty()) return 0;

        // Aggiorna targets (potrebbe cambiare all'interno della giornata)
        updateServersForCurrentTime();

        MsqEvent firstReq = pendingArrivals.getFirst();

        // Primo tentativo: server attivi e busy (x==1) con probabilità P_MATCH_BUSY
        int bestActive = -1;
        int bestCapActive = -1;

        rng.selectStream(7);
        for (int i = 1; i <= totalMax; i++) {
            MsqEvent s = event.get(i);
            if (s.x == 1 && s.capacitaRimanente >= firstReq.postiRichiesti && rng.random() < P_MATCH_BUSY) {
                if (s.capacitaRimanente > bestCapActive) {
                    bestCapActive = s.capacitaRimanente;
                    bestActive = i;
                }
            }
        }

        if (bestActive != -1) {
            assignToServer(bestActive, firstReq);
            pendingArrivals.removeFirst();
            return 1;
        }

        // Secondo tentativo: server attivi ma idle (x==0)
        int bestIdle = -1;
        int bestCapIdle = -1;
        for (int i = 1; i <= totalMax; i++) {
            MsqEvent s = event.get(i);
            if (s.x == 0 && s.capacitaRimanente >= firstReq.postiRichiesti && s.capacitaRimanente > bestCapIdle) {
                bestCapIdle = s.capacitaRimanente;
                bestIdle = i;
            }
        }

        if (bestIdle == -1) return 0; // nessun server disponibile

        // Attiva il server idle
        MsqEvent s = event.get(bestIdle);
        s.x = 1;

        // Assegna richieste dalla coda fino a saturare
        Iterator<MsqEvent> it = pendingArrivals.iterator();
        int totalMatched = 0;
        while (it.hasNext()) {
            MsqEvent req = it.next();
            if (req.postiRichiesti <= s.capacitaRimanente) {
                assignToServer(bestIdle, req);
                it.remove();
                totalMatched++;
                if (s.capacitaRimanente == 0) break;
            }
        }

        return totalMatched;
    }

    private void assignToServer(int serverIdx, MsqEvent req) {
        MsqEvent s = event.get(serverIdx);

        // 1. Tempo di servizio base per il nuovo passeggero
        double svcNew = distrs.getServiceTimeRideSharing(rng);

        // 2. Overhead batch proporzionale al numero di richieste già assegnate
        double alpha = 0.2;
        double overhead = svcNew * alpha * s.numRichiesteServite;

        // 3. Tempo totale reale del nuovo passeggero nel batch
        double realServiceTime = svcNew + overhead;

        // 4. Aggiorna il servizio cumulativo statistico
        sum[serverIdx].service += realServiceTime;

        // 5. Aggiorna lo stato del server
        s.capacitaRimanente -= req.postiRichiesti;
        s.numRichiesteServite++;
        s.postiRichiesti += req.postiRichiesti;

        // 6. Imposta il tempo di completamento del server
        if (!s.isBusy()) {
            // server libero → parte subito
            s.svc = realServiceTime;
            s.t = clock.current + realServiceTime;
            s.x = 1;
        } else {
            // server già occupato → aggiorna media ponderata
            s.svc = (s.svc * (s.numRichiesteServite - 1) + realServiceTime) / s.numRichiesteServite;
            s.t = clock.current + s.svc;
        }
    }

    // ritorna numero di passeggeri in servizio (somma numRichiesteServite su tutti i server)
    public int getPassengersInService() {
        int passengers = 0;
        for (int i = 1; i <= totalMax; i++) {
            passengers += event.get(i).getNumRichiesteServite();
        }
        return passengers;
    }

    // mantenuta l'interfaccia precedente (double) ma corretta
    public double getBusy() {
        return (double) getPassengersInService();
    }

    public int getNumBusyServers() {
        int busyServers = 0;
        for (int i = 1; i <= totalMax; i++) if (event.get(i).isBusy()) busyServers++;
        return busyServers;
    }

    public double getUtilization() {
        // uso active server area per calcolo corretto della rho
        double busyServerSeconds = areaCollector.getActiveServerArea();
        // numero di server fisicamente "presenti" (prendiamo somma target attuale)
        int[] cur = selector.getServers(clock.current);
        int servers = Math.max(1, Math.min(maxSmall, cur[0]) + Math.min(maxMedium, cur[1]) + Math.min(maxLarge, cur[2]));
        return (clock.current > 0 && servers > 0) ? busyServerSeconds / (servers * clock.current) : 0.0;
    }

    @Override
    public void resetStatistics() {
        areaCollector.reset();
        if (sum != null) for (MsqSum s : sum) if (s != null) s.reset();
    }

    public void generateFeedback(MsqEvent event) {
        int num_posti = event.postiRichiesti;
        event.t = clock.current; //prova greta
        if (num_posti <= 3) {
            // use get(0) to be safe on List type
            centriTradizionali.get(0).generateArrival(event.t);
        } else if (num_posti == 4) {
            centriTradizionali.get(1).generateArrival(event.t);
        } else {
            centriTradizionali.get(2).generateArrival(event.t);
        }
    }

    private void updateServersForCurrentTime() {
        int[] serversPerType = selector.getServers(clock.current);
        int smallTarget  = Math.min(serversPerType[0], maxSmall);
        int mediumTarget = Math.min(serversPerType[1], maxMedium);
        int largeTarget  = Math.min(serversPerType[2], maxLarge);

        this.targetSmall = smallTarget;
        this.targetMedium = mediumTarget;
        this.targetLarge = largeTarget;

        // SMALL: attiva i primi `smallTarget` server nella fascia smallStart..smallEnd
        int idx = smallStart;
        for (int i = 0; i < maxSmall; i++, idx++) {
            MsqEvent s = event.get(idx);
            if (i < smallTarget) {
                if (!s.isBusy()) {
                    s.capacita = s.capacitaRimanente = 3;
                    s.numRichiesteServite = 0;
                    s.postiRichiesti = 0;
                    s.svc = 0;
                    s.t = Double.POSITIVE_INFINITY;
                    s.x = 0; // attivo ma idle
                }
            } else {
                if (!s.isBusy()) {
                    s.x = -1;  // inattivo
                    s.t = Double.POSITIVE_INFINITY;
                    s.capacita = s.capacitaRimanente = 0;
                }
            }
        }

        // MEDIUM
        idx = mediumStart;
        for (int i = 0; i < maxMedium; i++, idx++) {
            MsqEvent s = event.get(idx);
            if (i < mediumTarget) {
                if (!s.isBusy()) {
                    s.capacita = s.capacitaRimanente = 4;
                    s.numRichiesteServite = 0;
                    s.postiRichiesti = 0;
                    s.svc = 0;
                    s.t = Double.POSITIVE_INFINITY;
                    s.x = 0;
                }
            } else {
                if (!s.isBusy()) {
                    s.x = -1;
                    s.t = Double.POSITIVE_INFINITY;
                    s.capacita = s.capacitaRimanente = 0;
                }
            }
        }

        // LARGE
        idx = largeStart;
        for (int i = 0; i < maxLarge; i++, idx++) {
            MsqEvent s = event.get(idx);
            if (i < largeTarget) {
                if (!s.isBusy()) {
                    s.capacita = s.capacitaRimanente = 8;
                    s.numRichiesteServite = 0;
                    s.postiRichiesti = 0;
                    s.svc = 0;
                    s.t = Double.POSITIVE_INFINITY;
                    s.x = 0;
                }
            } else {
                if (!s.isBusy()) {
                    s.x = -1;
                    s.t = Double.POSITIVE_INFINITY;
                    s.capacita = s.capacitaRimanente = 0;
                }
            }
        }

        // Numero effettivo di server attivi (includendo quelli occupati): troviamo highest active index
        int busyCount = 0;
        int highestActiveIndex = 0;
        for (int i = 1; i <= totalMax; i++) {
            if (event.get(i).isBusy()) busyCount++;
            if (event.get(i).x != -1) highestActiveIndex = i;
        }
        int targetCount = smallTarget + mediumTarget + largeTarget;
        numberOfServersInTheCenter = Math.max(targetCount, highestActiveIndex);
    }

}
