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
| `GET /api/v1/work-orders` / `/{id}` | any | list (filters `status`, `patientId`, `createdBefore`, `q` — simple, case-insensitive match on the order number; does not search by patient name) / detail |
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

## PDF y pagos de prueba

`GET /api/v1/invoices/{id}/pdf` descarga un PDF interno con factura, orden, líneas y totales COP; **no es una factura fiscal certificada**. Solo ADMIN/SELLER. El paciente se identifica por el UUID que pertenece a la factura; este servicio no consulta otra base de datos.

PSE/CARD requieren `POST /api/v1/invoices/{id}/payment-challenges` con `amountCents`, `method` y `reference` opcional. Devuelve `id`, `testCode`, `expiresAt` y un rótulo **SMS SIMULADO - SOLO PRUEBAS, SIN COBRO**. Confirmar en el endpoint existente `/payments`, añadiendo `challengeId` y `otp`. No recibe teléfono, número de tarjeta, CVV ni datos bancarios. El código se almacena como hash, caduca a los 180 segundos y admite 3 intentos; queda vinculado a usuario, factura, monto, método y referencia. Un código usado no permite otro pago; reintentar con la misma clave idempotente devuelve el resultado original. El proveedor configurado previamente no se conecta: la composición usa exclusivamente el gateway local. La referencia ficticia terminada en `0000` permite probar un rechazo del simulador.

TRANSFER/NEQUI/DAVIPLATA requieren `receipt: {contentType, base64}` en `/payments`. Solo PDF/PNG/JPEG, de 1 byte a 2 MiB; se contrastan MIME y firma del contenido. No se acepta HTML/SVG ni rutas de archivos. El comprobante y el pago se guardan en **la misma transacción**. `GET /api/v1/invoices/{id}/payments/{paymentId}/receipt` exige ADMIN/SELLER y coincidencia de factura. Se descarga como attachment con `no-store`/`nosniff`, nunca por una URL pública. La lista de pagos agrega `hasReceipt`; los pagos históricos sin adjunto siguen consultables. CASH/OTHER mantienen su flujo; las claves idempotentes no pueden reutilizarse para otros parámetros de pago.

### Compatibilidad y límites

- Aplicar primero `opti-sales-db` V020; después actualizar API/gateway y contenedor/portales de forma coordinada. Clientes antiguos que registren transferencias o medios electrónicos deben adoptar estos campos obligatorios. Los registros históricos no se modifican.
- El OTP es un **simulador de una sola instancia**: máximo 1000 desafíos retenidos, sin distribución ni SMS. Reiniciar pierde los desafíos y sus respuestas en memoria; un desafío antiguo falla de forma cerrada. Los pagos y comprobantes permanecen en PostgreSQL; consultar el historial antes de iniciar otra operación tras una respuesta incierta o reinicio. La respuesta idempotente de un OTP usado se conserva mientras el desafío siga retenido (hasta 13 minutos).
- Se valida tipo/firma/tamaño, sin antivirus ni certificación de autenticidad del comprobante. Los usuarios autorizados deben abrir únicamente comprobantes ficticios en estas pruebas.
- El PDF es una representación de texto con paginación y caracteres españoles (WinAnsi); no contiene firma digital, certificación fiscal ni códigos de autorización tributaria. No incorpora imágenes ni tipografías Unicode completas.

### Verificación

`mvn -B verify` cubre dominio, HTTP, caducidad, intentos, vinculación, concurrencia, reutilización, permisos, PDF y archivos inválidos. Con `TEST_DATABASE_URL` apuntando exclusivamente a una base aislada con las migraciones de ventas, también ejecuta persistencia real, idempotencia y rollback conjunto del pago/comprobante. No usar registros ni conexiones reales para estas pruebas. En cloud se ejecutaron 74 tests sin fallos ni omisiones, además de lectura independiente del PDF con `pdftotext`.
