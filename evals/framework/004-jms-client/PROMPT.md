# Urgent order events lose their delivery settings, and review wants this messaging code modernized

This is our order-events service. `POST /api/orders` publishes an order event to the `orders.events` queue, then waits for a confirmation over the `orders.confirmations` queue before responding.

Two problems came out of this sprint:

1. A real defect. Urgent orders are supposed to be published with priority 9, a five minute time to live, and non-persistent delivery. A broker inspection shows they arrive with priority 4, no expiration, and persistent delivery. The code clearly sets these values, but the broker never sees them. Nothing is logged; the settings are dropped silently.
2. Code review rejected the messaging layer as written. It is built on the callback-and-cast style from older Spring versions: manual message creation, a raw `Message` cast on receive, and a hand-rolled shared reply queue for the confirmation round trip. We run Spring Boot 4.1 on Spring Framework 7, and the review asks for the messaging layer to be rewritten on the framework's current fluent messaging entry point. Each send should be one chained statement that carries its own quality-of-service settings. The confirmation round trip should be a single request-reply call with a bounded timeout, not a send followed by a separate receive on a fixed reply queue.

Requirements:

- Urgent order events must actually reach the broker with priority 9, a five minute time to live, and non-persistent delivery.
- Normal order events keep broker defaults: default priority, no expiration, persistent.
- The confirmation wait stays synchronous with a bounded timeout. A missing reply must not hang the request thread.
- Keep the REST contract of `POST /api/orders` exactly as it is. The confirmation text in the response keeps its current format; for order `ORD-1` the response carries `CONFIRMED-ORD-1`.
- Keep the queue names `orders.events` and `orders.confirmations`, and keep the event payload format. Downstream consumers parse it.
- The old template-based messaging must be gone when you are done. Review already rejected patching it with configuration flags, and dropping to the raw JMS producer API is not modernization either.
- Do not downgrade Spring Boot or Spring Framework, and do not add new messaging libraries. Everything you need is already on the classpath.

You are done when urgent order events carry their delivery settings on the broker, normal events keep the defaults, and the confirmation round trip still answers.
