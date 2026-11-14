package com.splitsmart.security;

import com.splitsmart.repository.AccountRepository;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final AccountRepository accounts;

    public DatabaseUserDetailsService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return accounts.findById(username)
                .map(
                        account ->
                                User.withUsername(account.getUsername())
                                        .password(account.getPasswordHash())
                                        .roles("USER")
                                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
    }
}
