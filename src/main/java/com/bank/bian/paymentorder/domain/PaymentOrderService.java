package com.bank.bian.paymentorder.domain;

import com.bank.bian.paymentorder.events.DomainEvent;
import com.bank.bian.paymentorder.events.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Business rules for Payment Order (Process pattern) — intake and routing.
 *
 *  - Validation at intake: positive amount, ISO currency, both account refs,
 *    no self-transfer, and a per-order limit (bian.payments.max-order-minor,
 *    default 50_000_000 minor = ₹500,000). Validation failure → REJECTED
 *    (terminal) with the reason recorded — never silently dropped.
 *  - Valid orders auto-submit to Payment Execution through the ExecutionClient
 *    port (bian.payments.auto-submit, default true). The hand-off marks the
 *    point of no return: cancel works in RECEIVED/VALIDATED only; a SUBMITTED
 *    order is with execution (mirrors cheque stop-after-presentment).
 *  - Payment Execution reports back via applyExecutionResult — COMPLETED or
 *    FAILED with the executioner's reason. Only SUBMITTED orders accept it.
 */
@Service
public class PaymentOrderService {

    public static final String TOPIC_ORDERS = "bian.payments.payment-order";

    private final OrderRepository repository;
    private final EventPublisher events;
    private final ExecutionClient executionClient;
    private final long maxOrderMinor;
    private final boolean autoSubmit;
    private final Clock clock;
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    @Autowired
    public PaymentOrderService(OrderRepository repository, EventPublisher events,
                               ExecutionClient executionClient,
                               @Value("${bian.payments.max-order-minor:50000000}") long maxOrderMinor,
                               @Value("${bian.payments.auto-submit:true}") boolean autoSubmit) {
        this(repository, events, executionClient, maxOrderMinor, autoSubmit, Clock.systemUTC());
    }

    public PaymentOrderService(OrderRepository repository, EventPublisher events,
                               ExecutionClient executionClient, long maxOrderMinor,
                               boolean autoSubmit, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.executionClient = executionClient;
        this.maxOrderMinor = maxOrderMinor;
        this.autoSubmit = autoSubmit;
        this.clock = clock;
    }

    // ── intake (Initiate) ────────────────────────────────────────────────────

    public PaymentOrder initiate(String debtorAccountRef, String creditorAccountRef,
                                 long amountMinor, String currency, String remittanceInfo) {
        PaymentOrder order = PaymentOrder.receive("PO-" + UUID.randomUUID(),
                debtorAccountRef, creditorAccountRef, amountMinor,
                currency == null ? "INR" : currency, remittanceInfo, clock.instant());
        repository.save(order);

        String rejection = validate(order);
        if (rejection != null) {
            order.setStatus(PaymentOrder.Status.REJECTED);
            order.setStatusReason(rejection);
            order.setFinishedAt(clock.instant());
            repository.save(order);
            events.publish(DomainEvent.of(TOPIC_ORDERS, "payment-order.rejected", Map.of(
                    "orderId", order.getOrderId(), "reason", rejection)));
            return order;
        }

        order.setStatus(PaymentOrder.Status.VALIDATED);
        repository.save(order);
        events.publish(DomainEvent.of(TOPIC_ORDERS, "payment-order.accepted", Map.of(
                "orderId", order.getOrderId(),
                "debtorAccountRef", debtorAccountRef,
                "creditorAccountRef", creditorAccountRef,
                "amountMinor", amountMinor,
                "currency", order.getCurrency())));

        if (autoSubmit) {
            submit(order.getOrderId());
        }
        return retrieve(order.getOrderId());
    }

    private String validate(PaymentOrder o) {
        if (o.getDebtorAccountRef() == null || o.getDebtorAccountRef().isBlank()
                || o.getCreditorAccountRef() == null || o.getCreditorAccountRef().isBlank()) {
            return "ACCOUNT_REFS_REQUIRED";
        }
        if (o.getDebtorAccountRef().equals(o.getCreditorAccountRef())) {
            return "SELF_TRANSFER";
        }
        if (o.getAmountMinor() <= 0) {
            return "AMOUNT_NOT_POSITIVE";
        }
        if (!o.getCurrency().matches("[A-Z]{3}")) {
            return "CURRENCY_INVALID";
        }
        if (o.getAmountMinor() > maxOrderMinor) {
            return "ORDER_LIMIT_EXCEEDED:" + maxOrderMinor;
        }
        return null;
    }

    // ── submission hand-off ──────────────────────────────────────────────────

    public PaymentOrder submit(String orderId) {
        return withLock(orderId, order -> {
            if (order.getStatus() != PaymentOrder.Status.VALIDATED) {
                throw DomainException.rule("NOT_VALIDATED",
                        "submit requires VALIDATED (status: " + order.getStatus() + ")");
            }
            executionClient.submit(order);
            order.setStatus(PaymentOrder.Status.SUBMITTED);
            order.setSubmittedAt(clock.instant());
            repository.save(order);
            events.publish(DomainEvent.of(TOPIC_ORDERS, "payment-order.submitted", Map.of(
                    "orderId", orderId,
                    "debtorAccountRef", order.getDebtorAccountRef(),
                    "creditorAccountRef", order.getCreditorAccountRef(),
                    "amountMinor", order.getAmountMinor(),
                    "currency", order.getCurrency())));
            return order;
        });
    }

    /** Cancellation — possible only before the execution hand-off. */
    public PaymentOrder cancel(String orderId) {
        return withLock(orderId, order -> {
            if (order.getStatus() == PaymentOrder.Status.SUBMITTED) {
                throw DomainException.rule("ALREADY_SUBMITTED",
                        "order is with Payment Execution and can no longer be cancelled");
            }
            if (order.isTerminal()) {
                throw DomainException.rule("TERMINAL", "order is " + order.getStatus());
            }
            order.setStatus(PaymentOrder.Status.CANCELLED);
            order.setFinishedAt(clock.instant());
            repository.save(order);
            events.publish(DomainEvent.of(TOPIC_ORDERS, "payment-order.cancelled",
                    Map.of("orderId", orderId)));
            return order;
        });
    }

    /** Outcome callback from Payment Execution (HTTP bridge today, consumer later). */
    public PaymentOrder applyExecutionResult(String orderId, boolean completed, String reason) {
        return withLock(orderId, order -> {
            if (order.getStatus() != PaymentOrder.Status.SUBMITTED) {
                throw DomainException.rule("NOT_SUBMITTED",
                        "execution result applies to SUBMITTED orders only (status: " + order.getStatus() + ")");
            }
            order.setStatus(completed ? PaymentOrder.Status.COMPLETED : PaymentOrder.Status.FAILED);
            order.setStatusReason(reason);
            order.setFinishedAt(clock.instant());
            repository.save(order);
            events.publish(DomainEvent.of(TOPIC_ORDERS,
                    completed ? "payment-order.completed" : "payment-order.failed",
                    Map.of("orderId", orderId, "reason", reason == null ? "" : reason)));
            return order;
        });
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public PaymentOrder retrieve(String orderId) {
        return repository.findById(orderId)
                .orElseThrow(() -> DomainException.notFound("ORDER_UNKNOWN", "no order " + orderId));
    }

    public Collection<PaymentOrder> list() {
        return repository.findAll();
    }

    private <T> T withLock(String orderId, java.util.function.Function<PaymentOrder, T> body) {
        ReentrantLock lock = locks.computeIfAbsent(orderId, k -> new ReentrantLock());
        lock.lock();
        try {
            return body.apply(retrieve(orderId));
        } finally {
            lock.unlock();
        }
    }
}
