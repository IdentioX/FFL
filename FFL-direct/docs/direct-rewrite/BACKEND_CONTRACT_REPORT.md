Backend Contract And Legacy DB Mapping Report

Read-only analysis of the current FastAPI backend for planning an Android
DirectMySqlDataSource migration.

Scope:

Current repository: driver-api/

Backend source only. No database introspection was performed.

No backend code, database schema, or data was changed.

No credentials are included.

Target migration:

Current: Android -> FastAPI backend -> legacy MySQL
Target:  Android -> Direct MySQL data source -> legacy MySQL

Important direct-mode warning:

The backend currently centralizes secrets, password checks, driver access
checks, ownership checks, and write constraints.

Moving Android directly to MySQL moves those responsibilities into the Android
data source and the database permissions model.

The direct MySQL user must be tightly limited. Do not give Android broad
write permission to the legacy schema.

1. Project Structure

Important files inspected:

driver-api/
├── app/
│   ├── main.py
│   ├── config.py
│   ├── database.py
│   ├── dependencies/
│   │   └── auth.py
│   ├── repositories/
│   │   ├── auth_repository.py
│   │   ├── orders_repository.py
│   │   └── phone_directory_repository.py
│   ├── routers/
│   │   ├── auth.py
│   │   ├── driver_orders.py
│   │   └── system.py
│   ├── schemas/
│   │   ├── auth.py
│   │   ├── orders.py
│   │   └── phone_directory.py
│   └── services/
│       └── token_service.py
├── docs/
│   ├── API_CONTRACT.md
│   └── SMOKE_TEST.md
├── tests/
└── README.md

File responsibilities:

app/main.py

Creates the FastAPI app.

Includes auth, driver order, and system routers.

Defines GET /health.

Installs a global handler for disabled drivers.

app/config.py

Loads .env from the project root.

Reads DB and JWT settings.

Validates required env vars and MySQL SSL options.

app/database.py

Creates one PyMySQL connection per repository operation.

Uses DictCursor, so rows are dict-like.

Converts connection failures into DatabaseConnectionError.

Does not own schema creation or migrations.

app/services/token_service.py

Creates and decodes JWT access tokens.

Token payload fields are driver_id, driver_name, source, and exp.

app/dependencies/auth.py

Parses Authorization: Bearer TOKEN.

Rechecks current driver access in __hr_typepos.

Raises DRIVER_DISABLED when an existing token belongs to a disabled driver.

app/routers/auth.py

Implements /api/auth/drivers, /api/auth/login, and /api/auth/me.

app/routers/driver_orders.py

Implements order list, order details, delivered, route-opened,
notification summary, and phone directory endpoints.

Resolves driver identity from bearer token, with deprecated development/test
fallback via query driver_id on selected endpoints.

app/routers/system.py

Implements /system-info and /db-test.

app/repositories/auth_repository.py

Reads active drivers from __hr_typepos.

Checks parol for login.

Checks access for active-driver validation.

app/repositories/orders_repository.py

Reads orders from reys and reys_den.

Resolves route/contact data from meneger_contr_ffl and meneger_ffl.

Parses order ids like reys:123.

Maps legacy rows to Android-facing DTO shapes.

Marks orders delivered by updating only st_vv and date_att_log.

app/repositories/phone_directory_repository.py

Reads payment card settings from nastroiki.

Reads important phones from phone.

Reads active driver phones from __hr_typepos.

app/schemas/auth.py

Pydantic DTOs for driver options, login request/response, and /me.

app/schemas/orders.py

Pydantic DTOs for order list item, order details, previous drivers,
notification summary, and delivered response.

app/schemas/phone_directory.py

Pydantic DTOs for payment card, important phone rows, driver phones, and
phone directory response.

Current Git note:

driver-api/ is a Git repository on branch main.

A pre-existing deletion of .env.example was present before this report was
created and was not touched.

2. API Endpoints

GET /health

Source:

app/main.py

Auth:

None.

Request:

No headers, params, or body required.

Response:

{"status": "ok"}

Errors:

None intentionally raised by the endpoint.

Source tables:

None.

Business rules:

Pure app liveness check. Does not check database connectivity.

SQL:

None.

GET /db-test

Source:

app/routers/system.py

app/database.py

Auth:

None.

Request:

No headers, params, or body required.

Success response:

{
  "database": "connected",
  "ok": 1
}

Possible errors:

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

None.

Business rules:

Opens a configured MySQL connection.

Executes a trivial read-only query.

Closes the connection in finally.

Does not expose DB host, DB name, DB user, password, or raw DB errors.

SQL:

SELECT 1 AS ok;

GET /system-info

Source:

app/routers/system.py

Auth:

None.

Request:

No headers, params, or body required.

Success response:

{
  "appName": "driver-api",
  "appEnv": "development",
  "databaseConfigured": true,
  "dbHostSet": true,
  "dbNameSet": true
}

Possible errors:

None intentionally raised after app startup.

Startup can fail if required environment variables are missing.

Source tables:

None.

Business rules:

Safe config summary only.

Does not return host, database name, user, password, JWT secret, or credentials.

SQL:

None.

GET /api/auth/drivers

Source:

Router: app/routers/auth.py

Repository: app/repositories/auth_repository.py

Schema: app/schemas/auth.py

Auth:

None.

Request:

No query params or body.

Success response:

[
  {
    "driverKey": "main:97",
    "driverId": 97,
    "driverName": "Driver 97",
    "fio": "Driver Fio",
    "mobile": "+79990000000",
    "whatsapp": "+79990000000",
    "source": "main"
  }
]

Fields with blank values are normalized by the router:

fio, mobile, and whatsapp become null or are excluded because the
endpoint uses response_model_exclude_none=True.

Possible errors:

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

__hr_typepos

Confirmed not used by current code:

__hr_typepos_bf

Business rules:

Only active drivers are returned: access = 1.

Driver id must not be null.

Driver display name must not be null or blank.

driverKey is constructed as main:<posTypeId>.

parol is never selected or returned by this endpoint.

Sorted by driver display name.

SQL:

SELECT
  CONCAT('main:', posTypeId) AS driverKey,
  posTypeId AS driverId,
  posTypeName AS driverName,
  fio,
  mobile,
  whatsapp,
  'main' AS source
FROM `__hr_typepos`
WHERE posTypeId IS NOT NULL
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
ORDER BY driverName;

POST /api/auth/login

Source:

Router: app/routers/auth.py

Repository: app/repositories/auth_repository.py

Token service: app/services/token_service.py

Schema: app/schemas/auth.py

Auth:

None before login.

Request body:

{
  "driverId": 97,
  "password": "PASSWORD"
}

Compatibility request body:

{
  "driverKey": "main:97",
  "password": "PASSWORD"
}

Validation:

password is required.

At least one of driverId or driverKey is required.

If driverId is present, it must be greater than 0.

Body forbids extra fields through Pydantic extra="forbid".

Success response:

{
  "accessToken": "JWT",
  "tokenType": "bearer",
  "driverId": 97,
  "driverName": "Driver 97",
  "source": "main"
}

Possible errors:

422 for Pydantic validation errors, such as missing password or invalid
request shape.

401 {"detail": "Invalid credentials"} for wrong id/key/password, disabled
driver, unknown driver, blank driver name, invalid driver key, or unsupported
driver key source.

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

__hr_typepos

Confirmed not used by current code:

__hr_typepos_bf

Business rules:

Android should prefer driverId + password.

driverKey compatibility path accepts only main:<positive integer>.

Current repository whitelist is {"main": "__hr_typepos"}.

Passwords are compared directly against legacy parol; no hashing is visible
in backend code.

access = 1 is required for login.

parol is not returned in API JSON.

Login by driverId pushes password comparison into SQL.

Login by driverKey selects parol AS passwordFromDb, then router compares
request.password != passwordFromDb.

SQL for driverId + password login:

SELECT
  posTypeId AS driverId,
  posTypeName AS driverName,
  'main' AS source
FROM `__hr_typepos`
WHERE posTypeId = ?
  AND parol = ?
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
LIMIT 1;

SQL for driverKey login after parsing main:<id>:

SELECT
  posTypeId AS driverId,
  posTypeName AS driverName,
  ? AS source,
  parol AS passwordFromDb
FROM `__hr_typepos`
WHERE posTypeId = ?
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
LIMIT 1;

JWT payload created on success:

{
  "driver_id": 97,
  "driver_name": "Driver 97",
  "source": "main",
  "exp": "configured expiry timestamp"
}

GET /api/auth/me

Source:

Router: app/routers/auth.py

Dependency: app/dependencies/auth.py

Repository: app/repositories/auth_repository.py

Token service: app/services/token_service.py

Auth:

Required: Authorization: Bearer TOKEN.

Request:

No params or body.

Success response:

{
  "driverId": 97,
  "driverName": "Driver 97",
  "source": "main"
}

Possible errors:

401 {"detail": "Invalid credentials"} if header is missing, malformed,
token is invalid/expired/missing claims, or current driver no longer exists.

403 disabled-driver response if access != 1:

{
  "errorCode": "DRIVER_DISABLED",
  "message": "Доступ водителя отключён"
}

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

__hr_typepos for current access check.

Business rules:

/me does not refresh the driver name from DB; it returns token payload
identity after confirming current access.

A previously valid token stops working when __hr_typepos.access is no longer
1.

SQL for access check:

SELECT
  posTypeId AS driverId,
  access
FROM `__hr_typepos`
WHERE posTypeId = ?
LIMIT 1;

GET /api/driver/orders

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Schema: app/schemas/orders.py

Auth:

Production Android flow requires Authorization: Bearer TOKEN.

Deprecated fallback: query driver_id works only when APP_ENV is
development or test, and only when no valid bearer token is present.

Request params:

date: required, string in YYYY-MM-DD.

driver_id: optional deprecated development/test fallback, positive int.

ready: optional, defaults to all.

sort: optional, defaults to id_asc.

Allowed ready values:

all: no readiness filter.

ready: status = 2.

not_ready: status = 1.

partial: status = 1 AND col_s = 1.

Allowed sort values:

id_asc

id_desc

Current implementation note:

Both sort values are accepted, but repository SQL currently uses one fixed
ordering for both. The SORT_CLAUSES constant exists but is not used by the
list query.

Success response:

[
  {
    "orderId": "reys:1",
    "legacyId": 1,
    "source": "reys",
    "section": "FIRST_ROUTE",
    "sectionLabel": "Первый рейс",
    "sortIndex": 5,
    "orderNumber": "ORD-1",
    "driverId": 97,
    "driverName": "Driver 97",
    "clientName": "Client A",
    "clientInfo": "Client A",
    "deliveryNote": "Note 1",
    "plannedTime": "Note 1",
    "deliveryDate": "2026-05-18",
    "invoiceCount": 3,
    "invoicesCount": 3,
    "notReadyInvoices": 1,
    "isReady": true,
    "isPartiallyReady": true,
    "address": "г. Москва ул. Тверская д. 1",
    "routeText": "г. Москва ул. Тверская д. 1",
    "routeUrl": "https://maps.example/route",
    "routeMode": "link",
    "routeButtonLabel": "Маршрут",
    "addressSource": "meneger_contr_ffl",
    "addressMatchConfidence": "high",
    "clientPhone": "+79990000000",
    "loaderPhone": null,
    "managerName": "Икром",
    "managerPhone": "+79991112233",
    "hasRouteText": true,
    "displayTitle": "Client A",
    "readinessLabel": "Готово",
    "quantityReadiness": 1,
    "quantityLabel": "Частично готово",
    "isDelivered": false,
    "deliveredAt": null,
    "canMarkDelivered": true
  }
]

Possible errors:

400 {"detail": "Invalid date format. Use YYYY-MM-DD"}

400 {"detail": "driver_id must be greater than 0"}

400 {"detail": "Invalid ready value. Allowed values: all, ready, not_ready, partial"}

400 {"detail": "Invalid sort value. Allowed values: id_asc, id_desc"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables/functions:

reys

reys_den

MySQL function name_v(id_v)

Per-order hydration:

meneger_contr_ffl

meneger_ffl

Business rules:

Valid date is checked in Python before SQL using datetime.strptime.

Driver identity is token driver id when a valid bearer token exists.

Query driver_id cannot override a token driver id.

In production, query driver_id alone returns 401.

Driver access is checked even for development/test query fallback.

Orders from reys are first route.

Orders from reys_den are second route.

Delivered orders remain visible.

Delivered orders sort after active orders.

Within active/delivered grouping, first-route rows sort before second-route
rows.

Rows with usable sort_r sort before rows where sort_r is null or 0.

Empty or blank nomer_z maps to orderNumber = "Без номера".

status = 2 maps to ready.

status = 1 maps to not ready.

col_s = 1 maps to partially ready.

st_vv = 1 maps to delivered.

Main SQL:

SELECT
  orders.source_table,
  orders.section_order,
  orders.id,
  orders.nomer_z,
  orders.id_v,
  name_v(orders.id_v) AS driver_name,
  orders.name_c,
  orders.col,
  orders.vremya,
  orders.date,
  orders.status,
  orders.col_s,
  orders.sort_r,
  orders.st_vv,
  orders.date_att_log,
  COALESCE(orders.st_vv, 0) AS is_delivered
FROM (
  SELECT
    'reys' AS source_table,
    1 AS section_order,
    r.id,
    r.nomer_z,
    r.id_v,
    r.name_c,
    r.col,
    r.vremya,
    r.date,
    r.status,
    r.col_s,
    r.sort_r,
    r.st_vv,
    r.date_att_log
  FROM reys r
  WHERE r.date = ?
    AND r.id_v = ?
    /* optional ready clause for reys */

  UNION ALL

  SELECT
    'reys_den' AS source_table,
    2 AS section_order,
    d.id,
    d.nomer_z,
    d.id_v,
    d.name_c,
    d.col,
    d.vremya,
    d.date,
    d.status,
    d.col_s,
    d.sort_r,
    d.st_vv,
    d.date_att_log
  FROM reys_den d
  WHERE d.date = ?
    AND d.id_v = ?
    /* optional ready clause for reys_den */
) orders
ORDER BY
  is_delivered ASC,
  orders.section_order ASC,
  CASE
    WHEN orders.sort_r IS NULL OR orders.sort_r = 0 THEN 1
    ELSE 0
  END ASC,
  orders.sort_r ASC,
  orders.id ASC;

Ready clause substitutions:

-- ready=all
-- no additional clause

-- ready=ready
AND <alias>.status = 2

-- ready=not_ready
AND <alias>.status = 1

-- ready=partial
AND <alias>.status = 1
AND <alias>.col_s = 1

Route/contact hydration SQL, executed once per distinct nonblank nomer_z:

SELECT
  c.id,
  c.nomer_z,
  c.link,
  c.ooo,
  c.adress,
  c.tell_klient,
  c.tell_gruzchik,
  c.id_m,
  m.meneger AS manager_name,
  m.tel AS manager_phone,
  m.short_tel AS manager_short_phone
FROM meneger_contr_ffl c
LEFT JOIN meneger_ffl m
  ON m.id = c.id_m
WHERE TRIM(c.nomer_z) = TRIM(?)
ORDER BY c.id ASC;

GET /api/driver/orders/{order_id}

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Schema: app/schemas/orders.py

Auth:

Same as order list:

Bearer token in production.

Deprecated query driver_id only in development/test and only without a
token.

Path params:

order_id: required source-aware id, format reys:<id> or reys_den:<id>.

Query params:

driver_id: optional deprecated fallback.

Success response:

Same shape as DriverOrderResponse from the order list, but a single object.

Possible errors:

400 {"detail": "Invalid order id. Use source:id"}

400 {"detail": "Invalid order source"}

400 {"detail": "Invalid order id"}

400 {"detail": "driver_id must be greater than 0"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

404 {"detail": "Order not found"}

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables/functions:

Parsed source table: reys or reys_den.

MySQL function name_v(id_v).

Per-order hydration:

meneger_contr_ffl

meneger_ffl

Business rules:

The source prefix chooses the physical table from a fixed allowlist.

Ownership is checked with r.id = legacy_id AND r.id_v = resolved_driver_id.

If the order exists but belongs to another driver, response is 404.

No date filter is applied on this endpoint.

SQL:

SELECT
  ? AS source_table,
  ? AS section_order,
  r.id,
  r.nomer_z,
  r.id_v,
  name_v(r.id_v) AS driver_name,
  r.name_c,
  r.col,
  r.vremya,
  r.date,
  r.status,
  r.col_s,
  r.sort_r,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

Then the same meneger_contr_ffl hydration query is run if nomer_z is
nonblank.

GET /api/driver/orders/{order_id}/details

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Schema: app/schemas/orders.py

Auth:

Required: Authorization: Bearer TOKEN.

No query driver_id fallback on this endpoint.

Path params:

order_id: required source-aware id, format reys:<id> or reys_den:<id>.

Success response:

{
  "orderId": "reys:10",
  "legacyId": 10,
  "source": "reys",
  "section": "FIRST_ROUTE",
  "sectionLabel": "Первый рейс",
  "sortIndex": 1,
  "orderNumber": "ORD-10",
  "title": "Client Z",
  "clientName": "Client Z г. Москва ул. Тверская д. 1",
  "address": "г. Москва ул. Тверская д. 1",
  "plannedTime": "с 11:30 Call before arrival",
  "deliveryTime": "с 11:30",
  "comment": "Call before arrival",
  "routeText": "г. Москва ул. Тверская д. 1",
  "routeUrl": "https://maps.example/route",
  "routeMode": "link",
  "routeButtonLabel": "Маршрут",
  "addressSource": "meneger_contr_ffl",
  "addressMatchConfidence": "high",
  "clientPhone": "+79990000000",
  "loaderPhone": null,
  "managerName": "Икром",
  "managerPhone": "+79991112233",
  "invoiceCount": 3,
  "invoicesCount": 3,
  "isReady": true,
  "readinessLabel": "Готово",
  "quantityReadiness": 1,
  "quantityLabel": "Частично готово",
  "isDelivered": false,
  "deliveredAt": null,
  "canMarkDelivered": true,
  "previousDrivers": [
    {
      "driverId": 97,
      "driverName": "Driver 97",
      "driverPhone": "+79990000000",
      "driverWhatsapp": "+79991112233",
      "deliveryDate": "2026-05-10",
      "orderId": "reys:12345",
      "orderNumber": "ORD-12345"
    }
  ]
}

Possible errors:

400 {"detail": "Invalid order id. Use source:id"}

400 {"detail": "Invalid order source"}

400 {"detail": "Invalid order id"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

404 {"detail": "Order not found"}

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables/functions:

Parsed source table: reys or reys_den.

MySQL function name_v(id_v).

__hr_typepos for previous driver phones.

Per-order hydration:

meneger_contr_ffl

meneger_ffl

Business rules:

Driver identity always comes from bearer token.

Ownership is checked with id = legacy_id AND id_v = token_driver_id.

If the order exists but belongs to another driver, response is 404.

Details use the same source table for current order and previous-driver lookup.

Previous drivers are found by exact same legacy name_c, excluding the current
row, with dates before the current row date.

Previous drivers are limited to 5 rows sorted newest first by date DESC, id DESC.

Previous driver contact join uses only __hr_typepos; it does not join
__hr_typepos_bf.

Current order SQL:

SELECT
  ? AS source_table,
  ? AS section_order,
  r.id,
  r.nomer_z,
  r.id_v,
  name_v(r.id_v) AS driver_name,
  r.name_c,
  r.col,
  r.vremya,
  r.date,
  r.status,
  r.col_s,
  r.sort_r,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

Previous drivers SQL:

SELECT
  ? AS source_table,
  r.id_v AS driver_id,
  name_v(r.id_v) AS driver_name,
  d.mobile AS driver_phone,
  d.whatsapp AS driver_whatsapp,
  r.date AS delivery_date,
  r.id AS order_id,
  r.nomer_z AS order_number
FROM <same_reys_or_reys_den_table> r
LEFT JOIN `__hr_typepos` d
  ON d.PosTypeId = r.id_v
WHERE r.name_c = ?
  AND r.id <> ?
  AND r.date < ?
ORDER BY r.date DESC, r.id DESC
LIMIT 5;

Then the same meneger_contr_ffl hydration query is run for the current row.

POST /api/driver/orders/{order_id}/delivered

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Schema: app/schemas/orders.py

Auth:

Required: Authorization: Bearer TOKEN.

Token only; no query driver_id fallback.

Path params:

order_id: required source-aware id, format reys:<id> or reys_den:<id>.

Request body:

None.

Success response when updated:

{
  "status": "delivered",
  "orderId": "reys:10",
  "deliveredAt": "2026-05-18 14:20:00"
}

Success response when already delivered:

{
  "status": "already_delivered",
  "orderId": "reys:10",
  "deliveredAt": "2026-05-18 11:10:00"
}

Possible errors:

400 {"detail": "Invalid order id. Use source:id"}

400 {"detail": "Invalid order source"}

400 {"detail": "Invalid order id"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

404 {"detail": "Order not found"}

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

Parsed source table: reys or reys_den.

Business rules:

Driver identity always comes from bearer token.

Parsed source chooses the table.

The operation starts by selecting the row with FOR UPDATE.

Ownership is checked by id and id_v.

If no row matches, transaction rolls back and endpoint returns 404.

If st_vv is already 1, no update is executed. Transaction commits and the
endpoint returns already_delivered with existing date_att_log.

If not delivered, backend updates only:

st_vv = 1

date_att_log = NOW()

It does not update status, col_s, date, id_v, or any route/contact
fields.

Select-for-update SQL:

SELECT
  r.id,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?
FOR UPDATE;

Update SQL:

UPDATE <reys_or_reys_den>
SET st_vv = 1,
    date_att_log = NOW()
WHERE id = ?
  AND id_v = ?;

Post-update SQL:

SELECT
  r.id,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

Safe equivalent SQL for Android direct mode:

-- 1. Parse source-aware orderId locally and allow only reys or reys_den.
-- 2. Use the authenticated/current driver id, not a caller-provided override.
-- 3. Run in a transaction if the MySQL client supports it.

SELECT id, st_vv, date_att_log
FROM <reys_or_reys_den>
WHERE id = ?
  AND id_v = ?
FOR UPDATE;

-- If no row: treat as "Order not found".
-- If COALESCE(st_vv, 0) = 1: return already_delivered.

UPDATE <reys_or_reys_den>
SET st_vv = 1,
    date_att_log = NOW()
WHERE id = ?
  AND id_v = ?
  AND COALESCE(st_vv, 0) <> 1;

SELECT id, date_att_log
FROM <reys_or_reys_den>
WHERE id = ?
  AND id_v = ?;

POST /api/driver/orders/{order_id}/route-opened

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Auth:

Production Android flow requires Authorization: Bearer TOKEN.

Deprecated fallback: query driver_id works only in development or test,
and only without a valid token.

Path params:

order_id: required source-aware id, format reys:<id> or reys_den:<id>.

Query params:

source: optional string, defaults to android.

driver_id: optional deprecated development/test fallback.

Request body:

None.

Success response:

{"status": "logged"}

Possible errors:

400 {"detail": "Invalid order id. Use source:id"}

400 {"detail": "Invalid order source"}

400 {"detail": "Invalid order id"}

400 {"detail": "driver_id must be greater than 0"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

404 {"detail": "Order not found"}

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables/functions:

Parsed source table: reys or reys_den, for ownership validation only.

MySQL function name_v(id_v) is selected by get_driver_order_by_id.

Route/contact hydration may run as part of get_driver_order_by_id, even
though route-opened does not use the hydrated fields.

Business rules:

The endpoint validates the order exists and belongs to the resolved driver.

It writes only to application logs:

route_opened order_id=<order_id> driver_id=<driver_id> source=<source>

It does not persist a route-opened event in MySQL.

It does not update any order table field.

In direct MySQL mode there is no equivalent write unless a new event table or
legacy field is explicitly identified.

Validation SQL:

SELECT
  ? AS source_table,
  ? AS section_order,
  r.id,
  r.nomer_z,
  r.id_v,
  name_v(r.id_v) AS driver_name,
  r.name_c,
  r.col,
  r.vremya,
  r.date,
  r.status,
  r.col_s,
  r.sort_r,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

GET /api/driver/orders/notification-summary

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/orders_repository.py

Schema: app/schemas/orders.py

Auth:

Required: Authorization: Bearer TOKEN.

No query driver_id fallback.

Request params:

date: required, string in YYYY-MM-DD.

Success response:

{
  "date": "2026-05-18",
  "activeTotal": 7,
  "firstRouteCount": 5,
  "secondRouteCount": 2,
  "deliveredCount": 3,
  "secondRouteOrderIds": ["reys_den:101", "reys_den:102"]
}

Possible errors:

400 {"detail": "Invalid date format. Use YYYY-MM-DD"}

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

reys

reys_den

Business rules:

Driver id comes only from token.

Counts both route tables for a single date and driver.

firstRouteCount counts not-delivered reys rows.

secondRouteCount counts not-delivered reys_den rows.

deliveredCount counts delivered rows from both tables.

activeTotal = firstRouteCount + secondRouteCount.

secondRouteOrderIds contains not-delivered reys_den ids, formatted as
reys_den:<id>, sorted the same way as second-route active order ordering.

Count SQL:

SELECT
  COALESCE(SUM(
    CASE
      WHEN orders.source_table = 'reys'
        AND orders.is_delivered = 0 THEN 1
      ELSE 0
    END
  ), 0) AS first_route_count,
  COALESCE(SUM(
    CASE
      WHEN orders.source_table = 'reys_den'
        AND orders.is_delivered = 0 THEN 1
      ELSE 0
    END
  ), 0) AS second_route_count,
  COALESCE(SUM(
    CASE WHEN orders.is_delivered = 1 THEN 1 ELSE 0 END
  ), 0) AS delivered_count
FROM (
  SELECT
    'reys' AS source_table,
    CASE WHEN COALESCE(r.st_vv, 0) = 1 THEN 1 ELSE 0 END AS is_delivered
  FROM reys r
  WHERE r.date = ?
    AND r.id_v = ?

  UNION ALL

  SELECT
    'reys_den' AS source_table,
    CASE WHEN COALESCE(d.st_vv, 0) = 1 THEN 1 ELSE 0 END AS is_delivered
  FROM reys_den d
  WHERE d.date = ?
    AND d.id_v = ?
) orders;

Second-route ids SQL:

SELECT
  d.id
FROM reys_den d
WHERE d.date = ?
  AND d.id_v = ?
  AND COALESCE(d.st_vv, 0) <> 1
ORDER BY
  CASE
    WHEN d.sort_r IS NULL OR d.sort_r = 0 THEN 1
    ELSE 0
  END ASC,
  d.sort_r ASC,
  d.id ASC;

GET /api/driver/phone-directory

Source:

Router: app/routers/driver_orders.py

Repository: app/repositories/phone_directory_repository.py

Schema: app/schemas/phone_directory.py

Auth:

Required: Authorization: Bearer TOKEN.

No query driver_id fallback.

Request:

No query params or body.

Success response:

{
  "paymentCard": {
    "cardNumber": "2200 0000 0000 0000",
    "cardHolder": "Card Holder"
  },
  "phones": [
    {
      "name": "Диспетчер",
      "phone": "+79990000000"
    }
  ],
  "drivers": [
    {
      "driverId": 97,
      "name": "Driver 97",
      "phone": "+79991112233"
    }
  ]
}

Possible errors:

401 {"detail": "Invalid credentials"}

403 DRIVER_DISABLED response.

500 {"detail": "Database connection error"}

500 {"detail": "Database query error"}

Source tables:

nastroiki

phone

__hr_typepos

Business rules:

Authenticated driver must still be active, but returned directory is global,
not filtered to the requesting driver.

paymentCard.cardNumber comes from nastroiki.value where
variable = 'karta'.

paymentCard.cardHolder comes from nastroiki.value where
variable = 'imya_karta'.

Important phone list includes rows where phone.nomer_full is not null or blank.

Driver list includes active __hr_typepos rows with nonblank name and at
least one of mobile or whatsapp.

Driver phone chooses mobile first; if blank, falls back to whatsapp.

Drivers without a usable phone are skipped.

No phone splitting or deduplication is performed.

Payment card SQL:

SELECT
  MAX(CASE WHEN variable = 'karta' THEN value END) AS cardNumber,
  MAX(CASE WHEN variable = 'imya_karta' THEN value END) AS cardHolder
FROM nastroiki
WHERE variable IN ('karta', 'imya_karta');

Important phones SQL:

SELECT
  name,
  nomer_full AS phone
FROM phone
WHERE nomer_full IS NOT NULL
  AND TRIM(nomer_full) <> ''
ORDER BY name;

Driver phones SQL:

SELECT
  posTypeId AS driverId,
  posTypeName AS name,
  mobile,
  whatsapp
FROM `__hr_typepos`
WHERE posTypeId IS NOT NULL
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
  AND (
    (mobile IS NOT NULL AND TRIM(mobile) <> '')
    OR (whatsapp IS NOT NULL AND TRIM(whatsapp) <> '')
  )
ORDER BY name;

3. SQL Query Inventory

All active SQL found in backend source is listed here by repository.

app/routers/system.py

SELECT 1 AS ok;

app/repositories/auth_repository.py

Driver list:

SELECT
  CONCAT('main:', posTypeId) AS driverKey,
  posTypeId AS driverId,
  posTypeName AS driverName,
  fio,
  mobile,
  whatsapp,
  'main' AS source
FROM `__hr_typepos`
WHERE posTypeId IS NOT NULL
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
ORDER BY driverName;

Driver current access:

SELECT
  posTypeId AS driverId,
  access
FROM `__hr_typepos`
WHERE posTypeId = ?
LIMIT 1;

Login by driverKey:

SELECT
  posTypeId AS driverId,
  posTypeName AS driverName,
  ? AS source,
  parol AS passwordFromDb
FROM `__hr_typepos`
WHERE posTypeId = ?
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
LIMIT 1;

Login by driverId and password:

SELECT
  posTypeId AS driverId,
  posTypeName AS driverName,
  'main' AS source
FROM `__hr_typepos`
WHERE posTypeId = ?
  AND parol = ?
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
LIMIT 1;

app/repositories/orders_repository.py

Order list union:

SELECT
  orders.source_table,
  orders.section_order,
  orders.id,
  orders.nomer_z,
  orders.id_v,
  name_v(orders.id_v) AS driver_name,
  orders.name_c,
  orders.col,
  orders.vremya,
  orders.date,
  orders.status,
  orders.col_s,
  orders.sort_r,
  orders.st_vv,
  orders.date_att_log,
  COALESCE(orders.st_vv, 0) AS is_delivered
FROM (
  SELECT
    'reys' AS source_table,
    1 AS section_order,
    r.id,
    r.nomer_z,
    r.id_v,
    r.name_c,
    r.col,
    r.vremya,
    r.date,
    r.status,
    r.col_s,
    r.sort_r,
    r.st_vv,
    r.date_att_log
  FROM reys r
  WHERE r.date = ?
    AND r.id_v = ?
    /* optional ready filter */

  UNION ALL

  SELECT
    'reys_den' AS source_table,
    2 AS section_order,
    d.id,
    d.nomer_z,
    d.id_v,
    d.name_c,
    d.col,
    d.vremya,
    d.date,
    d.status,
    d.col_s,
    d.sort_r,
    d.st_vv,
    d.date_att_log
  FROM reys_den d
  WHERE d.date = ?
    AND d.id_v = ?
    /* optional ready filter */
) orders
ORDER BY
  is_delivered ASC,
  orders.section_order ASC,
  CASE
    WHEN orders.sort_r IS NULL OR orders.sort_r = 0 THEN 1
    ELSE 0
  END ASC,
  orders.sort_r ASC,
  orders.id ASC;

Address/contact candidate lookup:

SELECT
  c.id,
  c.nomer_z,
  c.link,
  c.ooo,
  c.adress,
  c.tell_klient,
  c.tell_gruzchik,
  c.id_m,
  m.meneger AS manager_name,
  m.tel AS manager_phone,
  m.short_tel AS manager_short_phone
FROM meneger_contr_ffl c
LEFT JOIN meneger_ffl m
  ON m.id = c.id_m
WHERE TRIM(c.nomer_z) = TRIM(?)
ORDER BY c.id ASC;

Notification summary count:

SELECT
  COALESCE(SUM(
    CASE
      WHEN orders.source_table = 'reys'
        AND orders.is_delivered = 0 THEN 1
      ELSE 0
    END
  ), 0) AS first_route_count,
  COALESCE(SUM(
    CASE
      WHEN orders.source_table = 'reys_den'
        AND orders.is_delivered = 0 THEN 1
      ELSE 0
    END
  ), 0) AS second_route_count,
  COALESCE(SUM(
    CASE WHEN orders.is_delivered = 1 THEN 1 ELSE 0 END
  ), 0) AS delivered_count
FROM (
  SELECT
    'reys' AS source_table,
    CASE WHEN COALESCE(r.st_vv, 0) = 1 THEN 1 ELSE 0 END AS is_delivered
  FROM reys r
  WHERE r.date = ?
    AND r.id_v = ?

  UNION ALL

  SELECT
    'reys_den' AS source_table,
    CASE WHEN COALESCE(d.st_vv, 0) = 1 THEN 1 ELSE 0 END AS is_delivered
  FROM reys_den d
  WHERE d.date = ?
    AND d.id_v = ?
) orders;

Notification second-route ids:

SELECT
  d.id
FROM reys_den d
WHERE d.date = ?
  AND d.id_v = ?
  AND COALESCE(d.st_vv, 0) <> 1
ORDER BY
  CASE
    WHEN d.sort_r IS NULL OR d.sort_r = 0 THEN 1
    ELSE 0
  END ASC,
  d.sort_r ASC,
  d.id ASC;

Single order:

SELECT
  ? AS source_table,
  ? AS section_order,
  r.id,
  r.nomer_z,
  r.id_v,
  name_v(r.id_v) AS driver_name,
  r.name_c,
  r.col,
  r.vremya,
  r.date,
  r.status,
  r.col_s,
  r.sort_r,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

Order details current order:

-- Same as single order SQL above.

Order details previous drivers:

SELECT
  ? AS source_table,
  r.id_v AS driver_id,
  name_v(r.id_v) AS driver_name,
  d.mobile AS driver_phone,
  d.whatsapp AS driver_whatsapp,
  r.date AS delivery_date,
  r.id AS order_id,
  r.nomer_z AS order_number
FROM <same_reys_or_reys_den_table> r
LEFT JOIN `__hr_typepos` d
  ON d.PosTypeId = r.id_v
WHERE r.name_c = ?
  AND r.id <> ?
  AND r.date < ?
ORDER BY r.date DESC, r.id DESC
LIMIT 5;

Delivered select:

SELECT
  r.id,
  r.st_vv,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?
FOR UPDATE;

Delivered update:

UPDATE <reys_or_reys_den>
SET st_vv = 1,
    date_att_log = NOW()
WHERE id = ?
  AND id_v = ?;

Delivered reread:

SELECT
  r.id,
  r.date_att_log
FROM <reys_or_reys_den> r
WHERE r.id = ?
  AND r.id_v = ?;

app/repositories/phone_directory_repository.py

Payment card:

SELECT
  MAX(CASE WHEN variable = 'karta' THEN value END) AS cardNumber,
  MAX(CASE WHEN variable = 'imya_karta' THEN value END) AS cardHolder
FROM nastroiki
WHERE variable IN ('karta', 'imya_karta');

Important phones:

SELECT
  name,
  nomer_full AS phone
FROM phone
WHERE nomer_full IS NOT NULL
  AND TRIM(nomer_full) <> ''
ORDER BY name;

Driver phones:

SELECT
  posTypeId AS driverId,
  posTypeName AS name,
  mobile,
  whatsapp
FROM `__hr_typepos`
WHERE posTypeId IS NOT NULL
  AND posTypeName IS NOT NULL
  AND posTypeName <> ''
  AND access = 1
  AND (
    (mobile IS NOT NULL AND TRIM(mobile) <> '')
    OR (whatsapp IS NOT NULL AND TRIM(whatsapp) <> '')
  )
ORDER BY name;

4. Table Mapping

reys

Purpose:

First-route order table.

Used columns:

id: legacy integer order id.

nomer_z: order number used for display and for joining route/contact
candidates through meneger_contr_ffl.nomer_z.

id_v: assigned driver id; maps to __hr_typepos.PosTypeId.

name_c: client/order text. Used as client display text and previous-driver
match key.

col: invoice count.

vremya: planned time, delivery note, and comment source.

date: delivery date.

status: main readiness state.

col_s: quantitative invoice readiness state.

sort_r: route sort index.

st_vv: delivery flag.

date_att_log: delivered timestamp.

Important field meanings:

status = 1: not ready.

status = 2: ready.

Other status: unknown readiness.

col_s = 0: no invoices ready, backend label Накладные не готовы.

col_s = 1: partially ready, backend label Частично готово.

col_s = 2: ready, backend label Готово.

Other col_s: unknown quantitative readiness.

st_vv = 1: delivered.

COALESCE(st_vv, 0) <> 1: active/not delivered.

Where used:

Order list.

Single order.

Order details.

Previous drivers for reys details.

Delivered update when order id source is reys.

Route-opened ownership validation.

Notification summary first route.

reys_den

Purpose:

Second-route order table.

Used columns:

Same active columns as reys:

id

nomer_z

id_v

name_c

col

vremya

date

status

col_s

sort_r

st_vv

date_att_log

Important field meanings:

Same as reys.

Where used:

Order list.

Single order.

Order details.

Previous drivers for reys_den details.

Delivered update when order id source is reys_den.

Route-opened ownership validation.

Notification summary second route.

secondRouteOrderIds.

__hr_typepos

Purpose:

Current active driver/auth table.

Used columns:

posTypeId / PosTypeId: driver id.

posTypeName / PosTypeName: driver display name.

fio: optional full name in driver options.

mobile: driver mobile phone.

whatsapp: driver WhatsApp phone.

parol: legacy driver password.

access: active/disabled flag.

Important field meanings:

access = 1: active and allowed to list/login/use token endpoints.

access != 1: disabled; token endpoints return DRIVER_DISABLED.

Where used:

Driver list.

Login.

Current access check for every protected endpoint.

Previous driver phone enrichment.

Phone directory driver phones.

Security:

parol is only used for login.

parol is never returned in API JSON.

__hr_typepos_bf

Purpose:

Mentioned in project notes and older docs as a possible legacy driver table.

Current backend usage:

Not used by active repository code.

Tests explicitly assert __hr_typepos_bf is not used by current auth queries.

Android direct implication:

Do not assume it should be queried unless the Android migration intentionally
restores older behavior or database/schema owners confirm it is required.

meneger_contr_ffl

Purpose:

Route/contact candidate table keyed by order number.

Used columns:

id: candidate id, used for sorting/tie-breaking and debug logging.

nomer_z: order number matched against reys.nomer_z or reys_den.nomer_z.

link: route URL candidate.

ooo: organization/client text used in candidate scoring.

adress: resolved delivery address.

tell_klient: client phone.

tell_gruzchik: loader phone.

id_m: manager id joined to meneger_ffl.id.

Where used:

Order list hydration.

Single order hydration.

Order details hydration.

Route-opened validation indirectly via get_driver_order_by_id.

Business rules:

Rows are fetched where TRIM(c.nomer_z) = TRIM(order_number).

If one candidate is found, it is selected with high confidence.

If multiple candidates are found, backend scores address/organization text
against name_c.

Missing, tied, or ambiguous candidates produce manual route behavior.

meneger_ffl

Purpose:

Manager lookup table for route/contact candidates.

Used columns:

id: manager id.

meneger: manager name.

tel: manager phone.

short_tel: fallback manager phone.

Where used:

Left joined from meneger_contr_ffl.id_m.

Business rules:

managerPhone uses meneger_ffl.tel first.

If tel is blank, fallback is short_tel.

phone

Purpose:

Important phone directory table.

Used columns:

name: label/person/department name.

nomer_full: phone number returned as phone.

Where used:

GET /api/driver/phone-directory.

Business rules:

Rows with null or blank nomer_full are skipped by SQL.

Results are sorted by name.

Backend does not split, normalize, or deduplicate phone numbers.

nastroiki

Purpose:

Settings table used for payment card details.

Used columns:

variable: setting key.

value: setting value.

Where used:

GET /api/driver/phone-directory.

Business rules:

variable = 'karta' maps to paymentCard.cardNumber.

variable = 'imya_karta' maps to paymentCard.cardHolder.

Missing values map to null.

MySQL function name_v(id)

Purpose:

Converts driver id to driver name for orders.

Where used:

Order list: name_v(orders.id_v) AS driver_name.

Single order: name_v(r.id_v) AS driver_name.

Details current order: name_v(r.id_v) AS driver_name.

Details previous drivers: name_v(r.id_v) AS driver_name.

Route-opened validation indirectly through single-order query.

Android direct implication:

Direct MySQL implementation can call name_v(id_v) if allowed.

Alternative is joining __hr_typepos, but that would not be identical unless
schema owners confirm name_v is equivalent.

5. Android DTO Mapping

DriverOrderDto

Backend schema:

DriverOrderResponse in app/schemas/orders.py.

Mapping:

Android/backend field

Legacy source

Rule

orderId

source + id

"reys:<id>" or "reys_den:<id>"

legacyId

reys.id / reys_den.id

Integer table id

source

synthetic

"reys" or "reys_den"

section

synthetic

FIRST_ROUTE for reys, SECOND_ROUTE for reys_den

sectionLabel

synthetic

Первый рейс or Второй рейс

sortIndex

sort_r

int(sort_r or 0)

orderNumber

nomer_z

Trimmed string; blank/null becomes Без номера

driverId

id_v

Integer

driverName

name_v(id_v)

May be null

clientName

name_c

Blank/null becomes ""

clientInfo

name_c

Same as clientName

deliveryNote

vremya

Blank/null becomes ""

plannedTime

vremya

Normalized string or null

deliveryDate

date

str(date)

invoiceCount

col

int(col or 0)

invoicesCount

col

Compatibility alias of invoiceCount

notReadyInvoices

col_s

Raw integer int(col_s or 0) despite field name

isReady

status

true when status == 2

isPartiallyReady

col_s

true when col_s == 1

address

meneger_contr_ffl.adress

Selected candidate address or null

routeText

selected address

Address only when route link is valid, else null

routeUrl

meneger_contr_ffl.link

Only if URL starts with allowed scheme

routeMode

derived

"link" when routeUrl exists, otherwise "manual"

routeButtonLabel

derived

Маршрут for link, Ручной маршрут for manual

addressSource

derived

meneger_contr_ffl, not_found, or ambiguous

addressMatchConfidence

derived

high, medium, or none

clientPhone

meneger_contr_ffl.tell_klient

Normalized nonblank text or null

loaderPhone

meneger_contr_ffl.tell_gruzchik

Normalized nonblank text or null

managerName

meneger_ffl.meneger

Normalized nonblank text or null

managerPhone

meneger_ffl.tel / short_tel

tel first, then short_tel

hasRouteText

derived

true when routeText is not blank

displayTitle

name_c

First 80 characters

readinessLabel

status

Готово, Не готово, or Неизвестно

quantityReadiness

col_s

Raw integer int(col_s or 0)

quantityLabel

col_s

See labels below

isDelivered

st_vv

true when st_vv == 1

deliveredAt

date_att_log

str(date_att_log) or null

canMarkDelivered

st_vv

false when delivered

Readiness labels:

status = 2: Готово

status = 1: Не готово

Other: Неизвестно

Quantity labels:

col_s = 0: Накладные не готовы

col_s = 1: Частично готово

col_s = 2: Готово

Other: Неизвестный статус накладных

OrderDetailsDto

Backend schema:

DriverOrderDetailsResponse in app/schemas/orders.py.

Mapping:

Android/backend field

Legacy source

Rule

orderId

source + id

"reys:<id>" or "reys_den:<id>"

legacyId

id

Integer table id

source

synthetic

"reys" or "reys_den"

section

synthetic

FIRST_ROUTE or SECOND_ROUTE

sectionLabel

synthetic

Первый рейс or Второй рейс

sortIndex

sort_r

int(sort_r or 0)

orderNumber

nomer_z

Blank/null becomes Без номера

title

parsed from name_c

Text before first address marker, else first 80 chars

clientName

name_c

Normalized nonblank text or ""

address

selected meneger_contr_ffl.adress

Current mapper uses route resolver address, not parsed name_c address

plannedTime

vremya

Normalized string or null

deliveryTime

parsed from vremya

Regex match or null

comment

parsed from vremya

vremya with delivery time removed, or null

routeText

selected address

Address only when route link is valid, else null

routeUrl

meneger_contr_ffl.link

Only if allowed scheme

routeMode

derived

"link" or "manual"

routeButtonLabel

derived

Маршрут or Ручной маршрут

addressSource

derived

meneger_contr_ffl, not_found, or ambiguous

addressMatchConfidence

derived

high, medium, or none

clientPhone

meneger_contr_ffl.tell_klient

Normalized nonblank text or null

loaderPhone

meneger_contr_ffl.tell_gruzchik

Normalized nonblank text or null

managerName

meneger_ffl.meneger

Normalized nonblank text or null

managerPhone

meneger_ffl.tel / short_tel

tel first, then short_tel

invoiceCount

col

int(col or 0)

invoicesCount

col

Compatibility alias

isReady

status

status == 2

readinessLabel

status

Готово, Не готово, or Неизвестно

quantityReadiness

col_s

Raw integer int(col_s or 0)

quantityLabel

col_s

Same mapping as order list

isDelivered

st_vv

st_vv == 1

deliveredAt

date_att_log

String or null

canMarkDelivered

st_vv

false when delivered

previousDrivers

same source order table + __hr_typepos

See below

Previous driver mapping:

Field

Legacy source

Rule

driverId

previous row id_v

Integer

driverName

name_v(id_v)

May be null

driverPhone

__hr_typepos.mobile

May be null

driverWhatsapp

__hr_typepos.whatsapp

May be null

deliveryDate

previous row date

str(date)

orderId

same source + previous row id

Source-aware id

orderNumber

previous row nomer_z

Blank/null becomes Без номера

PhoneDirectoryDto

Backend schema:

PhoneDirectoryResponse in app/schemas/phone_directory.py.

Mapping:

{
  "paymentCard": {
    "cardNumber": "nastroiki.value where variable='karta'",
    "cardHolder": "nastroiki.value where variable='imya_karta'"
  },
  "phones": [
    {
      "name": "phone.name",
      "phone": "phone.nomer_full"
    }
  ],
  "drivers": [
    {
      "driverId": "__hr_typepos.posTypeId",
      "name": "__hr_typepos.posTypeName",
      "phone": "mobile first, else whatsapp"
    }
  ]
}

Backend behavior:

Missing payment settings return null fields.

Important phones skip blank nomer_full.

Driver phones skip inactive drivers and drivers with no phone.

No splitting/deduplication.

NotificationSummaryDto

Backend schema:

NotificationSummaryResponse in app/schemas/orders.py.

Mapping:

Field

Source/rule

date

Request date string

activeTotal

firstRouteCount + secondRouteCount

firstRouteCount

Count reys rows for date/driver where COALESCE(st_vv, 0) <> 1

secondRouteCount

Count reys_den rows for date/driver where COALESCE(st_vv, 0) <> 1

deliveredCount

Count reys + reys_den rows where COALESCE(st_vv, 0) = 1

secondRouteOrderIds

Not-delivered reys_den ids as reys_den:<id>, sorted by route order

Auth DTOs

Driver option:

Field

Source/rule

driverKey

CONCAT('main:', posTypeId)

driverId

posTypeId

driverName

posTypeName

fio

fio, optional nonblank

mobile

mobile, optional nonblank

whatsapp

whatsapp, optional nonblank

source

Literal "main"

Login response:

Field

Source/rule

accessToken

JWT from create_access_token

tokenType

Literal default "bearer"

driverId

Auth row posTypeId

driverName

Auth row posTypeName

source

Literal "main"

Me response:

Field

Source/rule

driverId

JWT claim driver_id after DB access check

driverName

JWT claim driver_name after DB access check

source

JWT claim source after DB access check

Disabled-driver response:

{
  "errorCode": "DRIVER_DISABLED",
  "message": "Доступ водителя отключён"
}

6. Source-Aware Order IDs

Backend order id format:

<source>:<legacy_id>

Allowed sources:

reys

reys_den

Examples:

reys:123

reys_den:456

Parser behavior:

Converts input to string.

Splits on first :.

Missing : raises InvalidOrderIdError("Invalid order id. Use source:id").

Unknown source raises InvalidOrderIdError("Invalid order source").

Blank id part raises InvalidOrderIdError("Invalid order id").

Non-integer id part raises InvalidOrderIdError("Invalid order id").

Integer id <= 0 raises InvalidOrderIdError("Invalid order id").

Table selection:

reys     -> reys
reys_den -> reys_den

Section mapping:

reys     -> FIRST_ROUTE, "Первый рейс", section_order 1
reys_den -> SECOND_ROUTE, "Второй рейс", section_order 2

Endpoint impact:

GET /api/driver/orders/{order_id} selects from parsed source table.

GET /api/driver/orders/{order_id}/details selects from parsed source table
and searches previous drivers in the same source table.

POST /api/driver/orders/{order_id}/delivered updates parsed source table.

POST /api/driver/orders/{order_id}/route-opened validates parsed source
table ownership but does not write to DB.

Invalid id HTTP behavior:

Router catches InvalidOrderIdError and returns 400 with the exception text
as detail.

Direct Android rule:

Never concatenate arbitrary source into SQL.

Parse source-aware id locally and use a fixed allowlist to choose the table.

7. Auth And Access Logic

Driver list source:

Current code reads only __hr_typepos.

It filters access = 1.

It excludes null/blank posTypeName.

It excludes null posTypeId.

Login:

driverId + password path:

Query __hr_typepos.

Require posTypeId = driverId.

Require parol = password.

Require active access = 1.

Require nonblank posTypeName.

driverKey + password path:

Parse only main:<id>.

Query __hr_typepos.

Require active access = 1.

Select parol AS passwordFromDb.

Compare request password in Python.

JWT:

Algorithm defaults to HS256.

Expiry defaults to JWT_ACCESS_TOKEN_EXPIRE_DAYS, currently configured
default in code is 365 days.

Payload claims:

driver_id

driver_name

source

exp

Protected endpoint access:

Parse Authorization header.

Scheme must be Bearer.

Decode JWT.

Require all JWT claims.

Query current access from __hr_typepos.

If missing driver row: 401 {"detail": "Invalid credentials"}.

If access cannot be parsed as 1: disabled-driver 403 response.

What happens if access becomes 0:

Existing JWT remains cryptographically valid, but backend rejects it after DB
access check.

Response:

{
  "errorCode": "DRIVER_DISABLED",
  "message": "Доступ водителя отключён"
}

Protected endpoints covered by disabled-driver tests:

GET /api/auth/me

GET /api/driver/orders

GET /api/driver/orders/{order_id}

GET /api/driver/orders/{order_id}/details

POST /api/driver/orders/{order_id}/delivered

POST /api/driver/orders/{order_id}/route-opened

GET /api/driver/orders/notification-summary

GET /api/driver/phone-directory

Direct Android implication:

Without the backend, Android cannot rely on JWT enforcement unless direct mode
implements its own session model.

At minimum, every direct operation must recheck __hr_typepos.access = 1 for
the selected/current driver.

Password handling currently appears to be direct plaintext comparison to
parol, but this should be confirmed with schema/data owners before shipping.

8. Delivered Logic

Backend behavior:

Endpoint: POST /api/driver/orders/{order_id}/delivered

Auth: bearer token only.

Source-aware order id chooses reys or reys_den.

Selects matching row by id and id_v with FOR UPDATE.

If row does not exist for that driver, returns 404.

If st_vv is already 1, commits and returns:

{
  "status": "already_delivered",
  "orderId": "<source>:<id>",
  "deliveredAt": "<existing date_att_log or null>"
}

If not delivered, updates:

st_vv = 1

date_att_log = NOW()

Then rereads date_att_log, commits, and returns:

{
  "status": "delivered",
  "orderId": "<source>:<id>",
  "deliveredAt": "<new date_att_log>"
}

Ownership check:

WHERE id = ?
  AND id_v = ?

Fields not changed:

status

col_s

col

date

sort_r

nomer_z

name_c

vremya

id_v

Safe equivalent SQL:

START TRANSACTION;

SELECT id, st_vv, date_att_log
FROM <reys_or_reys_den>
WHERE id = ?
  AND id_v = ?
FOR UPDATE;

-- If no row, ROLLBACK and return not found.
-- If st_vv = 1, COMMIT and return already_delivered.

UPDATE <reys_or_reys_den>
SET st_vv = 1,
    date_att_log = NOW()
WHERE id = ?
  AND id_v = ?;

SELECT id, date_att_log
FROM <reys_or_reys_den>
WHERE id = ?
  AND id_v = ?;

COMMIT;

Direct Android minimum safeguards:

Use source allowlist.

Use current driver id in every WHERE.

Reject delivered for disabled driver before update.

Do not allow arbitrary SQL table names from the UI.

Do not allow updates to any table except reys and reys_den, and only the
two delivered columns.

9. Route-Opened Logic

Backend behavior:

Endpoint: POST /api/driver/orders/{order_id}/route-opened

Validates auth and current driver access.

Resolves driver id from token or development/test query fallback.

Parses source-aware order id.

Calls get_driver_order_by_id(order_id, driver_id) to validate ownership.

If no matching row, returns 404.

Logs an app log line.

Returns {"status": "logged"}.

Database writes:

None.

Exact table/field:

No route-opened table or field is present in backend code.

No INSERT, UPDATE, or event persistence is performed.

Unsupported behavior:

Because persistence is unsupported, direct Android mode can either:

Treat route-opened as a local no-op success after ownership validation, or

Keep calling the backend for this one telemetry endpoint, or

Add a new DB-backed event design only after explicit schema approval.

10. Route And Address Logic

Primary route/contact source:

meneger_contr_ffl, joined to meneger_ffl.

Order-to-route relation:

TRIM(meneger_contr_ffl.nomer_z) = TRIM(order.nomer_z).

Applies to both reys and reys_den.

Candidate fields:

meneger_contr_ffl.adress -> address.

meneger_contr_ffl.link -> route link candidate.

meneger_contr_ffl.tell_klient -> client phone.

meneger_contr_ffl.tell_gruzchik -> loader phone.

meneger_contr_ffl.id_m -> manager join key.

meneger_ffl.meneger -> manager name.

meneger_ffl.tel -> manager phone primary.

meneger_ffl.short_tel -> manager phone fallback.

Valid route URL schemes:

http://

https://

yandexmaps://

yandexnavi://

If link is blank or invalid:

routeUrl = null

routeMode = "manual"

routeButtonLabel = "Ручной маршрут"

routeText = null

If link is valid:

routeUrl = link

routeMode = "link"

routeButtonLabel = "Маршрут"

routeText = selected address

No candidates:

address = null

routeText = null

routeUrl = null

routeMode = "manual"

routeButtonLabel = "Ручной маршрут"

addressSource = "not_found"

addressMatchConfidence = "none"

Contact and manager fields null.

One candidate:

Candidate is selected.

addressSource = "meneger_contr_ffl"

addressMatchConfidence = "high"

Multiple candidates:

Backend scores candidates against legacy name_c.

Normalization:

Lowercase.

Replace ё with е.

Replace № with space.

Remove punctuation matched by the backend regex containing quotes,
backtick, guillemets, commas, punctuation, and brackets.

Replace -, /, \ with spaces.

Collapse repeated spaces.

Address scoring:

If candidate adress appears in order text: +120, confidence high.

Else if combined ooo + adress appears in order text: +100, confidence high.

Matching address tokens add up to 3 * 50 points.

Address tokens are skipped when:

length < 4

all digits

in address stopwords

Ambiguous result:

If top score is <= 0.

If more than one candidate ties for top score.

If second score > 0 and top score minus second score is < 40.

If confidence remains none.

Ambiguous output:

addressSource = "ambiguous"

addressMatchConfidence = "none"

Manual route fields.

Address markers used only by parse_order_text for details title/comment
parsing, not for final route address:

Москва

г.Москва

г. Москва

Московская обл

МО,

ул.

улица

пр-т

проспект

шоссе

ш.

наб.

набережная

пер.

переулок

дом

д.

стр.

строение

корп.

корпус

Important nuance:

parse_order_text() can parse an address-like substring from name_c, but
map_order_details() returns address from the route/contact resolver, not
from the parsed name_c address.

11. Phone And Contact Logic

Client phone source:

meneger_contr_ffl.tell_klient, selected through order number candidate.

Loader phone source:

meneger_contr_ffl.tell_gruzchik, selected through order number candidate.

Manager phone source:

meneger_ffl.tel first.

Fallback meneger_ffl.short_tel.

Driver phone source in previous drivers:

Previous drivers join __hr_typepos on PosTypeId = r.id_v.

mobile maps to driverPhone.

whatsapp maps to driverWhatsapp.

No fallback/selection; both fields are returned separately.

Driver phone source in phone directory:

Active __hr_typepos rows.

Returned phone uses:

mobile if nonblank.

Else whatsapp if nonblank.

Drivers with neither are skipped.

Important phone directory source:

phone.name

phone.nomer_full

Payment card source:

nastroiki.value where variable = 'karta'.

nastroiki.value where variable = 'imya_karta'.

Parsing/splitting/deduplication:

Backend normalizes whitespace for most order/contact text.

Backend does not split multiple phone numbers.

Backend does not deduplicate phones.

Backend does not validate phone number format.

12. Comment Parsing

Source fields:

name_c: client/order text.

vremya: planned time, delivery note, and comment source.

Order list:

Does not run parse_order_text.

clientName and clientInfo are normalized name_c.

deliveryNote is normalized vremya or "".

plannedTime is normalized vremya or null.

displayTitle is first 80 characters of clientInfo.

Order details:

Runs parse_order_text(name_c, vremya).

Delivery time parsing:

Regex:

\b(?:с\s*)?\d{1,2}(?::\d{2}|(?:\s*-\s*\d{1,2})?\s*ч)\b

Supported examples include:

11-13ч

11-13 ч

с 12ч

с 12 ч

11 ч

11:30

с 11:30

Details fields:

plannedTime: raw normalized vremya or null.

deliveryTime: first regex match in normalized vremya, or null.

comment: normalized vremya with matched delivery time removed; stripped of
surrounding  ,.;:-; null if empty.

Title/address parsing inside parse_order_text:

Repeated spaces are collapsed.

Finds earliest configured address marker in lowercased name_c.

If marker is found:

title is text before marker, or first 80 chars as fallback.

parsed address is text from marker onward.

parsed route text is parsed address or combined client/note text.

If marker is not found:

title is first 80 chars of name_c.

parsed address is null.

parsed route text is combined name_c + vremya.

Important current mapper behavior:

Parsed title, deliveryTime, and comment are returned.

Parsed address and parsed route text are not returned directly by details.

Final address, routeText, routeUrl, and route mode fields come from
meneger_contr_ffl resolution.

Phone parsing from comments:

Backend does not parse phones from vremya or name_c.

Raw vs display comment:

DriverOrderResponse.deliveryNote: raw normalized vremya.

DriverOrderResponse.plannedTime: raw normalized vremya or null.

DriverOrderDetailsResponse.plannedTime: raw normalized vremya or null.

DriverOrderDetailsResponse.comment: vremya after delivery-time extraction.

13. Notification Summary

Endpoint:

GET /api/driver/orders/notification-summary

Date handling:

date is required.

Python validates exact YYYY-MM-DD.

SQL compares r.date = ? and d.date = ?.

Driver handling:

Driver id comes from bearer token.

Query driver_id is ignored/not accepted by endpoint signature.

Current access is checked before SQL.

Source:

reys

reys_den

Logic:

For each route table, convert delivery state:

CASE WHEN COALESCE(st_vv, 0) = 1 THEN 1 ELSE 0 END

firstRouteCount: count reys rows where converted delivery state is 0.

secondRouteCount: count reys_den rows where converted delivery state is 0.

deliveredCount: count all rows from both tables where converted delivery
state is 1.

activeTotal: firstRouteCount + secondRouteCount.

secondRouteOrderIds: not-delivered reys_den rows only, formatted as
reys_den:<id>.

Second-route id sorting:

ORDER BY
  CASE
    WHEN sort_r IS NULL OR sort_r = 0 THEN 1
    ELSE 0
  END ASC,
  sort_r ASC,
  id ASC;

14. Security Notes For Direct Android DB Mode

Minimum DB permissions for direct mode:

SELECT:

__hr_typepos

reys

reys_den

meneger_contr_ffl

meneger_ffl

phone

nastroiki

execute/read permission for name_v(id) if the DB requires explicit
function privileges.

UPDATE:

Only reys.st_vv

Only reys.date_att_log

Only reys_den.st_vv

Only reys_den.date_att_log

Operations that must be constrained by driver id:

Reading orders:

WHERE date = ?
  AND id_v = ?

Reading one order:

WHERE id = ?
  AND id_v = ?

Reading details:

WHERE id = ?
  AND id_v = ?

Marking delivered:

WHERE id = ?
  AND id_v = ?

Route-opened validation, if retained:

WHERE id = ?
  AND id_v = ?

Operations that should remain read-only:

Driver list.

Login/access check.

Orders list.

Order details.

Previous drivers.

Notification summary.

Phone directory.

Route/contact lookup.

Tables that should never be writable from Android direct mode:

__hr_typepos

__hr_typepos_bf

meneger_contr_ffl

meneger_ffl

phone

nastroiki

Any unknown legacy tables.

Additional direct-mode safeguards:

Do not store admin/root DB credentials in Android.

Do not give Android schema-altering privileges.

Do not allow CREATE, ALTER, DROP, DELETE, or INSERT.

Do not allow broad UPDATE on route/order tables.

Use prepared statements.

Treat source-aware order id source as an enum, not SQL text.

Recheck access = 1 at login and before sensitive operations.

Avoid logging parol, DB credentials, or connection strings.

Consider that direct MySQL credentials in an APK can be extracted. A backend
remains safer for credential isolation and revocation.

15. Unknowns And Gaps

Items not clarified by backend source alone:

Whether parol is truly plaintext or contains some legacy transformed value.
Backend compares it directly.

Whether __hr_typepos_bf is still needed by the business. Current code does
not use it, despite older project notes mentioning it.

Exact production schema definitions, indexes, column types, and nullability.
This report is source-based and did not introspect MySQL.

Whether MySQL function name_v(id) is available to the proposed Android direct
DB user.

Whether name_v(id_v) returns the same name as __hr_typepos.posTypeName for
all historical drivers.

Whether previous-driver lookup should cross both reys and reys_den.
Backend currently searches only the same source table as the current order.

Whether exact name_c is a reliable key for previous deliveries.

Whether meneger_contr_ffl.nomer_z is unique. Backend handles multiple
candidates but ambiguity remains possible.

Whether meneger_contr_ffl.link is always a final navigation URL or sometimes
display text.

No persistent route-opened field/table exists in backend code.

sort=id_desc is accepted by API, but current repository SQL does not produce
descending id ordering.

notReadyInvoices is named like a count but maps directly to col_s, which
is a readiness state, not an invoice count.

Older docs mention labels like none/partial/full for quantityReadiness,
but current schema/code returns an integer.

Backend does not clarify timezone expectations for date_att_log = NOW().

Backend does not inspect DB permissions needed for Android direct mode.

Backend does not geocode or validate addresses externally.

16. Final Migration Recommendation

Android DirectMySqlDataSource methods that can be implemented safely from this
report, assuming DB connectivity and permissions are available:

getDrivers

login

getOrders

getOrderById if Android has such a method

getOrderDetails

markDelivered

getNotificationSummary

getPhoneDirectory

Methods that still require DB schema/introspection or product decision:

routeOpened persistence, because backend only logs and has no DB target.

Any implementation that needs __hr_typepos_bf, because current backend does
not use it.

Replacing name_v(id) with joins, unless schema owners confirm equivalence.

Hardening password handling, because backend only reveals direct parol
comparison.

Any broader write operation beyond delivered state.

Safest implementation order:

getDrivers and login

getOrders

getOrderDetails

markDelivered

notification-summary

phone-directory

route-opened

Recommended direct-mode implementation approach:

First implement read-only methods and compare Android DTO JSON against backend
responses for the same driver/date/order.

Then implement markDelivered only with a restricted DB user and transaction.

Treat route-opened as no-op or backend-only until a DB event target is
explicitly designed.

Keep the backend contract tests as the behavioral reference while replacing
the data source.