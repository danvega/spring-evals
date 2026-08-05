# A regular user deleted documents through the bulk cleanup API

We run a small document management service. Deleting a document and changing its owner are admin operations. The admin endpoints under `/api/admin/**` require the ADMIN role and that part works.

Yesterday a customer reported documents missing from their workspace. The audit trail shows a user with only the USER role called `POST /api/documents/bulk-cleanup` and every document id in the request body was deleted. That endpoint was added so people could clear out stale documents in one call. It reaches the same delete operation the admin endpoint uses, and nothing stops a plain user there.

Our security review board wrote the finding up like this: the sensitive operations (delete document, change owner) are only protected at the specific web entry points that happen to have rules today. Any other route to those operations is wide open. They want the protection to travel with the operations themselves, so every route, including ones added later, is covered. Patching only the one endpoint that got reported does not close the finding; if another route to the same operations would still get through, the finding stays open.

What we need:

1. A user with only the USER role must be denied on every route that deletes documents or changes owners, including bulk cleanup. Denied calls should return 403 and must not change any data.
2. Admins keep working. The `/api/admin/**` endpoints and the bulk cleanup endpoint must both still work for ADMIN users.

Constraints:

- Other backend modules call `DocumentService` directly. Keep its class name and public method signatures unchanged.
- Keep the bulk cleanup endpoint at its current path. Admins still rely on it.
- Do not hand-roll authorization by reading the security context inside business code. The review board rejected that pattern in another service last quarter.
- Do not downgrade Spring Boot or Spring Security.

You are done when a plain USER gets 403 on every route to the sensitive operations, the documents survive those denied attempts, and admins can still do everything they could before.
