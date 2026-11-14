package com.splitsmart.controller;

import com.splitsmart.service.GroupService;
import com.splitsmart.service.PaymentService;
import java.security.Principal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {
    private final GroupService groups;
    private final PaymentService payments;

    public PaymentController(GroupService groups, PaymentService payments) {
        this.groups = groups;
        this.payments = payments;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return payments.config();
    }

    @PostMapping(value = "/{operation}", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<Map<String, Object>> payment(
            Principal user,
            @PathVariable String operation,
            @RequestParam String group,
            @RequestParam MultiValueMap<String, String> form) {
        return ResponseEntity.status(operation.endsWith("orders") ? 201 : 200)
                .body(groups.payment(user.getName(), group, operation, form));
    }
}
