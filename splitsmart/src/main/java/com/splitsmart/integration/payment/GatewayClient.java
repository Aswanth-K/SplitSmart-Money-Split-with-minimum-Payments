package com.splitsmart.integration.payment;

/** Boundary for the external provider; tests use an isolated in-memory implementation. */
public interface GatewayClient {
    record Order(String id, long amount, String currency) {}

    record Payment(String id, String orderId, long amount, String currency, String status) {}

    boolean enabled();

    String keyId();

    Order createOrder(long amount, String receipt);

    Payment fetchPayment(String id);

    Payment findCapturedPayment(String orderId);

    boolean validSignature(String orderId, String paymentId, String signature);
}
