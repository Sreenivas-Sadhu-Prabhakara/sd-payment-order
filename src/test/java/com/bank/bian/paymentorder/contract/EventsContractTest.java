package com.bank.bian.paymentorder.contract;

import com.bank.bian.paymentorder.domain.PaymentOrderService;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.FileReader;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EVENT CONTRACT TEST — the topics this domain's code publishes to must be
 * exactly the topics api/events.yaml declares. Code and contract cannot drift.
 */
class EventsContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void codeTopicsAreDeclaredInTheEventContract() throws Exception {
        Map<String, Object> events = new Yaml().load(new FileReader("api/events.yaml"));
        assertThat(events.get("service")).isEqualTo("sd-payment-order");
        Set<String> topics = ((List<Map<String, Object>>) events.get("publishes")).stream()
                .map(t -> String.valueOf(t.get("topic")))
                .collect(Collectors.toSet());
        assertThat(topics).as("PaymentOrderService.TOPIC_ORDERS must be a declared publish topic").contains(PaymentOrderService.TOPIC_ORDERS);
        assertThat(topics).as("every declared topic should be backed by a code constant").hasSize(1);
    }
}
