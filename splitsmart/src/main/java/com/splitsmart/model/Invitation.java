package com.splitsmart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ss_invitations")
public class Invitation {
    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 40)
    private String groupId;

    @Column(nullable = false, length = 30)
    private String targetUsername;

    @Column(nullable = false, length = 30)
    private String invitedBy;

    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(nullable = false, length = 40)
    private String createdAt;

    protected Invitation() {}

    public Invitation(String group, String target, String by) {
        this.id = java.util.UUID.randomUUID().toString();
        this.groupId = group;
        this.targetUsername = target;
        this.invitedBy = by;
        this.createdAt = java.time.Instant.now().toString();
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getTargetUsername() {
        return targetUsername;
    }

    public String getInvitedBy() {
        return invitedBy;
    }

    public String getStatus() {
        return status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
