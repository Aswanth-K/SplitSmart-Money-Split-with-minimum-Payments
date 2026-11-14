package com.splitsmart.service;

import com.splitsmart.model.Expense;
import java.util.*;
import org.junit.jupiter.api.Test;

public class ExpenseServiceTest {
    @Test
    void allocationsAndBalances() {
        var people = List.of("A", "B", "C");
        var b =
                ExpenseService.balances(
                        people, List.of(new Expense("1", "Odd cents", 100, "A", people)));
        if (!Arrays.equals(b, new long[] {66, -33, -33}))
            throw new AssertionError("Rounding mismatch");
        if (!ExpenseService.allocate(100, "exact", List.of("0.20", "0.80"))
                .equals(List.of(20L, 80L))) throw new AssertionError("Exact split");
        if (!ExpenseService.allocate(101, "percentage", List.of("50", "25", "25"))
                .equals(List.of(51L, 25L, 25L))) throw new AssertionError("Percentage rounding");
        for (String mode : List.of("exact", "percentage")) {
            try {
                ExpenseService.allocate(100, mode, List.of("0.20", "0.70"));
                throw new AssertionError("Bad total accepted");
            } catch (IllegalArgumentException expected) {
            }
        }
        Random r = new Random(42);
        for (int trial = 0; trial < 1000; trial++) {
            List<Expense> entries = new ArrayList<>();
            long[] expected = new long[3];
            for (int j = 0; j < 20; j++) {
                long amount = 1 + r.nextInt(100000);
                int payer = r.nextInt(3);
                var subset = people.subList(0, 1 + r.nextInt(3));
                entries.add(new Expense("x", "Test", amount, people.get(payer), subset));
                expected[payer] += amount;
                for (int k = 0; k < subset.size(); k++)
                    expected[k] -= amount / subset.size() + (k < amount % subset.size() ? 1 : 0);
            }
            long[] actual = ExpenseService.balances(people, entries);
            if (!Arrays.equals(actual, expected) || Arrays.stream(actual).sum() != 0)
                throw new AssertionError("Balance mismatch");
        }
        System.out.println("PASS: exact rounding and 1,000 randomized balance cases");
    }
}
