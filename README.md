# Modular Monolith Integration with a React Frontend

Java Spring Boot (Order + Inventory modules, in-process) + Supabase (Postgres) + React.

## Project layout

```
backend/    Spring Boot app (edu.cit.carcueva.shop, edu.cit.carcueva.inventory)
frontend/   React (Vite) client
sql/        schema.sql — creates and seeds the Supabase tables
```

## 1. Supabase setup

1. Create a free project at supabase.com.
2. Open the SQL editor and run `sql/schema.sql` — this creates `inventory`
   and `orders` and seeds P100 (25), P200 (10), P300 (0).
3. Go to **Project Settings → Database → Connection string** and copy the
   JDBC URI, username, and password.

## 2. Backend setup

Requires JDK 17 and Maven installed locally.


```
cd backend
cp .env.example .env   # fill in your real Supabase values, .env is gitignored
```

Export the variables (or use an IDE run-config / `direnv`) before running:

```
export SUPABASE_DB_URL=jdbc:postgresql://<project-ref>.supabase.co:5432/postgres?sslmode=require
export SUPABASE_DB_USERNAME=postgres
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

Opens on `http://localhost:5173` and talks to the backend at
`http://localhost:8080/api/orders`.

## 4. Testing confirmed vs rejected paths

- **Confirmed:** order P100, quantity 5 (well under the seeded stock of 25).
- **Rejected:** order P300, any quantity ≥ 1 (seeded stock is 0), or order
  P200 with a quantity greater than 10.

> Add your Network tab screenshots here for both the confirmed and rejected
> requests (request payload + response body) once you've run it against
> your own Supabase project.

---

## Reflection

**1. What differs between integrating Order/Inventory in-process vs. as separate microservices over a network — what do you get for free, and what would you need to add back if split?**

In-process, `OrderService` calls `InventoryService.reserve(...)` as an ordinary Java method call. That call is synchronous, type-checked at compile time, and effectively atomic with respect to failure: either the JVM is up and the call happens, or nothing happens. I get transactional consistency almost for free — `reserve()` runs inside a single `@Transactional` boundary, so a stock decrement either commits or rolls back cleanly, and there's no possibility of the inventory update succeeding while the order write is lost, or vice versa, without me writing any extra code for that.

If I split Inventory into its own microservice, all of that has to be rebuilt explicitly. The call becomes a network request (HTTP or gRPC), which means I need to handle latency, timeouts, and partial failure — what happens if the inventory service accepts the reservation but the network drops before Order gets the response? I'd need idempotency keys so retries don't double-reserve stock, a serialization contract (DTOs, versioning) instead of shared Java types, and some strategy for cross-service consistency — either a saga/compensating-transaction pattern or accepting eventual consistency. I'd also need service discovery, health checks, and separate deployment, monitoring, and CI/CD pipelines. None of that is free in a monolith; the trade-off is that the monolith can't scale or deploy the two modules independently.

**2. Why does package-private visibility on `InventoryServiceImpl` matter for the module boundary — what breaks if it's public?**

Package-private visibility is what actually makes "module boundary" mean something in a monolith, rather than just being a folder-naming convention. Because `InventoryServiceImpl` is package-private, the compiler physically prevents any class in `edu.cit.carcueva.shop` from importing it, instantiating it, or casting an `InventoryService` reference back to it. The only thing Order can see is the public `InventoryService` interface (plus the public `InventoryItemView`/`ReservationResult` records), which is exactly the same shape of dependency it would have on a remote service's public API.

If `InventoryServiceImpl` were public, nothing would stop someone (today, or a teammate six months from now, under deadline pressure) from injecting the concrete impl directly, calling package-private repository methods reflectively, or bypassing `reserve()`'s validation by writing straight to `InventoryRepository`. That would silently reintroduce tight coupling: Order code would start depending on Inventory's persistence details and internal invariants, and the two modules could no longer be split apart later without a rewrite, because the "interface" was never actually enforced — it was just a suggestion.

**3. When would you extract Inventory into its own microservice, and what would need to change in your code to do it?**

I'd extract it once Inventory's scaling, deployment, or ownership needs genuinely diverge from Order's — for example, if inventory reads become far higher-volume than order writes, if a separate team owns inventory and needs to deploy independently, or if I want to swap Postgres for a different store optimized for stock lookups without touching Order at all.

To do it: I'd turn `InventoryService` into a REST (or gRPC) client instead of a Spring bean backed by a local repository — same public method signatures, but the implementation now does an HTTP call and deserializes the response into `InventoryItemView`/`ReservationResult`. I'd move the `inventory` table to its own database so the services stop sharing storage, add retry/timeout/circuit-breaker handling around the call, and replace the single `@Transactional` reservation with either a saga (reserve → confirm/compensate) or an accepted eventual-consistency model with reconciliation. Because Order was already coded against the `InventoryService` interface and never against the impl, this is a swap of the implementation behind that interface — Order's code doesn't need to change, which is the whole point of having enforced the boundary from day one.
