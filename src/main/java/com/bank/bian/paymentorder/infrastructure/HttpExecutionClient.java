package com.bank.bian.paymentorder.infrastructure;

import com.bank.bian.paymentorder.domain.ExecutionClient;
import com.bank.bian.paymentorder.domain.PaymentOrder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Phase 2d-ii loop closure: real hand-off to Payment Execution.
 *
 *   bian.payments.execution-url: http://sd-payment-execution.bian-operations:8080
 *
 * PE's /initiate runs the debit-credit saga SYNCHRONOUSLY and returns the
 * terminal outcome — so the outcome comes back in-band and Payment Order
 * completes/fails immediately, no callback round-trip needed on this path.
 */
@Component
@ConditionalOnProperty(name = "bian.payments.execution-url")
public class HttpExecutionClient implements ExecutionClient {

    private static final Logger log = LoggerFactory.getLogger("bian.execution-handoff");

    private final RestClient rest;
    private final String executionUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public HttpExecutionClient(RestClient.Builder builder,
                               @Value("${bian.payments.execution-url}") String executionUrl) {
        this.rest = builder.build();
        this.executionUrl = executionUrl;
    }

    @Override
    public SubmitResult submit(PaymentOrder order) {
        try {
            String body = rest.post()
                    .uri(executionUrl + "/v1/payment-transaction-procedure/initiate")
                    .header("Content-Type", "application/json")
                    .body(Map.of(
                            "orderRef", order.getOrderId(),
                            "debtorAccountRef", order.getDebtorAccountRef(),
                            "creditorAccountRef", order.getCreditorAccountRef(),
                            "amountMinor", order.getAmountMinor(),
                            "currency", order.getCurrency()))
                    .retrieve()
                    .body(String.class);
            JsonNode exec = mapper.readTree(body);
            String status = exec.path("status").asText();
            String reason = exec.path("failureReason").asText(null);
            // PE's terminal states: COMPLETED | FAILED_DEBIT | FAILED_COMPENSATED | FAILED_SUSPENSE
            return SubmitResult.outcome("COMPLETED".equals(status),
                    reason != null ? reason : status);
        } catch (Exception e) {
            // Hand-off itself failed — order stays SUBMITTED... no: submit() is
            // called pre-transition; report not-handed-off so the order remains
            // VALIDATED and can be retried or cancelled.
            log.warn("execution hand-off failed for {}: {}", order.getOrderId(), e.getMessage());
            return SubmitResult.failed("EXECUTION_UNREACHABLE: " + e.getMessage());
        }
    }
}
