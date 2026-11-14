package com.splitsmart.service;

import com.splitsmart.model.Expense;
import java.math.BigDecimal;
import java.util.*;

/** Pure integer-paise allocation and balance calculations. */
public final class ExpenseService {
    private ExpenseService() {}

    public static List<Long> allocate(long cents, String mode, List<String> values) {
        int n = values.size();
        if (n == 0) throw new IllegalArgumentException("Choose at least one friend.");
        List<Long> shares = new ArrayList<>();
        long sum = 0;
        if (mode.equals("equal")) {
            for (int i = 0; i < n; i++) shares.add(cents / n + (i < cents % n ? 1 : 0));
            return List.copyOf(shares);
        }
        if (!mode.equals("exact") && !mode.equals("percentage"))
            throw new IllegalArgumentException("Unknown split method.");
        List<Long> units = new ArrayList<>();
        for (String v : values) {
            long u;
            try {
                u = new BigDecimal(v).movePointRight(2).longValueExact();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "Enter nonnegative shares with at most two decimals.");
            }
            if (u < 0 || u > (mode.equals("exact") ? cents : 10000))
                throw new IllegalArgumentException("Share is out of range.");
            units.add(u);
            sum += u;
        }
        if (sum != (mode.equals("exact") ? cents : 10000))
            throw new IllegalArgumentException(
                    mode.equals("exact")
                            ? "Exact amounts must add up to the bill total."
                            : "Percentages must add up to 100%.");
        if (mode.equals("exact")) return List.copyOf(units);
        long assigned = 0;
        for (long u : units) {
            long share = cents * u / 10000;
            shares.add(share);
            assigned += share;
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < n; i++) order.add(i);
        order.sort(
                (a, b) -> Long.compare(cents * units.get(b) % 10000, cents * units.get(a) % 10000));
        for (int i = 0; i < cents - assigned; i++) {
            int j = order.get(i);
            shares.set(j, shares.get(j) + 1);
        }
        return List.copyOf(shares);
    }

    public static long[] balances(List<String> people, List<Expense> entries) {
        long[] b = new long[people.size()];
        Map<String, Integer> indices = new HashMap<>();
        for (int i = 0; i < people.size(); i++) indices.put(people.get(i), i);
        for (Expense e : entries) {
            b[indices.get(e.payer())] += e.cents();
            for (int i = 0; i < e.members().size(); i++)
                b[indices.get(e.members().get(i))] -= e.shares().get(i);
        }
        return b;
    }
}
