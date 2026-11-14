package com.splitsmart.integration.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitsmart.exception.ApiException;
import com.splitsmart.security.HmacSignature;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Test-mode only. Secrets never leave this server or appear in responses/log messages. */
@Component
public class RazorpayClient implements GatewayClient {
    private final String keyId, secret;
    private final ObjectMapper json;
    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public RazorpayClient(
            @Value("${razorpay.key-id:}") String keyId,
            @Value("${razorpay.key-secret:}") String secret,
            ObjectMapper json) {
        this.keyId = keyId.trim();
        this.secret = secret.trim();
        this.json = json;
    }

    public boolean enabled() {
        return keyId.startsWith("rzp_test_") && !secret.isBlank();
    }

    public String keyId() {
        return enabled() ? keyId : "";
    }

    private com.fasterxml.jackson.databind.JsonNode call(String method, String path, Object body) {
        if (!enabled())
            throw new ApiException(
                    503, "Configure Razorpay test keys on the server to enable checkout.");
        try {
            var builder =
                    HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/" + path))
                            .timeout(Duration.ofSeconds(20))
                            .header(
                                    "Authorization",
                                    "Basic "
                                            + Base64.getEncoder()
                                                    .encodeToString(
                                                            (keyId + ":" + secret)
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))
                            .header("Content-Type", "application/json");
            builder.method(
                    method,
                    body == null
                            ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new ApiException(
                        502,
                        "Razorpay could not complete the request. Check the test credentials or retry.");
            return json.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(502, "Payment request interrupted. Please retry.");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(502, "Could not reach Razorpay. Please retry.");
        }
    }

    public Order createOrder(long amount, String receipt) {
        var node =
                call(
                        "POST",
                        "orders",
                        Map.of("amount", amount, "currency", "INR", "receipt", receipt));
        String id = node.path("id").asText();
        if (!id.matches("order_[A-Za-z0-9]+")
                || node.path("amount").asLong() != amount
                || !node.path("currency").asText().equals("INR"))
            throw new ApiException(502, "Razorpay returned an unexpected order.");
        return new Order(id, amount, "INR");
    }

    public Payment fetchPayment(String id) {
        if (!id.matches("pay_[A-Za-z0-9]+")) throw new ApiException(400, "Invalid payment ID.");
        var node = call("GET", "payments/" + id, null);
        return new Payment(
                node.path("id").asText(),
                node.path("order_id").asText(),
                node.path("amount").asLong(),
                node.path("currency").asText(),
                node.path("status").asText());
    }

    public Payment findCapturedPayment(String orderId) {
        if (!orderId.matches("order_[A-Za-z0-9]+"))
            throw new ApiException(400, "Invalid order ID.");
        var result = call("GET", "orders/" + orderId + "/payments", null);
        for (var node : result.path("items"))
            if (node.path("status").asText().equals("captured"))
                return fetchPayment(node.path("id").asText());
        throw new ApiException(
                409,
                "No captured payment found yet. Do not pay again while verification is pending.");
    }

    public boolean validSignature(String orderId, String paymentId, String signature) {
        return enabled() && HmacSignature.verify(secret, orderId + "|" + paymentId, signature);
    }
}
