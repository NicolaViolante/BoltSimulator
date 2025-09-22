package org.uniroma2.PMCSN.controller;

import java.util.NavigableMap;
import java.util.TreeMap;

public class DailyLambdaSelectorMinutes {

    public enum Category {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }

    private final double veryLow;   // arrivi/minuto
    private final double low;       // arrivi/minuto
    private final double medium;    // arrivi/minuto
    private final double high;      // arrivi/minuto
    private final double veryHigh;  // arrivi/minuto

    // mappa: minuto di inizio fascia → categoria
    private final NavigableMap<Integer, Category> schedule = new TreeMap<>();

    public DailyLambdaSelectorMinutes(double veryLow, double low, double medium,
                                      double high, double veryHigh) {
        this.veryLow = veryLow;
        this.low = low;
        this.medium = medium;
        this.high = high;
        this.veryHigh = veryHigh;
        int x = 120;
        // Weekday — 0 = 03:00
        schedule.put(0,    Category.VERY_LOW);   // 03:00 – 05:00

        schedule.put(0+x,    Category.VERY_LOW);   // 03:00 – 05:00
        schedule.put(120+x,  Category.LOW);        // 05:00 – 07:00
        schedule.put(240+x,  Category.VERY_HIGH);  // 07:00 – 09:00 (picco mattina)
        schedule.put(360+x,  Category.MEDIUM);     // 09:00 – 14:00
        schedule.put(660+x,  Category.LOW);        // 14:00 – 16:00 (pomeriggio lento)
        schedule.put(780+x,  Category.HIGH);       // 16:00 – 19:00 (picco serale/commute)
        schedule.put(960+x,  Category.MEDIUM);     // 19:00 – 22:00 (cena/uscite)
        schedule.put(1140+x, Category.LOW);        // 22:00 – 00:00 (calo netto)
        schedule.put(1260+x, Category.VERY_LOW);   // 00:00 – 03:00

    }

    /** Legge i valori da System properties o defaults */
    public static DailyLambdaSelectorMinutes fromSystemPropertiesOrDefaults() {
        double veryLow  = getDoubleProperty("simulation.lambdasimpleverylow", 1.0);
        double low      = getDoubleProperty("simulation.lambdasimplelow", 2.0);
        double medium   = getDoubleProperty("simulation.lambdasimplemedium", 3.0);
        double high     = getDoubleProperty("simulation.lambdasimplehigh", 4.0);
        double veryHigh = getDoubleProperty("simulation.lambdasimpleveryhigh", 5.0);

        return new DailyLambdaSelectorMinutes(veryLow, low, medium, high, veryHigh);
    }

    private static double getDoubleProperty(String key, double defaultValue) {
        String val = System.getProperty(key);
        if (val != null) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    /** Restituisce la categoria corrente al tempo t (in minuti) */
    public Category getCategory(double timeMinutes) {
        int t = (int) (timeMinutes % (24 * 60)); // modulo 1440 per ciclo giornaliero
        return schedule.floorEntry(t).getValue();
    }

    /** Restituisce la λ corrente in arrivi/minuto */
    public double getLambdaPerMinute(double timeMinutes) {
        Category c = getCategory(timeMinutes);
        return switch (c) {
            case VERY_LOW -> veryLow;
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case VERY_HIGH -> veryHigh;
        };
    }

    /** Restituisce il minuto del prossimo cambio fascia */
    public double getNextSwitchTimeMinutes(double timeMinutes) {
        int minuteOfDay = (int) (timeMinutes % (24 * 60));
        // giorno base (multiplo di 1440)
        int baseDayStart = (int) (Math.floor(timeMinutes / (24.0 * 60)) * (24 * 60));

        Integer nextKey = schedule.higherKey(minuteOfDay);
        if (nextKey != null) {
            // il prossimo cambio avviene nello stesso giorno
            return baseDayStart + nextKey;
        } else {
            // wrap: prossimo cambio è nel giorno successivo (primo key della mappa)
            Integer firstKey = schedule.firstKey(); // tipicamente 0
            return baseDayStart + 24 * 60 + firstKey;
        }
    }
}
