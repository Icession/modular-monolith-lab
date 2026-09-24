# INTEGRATION.md — LegacySupply

Everything marked ✏️ comes from my own probing of LegacySupply (Part B).
The design sections describe how the `edu.cit.carcueva.supplier` module handles it.

---

## 1. Product mapping

From `GET /catalog` with my own Client ID. Pack sizes are per partner, so these are mine only.

| Our product ID | Our name | LegacySupply SupplierSku | PackSize | Catalog description |
|---|---|---|---|---|
| P100 | Wireless Mouse | ✏️ | ✏️ | ✏️ |
| P200 | Mechanical Keyboard | ✏️ | ✏️ | ✏️ |
| P300 | USB-C Hub | ✏️ | ✏️ | ✏️ |

The same values are configured in `backend/src/main/resources/application.properties` as
`supplier.items=P100:<sku>:<pack>,...`. That line is the only place the mapping lives.
`SupplierItemMapping` reads it; Order and Inventory never see it.

---

## 2. Sessions

- **How it works:** `POST /auth/token` with an `AuthRequest` (ClientId + ApiKey) returns a
  `SessionToken`. Every other call sends it in the `X-LS-Session` header.
- **How long it actually lasts (measured):** ✏️ about ___ minutes. Measured by: ✏️ (e.g. "got a
  token at 18:20:05, called GET /catalog every 30s, first 401 came back at 18:__:__ with code ✏️").
- **What happens when it expires:** ✏️ (HTTP status + code you got, e.g. `401 E-AUTH-07 Session not valid.`)
- **How the adapter handles it:** `LegacySupplySession` signs in lazily and caches the token.
  When any call comes back 401 (other than `E-AUTH-01`, which means the credentials themselves are
  wrong), `LegacySupplyClient` drops the token and the next try signs in again automatically.
  The app logs `Session rejected after Ns` each time, which also confirms the lifetime above.

---

## 3. Error codes I actually received

Only codes I saw myself, with the request that caused each.

| Code | HTTP | What actually caused it (my request) | How the adapter treats it |
|---|---|---|---|
| ✏️ | ✏️ | ✏️ | ✏️ |
| ✏️ | ✏️ | ✏️ | ✏️ |
| ✏️ | ✏️ | ✏️ | ✏️ |

How the adapter treats each kind (`LegacySupplyException.Kind`):

| HTTP | Kind | Adapter behaviour |
|---|---|---|
| timeout / no response / 5xx (`E-SYS-50`, `E-SYS-99`) / non-XML body | TRANSIENT | retry with backoff (max 3 tries); if still failing, the reorder stays **PENDING** and a 20s local cool-down starts |
| 401 `E-AUTH-02/03/07` | AUTH | drop session, sign in again, retry |
| 401 `E-AUTH-01` | CREDENTIALS | stop; reorder stays **PENDING**; 5 min cool-down (config problem) |
| 429 `E-RATE-03` | RATE_LIMITED | no retry; reorder stays **PENDING**; 60s cool-down |
| 409 `E-IDEM-04` | CONFLICT | reorder marked **NEEDS_ATTENTION** (a person must look) |
| 404 `E-PO-04` | NOT_FOUND | while tracking: order marked **FAILED** ("supplier no longer knows this PO") |
| other 4xx (`E-SKU-02`, `E-QTY-11`, `E-REF-05`, `E-FMT-*`) | REJECTED | reorder marked **FAILED** with the reason; retrying would get the same answer |

---

## 4. Qty and Uom, in my own words

✏️ Write this yourself. The shape of the explanation:

- `Qty` is **how many of LegacySupply's selling units** I'm ordering. It is not a count of single items.
- `Uom` (unit of measure) says **what that selling unit is**, e.g. `CS` = case. One case holds `PackSize` single items.
- So **single items received = Qty × PackSize**.

**Worked example (✏️ use your real numbers):** P100 Wireless Mouse maps to SKU ✏️ with PackSize ✏️.
Stock drops to 3 and the target is 20, so we need 17 mice. 17 ÷ ✏️12 = 1.42, rounded **up** to
2 cases, so we send `<Qty>2</Qty>`. The acknowledgement comes back with `<Uom>CS</Uom>`, and on delivery
Inventory receives 2 × 12 = **24** mice. Rounding up means we never receive fewer than we needed.
LegacySupply only accepts Qty 1–99, so the adapter clamps to that range.

---

## 5. Design decisions

### No duplicate purchase orders
1. **Saved before sent.** A reorder is written to `supplier_orders` as PENDING *before* any HTTP call.
2. **One X-Request-Id forever.** `request_id` (a UUID) is generated once when the row is created and
   reused on every send: retries, scheduled re-sends, even after an app restart. LegacySupply does not
   process the same X-Request-Id twice.
3. **Look before re-sending.** If a reorder has been attempted before (`attempts > 0`), the adapter first
   calls `GET /purchase-orders?buyerRef=...`. If LegacySupply already has it, we adopt that PO number
   instead of sending again.
4. **One open reorder per product.** A new low-stock event for a product that already has an open reorder
   doesn't create a second one.
5. **BuyerRef** = `RO-<supplier_orders.id>-<first 6 chars of request_id>`. It's unique per reorder, and
   still unique even if the table is ever reset.

### No lost reorders
If LegacySupply is slow, down or refusing, the reorder stays **PENDING** in our database.
`SupplierOrderScheduler.dispatchPendingReorders` (`@Scheduled`, every 20s) re-sends PENDING rows that
haven't been touched for 15s. Because the row lives in Postgres, a restart doesn't lose it either.

### Staying within the quota
- The session token is cached; we sign in only when needed.
- Tracking polls only orders that are still open, at most 10 per run, every 60s.
- Both jobs **stop the current run at the first failure** instead of trying every row.
- After an outage (20s), a 429 (60s) or bad credentials (5 min), the client pauses all calls locally.
- The frontend auto-refresh polls **our** backend only, never LegacySupply.

### Status mapping (LegacySupply → ours)

| LegacySupply StatusCode | Meaning | Our `SupplierOrderStatus` |
|---|---|---|
| 10 | Accepted | SUBMITTED |
| 20 | Picking | IN_PROGRESS |
| 30 | Shipped | SHIPPED |
| 40 | Delivered | DELIVERED → publishes `ReplenishmentReceivedEvent`; Inventory restocks `units` |
| anything else | (not in the manual) | NEEDS_ATTENTION (see below) |

### Unexpected statuses: what my system does
If tracking returns a StatusCode the manual doesn't list, the order is marked **NEEDS_ATTENTION**, the
raw code is written to `last_error` (visible in `GET /api/supplier/orders` and the frontend panel), and
a warning is logged. **We keep tracking it**, because it may still turn into 40 (Delivered), and we
don't want to miss a delivery. Stock is only ever added on 40, so an unknown code can never add stock
by mistake. While a product has a NEEDS_ATTENTION reorder, no new reorder is placed for it, so an
unclear order can't turn into two orders. ✏️ If you actually saw an unexpected code, name it here.

### Delivery → restock without Order/Inventory calling the supplier
The supplier module publishes `inventory.ReplenishmentReceivedEvent(productId, units, reference)`.
The event is owned by Inventory, in Inventory's own terms: product ID and single units, no SKU/case/pack.
`inventory.ReplenishmentListener` restocks. It runs in the **same transaction** that marks the order
DELIVERED, so "delivered" and "stock added" commit together, and marking it DELIVERED twice is a no-op.
The dependency only points supplier → inventory, never back.

### Why the low-stock listener is `@Async` (Lab 2's listeners are not)
The low-stock reorder listener calls an external system that may take up to ~10s (3 tries × 3s timeout
+ backoff). It is `@Async` + `@TransactionalEventListener(AFTER_COMMIT)`, so a customer's order is never
slowed down by LegacySupply, and a customer order that rolls back never triggers a purchase.
