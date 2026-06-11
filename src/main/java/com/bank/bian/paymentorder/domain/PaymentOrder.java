package com.bank.bian.paymentorder.domain;

import java.time.Instant;

/**
 * Control record made real: "Payment Order Procedure" — one customer payment
 * instruction from intake to outcome.
 *
 * State machine:
 *   RECEIVED → VALIDATED → SUBMITTED → COMPLETED | FAILED
 *      └─(validation fails)→ REJECTED              (terminal)
 *   cancel: allowed in RECEIVED/VALIDATED only — once SUBMITTED the order is
 *   with Payment Execution and can no longer be cancelled (mirrors the
 *   cheque stop-after-presentment rule).
 */
public class PaymentOrder {

    public enum Status { RECEIVED, VALIDATED, REJECTED, SUBMITTED, COMPLETED, FAILED, CANCELLED }

    private String orderId;
    private String debtorAccountRef;
    private String creditorAccountRef;
    private long amountMinor;
    private String currency;
    private String remittanceInfo;
    private Status status = Status.RECEIVED;
    private String statusReason;
    private Instant receivedAt;
    private Instant submittedAt;
    private Instant finishedAt;

    public static PaymentOrder receive(String orderId, String debtorAccountRef, String creditorAccountRef,
                                       long amountMinor, String currency, String remittanceInfo, Instant now) {
        PaymentOrder o = new PaymentOrder();
        o.orderId = orderId;
        o.debtorAccountRef = debtorAccountRef;
        o.creditorAccountRef = creditorAccountRef;
        o.amountMinor = amountMinor;
        o.currency = currency;
        o.remittanceInfo = remittanceInfo;
        o.receivedAt = now;
        return o;
    }

    public boolean isTerminal() {
        return status == Status.REJECTED || status == Status.COMPLETED
                || status == Status.FAILED || status == Status.CANCELLED;
    }

    public String getOrderId() { return orderId; }
    public String getDebtorAccountRef() { return debtorAccountRef; }
    public String getCreditorAccountRef() { return creditorAccountRef; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public String getRemittanceInfo() { return remittanceInfo; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
}
