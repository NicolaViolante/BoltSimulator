package org.uniroma2.PMCSN.centers;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;
import org.uniroma2.PMCSN.controller.Sistema;
import org.uniroma2.PMCSN.libs.Rngs;
import org.uniroma2.PMCSN.model.*;
import org.uniroma2.PMCSN.utils.Distrs;

import java.util.ArrayList;
import java.util.List;

public class SimpleMultiServerNode implements Node {

    private static final int ARRIVAL = 0;

    // RNG e config
    private final Rngs rng;

    private final MsqTime clock = new MsqTime();
    private final Area areaCollector = new Area();
    private final Distrs distrs = new Distrs();

    // somme di servizio per ciascun server
    private final MsqSum[] sum;

    private final MsqServer[] serversCompletition;

    // eventi: [0]=arrival, [1..S]=departure
    private final List<MsqEvent> event;

    // stato
    private int   numberJobInSystem = 0; /*numero di job attualmente nel centro, sia in coda che in servizio*/
    private double arrivalTime = 0.0;
    int numberOfServersInTheCenter; /*numero di servers nel centro*/
    private double lastArrivalTimeInBatch;


    // parametri da config
    private final double P_EXIT;

    // contesto
    private final int centerIndex;
    private final Sistema system;

    public SimpleMultiServerNode(Sistema system, int centerIndex, Rngs rng) {
        this.system      = system;
        this.centerIndex = centerIndex;
        this.rng         = rng;

        // leggi probabilità
        ConfigurationManager config = new ConfigurationManager();
        P_EXIT   = config.getDouble("probabilities", "exit");

        // leggi configurazione server
        String[] srv = config.getString("simulation", "servers").split(",");
        Integer[] SERVERS = new Integer[srv.length];
        for (int i = 0; i < srv.length; i++) {
            SERVERS[i] = Integer.parseInt(srv[i].trim());
        }
        numberOfServersInTheCenter = SERVERS[centerIndex];

        // init somme ed eventi
        sum   = new MsqSum[numberOfServersInTheCenter + 1];
        event = new ArrayList<>(numberOfServersInTheCenter + 1);
        serversCompletition = new MsqServer[numberOfServersInTheCenter + 1];
        for (int i = 0; i <= numberOfServersInTheCenter; i++) {
            sum[i]   = new MsqSum();
            event.add(new MsqEvent());
            serversCompletition[i] = new MsqServer();

        }

        /*genero il tempo di primo arrivo*/
        arrivalTime = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, arrivalTime);

        lastArrivalTimeInBatch = arrivalTime;

        MsqEvent arr = event.getFirst();
        arr.t = arrivalTime;
        arr.x = 1;

        this.resetState();
    }

    @Override
    public double peekNextEventTime() {
        double tmin = Double.POSITIVE_INFINITY;
        for (MsqEvent e : event) {
            if (e.x == 1 && e.t < tmin)
                tmin = e.t;
        }
        return tmin;
    }

    @Override
    public int peekNextEventType() {
        double tmin = Double.POSITIVE_INFINITY;
        int    idx  = -1;
        for (int i = 0; i < event.size(); i++) {
            MsqEvent e = event.get(i);
            if (e.x == 1 && e.t < tmin) {
                tmin = e.t;
                idx  = i;
            }
        }
        return idx;
    }

    @Override
    public int processNextEvent(double t) {
        int e = peekNextEventType();
        MsqEvent ev = event.get(e);
        clock.next = ev.t;

        clock.current = clock.next;

        // ARRIVAL esterno o routing
        if (e == ARRIVAL || e > numberOfServersInTheCenter) {
            if (e == ARRIVAL) {

                lastArrivalTimeInBatch = clock.current;

                /*incremento le variabili di interesse*/
                numberJobInSystem++;
                //System.out.printf("[DEBUG] ARRIVAL a t=%.4f, numberJobInSystem=%d\n", clock.current, numberJobInSystem);
                /*incremento le variabili di interesse*/

                MsqEvent arr = event.getFirst();
                arr.t = distrs.getNextArrivalTimeSimpleCenter(rng, system, centerIndex, clock.current);
                /*tempo del successivo arrivo*/
                arr.x = 1;
                rng.selectStream(5);
                double rnd = rng.random();
                //System.out.printf("[DEBUG] RNG per uscita: %.4f\n", rnd);
                if (rnd < P_EXIT) {
                    numberJobInSystem--;
                    //System.out.printf("[DEBUG] ARRIVAL esce subito, numberJobInSystem=%d\n", numberJobInSystem);
                    return -1;
                }
            }

            int serverIndex = findOne();

            if (serverIndex != -1) {

                /*significa che il server è libero*/

                double serviceTimeSimple = distrs.getServiceTimeSimple(rng);
                /*tempo di servizio*/

                MsqEvent sEvent = event.get(serverIndex); /*evento di completamento*/
                sEvent.t = clock.current + serviceTimeSimple;

                /*completion time*/
                serversCompletition[serverIndex].setLastCompletionTime(sEvent.t);
                /*completion time*/

                sEvent.x = 1; /*server occupato*/

                sum[serverIndex].service += serviceTimeSimple;
                /*sommiamo il tempo di servizio, questo ci servità poi per il calcolo delle statistiche*/

                if (e > numberOfServersInTheCenter) {
                    event.remove(e);  // rimuovi evento routing
                }
                return serverIndex;
            }
        } else {
            // DEPARTURE
            numberJobInSystem--;
            sum[e].served++;

            if (numberJobInSystem >= numberOfServersInTheCenter) {
                double serviceTimeSimple = distrs.getServiceTimeSimple(rng);

                MsqEvent sEvent = event.get(e);
                sEvent.t = clock.current + serviceTimeSimple; /*nuovo tempo di completamento*/


                /*completion time*/
                serversCompletition[e].setLastCompletionTime(sEvent.t);
                /*completion time*/
                sum[e].service += serviceTimeSimple;
                /*sum[e].served++;*/
                return e;
            } else {
                event.get(e).x = 0;
            }
        }
        return -1;
    }

    @Override
    public void integrateTo(double t) {
        /*la t sarebbe t.next*/
        if (t <= clock.current) return;
        double dt = t - clock.current;
        areaCollector.incNodeArea(dt * numberJobInSystem);
        int busy = Math.min(numberJobInSystem, sum.length - 1);
        areaCollector.incServiceArea(dt * busy);
        if (numberJobInSystem > busy) {
            areaCollector.incQueueArea(dt * (numberJobInSystem - busy));
        }
        clock.current = t;
    }

    @Override
    public void resetState() {
        this.numberJobInSystem = 0;
        this.clock.current     = 0.0;
        this.clock.next        = 0.0;
        this.arrivalTime       = 0.0;
        this.areaCollector.reset();
        for (MsqSum s : sum) s.reset();
        for(MsqServer s : serversCompletition ) s.reset();
    }

    @Override
    public Area getAreaObject() {
        return areaCollector;
    }

    @Override
    public MsqSum[] getMsqSums() {
        return sum;
    }

    // helper privati

    private int findOne() {
        /*selection in order*/
        for (int i = 1; i < event.size(); i++) {
            if (event.get(i).x == 0) return i;
        }
        return -1;
    }

    /**
     * Restituisce il tempo totale di servizio / (server * tempo corrente)
     */
    public double getUtilization() {
        // areaService devi averla già accumulata
        double busyTime = areaCollector.getServiceArea();
        int servers     = sum.length - 1; // sum[0] è arrival
        double t        = clock.current;
        return (t > 0 && servers > 0) ? busyTime / (servers * t) : 0.0;
    }

    /**
     * Restituisce l'id del centro di servizio
     */
    public int getId() { return centerIndex; }

    // metodo di reset per warmup
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
        return 0;
    }
}
