package com.splitsmart.service;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.exception.ApiException;
import com.splitsmart.integration.payment.GatewayClient;
import com.splitsmart.integration.payment.RazorpayClient;
import com.splitsmart.model.PaymentEntry;
import com.splitsmart.security.HmacSignature;
import java.util.*;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {
    static class FakeGateway implements GatewayClient {
        int orders;
        long amount;
        String currency = "INR", status = "captured";

        public boolean enabled() {
            return true;
        }

        public String keyId() {
            return "rzp_test_fixture";
        }

        public Order createOrder(long amount, String receipt) {
            orders++;
            this.amount = amount;
            return new Order("order_fixture", amount, "INR");
        }

        public Payment fetchPayment(String id) {
            return new Payment(id, "order_fixture", amount, currency, status);
        }

        public Payment findCapturedPayment(String order) {
            return fetchPayment("pay_fixture");
        }

        public boolean validSignature(String order, String payment, String signature) {
            return signature.equals("valid");
        }
    }

    @Test
    void providerVerificationAndReplayProtection() {
        var gateway = new FakeGateway();
        var service = new PaymentService(gateway, new ObjectMapper());
        var ledger = new ArrayList<PaymentEntry>();
        service.create(ledger, "bob", "alice", 5000);
        service.create(ledger, "bob", "alice", 5000);
        assertEquals(1, gateway.orders);
        assertThrows(
                ApiException.class,
                () -> service.verify(ledger, "order_fixture", "pay_fixture", "forged"));
        gateway.amount = 4999;
        assertThrows(
                ApiException.class,
                () -> service.verify(ledger, "order_fixture", "pay_fixture", "valid"));
        gateway.amount = 5000;
        gateway.currency = "USD";
        assertThrows(
                ApiException.class,
                () -> service.verify(ledger, "order_fixture", "pay_fixture", "valid"));
        gateway.currency = "INR";
        gateway.status = "authorized";
        assertThrows(
                ApiException.class,
                () -> service.verify(ledger, "order_fixture", "pay_fixture", "valid"));
        gateway.status = "captured";
        service.reconcile(ledger, "order_fixture");
        service.verify(ledger, "order_fixture", "pay_fixture", "valid");
        assertEquals(1, ledger.size());
        assertTrue(PaymentService.countsAsRepayment(ledger.get(0)));
    }

    @Test
    void simulatorOutcomesAndIdempotency() {
        var gateway = new FakeGateway();
        var service = new PaymentService(gateway, new ObjectMapper());
        var ledger = new ArrayList<PaymentEntry>();
        for (String outcome : List.of("failed", "cancelled", "success")) {
            String id = service.createDemo(ledger, "bob", "alice", 5000).get("orderId").toString();
            service.completeDemo(ledger, id, outcome);
            service.completeDemo(ledger, id, outcome);
            assertEquals(
                    outcome.equals("success"),
                    PaymentService.countsAsRepayment(ledger.get(ledger.size() - 1)));
            assertThrows(ApiException.class, () -> service.completeDemo(ledger, id, "invalid"));
        }
        assertEquals(3, ledger.size());
        assertEquals(0, gateway.orders);
    }

    @Test
    void hmacAndTestKeyRestrictions() {
        var json = new ObjectMapper();
        assertFalse(new RazorpayClient("rzp_live_example", "secret", json).enabled());
        assertFalse(new RazorpayClient("", "", json).enabled());
        assertTrue(
                HmacSignature.verify(
                        "Jefe",
                        "what do ya want for nothing?",
                        "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"));
        assertFalse(
                HmacSignature.verify(
                        "Jefe",
                        "changed",
                        "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843"));
    }
}
