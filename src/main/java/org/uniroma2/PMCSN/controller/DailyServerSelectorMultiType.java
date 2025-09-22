package org.uniroma2.PMCSN.controller;

import java.util.NavigableMap;
import java.util.TreeMap;

public class DailyServerSelectorMultiType {

    public enum Category {
        VERY_LOW, LOW, MEDIUM, HIGH, VERY_HIGH
    }

    // numero di veicoli per tipo
    private final int smallLow, mediumLow, largeLow;
    private final int smallMed, mediumMed, largeMed;
    private final int smallHigh, mediumHigh, largeHigh;
    private final int smallVeryHigh, mediumVeryHigh, largeVeryHigh;
    private final int smallVeryLow, mediumVeryLow, largeVeryLow;

    private final NavigableMap<Integer, Category> schedule = new TreeMap<>();

    public DailyServerSelectorMultiType(
            int smallVeryLow, int mediumVeryLow, int largeVeryLow,
            int smallLow, int mediumLow, int largeLow,
            int smallMed, int mediumMed, int largeMed,
            int smallHigh, int mediumHigh, int largeHigh,
            int smallVeryHigh, int mediumVeryHigh, int largeVeryHigh
    ) {
        this.smallVeryLow = smallVeryLow; this.mediumVeryLow = mediumVeryLow; this.largeVeryLow = largeVeryLow;
        this.smallLow = smallLow; this.mediumLow = mediumLow; this.largeLow = largeLow;
        this.smallMed = smallMed; this.mediumMed = mediumMed; this.largeMed = largeMed;
        this.smallHigh = smallHigh; this.mediumHigh = mediumHigh; this.largeHigh = largeHigh;
        this.smallVeryHigh = smallVeryHigh; this.mediumVeryHigh = mediumVeryHigh; this.largeVeryHigh = largeVeryHigh;

        // esempio di schedule
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

    public double getNextSwitchTimeMinutes(double timeMinutes) {
        int minuteOfDay = (int)(timeMinutes % 1440);
        int baseDayStart = (int)(Math.floor(timeMinutes / 1440.0) * 1440);

        Integer nextKey = schedule.higherKey(minuteOfDay);
        if (nextKey != null) return baseDayStart + nextKey;

        return baseDayStart + 1440 + schedule.firstKey();
    }
}
