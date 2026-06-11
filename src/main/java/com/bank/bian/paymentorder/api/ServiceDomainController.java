package com.bank.bian.paymentorder.api;

import com.bank.bian.paymentorder.domain.DomainException;
import com.bank.bian.paymentorder.domain.PaymentOrder;
import com.bank.bian.paymentorder.domain.PaymentOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * BIAN semantic API for "Payment Order" — Phase 2b-c, real domain.
 * Control record: Payment Order Procedure.
 *
 * Contract: api/openapi.yaml (owned by this repo).
 */
@RestController
@RequestMapping("/v1")
public class ServiceDomainController {

    static final String CR = "payment-order-procedure";

    private final PaymentOrderService service;

    public ServiceDomainController(PaymentOrderService service) {
        this.service = service;
    }

    @GetMapping("/service-domain")
    public Map<String, String> serviceDomain() {
        return Map.of(
                "serviceDomain", "Payment Order",
                "businessArea", "Operations and Execution",
                "businessDomain", "Payments",
                "functionalPattern", "Process",
                "assetType", "Payment Order",
                "controlRecord", "Payment Order Procedure",
                "version", "0.2.0",
                "phase", "2b-deep"
        );
    }

    public record OrderRequest(String debtorAccountRef, String creditorAccountRef,
                               long amountMinor, String currency, String remittanceInfo) {}

    @PostMapping("/" + CR + "/initiate")
    public ResponseEntity<PaymentOrder> initiate(@RequestBody OrderRequest req) {
        PaymentOrder order = service.initiate(req.debtorAccountRef(), req.creditorAccountRef(),
                req.amountMinor(), req.currency(), req.remittanceInfo());
        // REJECTED is still a created resource — the order exists with its reason
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/" + CR)
    public Collection<PaymentOrder> list() {
        return service.list();
    }

    @GetMapping("/" + CR + "/{orderId}/retrieve")
    public PaymentOrder retrieve(@PathVariable String orderId) {
        return service.retrieve(orderId);
    }

    /** Explicit submission — only relevant when bian.payments.auto-submit=false. */
    @PostMapping("/" + CR + "/{orderId}/submit")
    public PaymentOrder submit(@PathVariable String orderId) {
        return service.submit(orderId);
    }

    @PutMapping("/" + CR + "/{orderId}/control")
    public PaymentOrder control(@PathVariable String orderId, @RequestBody Map<String, String> body) {
        if (!"cancel".equalsIgnoreCase(body.getOrDefault("action", ""))) {
            throw DomainException.invalid("UNKNOWN_ACTION", "supported control action: cancel");
        }
        return service.cancel(orderId);
    }

    /** Outcome callback from Payment Execution. */
    @PutMapping("/" + CR + "/{orderId}/execution-result")
    public PaymentOrder executionResult(@PathVariable String orderId,
                                        @RequestBody Map<String, Object> body) {
        boolean completed = Boolean.TRUE.equals(body.get("completed"));
        return service.applyExecutionResult(orderId, completed, (String) body.get("reason"));
    }
}
