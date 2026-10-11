package com.saaketh.budget.recurring;

import com.saaketh.budget.recurring.RecurringDetector.Cadence;
import com.saaketh.budget.recurring.RecurringDetector.Charge;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates fake but realistic statement history for one student, with the right answers attached,
 * so the detector's accuracy can be measured. Seeded, so every run produces the same data.
 *
 * <p>Includes the hard cases on purpose: price increases, posting-date jitter, a skipped month,
 * varying bills, cancelled and newly started subscriptions, and look-alikes that are NOT recurring
 * (coffee, groceries, rides, takeout at a similar price).
 */
final class SyntheticStatements {

    /** The right answer for one merchant. cadence null = not recurring. */
    record Label(Cadence cadence, boolean active) {
        boolean recurring() {
            return cadence != null;
        }
    }

    record Dataset(List<Charge> charges, Map<String, Label> labels, LocalDate asOf) {
    }

    static final int MONTHS = 18;

    private final Random random;
    private final LocalDate start;
    private final LocalDate end;
    private final List<Charge> charges = new ArrayList<>();
    private final Map<String, Label> labels = new LinkedHashMap<>();

    private SyntheticStatements(long seed) {
        random = new Random(seed);
        start = LocalDate.of(2025, 1, 1);
        end = start.plusMonths(MONTHS).minusDays(1);
    }

    static Dataset generate(long seed) {
        SyntheticStatements s = new SyntheticStatements(seed);
        s.recurring();
        s.lookAlikes();
        return new Dataset(List.copyOf(s.charges), Map.copyOf(s.labels), s.end);
    }

    // --- things that ARE recurring ---

    private void recurring() {
        // Streaming with a price increase partway through.
        monthly("NETFLIX.COM", "15.49", "17.99", 2, true);
        if (chance(0.7)) {
            // Card-processor IDs that change every month.
            monthlyVaryingSuffix("SPOTIFY USA ", "11.99");
        }
        if (chance(0.6)) {
            monthly("PLANET FITNESS #" + digits(4), "24.99", null, 3, true);
        }
        // Phone bill: amount varies a little every month.
        variableMonthly("VERIZON WIRELESS PAYMENTS", 55, 72);
        if (chance(0.5)) {
            // Electric: varies a lot with the seasons.
            variableMonthly("DUKE ENERGY PAYMENT", 38, 105);
        }
        if (chance(0.5)) {
            // Bills every 4 weeks rather than monthly.
            every("NYTIMES DIGITAL", "4.00", 28, Cadence.MONTHLY, 1);
        }
        if (chance(0.4)) {
            every("CAMPUS LAUNDRY", "12.00", 7, Cadence.WEEKLY, 1);
        }
        if (chance(0.5)) {
            yearlyPrime();
        }
        if (chance(0.4)) {
            quarterly("GEICO AUTO", "312.00");
        }
        if (chance(0.6)) {
            // Cancelled a while ago.
            cancelled("HULU 877-8244858", "7.99");
        }
        if (chance(0.5)) {
            // Started only recently: just 3 charges so far.
            recentStart("DISNEY PLUS", "13.99");
        }
        if (chance(0.5)) {
            skippedMonth("CRUNCHYROLL", "7.99");
        }
    }

    private void monthly(String description, String price, String newPrice, int jitterDays, boolean active) {
        int day = 1 + random.nextInt(28);
        int priceChangeMonth = 4 + random.nextInt(MONTHS - 6);
        for (int m = 0; m < MONTHS; m++) {
            String amount = newPrice != null && m >= priceChangeMonth ? newPrice : price;
            add(jitter(start.plusMonths(m).withDayOfMonth(day), jitterDays), description, amount);
        }
        label(description, Cadence.MONTHLY, active);
    }

    private void monthlyVaryingSuffix(String prefix, String price) {
        int day = 1 + random.nextInt(28);
        String first = null;
        for (int m = 0; m < MONTHS; m++) {
            String description = prefix + "P" + digits(3) + "A" + digits(4);
            first = first == null ? description : first;
            add(jitter(start.plusMonths(m).withDayOfMonth(day), 1), description, price);
        }
        label(first, Cadence.MONTHLY, true);
    }

    private void variableMonthly(String description, int low, int high) {
        int day = 1 + random.nextInt(28);
        for (int m = 0; m < MONTHS; m++) {
            add(jitter(start.plusMonths(m).withDayOfMonth(day), 2), description, cents(low, high));
        }
        label(description, Cadence.MONTHLY, true);
    }

    private void every(String description, String price, int days, Cadence cadence, int jitterDays) {
        for (LocalDate d = start.plusDays(random.nextInt(days)); !d.isAfter(end); d = d.plusDays(days)) {
            add(jitter(d, jitterDays), description, price);
        }
        label(description, cadence, true);
    }

    private void yearlyPrime() {
        LocalDate first = start.plusDays(random.nextInt(150));
        add(first, "AMAZON PRIME*" + letters(6), "139.00");
        add(jitter(first.plusYears(1), 1), "AMAZON PRIME*" + letters(6), "139.00");
        label("AMAZON PRIME*X", Cadence.YEARLY, true);
    }

    private void quarterly(String description, String price) {
        LocalDate first = start.plusDays(random.nextInt(60));
        for (int q = 0; first.plusMonths(3L * q).isBefore(end); q++) {
            add(jitter(first.plusMonths(3L * q), 2), description, price);
        }
        label(description, Cadence.QUARTERLY, true);
    }

    private void cancelled(String description, String price) {
        int day = 1 + random.nextInt(28);
        int months = 4 + random.nextInt(6); // stopped at least 8 months before the end
        for (int m = 0; m < months; m++) {
            add(jitter(start.plusMonths(m).withDayOfMonth(day), 1), description, price);
        }
        label(description, Cadence.MONTHLY, false);
    }

    private void recentStart(String description, String price) {
        int day = 1 + random.nextInt(28);
        for (int m = MONTHS - 3; m < MONTHS; m++) {
            add(jitter(start.plusMonths(m).withDayOfMonth(day), 1), description, price);
        }
        label(description, Cadence.MONTHLY, true);
    }

    private void skippedMonth(String description, String price) {
        int day = 1 + random.nextInt(28);
        int skipped = 2 + random.nextInt(MONTHS - 4);
        for (int m = 0; m < MONTHS; m++) {
            if (m != skipped) {
                add(jitter(start.plusMonths(m).withDayOfMonth(day), 1), description, price);
            }
        }
        label(description, Cadence.MONTHLY, true);
    }

    // --- look-alikes that are NOT recurring ---

    private void lookAlikes() {
        // Coffee a few times a week, nearly the same price every time.
        randomVisits("SQ *JOES COFFEE " + digits(4), 2, 6, 4, 7);
        randomVisits("STARBUCKS STORE " + digits(5), 3, 12, 5, 6);
        // Groceries roughly weekly, but the amount changes a lot.
        randomVisits("HARRIS TEETER #" + digits(4), 5, 10, 25, 140);
        randomVisits("TRADER JOE S #" + digits(3), 10, 25, 20, 90);
        // Rides and takeout at random.
        randomVisits("UBER *TRIP", 3, 15, 8, 32);
        randomVisits("DOORDASH*CHIPOTLE", 6, 20, 14, 26);
        // Takeout about monthly, at a steady price: the hardest look-alike.
        randomVisits("CHIPOTLE ONLINE", 15, 45, 11, 14);
        randomVisits("AMAZON MKTPL*" + letters(6), 4, 20, 9, 85);
        // A couple of one-off big purchases.
        add(start.plusDays(40 + random.nextInt(100)), "BEST BUY " + digits(5), cents(80, 400));
        add(start.plusDays(300 + random.nextInt(100)), "BEST BUY " + digits(5), cents(80, 400));
        add(start.plusDays(random.nextInt(500)), "UNC STUDENT STORES", cents(20, 180));
        // Money in: paychecks every two weeks and a refund. Never subscriptions.
        for (LocalDate d = start.plusDays(4); !d.isAfter(end); d = d.plusDays(14)) {
            charges.add(new Charge(d, "PAYROLL DIRECT DEP", new BigDecimal("640.00")));
        }
        charges.add(new Charge(start.plusDays(random.nextInt(500)), "NETFLIX.COM", new BigDecimal("15.49")));
    }

    /** Visits with random gaps (minGap..maxGap days) and amounts (low..high dollars). */
    private void randomVisits(String description, int minGap, int maxGap, int low, int high) {
        boolean sameEveryTime = description.startsWith("AMAZON MKTPL");
        String key = null;
        for (LocalDate d = start.plusDays(random.nextInt(maxGap)); !d.isAfter(end);
                d = d.plusDays(minGap + random.nextInt(maxGap - minGap + 1))) {
            String text = sameEveryTime ? "AMAZON MKTPL*" + letters(6) : description;
            key = key == null ? text : key;
            add(d, text, cents(low, high));
        }
        label(key, null, false);
    }

    // --- helpers ---

    private void add(LocalDate date, String description, String amountSpent) {
        if (!date.isBefore(start) && !date.isAfter(end)) {
            charges.add(new Charge(date, description, new BigDecimal(amountSpent).negate()));
        }
    }

    private void label(String anyDescription, Cadence cadence, boolean active) {
        labels.put(MerchantKey.of(anyDescription), new Label(cadence, active));
    }

    private LocalDate jitter(LocalDate date, int days) {
        return date.plusDays(random.nextInt(2 * days + 1) - days);
    }

    private boolean chance(double p) {
        return random.nextDouble() < p;
    }

    private String cents(int lowDollars, int highDollars) {
        int c = lowDollars * 100 + random.nextInt((highDollars - lowDollars) * 100 + 1);
        return BigDecimal.valueOf(c).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private String digits(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** A reference code like "2K4LM8". Always has a digit, as real ones nearly always do (see MerchantKey). */
    private String letters(int n) {
        StringBuilder sb = new StringBuilder().append(random.nextInt(10));
        for (int i = 1; i < n; i++) {
            sb.append(random.nextBoolean() ? (char) ('A' + random.nextInt(26)) : (char) ('0' + random.nextInt(10)));
        }
        return sb.toString();
    }
}
