# Lab 2: Extending the Modular Monolith

Java Spring Boot (Order + Inventory + new Notification modules, in-process) + Supabase (Postgres) + React.

Builds on Lab 1: same three modules' boundary rules apply, plus a new
Notification module wired up via Spring's in-process event publishing
(`ApplicationEventPublisher` / `@EventListener`) rather than a direct
method call.

## Project layout

```
backend/    Spring Boot app
  edu.cit.carcueva.shop          Order module (unchanged package name)
  edu.cit.carcueva.inventory     Inventory module (unchanged package name)
  edu.cit.carcueva.notification  NEW Notification module
frontend/   React (Vite) client — cart-based multi-item order form,
            live inventory table, order history with Cancel, notification feed
sql/        schema.sql — recreates the full schema from scratch, including seed data
```

## 1. Supabase setup (carried over from Lab 1 — unchanged)

1. Create a free project at supabase.com (or reuse your Lab 1 project).
2. Open the SQL editor and run `sql/schema.sql`. **This script drops and
   recreates `inventory`, `orders`, `order_items`, and `notifications`
   from scratch** — it's safe to re-run even if you already have the
   Lab 1 schema in place, and it reseeds P100 (25), P200 (10), P300 (0).
3. Get your connection string from **Project Settings → Database →
   Connect**. If you hit a DNS/`UnknownHostException` on the direct
   connection string, use the **Session pooler** string instead (host
   like `aws-0-<region>.pooler.supabase.com`, username formatted as
   `postgres.<project-ref>`).

## 2. Backend setup

Requires JDK 17 and Maven (or just use your IDE's bundled Maven, e.g.
IntelliJ's Maven panel + a Run Configuration with these as Environment
variables instead of a shell `.env`).

```
cd backend
cp .env.example .env   # fill in your real Supabase values, .env is gitignored
```

```
export SUPABASE_DB_URL=jdbc:postgresql://<host>:5432/postgres
export SUPABASE_DB_USERNAME=postgres.<project-ref>
export SUPABASE_DB_PASSWORD=<your-password>
export CORS_ALLOWED_ORIGIN=http://localhost:5173

mvn spring-boot:run
```

The API starts on `http://localhost:8080`.

## 3. Frontend setup

```
cd frontend
npm install
npm run dev
```

Opens on `http://localhost:5173`.

## 4. API surface

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/orders` | Place a multi-item order — `{ items: [{ productId, quantity }, ...] }`. All-or-nothing: validates every line against current stock before reserving anything. |
| GET | `/api/orders` | Order history, each with its line items and status. |
| POST | `/api/orders/{orderId}/cancel` | Cancel a CONFIRMED order and restock every line item. 404 if the order doesn't exist, 409 if already cancelled. |
| GET | `/api/inventory` | All products with current stock. |
| GET | `/api/notifications` | Notification log (order confirmations/rejections, low-stock alerts), newest first. |

## 5. Testing all four required scenarios

- **Multi-item order, all succeed (CONFIRMED):** e.g. `{"items":[{"productId":"P100","quantity":2},{"productId":"P200","quantity":1}]}`.
- **Multi-item order, one item fails (REJECTED, no partial reservation):** e.g. add a P300 (stock 0) line to an otherwise-fine order, then check `GET /api/inventory` afterward to confirm the P100/P200 stock did NOT move.
- **Cancel with restock reflected in GET /api/inventory:** place a CONFIRMED order, note the stock drop in the inventory table, click Cancel, and confirm the stock returns to its pre-order value.
- **Notification feed showing all three entry types:** after the above, `GET /api/notifications` (or the Notifications panel in the UI) should show an order-confirmed entry, an order-rejected entry, and — if any reservation dropped a product below the threshold of 5 — a low-stock "reorder needed" entry.

**Confirmed multi-item order — request payload:**
![confirmed multi-item payload](confirmed-multi-payload.png)

**Confirmed multi-item order — response:**
![confirmed multi-item response](confirmed-multi-response.png)

**Rejected multi-item order (no partial reservation) — request payload:**
![rejected multi-item payload](rejected-multi-payload.png)

**Rejected multi-item order — response:**
![rejected multi-item response](rejected-multi-response.png)

**Cancel — restock reflected in GET /api/inventory:**
![cancel restock evidence](cancel-restock.png)

**Notification feed — confirmed, rejected, and low-stock entries:**
![notification feed](notification-feed.png)

## 6. On `@Async`

The Notification module's `@EventListener` methods run **synchronously**
(no `@Async`). For this lab, a notification write is a single cheap
insert, and running it inline guarantees a notification exists by the
time the triggering HTTP request (order placement, cancellation) returns
— which makes the required Network-tab evidence deterministic instead of
racy. The trade-off is that Notification's write latency is on the
critical path of Order's and Inventory's requests, and an exception in a
listener would currently propagate back into the request that triggered
it. Making these listeners `@Async` would remove that coupling at the
cost of losing the "notification is guaranteed to exist by response
time" guarantee — worth doing once Notification does something heavier
than a single insert (e.g. calling an email/SMS provider).

---

## Reflection

### 1. Transaction Management & Data Consistency

**Current Monolithic Architecture:**
Multi-item orders maintain atomicity by leveraging a shared Spring `@Transactional` context. `OrderService.placeOrder` performs read-only pre-validation on stock before executing actual reservations inside `OrderTransactionExecutor.reserveAndConfirm`. Because the Order and Inventory modules share a single JVM, `DataSource`, and `PlatformTransactionManager`, calling `InventoryService.reserve()` joins the same database transaction via Spring's `REQUIRED` propagation. If any item's reservation fails, it triggers an unchecked exception that rolls back previously reserved items and uncommitted `order`/`order_items` rows together in a single `COMMIT/ROLLBACK` operation.

**Microservices Considerations:**
If Order and Inventory are split across a network, that shared transaction disappears. Maintaining data consistency would require:

* **Saga Pattern:** Reserving items sequentially and applying compensating transactions (explicit "release/unreserve" calls) to roll back successful reservations if a subsequent item fails.
* **Two-Phase Pattern:** Holding tentative, un-decremented reservations until the whole order is confirmed, then committing or releasing all at once.
* **Idempotency & Timeout Handling:** Idempotency keys on reserve/release calls are required for network retries to prevent double-reserving or double-releasing stock, alongside scheduled cleanups for orphaned tentative reservations.

### 2. Event-Driven Architecture vs. Direct Coupling

**Current Monolithic Architecture:**
To prevent `OrderService` from being bottlenecked by Notification's latency or having a compile-time dependency on its public interface, the system decouples them. Instead of a direct call, `OrderService` uses `ApplicationEventPublisher.publishEvent(...)` to broadcast plain data records (`OrderPlacedEvent`/`OrderRejectedEvent`). `OrderService` doesn't need to know if the Notification module exists; it simply publishes the event, and Notification registers an `@EventListener` to pick it up. This eliminates direct coupling, even though both currently run synchronously on the same JVM thread.

**Microservices Considerations:**
Extracting the Notification module into its own service requires replacing the in-process `ApplicationEventPublisher` with a message broker (e.g., Kafka, RabbitMQ, SQS).

* **Transactional Outbox Pattern:** To ensure events are never lost if the broker is down, events must be written to a local outbox table within the same transaction as the order, then relayed asynchronously to the broker.
* **At-Least-Once Delivery:** The Notification service must be designed idempotently, consuming from a queue/topic rather than an `@EventListener`, to handle potential duplicate message deliveries.

### 3. Microservices Migration Strategy

**Target for First Extraction:** The **Notification** Module.

**Rationale:**
Notification is a pure consumer with a strict one-way dependency. Nothing in Order or Inventory imports `edu.cit.carcueva.notification`, and Notification never depends on `OrderService` or `InventoryService`—only on plain event records. It is also the least consistency-critical component (a delayed notification won't break the system like a delayed stock reservation would), making it the lowest-risk module to extract without breaking business logic.

**Extraction Steps:**

1. Replace the `ApplicationEventPublisher.publishEvent(...)` calls in `OrderService`/`OrderTransactionExecutor` and `InventoryServiceImpl` with publishes to a message broker topic, serializing the `OrderPlacedEvent`/`OrderRejectedEvent`/`LowStockEvent` shapes as JSON instead of in-JVM objects.
2. Isolate the `Notification` entity, repository, listener, and controller into their own Spring Boot service with a dedicated database and its own `/api/notifications` endpoint.
3. Implement an outbox table in the Order and Inventory databases so the triggering write and event publication remain atomic.
4. Update the frontend's notification panel to point to the new `/api/notifications` URL, leaving the `InventoryService` public contracts and core REST endpoints entirely unchanged.