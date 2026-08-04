# Our error responses fail the partner's contract check

A partner is integrating with this invoice API and their contract tests reject our error responses. Right now, requesting an invoice that does not exist returns a generic 500 with Spring's default error body.

The partner requires RFC 9457 problem details for errors. For a missing invoice, the response must:

- Have status 404
- Have the `application/problem+json` content type
- Have the title `Invoice Not Found`
- Have a `detail` that names the requested invoice id
- Carry the requested id in a custom `invoice_id` property

Constraints:

- Do not add dependencies
- Use the framework's built-in problem details support rather than building error response objects by hand
- The happy path responses must not change

You are done when a request for a missing invoice returns a compliant problem details response.
