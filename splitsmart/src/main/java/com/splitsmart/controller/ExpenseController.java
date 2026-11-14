package com.splitsmart.controller;

import com.splitsmart.exception.ApiException;
import com.splitsmart.service.GroupService;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ExpenseController {
    private final GroupService groups;

    public ExpenseController(GroupService groups) {
        this.groups = groups;
    }

    @GetMapping("/groups")
    public Map<String, Object> list(Principal user) {
        return groups.list(user.getName());
    }

    @PostMapping("/groups")
    public ResponseEntity<Map<String, Object>> create(Principal user, @RequestParam String name) {
        return ResponseEntity.status(201).body(groups.create(user.getName(), name));
    }

    @GetMapping("/state")
    public Map<String, Object> state(Principal user, @RequestParam String group) {
        return groups.state(user.getName(), group);
    }

    @PostMapping(
            value = {"/members", "/expenses", "/delete", "/reset", "/demo"},
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> mutate(
            Principal user,
            @RequestParam String group,
            @RequestParam MultiValueMap<String, String> form,
            jakarta.servlet.http.HttpServletRequest request) {
        return groups.mutate(user.getName(), group, request.getRequestURI().substring(5), form);
    }

    @GetMapping("/users")
    public List<Map<String, String>> search(
            Principal user, @RequestParam String group, @RequestParam(defaultValue = "") String q) {
        return groups.searchUsers(user.getName(), group, q);
    }

    @GetMapping("/invitations")
    public List<Map<String, String>> invitations(Principal user) {
        return groups.invitations(user.getName());
    }

    @PostMapping("/invitations/{id}/{action}")
    public Map<String, String> answer(
            Principal user, @PathVariable String id, @PathVariable String action) {
        if (!Set.of("accept", "decline").contains(action))
            throw new ApiException(404, "Unknown invitation action.");
        return groups.answerInvitation(user.getName(), id, action.equals("accept"));
    }
}
