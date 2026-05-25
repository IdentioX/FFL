# Android Auth Flow

## First Launch

On first launch the app reads `AuthSessionManager`.

- If no saved token exists, the app shows `SetupAuthScreen`.
- The driver list is loaded from the backend.
- The driver selects only by displayed `driverName`.
- The driver enters the password once.
- After successful login, the app saves the auth session and opens orders.

The auth screen is not part of the normal driver UI after setup. To show setup again, clear the app data.

## Backend Endpoints

- `GET /api/auth/drivers` loads selectable drivers.
- `POST /api/auth/login` logs in with `driverId` and `password`.
- `GET /api/auth/me` validates the saved token.
- `GET /api/driver/orders` loads orders with `date=YYYY-MM-DD` and `Authorization: Bearer TOKEN`.

The `driver_id` query parameter for orders is deprecated. Normal Android order loading uses the bearer token instead.

## Token Storage

Auth session data is stored with DataStore Preferences:

- `accessToken`
- `driverId`
- `driverName`

The password is never stored. It is only held in setup screen state until login finishes or the dialog is dismissed.

## Reset

Reset auth by clearing Android app data. This removes the DataStore session and makes the next launch show setup auth again.

## Manual Test Checklist

1. Clear app data.
2. Open the app.
3. Verify the driver list loads.
4. Select a driver.
5. Enter a wrong password.
6. Verify `Неверный пароль` is shown and setup stays open.
7. Enter the correct password.
8. Verify orders are shown.
9. Restart the app.
10. Verify the auth screen does not appear.
11. Verify orders still load.
