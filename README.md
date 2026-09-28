# opti-sales-api

Work orders, invoices and payments. Owns the sales domain.

Part of the OptiView distributed system (team `opti`). Governance and documentation live in
[`opti-docs`](https://github.com/code-corhuila/opti-docs).

## Architecture

Hexagonal, in three Maven modules so the rule is enforced by the compiler:

| Module | Contents | Depends on |
|---|---|---|
| `sales-core` | domain, ports, use cases. **No framework dependency** | nothing |
| `sales-adapters` | HTTP inbound adapter, PostgreSQL outbound adapter | core |
| `sales-app` | composition root: wires everything and declares every limit (`application.yml`) | adapters |

The database schema is **not** here: it lives in [`opti-sales-db`](https://github.com/code-corhuila/opti-sales-db).
Other domains are reached only through their published API, never through their database.

## Public contract

Base path `/api/v1`. JSON in `camelCase`, UUID ids, money in cents, dates RFC 3339 UTC. Every
error uses `{"error", "message", "details"?, "traceId"}`. Full specification: [`openapi/openapi.yaml`](openapi/openapi.yaml).

| Method and path | Roles | Answers |
|---|---|---|
| `POST /api/v1/work-orders` | SERVICE | opens a quotation and its invoice (only the workflow prices an order) |
| `GET /api/v1/work-orders` / `/{id}` | any | list (filters `status`, `patientId`, `createdBefore`) / detail |
| `POST /api/v1/work-orders/{id}/approve` `/advance` `/cancel` | ADMIN, SELLER (cancel also SERVICE) | lifecycle transitions |
| `GET /api/v1/invoices` / `/{id}` | any | list (filter `status`, `workOrderId`) / detail |
| `POST /api/v1/invoices/{id}/payments` | ADMIN, SELLER | records an abono (idempotent) |
| `GET /api/v1/invoices/{id}/payments` | any | payment history |
| `GET /health` | none | `200` |

Cross-cutting rules (numeral 5.3): the JWT (RS256) is validated by this service, creations require
`Idempotency-Key` (8-128 chars), listings are paginated (`page` from 1, `limit` 1-100, default 20,
newest first) and every response carries `X-Correlation-Id`, also written in each JSON log line.

## Run

The whole platform is started from `opti-infra` (see its README). To work on this service alone:

```bash
cp .env.example .env            # fill in the values
mvn -B verify                   # unit + HTTP tests (+ persistence tests if TEST_DATABASE_URL is set)
docker compose --env-file .env -f deploy/compose.yml build
```

Configuration (all from the environment, see `.env.example`): `DATABASE_URL`, `DATABASE_USER`,
`DATABASE_PASSWORD`, `JWT_PUBLIC_KEY` or `JWT_PUBLIC_KEY_FILE`.

## Explicit limits (numeral 5.3.10)

Declared in `sales-app/src/main/resources/application.yml`: header/read timeout 5 s, idle keep-alive
60 s, connection pool 10, wait for a connection 5 s, statement timeout 5 s, graceful shutdown 20 s.

## Depends on

`opti-sales-db` (its own PostgreSQL instance) and the public key of the identity service.
