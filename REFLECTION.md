# REFLECTION.md

The questions below are copied from my self-check page (https://legacysupply.onrender.com/verify).
They are generated from my own traffic. Each answer refers to my own logs, data and code.

# Lab 3 — LegacySupply

## Question 1
> At 20:07:18 your request for BuyerRef "RO-4-a4adbf" (X-Request-Id a4adbf12-cf42-4daf-aadd-e4f174e19401) received a 503, but LegacySupply had already created PO-100072. Walk through exactly what your adapter did next, and explain why that did or did not result in a second order.

The 503 is a TRANSIENT error, so `LegacySupplyClient.withRetry()` logged "attempt 1/3 failed", waited a short backoff (300 ms doubled each attempt, plus random jitter) and called `placeOrder` again. The retry sent the same BuyerRef "RO-4-a4adbf" and the same X-Request-Id, because the request ID is created once when the reorder row is saved (`SupplierOrder.requestId`) and reused on every attempt, never generated per call. LegacySupply recognised the repeated X-Request-Id and answered with the order it had already created, PO-100072, instead of making a new one. My adapter saved that PO number with `store.markSubmitted()`, so my `supplier_orders` row RO-4-a4adbf shows PO-100072 and was later tracked to DELIVERED. As an extra guard, if a reorder is ever retried later from the database (attempts > 0), `LegacySupplyGateway.submit()` first looks the order up by BuyerRef and adopts the existing PO instead of sending again. The self-check confirms this: 0 duplicates, with chaos events on orders all counted as safe replays.

## Question 2
> PO-100073 (BuyerRef "RO-5-94521c") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?

At first my `StatusTranslator` only knew 10, 20, 30 and 40, so PO-100073 came back as an unknown code and my tracker marked it NEEDS_ATTENTION with "Unexpected supplier status code 90" instead of guessing. Looking at the order, it never moved forward to shipped or delivered, the stock never arrived, and 90 sits after the normal 10→40 flow like a final state, so I treated it as "cancelled by the supplier". I added `case "90" -> CANCELLED` to `StatusTranslator` and a CANCELLED status in my domain. Now `SupplierOrderScheduler.trackOpenOrders()` marks the order CANCELLED and immediately places a replacement reorder for the same product and units, so the missing stock is ordered again. My row for RO-5-94521c shows "Cancelled by supplier (status 90) - replaced by reorder #7", and that replacement (PO-100124) was later delivered.

## Question 3
> LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.

`LegacySupplySession` records the time it signed in, and when a token is rejected it logs how old the token was. My logs show lines like "[LegacySupply] Session rejected after 160s (E-AUTH-07 Session not valid). Signing in again.", so my sessions last about 160 seconds (roughly 2.5 minutes). My adapter does not guess the expiry time. It reuses the same token until LegacySupply rejects it. When a call fails with an AUTH error (E-AUTH-07), `withRetry()` calls `session.invalidate(token)`, and the next attempt calls `currentToken()`, which sees no token and signs in again with `POST /auth/token`. `invalidate()` only clears the token if it is still the one that failed, so several threads hitting an expired session at the same time cause only one new sign-in. The self-check shows this working: "Renews expired sessions — 60 sign-ins, 53 requests with an expired session".

# Lab 4 — Marketplace (Tiangge)

The three questions below are copied from the Marketplace section of my self-check page.
Times in my app's logs and database are about 10 seconds behind Tiangge's clock, because my laptop clock runs slightly behind.

## Question 1
> Tiangge order TG-9K32EH (10 x P200) was accepted at 18:25:36. At that moment your last published stock for P200 was 1, and the stock Tiangge worked out from your own decisions, cancellations and deliveries was 1. Where did your application's stock figure come from, and why did it disagree?

My stock figure always comes from my own Inventory module: the `stock` column in `inventory_items`, which `StockPublisher` reads through `InventoryService.getItem()` and sends with `PUT /stock`. TG-9K32EH was first BACKORDERED at 18:19:56 ("Waiting for supplier delivery of {P200=2}") because P200 only had a few units left. A LegacySupply delivery for P200 then arrived, and my Inventory was restocked, so my app really did have enough P200 to fill the 10 units. The bug was the order of the messages: my stock publisher holds stock updates while decisions are still being reported, so the delivered stock was waiting in the queue, and `BackorderResolver` sent the `ACCEPTED` resolution (18:25:24 on my clock) before Tiangge ever saw the higher stock. From Tiangge's side I sold 10 units while my last published figure was still 1, so it counted an oversell. I fixed it in `BackorderResolver.resolveIfDue()`: before filling a backorder whose items are now in stock, it calls `stockPublisher.flush(..., true)` so the delivered stock is published first, then the resolution, then the new lower stock.

## Question 2
> Event evt_69983f3f9aff1cc6 (order TG-CJNYMX) reached your application twice, as seq 110 and seq 111, and you processed it once. Show the code and the stored data that made the second delivery harmless, and explain what would happen if your application restarted between the two.

The `channel_orders` table uses the Tiangge order ID as its primary key, so each Tiangge order can only have one row. In `FeedPoller.readOnePage()` the placements are collected with `placements.putIfAbsent(event.orderId(), event)`, so two copies in the same page become one. Then `OrderDecider.onOrderPlaced()` does `repository.findById(event.orderId())`; if a row exists and its state is not `NEW`, it logs "delivered again ... already handled, skipping" and returns without touching Inventory. The stored data that made this safe is the row `TG-CJNYMX` → `shop_order_id = 104`, state `ACCEPTED` (later `CUSTOMER_CANCELLED`), lines `P200:8,P300:3`. If my app had restarted between seq 110 and 111, it would load the saved cursor from `channel_cursor` and read again from there. When seq 111 arrived it would find the existing row and skip it. If the crash happened in the middle of deciding, the row would still be `NEW`, so the decision would be finished once, either by `decide()` or by `resumeUndecided()`. Either way, SO-104 is created only once.

## Question 3
> Order TG-5WAVB5 was backordered at 17:29:41 and accepted at 17:33:10, after PO-101304 was delivered at 17:31:26. Trace how the delivery reached your Inventory and what then resumed the backordered order.

TG-5WAVB5 (7 × P100) was backordered because P100 was 4 units short and purchase order PO-101304 (RO-16-30fcc3, 40 units of NEC-7110) was already on its way. LegacySupply delivered it at 17:31:26 while my app was stopped for the restart test (offline 17:29:44–17:32:19), so nothing happened until the app came back. After the restart, `SupplierOrderScheduler.trackOpenOrders()` (every 20 seconds) called LegacySupply, saw StatusCode 40, translated it to `DELIVERED` and called `SupplierOrderStore.markDelivered()` (17:32:39 on my clock). That method publishes a `ReplenishmentReceivedEvent`; the Inventory module's `ReplenishmentListener` calls `InventoryService.restock("P100", 40)`, and the supplier module never touches Inventory directly. The same event sets a flag in the channel's `BackorderResolver`, whose 2-second job then found the `BACKORDERED` row and called `OrderService.fulfilBackorder(106)`, which reserved the 7 units and changed SO-106 to `CONFIRMED`. Finally, `TianggeReporter` sent the `ACCEPTED` resolution to Tiangge (17:32:58 on my clock, 17:33:10 on Tiangge's), and the new P100 stock was published right after.
