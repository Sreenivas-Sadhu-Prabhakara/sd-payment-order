package com.bank.bian.paymentorder.domain;

/**
 * Outbound port to Payment Execution. The submission hand-off is the
 * payments-flagship choreography seam:
 *   - Phase 2b-c default: logging adapter — submission is recorded and the
 *     order rests in SUBMITTED until Payment Execution reports back via
 *     PUT /{orderId}/execution-result (manual or scripted).
 *   - In-cluster HTTP adapter / Kafka producer replace it without touching
 *     domain code.
 */
public interface ExecutionClient {

    /** @return true if the submission was handed off */
    boolean submit(PaymentOrder order);
}
