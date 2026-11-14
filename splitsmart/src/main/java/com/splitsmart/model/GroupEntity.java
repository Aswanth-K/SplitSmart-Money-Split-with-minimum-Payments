package com.splitsmart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ss_groups")
public class GroupEntity {
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 30)
    private String ownerUsername;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Version private long version;

    protected GroupEntity() {}

    public GroupEntity(String id, String name, String owner, String payload) {
        this.id = id;
        this.name = name;
        this.ownerUsername = owner;
        this.payload = payload;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getOwnerUsername() {
        return ownerUsername;
    }

    public String getPayload() {
        return payload;
    }

    public long getVersion() {
        return version;
    }

    public void setOwnerUsername(String ownerUsername) {
        this.ownerUsername = ownerUsername;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }
}
