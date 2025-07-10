package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.Distrs;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class RideSharingMultiServerNode implements Node {

    private static final int ARRIVAL = 0;
    private final Rngs rng;


    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final Distrs distrs = new Distrs();
    private final MsqSum[] sum;
    private final List<MsqEvent> event;

    private final List<MsqEvent> pendingArrivals = new ArrayList<>();

    private int numberJobInSystem = 0;
    private final int numberJobsProcessed = 0;
    private final int RIDESERVERS;
    private final int Ssmall;
    private final int Smedium;
    private final int Slarge;

    private final double P_EXIT;
    private final double P_FEEDBACK;
    private final double P_MATCH_BUSY;
    private final double P_MATCH_IDLE;
    private final double TIME_WINDOW;
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
        RIDESERVERS = config.getInt("simulation", "rideServers");

        sum = new MsqSum[RIDESERVERS + 1];
        event = new ArrayList<>(RIDESERVERS + 1);

        for (int i = 0; i <= RIDESERVERS; i++) {

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
        for (int i = 0; i <= RIDESERVERS; i++) {
            MsqEvent ev = event.get(i);
            if (ev.x == 1 && ev.t < tmin) tmin = ev.t;
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        int best = -1;
        double tmin = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= RIDESERVERS; i++) {
            MsqEvent ev = event.get(i);
            if (ev.x == 1 && ev.t < tmin) {
                tmin = ev.t;
                best = i;
            }
        }
        return best;
    }

    // Campo di classe:
    private double nextMatchTime = Double.POSITIVE_INFINITY;

    @Override
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        clock.current = t;

        if (e == ARRIVAL) {
            // 1) ARRIVAL
            numberJobInSystem++;

            // Schedule next ARRIVAL
            MsqEvent arr = new MsqEvent();
            arr.t = distrs.getNextArrivalTimeRideSharing(rng, clock.current);
            arr.x = 1;
            arr.postiRichiesti = getNumPosti();
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
            }

            // 2) Accumulo in coda
            pendingArrivals.add(arr);

            // 3) Imposto finestra se prima
            if (Double.isInfinite(nextMatchTime)) {
                nextMatchTime = clock.current + TIME_WINDOW;
            }

        } else {
            // DEPARTURE: significa che il server e ha terminato la richiesta con tempo minimo

            MsqEvent server = event.get(e);
            if (server.richiesteInServizio.isEmpty()) {
                // Nessuna richiesta da completare: errore o stato inatteso
                server.x = 0;
                return e;
            }

            // Rimuovo la richiesta completata (quella con t minimo)
            MsqEvent completedReq = server.richiesteInServizio.poll();
            numberJobInSystem -= 1;
            sum[e].served += 1;
            sum[e].service += completedReq.svc;
            server.capacitaRimanente += completedReq.postiRichiesti; // libera capacità
            server.numRichiesteServite--;

            // Se ci sono altre richieste in corso, aggiorno il tempo di completamento al minimo delle restanti
            if (!server.richiesteInServizio.isEmpty()) {
                MsqEvent nextCompletion = server.richiesteInServizio.peek();
                server.t = nextCompletion.t;
            } else {
                // Nessuna richiesta rimasta, server torna inattivo
                server.x = 0;
                server.t = Double.POSITIVE_INFINITY;
                server.startServiceTime = -1;
                server.postiRichiesti = 0;
                server.capacitaRimanente = server.capacita;
            }

            return e;
        }

        // 5) Batch‑matching
        if (clock.current >= nextMatchTime) {
            while (true) {
                int matched = findOne();
                if (matched == 0) {
                    if (!pendingArrivals.isEmpty()) {
                        numberJobInSystem --;
                        MsqEvent toFb = pendingArrivals.getFirst();
                        generateFeedback(toFb);
                        pendingArrivals.removeFirst();
                        /*aggiunta per prova*/
                        continue;
                        /*aggiunta per prova*/
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
        double dt = t - clock.current; /*intervallo di tempo da integrare*/
        areaCollector.incNodeArea(dt * numberJobInSystem);

        int busy = 0;
        for (int i = 1; i < event.size(); i++) {
            busy += event.get(i).getNumRichiesteInServizio();
        }
        areaCollector.incServiceArea(dt * busy);

        int inQueue = Math.max(0, numberJobInSystem - busy);
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

    public int findOne() {
        if (pendingArrivals.isEmpty()) return 0;

        // 1. Prendo la PRIMA richiesta in coda
        MsqEvent firstReq = pendingArrivals.getFirst();

        // 2. CERCO best‑fit tra i server *attivi*
        int bestActive = -1;
        double bestCapActive = -1;

        rng.selectStream(3);
        for (int i = 1; i <= RIDESERVERS; i++) {
            if (event.get(i).x == 1
                    && event.get(i).capacitaRimanente>= firstReq.postiRichiesti
                    && rng.random() < P_MATCH_BUSY /*indica la probabilità che sto nel percorso giusto*/
                    && event.get(i).capacitaRimanente > bestCapActive) {
                bestCapActive = event.get(i).capacitaRimanente;
                bestActive = i;
            }
        }

        if (bestActive != -1) {
            // 2.a Assegno *solo* la prima richiesta a questo server
            assignToServer(bestActive, firstReq);
            pendingArrivals.removeFirst();
            return 1;
        }

        // 3. FALLBACK interno: best‑fit tra server *inattivi*
        int bestIdle = -1; double bestCapIdle = -1;
        rng.selectStream(4);
        for (int i = 1; i <= RIDESERVERS; i++) {
            if (event.get(i).x == 0
                    && event.get(i).capacitaRimanente >= firstReq.postiRichiesti
                    && rng.random() < P_MATCH_IDLE
                    && event.get(i).capacitaRimanente > bestCapIdle) {
                bestCapIdle = event.get(i).capacitaRimanente;
                bestIdle = i;
            }
        }
        if (bestIdle == -1) {
            return 0;  // né attivi né inattivi hanno accettato
        }

        // 3.a Attivo il server e *accorpo* quante richieste posso
        event.get(bestIdle).x = 1;
        int totalMatched = 0;
        Iterator<MsqEvent> it = pendingArrivals.iterator();
        while (it.hasNext()) {
            MsqEvent req = it.next();
            if (req.postiRichiesti <= event.get(bestIdle).capacitaRimanente) {
                assignToServer(bestIdle, req);
                it.remove();
                totalMatched++;
                if (event.get(bestIdle).capacitaRimanente == 0) break;
            }
        }
        return totalMatched; //totale di richieste matchate
    }

    private void assignToServer(int serverIdx, MsqEvent req) {
        MsqEvent server = event.get(serverIdx);

        // Genera il tempo di servizio base
        double svcTime = distrs.getServiceTimeRideSharing(rng);

        // Aggiungi un incremento simbolico per salita/discesa passeggeri (5% in più)
        double alpha = 0.05;
        svcTime *= (1 + alpha);

        req.startServiceTime = clock.current;
        req.svc = svcTime;
        req.t = clock.current + svcTime;

        // Aggiungo la richiesta alla coda prioritaria del server
        server.richiesteInServizio.add(req);

        // Aggiorno il tempo di completamento del server con la richiesta più "vicina" alla fine
        MsqEvent nextCompletion = server.richiesteInServizio.peek();
        if (nextCompletion != null) {
            server.t = nextCompletion.t;
            server.x = 1; // server attivo
        }

        server.capacitaRimanente -= req.postiRichiesti;
        server.numRichiesteServite++;
        server.postiRichiesti += req.postiRichiesti;
    }
}

