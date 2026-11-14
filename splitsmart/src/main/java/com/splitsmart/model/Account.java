package com.splitsmart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ss_accounts")
public class Account {
    @Id
    @Column(length = 30)
    private String username;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    protected Account() {}

    public Account(String username, String displayName, String hash) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = hash;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    @com.fasterxml.jackson.annotation.JsonIgnore
    public String getPasswordHash() {
        return passwordHash;
    }
}
