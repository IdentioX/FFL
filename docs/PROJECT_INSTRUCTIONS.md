# Project Instructions

## Project Snapshot

This repository contains a single-module Android application named `FFl24`.

- Root Gradle project: `FFl24`
- Android module: `app`
- Package/application ID: `ru.ffl24.zakaz`
- UI stack: Kotlin + Jetpack Compose + Material 3
- Networking: Retrofit + Gson + OkHttp logging interceptor
- State management: `ViewModel` + Kotlin `StateFlow`
- Main business flow: load driver orders from a backend, show readiness status, open a route in Yandex Maps, and log that the route was opened.

## Repository Map

- `settings.gradle.kts` defines the root project and includes `:app`.
- `build.gradle.kts` contains top-level Gradle plugin aliases.
- `gradle/libs.versions.toml` centralizes Android Gradle Plugin, Kotlin, Compose, Retrofit, OkHttp, and test dependency versions.
- `gradle/gradle-daemon-jvm.properties` requires Gradle daemon toolchain version `21`.
- `app/build.gradle.kts` configures Android SDK, app ID, Compose, dependencies, and `DRIVER_API_BASE_URL`.
- `app/src/main/AndroidManifest.xml` grants internet access, allows cleartext traffic, and declares `MainActivity`.
- `app/src/main/java/ru/ffl24/zakaz/MainActivity.kt` wires the app shell, top bar, settings toggle, and `DriverOrdersViewModel`.
- `app/src/main/java/ru/ffl24/zakaz/api/` contains Retrofit service and provider setup.
- `app/src/main/java/ru/ffl24/zakaz/model/DriverOrderDto.kt` defines the backend order DTO.
- `app/src/main/java/ru/ffl24/zakaz/repository/DriverOrdersRepository.kt` wraps backend calls and maps common network errors to user-readable messages.
- `app/src/main/java/ru/ffl24/zakaz/viewmodel/DriverOrdersViewModel.kt` owns loading/error/order state.
- `app/src/main/java/ru/ffl24/zakaz/settings/AppSettingsStore.kt` persists API URL and driver ID in `SharedPreferences`.
- `app/src/main/java/ru/ffl24/zakaz/navigation/YandexRouteNavigator.kt` builds and opens Yandex Maps intents.
- `app/src/main/java/ru/ffl24/zakaz/ui/` contains Compose screens, components, and theme files.
- `docs/ANDROID_SMOKE_TEST.md` and `docs/YANDEX_ROUTE_MANUAL_TEST.md` contain existing manual QA checklists.

## Runtime Flow

1. `MainActivity` creates `DriverOrdersViewModel` through a local factory.
2. The factory reads the current API base URL and driver ID from `AppSettingsStore`.
3. `DriverOrdersViewModel` calls `DriverOrdersRepository.getDriverOrders`.
4. `DriverOrdersRepository` calls `DriverApiService.getDriverOrders`.
5. `DriverOrdersScreen` observes `isLoading`, `orders`, and `errorMessage` with `collectAsStateWithLifecycle`.
6. `DriverOrderCard` renders each order and opens routing through `YandexRouteNavigator`.
7. If route opening succeeds, `DriverOrdersViewModel.logRouteOpened` sends the backend logging request.

## Backend Contract

The app expects these endpoints relative to the configured base URL:

- `GET /health`
- `GET /api/driver/orders?date=YYYY-MM-DD&driver_id=ID`
- `POST /api/driver/orders/{order_id}/route-opened?order_id=ID&driver_id=ID&source=android`

`DriverOrderDto` expects these fields:

- `orderId`
- `orderNumber`
- `driverId`
- `driverName`
- `clientInfo`
- `deliveryNote`
- `deliveryDate`
- `invoicesCount`
- `notReadyInvoices`
- `isReady`
- `isPartiallyReady`
- `routeText`
- `lat`
- `lon`
- `hasRouteText`
- `displayTitle`
- `readinessLabel`

Latitude and longitude accept alternate JSON names through `@SerializedName`:

- `lat`, `latitude`, `routeLatitude`, `route_latitude`
- `lon`, `longitude`, `routeLongitude`, `route_longitude`

## Configuration

Default backend URL is set in `app/build.gradle.kts` through the Gradle property `DRIVER_API_BASE_URL`.

If the property is missing, the default is:

```text
http://10.0.2.2:8080/
```

That value is suitable for an Android emulator talking to a backend running on the host machine.

You can override it from the command line:

```powershell
.\gradlew.bat assembleDebug -PDRIVER_API_BASE_URL=http://10.0.2.2:8080/
```

The app also lets the user change the backend URL and driver ID at runtime from the Settings screen. These settings are saved in `SharedPreferences`.

Default driver ID:

```text
97
```

## Build And Test Commands

Use PowerShell from the repository root:

```powershell
cd G:\Codes\projects
```

Build debug APK:

```powershell
.\gradlew.bat assembleDebug
```

Run local JVM unit tests:

```powershell
.\gradlew.bat testDebugUnitTest
```

Run Android instrumented tests with an emulator or device connected:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Install debug APK on a connected emulator or device:

```powershell
.\gradlew.bat installDebug
```

Current local verification note:

- `java -version` currently reports OpenJDK `17.0.19`.
- The Gradle daemon config requests Java toolchain `21`.
- A test run attempted with `.\gradlew.bat testDebugUnitTest` failed before compilation because Gradle could not download the Java 21 toolchain from Foojay.
- To verify locally, install JDK 21 or fix the toolchain download path, then rerun the Gradle commands above.

## Manual QA Checklist

Use the existing docs first:

- `docs/ANDROID_SMOKE_TEST.md`
- `docs/YANDEX_ROUTE_MANUAL_TEST.md`

Minimum manual path after a code change:

1. Start or point to a reachable backend.
2. Open the app.
3. Open Settings.
4. Set API base URL and driver ID.
5. Tap `Save settings`.
6. Tap `Test connection`.
7. Return to Driver Orders.
8. Verify loading, empty, error, and successful list states.
9. Tap `Route` on an order with coordinates.
10. Tap `Route` on an order that only has `routeText`.
11. Verify route-opened logging reaches the backend.
12. Disable network or use a bad URL and verify the app shows a readable error instead of crashing.

## Known Issues And Risks

- `DriverOrdersViewModel` currently uses a hardcoded date: `2026-05-18`. This is useful for test data but does not actually load today's orders.
- `docs/ANDROID_SMOKE_TEST.md` says the Route button should be disabled when route data is missing, but `DriverOrderCard` currently always enables the button. Missing route data is handled by a toast in `YandexRouteNavigator`.
- Existing tests are template tests only. There are no meaningful unit tests for repository error mapping, settings normalization, route URI generation, or ViewModel state transitions.
- `android:usesCleartextTraffic="true"` is enabled. This is practical for local HTTP backends but should be reviewed before production release.
- Debug builds log full HTTP bodies through OkHttp. Avoid using production secrets or private customer data in debug logs.
- `local.properties` is environment-specific and should not be treated as portable project configuration.
- The UI strings are mostly hardcoded in English, while Yandex route toasts are Russian. Decide on one localization strategy before expanding the UI.

## Development Guidelines For Me

When I work on this project, I should:

- Preserve the current architecture unless the requested change clearly requires a larger refactor.
- Keep networking changes inside `api/` and `repository/`.
- Keep user-visible loading, empty, and error behavior in `DriverOrdersScreen`.
- Keep route-specific logic inside `YandexRouteNavigator`; do not spread URI construction into composables.
- Keep persistent app settings inside `AppSettingsStore`.
- Prefer small, focused changes and add tests for logic-heavy code.
- Use existing Compose Material 3 patterns instead of introducing another UI framework.
- Avoid changing Gradle, SDK, or dependency versions unless the task requires it.
- Check existing docs before changing behavior that already has a manual test checklist.
- Never overwrite unrelated user changes in the working tree.

Recommended test targets after edits:

- For repository/API behavior: add local unit tests around result/error mapping.
- For settings behavior: add tests for `normalizeBaseUrl`.
- For route behavior: extract pure route URI building if route tests are needed.
- For UI behavior: use Compose UI tests only when the behavior cannot be covered at a lower level.

## Development Guidelines For You

When you continue working on the app:

- Use Android Studio with JDK 21 configured, or install JDK 21 for Gradle.
- Keep backend base URL configurable; do not hardcode a production server in Kotlin source.
- Use emulator URL `http://10.0.2.2:PORT/` for a backend running on the same PC.
- Use a LAN IP such as `http://192.168.x.x:PORT/` for a physical Android device.
- After changing backend response fields, update `DriverOrderDto` and run a real smoke test with backend data.
- After changing route behavior, run `docs/YANDEX_ROUTE_MANUAL_TEST.md`.
- After changing order loading or settings, run `docs/ANDROID_SMOKE_TEST.md`.
- Before release, review cleartext traffic, debug HTTP logging, package name, app name, icons, and ProGuard/minification settings.

## Suggested Next Improvements

1. Replace the hardcoded `2026-05-18` date with the actual current date or a user-selectable date.
2. Decide whether the Route button should be disabled when route data is missing, then align code and docs.
3. Add unit tests for `AppSettingsStore.normalizeBaseUrl`.
4. Add unit tests for repository HTTP and network error messages.
5. Extract route URI construction into a pure function that can be tested without Android intents.
6. Localize all user-facing strings through `strings.xml`.
7. Add a small README at repository root that points to this file and the QA checklists.
