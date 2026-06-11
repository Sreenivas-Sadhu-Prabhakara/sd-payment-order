package com.bank.bian.paymentorder.infrastructure;

import com.bank.bian.paymentorder.domain.OrderRepository;
import com.bank.bian.paymentorder.domain.PaymentOrder;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Phase 2 adapter; transition atomicity is the service layer's per-order lock. */
@Repository
public class InMemoryOrderRepository implements OrderRepository {

    private final Map<String, PaymentOrder> orders = new ConcurrentHashMap<>();

    @Override
    public void save(PaymentOrder order) {
        orders.put(order.getOrderId(), order);
    }

    @Override
    public Optional<PaymentOrder> findById(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    @Override
    public Collection<PaymentOrder> findAll() {
        return orders.values();
    }
}
