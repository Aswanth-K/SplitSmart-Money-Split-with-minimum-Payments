package com.splitsmart.model;

public record PaymentEntry(
        String orderId,
        String from,
        String to,
        long cents,
        String status,
        String paymentId,
        String createdAt,
        String verifiedAt) {}
