package com.splitsmart.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.exception.ApiException;
import com.splitsmart.integration.payment.GatewayClient;
import com.splitsmart.model.PaymentEntry;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

/** Payment verification and simulation inside a locked group database transaction. */
@Service
public class PaymentService {
    private final GatewayClient gateway;
    private final ObjectMapper json;

    public PaymentService(GatewayClient gateway, ObjectMapper json) {
        this.gateway = gateway;
        this.json = json;
    }

    public Map<String, Object> config() {
        return Map.of(
                "simulatorEnabled",
                true,
                "enabled",
                gateway.enabled(),
                "mode",
                "test",
                "provider",
                "razorpay",
                "message",
                gateway.enabled()
                        ? "Test mode only. No real money moves to friends."
                        : "Test payments need server configuration. See PAYMENTS.md.");
    }

    public synchronized Map<String, Object> createDemo(
            List<PaymentEntry> records, String from, String to, long amount) {
        if (amount <= 0 || amount > 100000000) throw new ApiException(400, "Invalid demo amount.");
        PaymentEntry order =
                records.stream()
                        .filter(
                                e ->
                                        e.status().equals("demo_created")
                                                && e.from().equals(from)
                                                && e.to().equals(to)
                                                && e.cents() == amount)
                        .findFirst()
                        .orElse(null);
        if (order == null) {
            if (records.size() >= 10000)
                throw new ApiException(400, "Payment history limit reached.");
            order =
                    new PaymentEntry(
                            "demo_" + UUID.randomUUID(),
                            from,
                            to,
                            amount,
                            "demo_created",
                            "",
                            Instant.now().toString(),
                            "");
            records.add(order);
        }
        return Map.of(
                "orderId",
                order.orderId(),
                "amount",
                order.cents(),
                "from",
                from,
                "to",
                to,
                "mode",
                "demo");
    }

    public synchronized PaymentEntry demoOrder(List<PaymentEntry> records, String id) {
        if (!id.startsWith("demo_")) throw new ApiException(400, "Not a demo order.");
        return records.stream()
                .filter(e -> e.orderId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ApiException(404, "Demo order not found in this group."));
    }

    public synchronized void completeDemo(List<PaymentEntry> records, String id, String outcome) {
        if (!Set.of("success", "failed", "cancelled").contains(outcome))
            throw new ApiException(400, "Choose success, failed, or cancelled.");
        PaymentEntry order = demoOrder(records, id);
        String status = "demo_" + outcome;
        if (!order.status().equals("demo_created")) {
            if (order.status().equals(status)) return;
            throw new ApiException(
                    409, "This demo attempt already has an outcome. Start a new attempt.");
        }
        int index = records.indexOf(order);
        records.set(
                index,
                new PaymentEntry(
                        order.orderId(),
                        order.from(),
                        order.to(),
                        order.cents(),
                        status,
                        outcome.equals("success") ? "sim_" + UUID.randomUUID() : "",
                        order.createdAt(),
                        Instant.now().toString()));
    }

    static boolean countsAsRepayment(PaymentEntry entry) {
        return entry.status().equals("verified") || entry.status().equals("demo_success");
    }

    public synchronized Map<String, Object> create(
            List<PaymentEntry> records, String from, String to, long amount) {
        if (!gateway.enabled())
            throw new ApiException(
                    503, "Configure Razorpay test keys on the server to enable checkout.");
        if (amount < 100 || amount > 100000000)
            throw new ApiException(400, "Test checkout supports repayments from ₹1 to ₹1,000,000.");
        // Reuse an unverified order for retries instead of issuing duplicate payment attempts.
        PaymentEntry order =
                records.stream()
                        .filter(
                                e ->
                                        e.status().equals("created")
                                                && e.from().equals(from)
                                                && e.to().equals(to)
                                                && e.cents() == amount)
                        .findFirst()
                        .orElse(null);
        if (order == null) {
            if (records.size() >= 10000)
                throw new ApiException(400, "Payment history limit reached.");
            var external = gateway.createOrder(amount, UUID.randomUUID().toString());
            if (external.amount() != amount || !external.currency().equals("INR"))
                throw new ApiException(502, "Unexpected payment order.");
            order =
                    new PaymentEntry(
                            external.id(),
                            from,
                            to,
                            amount,
                            "created",
                            "",
                            Instant.now().toString(),
                            "");
            records.add(order);
        }
        return Map.of(
                "orderId",
                order.orderId(),
                "amount",
                order.cents(),
                "currency",
                "INR",
                "keyId",
                gateway.keyId(),
                "from",
                from,
                "to",
                to,
                "mode",
                "test");
    }

    public synchronized void reconcile(List<PaymentEntry> records, String orderId) {
        if (!gateway.enabled())
            throw new ApiException(503, "Razorpay test payments are not configured.");
        PaymentEntry order =
                records.stream()
                        .filter(e -> e.orderId().equals(orderId))
                        .findFirst()
                        .orElseThrow(() -> new ApiException(404, "Order not found in this group."));
        if (order.status().equals("verified")) return;
        var payment = gateway.findCapturedPayment(orderId);
        recordVerified(records, orderId, payment.id());
    }

    public synchronized void verify(
            List<PaymentEntry> records, String orderId, String paymentId, String signature) {
        if (!gateway.enabled())
            throw new ApiException(503, "Razorpay test payments are not configured.");
        int index = -1;
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).orderId().equals(orderId)) index = i;
        if (index < 0) throw new ApiException(404, "Payment order does not belong to this group.");
        PaymentEntry order = records.get(index);
        if (!gateway.validSignature(order.orderId(), paymentId, signature))
            throw new ApiException(400, "Payment signature verification failed.");
        recordVerified(records, orderId, paymentId);
    }

    private void recordVerified(List<PaymentEntry> records, String orderId, String paymentId) {
        int index = -1;
        for (int i = 0; i < records.size(); i++)
            if (records.get(i).orderId().equals(orderId)) index = i;
        if (index < 0) throw new ApiException(404, "Order not found.");
        PaymentEntry order = records.get(index);
        if (order.status().equals("verified")) {
            if (!order.paymentId().equals(paymentId))
                throw new ApiException(409, "Order already has a different verified payment.");
            return; // idempotent callback; no second balance adjustment
        }
        var payment = gateway.fetchPayment(paymentId);
        if (!payment.id().equals(paymentId)
                || !payment.orderId().equals(order.orderId())
                || payment.amount() != order.cents()
                || !payment.currency().equals("INR"))
            throw new ApiException(400, "Payment details do not match the saved order.");
        if (!payment.status().equals("captured"))
            throw new ApiException(
                    409,
                    "Payment is not captured yet. Retry verification from payment history; do not pay again.");
        if (records.stream().anyMatch(e -> e.paymentId().equals(paymentId)))
            throw new ApiException(409, "Payment has already been recorded.");
        records.set(
                index,
                new PaymentEntry(
                        order.orderId(),
                        order.from(),
                        order.to(),
                        order.cents(),
                        "verified",
                        paymentId,
                        order.createdAt(),
                        Instant.now().toString()));
    }

    public String json(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (IOException e) {
            throw new ApiException(500, "Could not serialize payment data.");
        }
    }
}
