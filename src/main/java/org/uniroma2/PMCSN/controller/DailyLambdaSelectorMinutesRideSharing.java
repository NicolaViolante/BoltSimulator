package org.uniroma2.PMCSN.controller;

import java.util.NavigableMap;
import java.util.TreeMap;

public class DailyLambdaSelectorMinutesRideSharing {

    public enum Category {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }

    private final double veryLow;
    private final double low;
    private final double medium;
    private final double high;
    private final double veryHigh;

    private final NavigableMap<Integer, Category> schedule = new TreeMap<>();

    public DailyLambdaSelectorMinutesRideSharing(double veryLow, double low, double medium,
                                                 double high, double veryHigh) {
        this.veryLow = veryLow;
        this.low = low;
        this.medium = medium;
        this.high = high;
        this.veryHigh = veryHigh;
        int x = 120;
        // stesso schema di fasce giornaliere usato nel caso "simple"
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
    public static DailyLambdaSelectorMinutesRideSharing fromSystemPropertiesOrDefaults() {
        double veryLow  = getDoubleProperty("simulation.lambdarideverylow", 0.5);
        double low      = getDoubleProperty("simulation.lambdaridelow", 1.2);
        double medium   = getDoubleProperty("simulation.lambdaridemedium", 2.5);
        double high     = getDoubleProperty("simulation.lambdaridehigh", 3.8);
        double veryHigh = getDoubleProperty("simulation.lambdarideveryhigh", 5.0);

        return new DailyLambdaSelectorMinutesRideSharing(veryLow, low, medium, high, veryHigh);
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

    public Category getCategory(double timeMinutes) {
        int t = (int) (timeMinutes % (24 * 60));
        return schedule.floorEntry(t).getValue();
    }

    public double getLambdaPerMinute(double timeMinutes) {
        return switch (getCategory(timeMinutes)) {
            case VERY_LOW -> veryLow;
            case LOW -> low;
            case MEDIUM -> medium;
            case HIGH -> high;
            case VERY_HIGH -> veryHigh;
        };
    }

    public double getNextSwitchTimeMinutes(double timeMinutes) {
        int minuteOfDay = (int) (timeMinutes % (24 * 60));
        int baseDayStart = (int) (Math.floor(timeMinutes / (24.0 * 60)) * (24 * 60));

        Integer nextKey = schedule.higherKey(minuteOfDay);
        if (nextKey != null) {
            return baseDayStart + nextKey;
        } else {
            Integer firstKey = schedule.firstKey();
            return baseDayStart + 24 * 60 + firstKey;
        }
    }
}