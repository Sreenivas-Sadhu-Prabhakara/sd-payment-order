package com.bank.bian.paymentorder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Boot + API smoke: intake → submitted → execution result, through HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationTests {

    static final String CR = "/v1/payment-order-procedure";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    String url(String path) { return "http://localhost:" + port + path; }

    @Test
    void orderJourney_initiateAutoSubmitComplete() {
        var created = rest.postForEntity(url(CR + "/initiate"),
                Map.of("debtorAccountRef", "CA-D1", "creditorAccountRef", "CA-C1",
                        "amountMinor", 250_000, "currency", "INR", "remittanceInfo", "invoice 42"),
                Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().get("status")).isEqualTo("SUBMITTED"); // auto-submit default
        String id = (String) created.getBody().get("orderId");

        var done = rest.exchange(url(CR + "/" + id + "/execution-result"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("completed", true, "reason", "settled")),
                Map.class);
        assertThat(done.getBody().get("status")).isEqualTo("COMPLETED");

        // cancel after submission/completion → 409
        var cancel = rest.exchange(url(CR + "/" + id + "/control"),
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("action", "cancel")), Map.class);
        assertThat(cancel.getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void invalidOrderIsCreatedAsRejectedWithReason() {
        var created = rest.postForEntity(url(CR + "/initiate"),
                Map.of("debtorAccountRef", "CA-S", "creditorAccountRef", "CA-S",
                        "amountMinor", 1_000, "currency", "INR"),
                Map.class);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        assertThat(created.getBody().get("status")).isEqualTo("REJECTED");
        assertThat(created.getBody().get("statusReason")).isEqualTo("SELF_TRANSFER");
    }
}
