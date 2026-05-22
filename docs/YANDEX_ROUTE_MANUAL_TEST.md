# Yandex Route Manual Test

## Goal

Verify that the Android app opens only Yandex Maps for car routing and handles missing data safely.

## Checks

### 1. Coordinates have priority

Setup:
- Use an order containing both `lat/lon` and `routeText`.

Action:
- Tap `Route`.

Expected:
- `ru.yandex.yandexmaps` opens.
- A car route is built from the driver's current location to the destination coordinates.
- `routeText` is ignored when coordinates are present.

### 2. Text fallback works

Setup:
- Use an order with missing `lat/lon` and non-empty `routeText`.

Action:
- Tap `Route`.

Expected:
- `ru.yandex.yandexmaps` opens.
- A car route is built using `routeText`.

### 3. Missing route data is safe

Setup:
- Use an order with empty `routeText` and missing `lat/lon`.

Action:
- Tap `Route`.

Expected:
- App does not crash.
- Toast is shown: `Нет данных для маршрута`.

### 4. Missing Yandex Maps is safe

Setup:
- Remove or disable Yandex Maps on the test device.

Action:
- Tap `Route` on an order with valid route data.

Expected:
- App does not crash.
- Toast is shown: `Яндекс Карты не найдены`.

### 5. Route-opened logging still works

Setup:
- Use an order with valid route data and a reachable backend.

Action:
- Tap `Route`.

Expected:
- Yandex Maps opens immediately.
- Backend receives `POST /api/driver/orders/{order_id}/route-opened`.
- If logging fails, route opening still succeeds.
