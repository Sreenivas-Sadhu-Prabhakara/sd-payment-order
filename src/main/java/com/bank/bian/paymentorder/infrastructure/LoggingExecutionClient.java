package com.bank.bian.paymentorder.infrastructure;

import com.bank.bian.paymentorder.domain.ExecutionClient;
import com.bank.bian.paymentorder.domain.PaymentOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Phase 2b-c adapter: the hand-off is logged in the exact shape Payment
 * Execution's /initiate expects; the order rests in SUBMITTED until the
 * execution-result callback arrives.
 */
@Component
// Active only when no execution-url is configured (havingValue="false" can
// never equal a real URL, so a set property disables this bean).
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "bian.payments.execution-url", havingValue = "false", matchIfMissing = true)
public class LoggingExecutionClient implements ExecutionClient {

    private static final Logger log = LoggerFactory.getLogger("bian.execution-handoff");

    @Override
    public SubmitResult submit(PaymentOrder order) {
        log.info("submit -> sd-payment-execution /initiate: orderRef={} debtor={} creditor={} amountMinor={} currency={}",
                order.getOrderId(), order.getDebtorAccountRef(), order.getCreditorAccountRef(),
                order.getAmountMinor(), order.getCurrency());
        return SubmitResult.handedOffOnly();
    }
}
