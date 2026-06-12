package com.bank.bian.paymentorder.domain;

import com.bank.bian.paymentorder.events.DomainEvent;
import com.bank.bian.paymentorder.events.EventPublisher;
import com.bank.bian.paymentorder.infrastructure.InMemoryOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Intake validation, the submission hand-off, cancellation window, outcomes. */
class PaymentOrderServiceTest {

    static class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publish(DomainEvent event) { events.add(event); }
        List<String> types() { return events.stream().map(DomainEvent::type).toList(); }
    }

    static class RecordingExecutionClient implements ExecutionClient {
        final List<String> submitted = new ArrayList<>();
        SubmitResult next = SubmitResult.handedOffOnly();
        @Override public SubmitResult submit(PaymentOrder order) {
            submitted.add(order.getOrderId());
            return next;
        }
    }

    RecordingPublisher events;
    RecordingExecutionClient execution;

    PaymentOrderService autoSubmitService() {
        return new PaymentOrderService(new InMemoryOrderRepository(), events, execution,
                50_000_000, true, Clock.systemUTC());
    }

    PaymentOrderService manualService() {
        return new PaymentOrderService(new InMemoryOrderRepository(), events, execution,
                50_000_000, false, Clock.systemUTC());
    }

    @BeforeEach
    void setUp() {
        events = new RecordingPublisher();
        execution = new RecordingExecutionClient();
    }

    @Nested
    class IntakeValidation {
        @Test
        void validOrderAutoSubmitsToExecution() {
            PaymentOrder o = autoSubmitService().initiate("CA-D", "CA-C", 100_000, "INR", "rent");
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.SUBMITTED);
            assertThat(execution.submitted).containsExactly(o.getOrderId());
            assertThat(events.types()).containsExactly("payment-order.accepted", "payment-order.submitted");
        }

        @Test
        void selfTransferRejectedWithReason() {
            PaymentOrder o = autoSubmitService().initiate("CA-X", "CA-X", 100, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.REJECTED);
            assertThat(o.getStatusReason()).isEqualTo("SELF_TRANSFER");
            assertThat(execution.submitted).isEmpty();
        }

        @Test
        void orderAboveLimitRejected() {
            PaymentOrder o = autoSubmitService().initiate("CA-D", "CA-C", 50_000_001, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.REJECTED);
            assertThat(o.getStatusReason()).startsWith("ORDER_LIMIT_EXCEEDED");
        }

        @Test
        void rejectionIsTerminal_noSubmitNoCancel() {
            PaymentOrderService s = autoSubmitService();
            PaymentOrder o = s.initiate("CA-D", "CA-C", -5, "INR", null);
            assertThat(o.getStatusReason()).isEqualTo("AMOUNT_NOT_POSITIVE");
            assertThatThrownBy(() -> s.submit(o.getOrderId())).hasMessageContaining("VALIDATED");
            assertThatThrownBy(() -> s.cancel(o.getOrderId())).hasMessageContaining("REJECTED");
        }
    }

    @Nested
    class CancellationWindow {
        @Test
        void validatedOrderCancellable_submittedOrderIsNot() {
            PaymentOrderService s = manualService(); // no auto-submit → rests in VALIDATED
            PaymentOrder o = s.initiate("CA-D", "CA-C", 100_000, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.VALIDATED);

            PaymentOrder cancelled = s.cancel(o.getOrderId());
            assertThat(cancelled.getStatus()).isEqualTo(PaymentOrder.Status.CANCELLED);

            PaymentOrder second = s.initiate("CA-D", "CA-C", 200_000, "INR", null);
            s.submit(second.getOrderId());
            assertThatThrownBy(() -> s.cancel(second.getOrderId()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("no longer be cancelled");
        }
    }

    @Nested
    class LoopClosure {
        /** 2d-ii: a synchronous adapter (HTTP → PE) returns the outcome in-band. */
        @Test
        void inBandOutcomeCompletesTheOrderImmediately() {
            execution.next = ExecutionClient.SubmitResult.outcome(true, "settled");
            PaymentOrder o = autoSubmitService().initiate("CA-D", "CA-C", 100_000, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.COMPLETED);
            assertThat(events.types()).containsExactly(
                    "payment-order.accepted", "payment-order.submitted", "payment-order.completed");
        }

        @Test
        void inBandSagaFailureMarksFailedWithExecutionReason() {
            execution.next = ExecutionClient.SubmitResult.outcome(false, "CREDIT_FAILED:account closed");
            PaymentOrder o = autoSubmitService().initiate("CA-D", "CA-C", 100_000, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.FAILED);
            assertThat(o.getStatusReason()).contains("CREDIT_FAILED");
        }

        @Test
        void transportFailureKeepsOrderValidatedAndRetryable() {
            execution.next = ExecutionClient.SubmitResult.failed("EXECUTION_UNREACHABLE: timeout");
            PaymentOrderService s = autoSubmitService();
            PaymentOrder o = s.initiate("CA-D", "CA-C", 100_000, "INR", null);
            assertThat(o.getStatus()).isEqualTo(PaymentOrder.Status.VALIDATED);
            assertThat(events.types()).contains("payment-order.handoff-failed");
            // retry succeeds once the transport recovers
            execution.next = ExecutionClient.SubmitResult.outcome(true, "settled");
            assertThat(s.submit(o.getOrderId()).getStatus()).isEqualTo(PaymentOrder.Status.COMPLETED);
        }
    }

    @Nested
    class ExecutionOutcome {
        @Test
        void submittedOrderCompletesOnExecutionSuccess() {
            PaymentOrderService s = autoSubmitService();
            PaymentOrder o = s.initiate("CA-D", "CA-C", 100_000, "INR", null);
            PaymentOrder done = s.applyExecutionResult(o.getOrderId(), true, "settled");
            assertThat(done.getStatus()).isEqualTo(PaymentOrder.Status.COMPLETED);
            assertThat(events.types()).contains("payment-order.completed");
        }

        @Test
        void executionFailureMarksFailedWithReason() {
            PaymentOrderService s = autoSubmitService();
            PaymentOrder o = s.initiate("CA-D", "CA-C", 100_000, "INR", null);
            PaymentOrder failed = s.applyExecutionResult(o.getOrderId(), false, "DEBIT_FAILED:insufficient funds");
            assertThat(failed.getStatus()).isEqualTo(PaymentOrder.Status.FAILED);
            assertThat(failed.getStatusReason()).contains("insufficient funds");
        }

        @Test
        void resultOnNonSubmittedOrderRejected() {
            PaymentOrderService s = manualService();
            PaymentOrder o = s.initiate("CA-D", "CA-C", 100_000, "INR", null); // VALIDATED
            assertThatThrownBy(() -> s.applyExecutionResult(o.getOrderId(), true, "x"))
                    .hasMessageContaining("SUBMITTED orders only");
        }
    }
}
