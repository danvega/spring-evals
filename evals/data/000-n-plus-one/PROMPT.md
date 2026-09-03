# The authors endpoint is hammering the database

`GET /api/authors` returns every author with their book titles. It works, but it is slow in production and getting slower as the catalog grows.

Our DBA pulled the query log for a single request to this endpoint and found hundreds of near-identical queries, one per author:

```sql
select ... from book where author_id = ?
select ... from book where author_id = ?
select ... from book where author_id = ...
```

Production has tens of thousands of authors. One HTTP request should not turn into thousands of database queries.

Fix the data access so a single request to `GET /api/authors` issues only a handful of queries.

Constraints:

- The JSON response shape must not change, and it must still include every author with all of their book titles
- No pagination, no caching, no dropping data; the DBA wants the query pattern fixed, not hidden
- Stay on JPA for this endpoint. The entity mapping is what the rest of the service is built on, so keep reading through it; hand-written SQL or a JDBC read path around the mapping is not the fix we want
- `DataSeeder` simulates production data and must not change

You are done when the endpoint returns the same data with a constant number of queries per request.
