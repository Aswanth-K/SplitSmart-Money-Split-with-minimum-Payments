CREATE TABLE ss_accounts (
 username VARCHAR(30) PRIMARY KEY,
 display_name VARCHAR(80) NOT NULL,
 password_hash VARCHAR(100) NOT NULL
);
CREATE TABLE ss_groups (
 id VARCHAR(40) PRIMARY KEY,
 name VARCHAR(80) NOT NULL,
 owner_username VARCHAR(30) REFERENCES ss_accounts(username),
 payload TEXT NOT NULL,
 version BIGINT NOT NULL DEFAULT 0
);
CREATE TABLE ss_memberships (
 id VARCHAR(80) PRIMARY KEY,
 group_id VARCHAR(40) NOT NULL REFERENCES ss_groups(id),
 username VARCHAR(30) NOT NULL REFERENCES ss_accounts(username),
 member_name VARCHAR(80) NOT NULL,
 UNIQUE(group_id,username),
 UNIQUE(group_id,member_name)
);
CREATE INDEX membership_user ON ss_memberships(username);
CREATE TABLE ss_invitations (
 id VARCHAR(40) PRIMARY KEY,
 group_id VARCHAR(40) NOT NULL REFERENCES ss_groups(id),
 target_username VARCHAR(30) NOT NULL REFERENCES ss_accounts(username),
 invited_by VARCHAR(30) NOT NULL REFERENCES ss_accounts(username),
 status VARCHAR(16) NOT NULL,
 created_at VARCHAR(40) NOT NULL
);
CREATE INDEX invitation_target_status ON ss_invitations(target_username,status);
CREATE TABLE ss_system (id VARCHAR(40) PRIMARY KEY);
