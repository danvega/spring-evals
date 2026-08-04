# Make the payments service resilient

Two production problems in this payments service need fixing.

**1. Transient gateway failures.** Our payment provider's edge drops roughly the first two connection attempts for every new order, then requests go through. Right now `POST /api/payments/{orderId}/charge` returns a 500 whenever that happens. We want the service to retry automatically (up to 3 retries with a short delay between attempts) so callers just see success.

**2. Report generation melts the box.** `POST /api/reports/run` generates settlement reports. Generation is memory-hungry: when more than 2 run at the same time the instance falls over. Cap report generation at 2 concurrent executions. Extra requests should wait their turn, not fail.

Constraints:

- Do not add any dependencies. The platform team explicitly rejected Spring Retry and Resilience4j; the pom stays as it is.
- `PaymentGateway` and `ReportGenerator` simulate the external systems. Do not modify them, and do not catch-and-loop around them by hand; we want a declarative solution the team can reuse.
- Keep all endpoints and response shapes unchanged.

You are done when charging a fresh order succeeds on the first API call and concurrent report requests never exceed 2 simultaneous generations.
