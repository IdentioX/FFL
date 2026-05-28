# Android Smoke Test

## Purpose

This checklist verifies the Android app can connect to the backend, load driver orders, display localized text correctly, open routes in maps, and handle offline or invalid backend conditions without crashing.

## Preconditions

- Android app is installed on an emulator or device.
- Backend is available from the device or emulator network.
- Test data exists for the configured `driverId`.

## 1. Start backend

Action:
- Start the backend service locally or in the test environment.
- Confirm that the backend exposes:
  - `GET /health`
  - `GET /api/driver/orders`
  - `POST /api/driver/orders/{order_id}/route-opened`

Expected behavior:
- Backend starts without errors.
- `GET /health` returns a successful response.
- Orders endpoint returns data for the test driver, or an empty list if that scenario is being tested.

## 2. Configure API URL in app

Action:
- Open the app.
- Open the `Settings` screen from the top bar.
- Enter the backend base URL.
- Enter the test `driverId`.
- Tap `Save settings`.
- Tap `Test connection`.

Expected behavior:
- Settings are saved without restarting the app.
- `Test connection` calls `GET /health`.
- A success message appears for a healthy backend.
- If the URL is invalid or unreachable, a readable error message appears instead of a crash.

## 3. Open orders screen

Action:
- Return to the `Driver Orders` screen from the top bar.

Expected behavior:
- The screen opens normally.
- Loading indicator appears while orders are being fetched.
- UI stays responsive during loading.

## 4. Verify orders appear

Action:
- Wait for the orders request to complete.

Expected behavior:
- Real backend orders are displayed in a scrollable list.
- Each card shows:
  - `displayTitle`
  - `deliveryNote` or fallback text if missing
  - `readinessLabel`
  - `invoicesCount`
  - `quantityLabel`
- Card colors reflect readiness state:
  - ready when backend `isReady=true`
  - warning/not ready when backend `isReady=false`
- Android does not calculate readiness from `quantityReadiness`; it displays backend labels.

## 5. Verify Cyrillic displays correctly

Action:
- Use backend data containing Cyrillic text in fields such as `displayTitle`, `deliveryNote`, `clientInfo`, or `driverName`.

Expected behavior:
- Cyrillic text renders correctly in the UI.
- No mojibake, replacement symbols, or broken layout appears.

## 6. Verify route button opens maps

Action:
- On an order with non-empty `routeText`, tap the `Route` button.

Expected behavior:
- The button is enabled.
- A maps app opens through Android `ACTION_VIEW`.
- The destination/search text matches the backend `routeText`.
- If no maps app is installed, a toast appears: `No maps app installed.`

Additional check:
- On an order with empty or missing `routeText`, verify the `Route` button is disabled.

## 7. Verify pull-to-refresh works

Action:
- Pull down on the orders list.

Expected behavior:
- Refresh indicator appears.
- Orders are reloaded from backend.
- UI remains interactive.
- If backend data changed, the list reflects the updated response after refresh.

## 8. Verify error state

Action:
- Break connectivity by enabling airplane mode, disconnecting network, or entering a wrong API URL in `Settings`.
- Open the orders screen or tap `Retry`.

Expected behavior:
- App does not crash.
- A readable error message appears, for example:
  - no internet
  - timeout
  - invalid API URL
  - server unavailable
- `Retry` triggers another orders request.

## 9. Verify empty state

Action:
- Use a `driverId` or date combination that returns no orders.

Expected behavior:
- Empty state is shown instead of a blank screen or crash.
- Message indicates there are no orders for today.
- Pull-to-refresh and retry behavior remain available.

## 10. Verify backend route-opened logging

Action:
- With a valid backend and a route-enabled order, tap `Route`.
- Check backend logs, database, or request traces for:
  - `POST /api/driver/orders/{order_id}/route-opened`
  - `order_id`
  - `driver_id`
  - `source=android`

Expected behavior:
- Route still opens immediately.
- Backend receives the route-opened event.
- If the logging request fails, maps still open and the app does not show a blocking error.

## Pass criteria

Smoke test passes when:
- Orders load successfully from the configured backend.
- Settings changes apply without recompiling the app.
- Health check works from the settings screen.
- Route opening works for valid routes.
- Route-opened backend logging is sent.
- Error and empty states are readable and stable.
- Offline or malformed backend responses do not crash the app.
