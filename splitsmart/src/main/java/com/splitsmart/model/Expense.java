package com.splitsmart.model;

import java.util.*;

public record Expense(
        String id,
        String title,
        long cents,
        String payer,
        List<String> members,
        List<Long> shares,
        String mode,
        String createdBy) {
    public Expense(String id, String title, long cents, String payer, List<String> members) {
        this(id, title, cents, payer, members, equalShares(cents, members.size()), "equal", null);
    }

    public Expense(
            String id,
            String title,
            long cents,
            String payer,
            List<String> members,
            List<Long> shares,
            String mode) {
        this(id, title, cents, payer, members, shares, mode, null);
    }

    private static List<Long> equalShares(long amount, int count) {
        if (count == 0) throw new IllegalArgumentException("Choose at least one friend.");
        List<Long> result = new ArrayList<>();
        for (int i = 0; i < count; i++) result.add(amount / count + (i < amount % count ? 1 : 0));
        return List.copyOf(result);
    }
}
