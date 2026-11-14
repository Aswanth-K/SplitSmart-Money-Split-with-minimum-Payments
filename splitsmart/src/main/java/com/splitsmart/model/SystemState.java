package com.splitsmart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ss_system")
public class SystemState {
    @Id
    @Column(length = 40)
    private String id;

    protected SystemState() {}

    public SystemState(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }
}
