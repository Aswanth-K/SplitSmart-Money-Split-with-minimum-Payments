package com.splitsmart.controller;

import com.splitsmart.service.AccountService;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, String>> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("token", token.getToken(), "headerName", token.getHeaderName()));
    }

    @GetMapping("/me")
    public Map<String, String> me(Principal principal) {
        return accounts.me(principal.getName());
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @RequestParam String username,
            @RequestParam(defaultValue = "") String displayName,
            @RequestParam String password)
            throws Exception {
        return ResponseEntity.status(201).body(accounts.register(username, displayName, password));
    }
}
