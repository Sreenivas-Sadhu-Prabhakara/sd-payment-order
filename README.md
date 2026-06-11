# Payment Order

BIAN Service Domain microservice — **Phase 2b-c DEEP build** (graduated; see `.bian-graduated`). The intake half of the **payments flagship** (Payment Execution is the other half).

| | |
|---|---|
| **Business Area / Domain** | Operations and Execution / Payments |
| **Pattern / Control Record** | Process / Payment Order Procedure |
| **K8s Namespace** | `bian-operations` |

## The order lifecycle

```
RECEIVED ─validate─▶ VALIDATED ─submit─▶ SUBMITTED ─▶ COMPLETED | FAILED
    └─(fails)─▶ REJECTED                      ▲ execution-result callback
cancel: RECEIVED/VALIDATED only — once SUBMITTED the order is with
Payment Execution (mirrors the cheque stop-after-presentment rule)
```

- **Validation at intake**: account refs present, no self-transfer, positive amount, ISO currency, per-order limit (`bian.payments.max-order-minor`, default ₹500,000). Failure → `REJECTED` **with the reason recorded** — a rejected order is still a created, queryable resource.
- **Auto-submit** (`bian.payments.auto-submit`, default true): valid orders hand off to Payment Execution immediately via the `ExecutionClient` port (logging adapter today; HTTP/Kafka adapter later).
- **Outcome** arrives on `PUT /{id}/execution-result` from Payment Execution.

## API (contracts owned by this repo: [`api/openapi.yaml`](api/openapi.yaml), [`api/events.yaml`](api/events.yaml))

```bash
mvn spring-boot:run
CR=/v1/payment-order-procedure
curl -s -X POST localhost:8080$CR/initiate -H 'content-type: application/json' \
  -d '{"debtorAccountRef":"CA-D","creditorAccountRef":"CA-C","amountMinor":250000,"remittanceInfo":"invoice 42"}'
# → status SUBMITTED · then, as Payment Execution:
curl -s -X PUT localhost:8080$CR/PO-…/execution-result -H 'content-type: application/json' \
  -d '{"completed":true,"reason":"settled"}'
```

## Persistence & tests

In-memory port/adapter. Postgres staged in [`db/schema.sql`](db/schema.sql) — gated (the no-self-transfer rule is a DB CHECK too). `mvn verify` proves validation reasons, auto-submit hand-off, the cancellation window, and the outcome callback guard.
