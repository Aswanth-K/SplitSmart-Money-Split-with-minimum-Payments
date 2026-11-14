package com.splitsmart.model;

import java.util.*;

/** A group aggregate saved atomically with its version and membership transaction. */
public class GroupState {
    public List<String> members = new ArrayList<>();
    public List<Expense> expenses = new ArrayList<>();
    public List<PaymentEntry> payments = new ArrayList<>();

    public GroupState() {}
}
