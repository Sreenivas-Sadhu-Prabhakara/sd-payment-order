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

    /**
     * Outcome of a submission. Payment Execution's API is synchronous, so an
     * HTTP adapter can return the terminal outcome in-band (completed != null);
     * fire-and-forget adapters (logging today, Kafka later) return handed-off
     * only and the outcome arrives via the execution-result callback instead.
     */
    record SubmitResult(boolean handedOff, Boolean completed, String reason) {
        public static SubmitResult handedOffOnly() { return new SubmitResult(true, null, null); }
        public static SubmitResult outcome(boolean completed, String reason) {
            return new SubmitResult(true, completed, reason);
        }
        public static SubmitResult failed(String reason) { return new SubmitResult(false, null, reason); }
    }

    SubmitResult submit(PaymentOrder order);
}
