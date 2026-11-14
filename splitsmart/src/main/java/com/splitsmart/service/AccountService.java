package com.splitsmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.exception.ApiException;
import com.splitsmart.model.Account;
import com.splitsmart.model.GroupEntity;
import com.splitsmart.model.GroupState;
import com.splitsmart.model.Membership;
import com.splitsmart.repository.AccountRepository;
import com.splitsmart.repository.GroupRepository;
import com.splitsmart.repository.MembershipRepository;
import com.splitsmart.repository.SystemStateRepository;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final GroupRepository groups;
    private final MembershipRepository memberships;
    private final SystemStateRepository system;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;

    public AccountService(
            AccountRepository accounts,
            GroupRepository groups,
            MembershipRepository memberships,
            SystemStateRepository system,
            PasswordEncoder passwords,
            ObjectMapper json) {
        this.accounts = accounts;
        this.groups = groups;
        this.memberships = memberships;
        this.system = system;
        this.passwords = passwords;
        this.json = json;
    }

    static String username(String value) {
        String name = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9_]{3,30}"))
            throw new ApiException(
                    400, "Username must be 3–30 lowercase letters, digits, or underscores.");
        return name;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, String> register(String input, String display, String password)
            throws Exception {
        String username = username(input);
        String displayName = display == null || display.isBlank() ? username : display.trim();
        if (displayName.length() > 80 || displayName.chars().anyMatch(Character::isISOControl))
            throw new ApiException(400, "Use a display name up to 80 printable characters.");
        if (password == null
                || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(
                    400, "Use a password of at least 8 characters and at most 72 bytes.");
        system.registrationLock()
                .orElseThrow(() -> new IllegalStateException("Database import has not completed"));
        if (accounts.existsById(username))
            throw new ApiException(409, "That username is already registered.");
        boolean first = accounts.count() == 0;
        accounts.saveAndFlush(new Account(username, displayName, passwords.encode(password)));
        if (first)
            for (GroupEntity group : groups.findByOwnerUsernameIsNull()) {
                var state = json.readValue(group.getPayload(), GroupState.class);
                String name =
                        state.members.contains(username)
                                ? username
                                : state.members.contains("You") ? "You" : username;
                if (!state.members.contains(name)) state.members.add(name);
                group.setOwnerUsername(username);
                group.setPayload(json.writeValueAsString(state));
                groups.save(group);
                memberships.save(new Membership(group.getId(), username, name));
            }
        return Map.of("username", username, "displayName", displayName);
    }

    public Map<String, String> me(String username) {
        var account = accounts.findById(username).orElseThrow();
        return Map.of("username", account.getUsername(), "displayName", account.getDisplayName());
    }
}
