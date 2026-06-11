package com.bank.bian.paymentorder.domain;

import java.util.Collection;
import java.util.Optional;

/** Persistence port — in-memory now, Postgres when the platform hydrates. */
public interface OrderRepository {

    void save(PaymentOrder order);

    Optional<PaymentOrder> findById(String orderId);

    Collection<PaymentOrder> findAll();
}
