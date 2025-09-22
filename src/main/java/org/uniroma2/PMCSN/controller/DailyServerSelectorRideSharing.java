package org.uniroma2.PMCSN.controller;

import org.uniroma2.PMCSN.configuration.ConfigurationManager;

import java.util.NavigableMap;
import java.util.TreeMap;

public class DailyServerSelectorRideSharing {

    public enum Category {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }

    private final int smallVeryLow, mediumVeryLow, largeVeryLow;
    private final int smallLow, mediumLow, largeLow;
    private final int smallMed, mediumMed, largeMed;
    private final int smallHigh, mediumHigh, largeHigh;
    private final int smallVeryHigh, mediumVeryHigh, largeVeryHigh;

    private final NavigableMap<Integer, Category> schedule = new TreeMap<>();

    public DailyServerSelectorRideSharing(ConfigurationManager config) {
        // leggo dal file di configurazione
        this.smallVeryLow = config.getInt("ridesharing", "small.verylow");
        this.mediumVeryLow = config.getInt("ridesharing", "medium.verylow");
        this.largeVeryLow = config.getInt("ridesharing", "large.verylow");

        this.smallLow = config.getInt("ridesharing", "small.low");
        this.mediumLow = config.getInt("ridesharing", "medium.low");
        this.largeLow = config.getInt("ridesharing", "large.low");

        this.smallMed = config.getInt("ridesharing", "small.medium");
        this.mediumMed = config.getInt("ridesharing", "medium.medium");
        this.largeMed = config.getInt("ridesharing", "large.medium");

        this.smallHigh = config.getInt("ridesharing", "small.high");
        this.mediumHigh = config.getInt("ridesharing", "medium.high");
        this.largeHigh = config.getInt("ridesharing", "large.high");

        this.smallVeryHigh = config.getInt("ridesharing", "small.veryhigh");
        this.mediumVeryHigh = config.getInt("ridesharing", "medium.veryhigh");
        this.largeVeryHigh = config.getInt("ridesharing", "large.veryhigh");

        // Weekday — 0 = 03:00
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

    public Category getCategory(double timeMinutes) {
        int t = (int)(timeMinutes % 1440);
        return schedule.floorEntry(t).getValue();
    }

    public int[] getServers(double timeMinutes) {
        Category c = getCategory(timeMinutes);
        return switch (c) {
            case VERY_LOW -> new int[]{smallVeryLow, mediumVeryLow, largeVeryLow};
            case LOW -> new int[]{smallLow, mediumLow, largeLow};
            case MEDIUM -> new int[]{smallMed, mediumMed, largeMed};
            case HIGH -> new int[]{smallHigh, mediumHigh, largeHigh};
            case VERY_HIGH -> new int[]{smallVeryHigh, mediumVeryHigh, largeVeryHigh};
        };
    }
}