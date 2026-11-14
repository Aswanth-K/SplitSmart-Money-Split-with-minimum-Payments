package com.splitsmart.model;

import jakarta.persistence.*;

@Entity
@Table(name = "ss_memberships")
public class Membership {
    @Id
    @Column(length = 80)
    private String id;

    @Column(nullable = false, length = 40)
    private String groupId;

    @Column(nullable = false, length = 30)
    private String username;

    @Column(nullable = false, length = 80)
    private String memberName;

    protected Membership() {}

    public Membership(String group, String username, String name) {
        this.id = group + "|" + username;
        this.groupId = group;
        this.username = username;
        this.memberName = name;
    }

    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getUsername() {
        return username;
    }

    public String getMemberName() {
        return memberName;
    }
}
