# Order Service

## At a Glance

- **Purpose:** manage customer orders and order state.
- **Source of truth:** PostgreSQL order records.
- **Sync dependencies:** `user-service` for user enrichment and validation.
- **Async path:** consumes payment outcome events from Kafka.
- **Idempotency:** `Idempotency-Key` for write operations.
- **Identity:** upstream gateway provides user context.

---

## Request Flow

```text
Client
  |
  v
Gateway / trusted identity headers
  |
  v
order-service
  |--> PostgreSQL (orders, items, processed payment events)
  |--> user-service (user enrichment / validation)
  |--> Redis (idempotency state)
  |
  v
Kafka consumer
  |
  v
Process payment outcome
  |
  v
Update order state
```

---

## What It Owns

- orders;
- order items;
- order status transitions;
- total price calculation;
- processed payment event tracking;
- order read and write APIs;
- internal order-price lookup for `payment-service`.

---

## Order State Model

Current order states in code:

- `PENDING` - order is created, but not yet confirmed or cancelled.
- `CONFIRMED` - order payment succeeded and the order is accepted.
- `CANCELLED` - order is terminated and can no longer move forward.

State flow:

```text
PENDING -> CONFIRMED
PENDING -> CANCELLED
CONFIRMED -> CANCELLED
CANCELLED -> terminal
```

Order update rules:

- order content can be updated only while the order is `PENDING`;
- status transitions are validated before persistence;
- invalid transitions are rejected.

---

## Dependencies

### Synchronous

- `user-service` - used to enrich order responses and validate users for internal operations.

### Asynchronous

- Kafka - receives payment outcome events.
- Redis - stores idempotency state.
- PostgreSQL - stores orders, order items, and processed payment event records.

---

## API Surface

### `POST /orders`

Creates a new order for the current authenticated user.

Flow:
1. requires `Idempotency-Key`;
2. resolves item data through repository lookups;
3. creates an order in `PENDING` state;
4. persists order items and total price;
5. returns the created order.

### `GET /orders/{id}`

Returns an order by id.

- owner can read;
- admin can read any order.

### `GET /orders`

Returns paginated orders with filtering.

- non-admin users can see only their own orders;
- admins can see all orders.

### `GET /orders/user/{userId}`

Returns paginated orders for a specific user.

- admin only.

### `PUT /orders/{id}`

Replaces order content.

- allowed only for `PENDING` orders;
- requires `Idempotency-Key`.

### `PATCH /orders/{id}/status`

Changes order status when the transition is valid.

- requires `Idempotency-Key`;
- validates allowed transitions.

### `DELETE /orders/{id}`

Deletes an order by id.

- admin only.

### `DELETE /orders/user/{userId}`

Deletes all orders for a given user.

- admin only;
- requires `Idempotency-Key`.

### `GET /orders/internal/{id}/total-price`

Internal endpoint used by `payment-service` to resolve the total order price.

- expects `X-User-Id`;
- returns only the total price.

---

## Payment Event Processing

`order-service` consumes payment outcome events from Kafka and updates order state accordingly.

Processing rules:

- duplicate payment events are ignored using `processed_payment_events`;
- failed payment attempts are recorded, but they do not confirm the order;
- successful payment events can move a `PENDING` order to `CONFIRMED`;
- if the order is already `CONFIRMED`, the event is ignored;
- if the order is not in a compatible state, the event is skipped.

Processed payment event storage:

- table: `processed_payment_events`
- key: `payment_id`
- purpose: deduplicate payment event replay

---

## Persistence Model

### `orders`

Important fields:

- `id`
- `userId`
- `status`
- `totalPrice`
- `createdAt`
- `updatedAt`
- `version`

### `order_items`

Important fields:

- `id`
- `orderId`
- `itemId`
- `quantity`

### `items`

Reference catalog used to validate and price order lines.

### `processed_payment_events`

Important fields:

- `payment_id`
- `order_id`
- `status`
- `processed_at`

---

## Failure Behavior

- `user-service` down -> order creation or order read enrichment may fail.
- invalid order transition -> request is rejected.
- duplicate payment event -> safely ignored.
- repeated identical write request -> cached response from idempotency layer.
- unauthorized access -> `401` / `403`.

---

## Observability

- **Logging:** request and service logs include trace context.
- **Tracing:** exported through OpenTelemetry Collector.
- **Metrics:** exported through OpenTelemetry Collector and Prometheus.
- **Health:** exposed via Spring Boot Actuator.

---

## Security Boundary

Upstream gateway provides the identity context.

Current headers:

- `X-User-Id`
- `X-User-Email`
- `X-User-Roles`

Authorization is enforced in Spring Security and service-layer checks.

---

## Operational Notes

- The service uses PostgreSQL with JPA and optimistic locking on orders.
- Outbox is not used here; Kafka consumption is handled directly through the consumer.
- Order confirmation is driven by payment events, not by synchronous payment callbacks.
- External user enrichment affects read latency for order responses.

---

## Summary

`order-service` is the order write-model for the system. It owns order state, exposes order management APIs, and consumes payment outcome events so that order status can be updated asynchronously after payment completion.
