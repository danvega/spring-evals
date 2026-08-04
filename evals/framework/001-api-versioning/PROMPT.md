# Add v2 of the users endpoint without breaking v1 clients

Our users API returns a single `name` field. The mobile team needs first and last name split apart, but thousands of existing integrations parse the current shape and must keep working unchanged.

What we need:

- A version 2.0 of `GET /api/users/{id}` that returns `firstName` and `lastName` instead of `name`, keeping the same `id` and `email` fields
- Version 1.0 keeps returning the exact current shape
- Both versions live at the same path. We do not want `/v1/` or `/v2/` path prefixes, separate per-version controllers, or hand-parsed headers
- Clients select the version with the `X-API-Version` request header
- Requests that send no version header must keep working and get version 1.0

Constraints:

- Do not add dependencies
- Use the framework's own API versioning support, not custom routing code

You are done when the same URL serves both shapes depending on the `X-API-Version` header, and headerless requests still get the v1 shape.
