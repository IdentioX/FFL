# P3 — водители и авторизация (не проверено)

После остановки на P2 пользователь написал **«Пропускай все»**. Это принято как указание продолжить код P3 без прежней остановки и без запуска проверок. P2 остаётся BLOCKED; фиктивный POSITIVE не ставился. Новые source changes не собраны, тесты/линт/emulator/device/MySQL не запускались. Документ описывает реализацию по исходникам, не доказанную runtime работоспособность. P4 не начат.

## Данные

DirectMySqlDriverDataSource работает только с __hr_typepos:

- getDrivers: SELECT id/name/fio/mobile/whatsapp, access=1, id/name not null, name<>'', ORDER BY driverName. Parol не выбирается. Условие имени не усилено до TRIM. Некорректная/неположительная identity даёт безопасную data error, а не фиктивный id.
- login: access lookup → prepared SELECT по posTypeId/parol/access=1 → повторный access lookup. Пароль передаётся setString, не возвращается из DB и не сохраняется. Нет активного совпадения → InvalidCredentials; отключённый водитель → DriverDisabled. Удаление между проверками даёт invalid credentials. Это последовательные reads, не серверная атомарная авторизация.
- checkAccess: prepared SELECT id/access с LIMIT1; null/нечисловое/значение, не равное числу 1, → DISABLED; нет строки → MISSING. Дробные/нестандартные legacy значения, charset/collation/password semantics требуют проверки на настоящей схеме. Клиент не выполняет UPDATE access для создания тестов.

MySqlReadExecutor выделен из P2 probe и используется обеими частями. Сохраняются Dispatchers.IO, общий Semaphore(2), connect/socket/query/operation timeouts, scope use/close, readOnly, отсутствие auto-retry. Ошибки драйвера/SSL/timeout не превращаются в неверный пароль. Driver connection/CA/TLS конфигурация не ослаблялась. Нового API, DDL, business-write, произвольной таблицы или JWT нет.

## Сессия и concurrency

DataStoreDriverSessionStore использует preferencesDataStore `ffl_direct_session_v1`, только version/driver_id/display_name. Отдельные app/storage от legacy JWT. Application создаёт один graph; factory по умолчанию без Android Context использует явно непостоянный InMemory store для изолированного wiring. Старые JWT не читаются/не мигрируют. Невалидный формат/версия очищается и ведёт ко входу.

AuthSessionManager держит disk Mutex и revision для поздних login/restore. Начало restore сбрасывает in-memory grant; наличие id на диске само по себе не даёт UI-сессию. Публикация только после fresh access, с проверкой revision/cancellation. Logout/disabled/missing очищают память и persistent session; устаревшая операция не должна перепубликовать старую identity. Отмена DataStore save пытается очистить уже committed hint. При IO failure очистка может не состояться: UI показывает storage error и не заявляет успешный logout; гарантии при дисковых сбоях/гонках не проверены.

AuthRepository.checkAccess использует текущую сессию менеджера без driverId override из protected UI. Сбой сети скрывает защищённое состояние; disk hint может остаться для явного retry, который вновь проверяет доступ. Будущие операции P4+ обязаны вызывать gate, а не брать id из UI. Shared DB account и подменяемая локальная identity не являются server-enforced authentication; DRIVER_ROLLOUT=BLOCKED_SECURITY.

## UI и пароль

Новый SetupAuthViewModel и Compose screen следуют имеющемуся search/select/password/loading/retry паттерну, без старого API setup. При configured startup выполняются restore/access или getDrivers; SELECT1 probe остаётся техническим helper, не экраном «вход пока недоступен». Без DB config остаются ConfigurationMissing/Invalid без полей host/password.

После login — минимальный P3 экран имени/проверки доступа/выхода. Заказов/трёх разделов/доставки нет. Disabled — «Недоступно» и обновление списка/возврат к выбору, без автоматического восстановления отключённой сессии. Это developer UX по разумному допущению, не отдельная пользовательская приёмка C02 и не полный P9 parity.

Пароль String живёт только в remember диалога, без rememberSaveable/SavedStateHandle/StateFlow. До login он переносится в CharArray, поле очищается; буфер стирается при завершении, отмене, background/onCleared. Ошибка не сохраняет пароль, повтор требует нового ввода. JDBC требует временную immutable String: гарантировать zeroization всех JVM/IME копий нельзя. Логи паролей/DB реквизитов/сырого SQL error не добавлены.

ON_STOP отменяет auth job, очищает ввод, скрывает authenticated UI. ON_START вновь делает restore/access; network/config/TLS/storage failures блокируют вход без offline bypass. Theme/settings/back остаются отдельными от auth credentials. Всё перечисленное требует последующих UI/process-death/network tests.

## Что не запускалось

Ни Gradle/dependency resolution/compile, ни unit tests/lint/static validator, ни instrumentation/emulator/physical device/MySQL. Новых auth tests и fresh XML нет; прошлые 26 unit/11 Android PASS — только P2. Имеющийся APK — P2; новые исходники P3 в нём отсутствуют. SQL/password/collation/read-only mode на настоящей DB не проверены, равно как DataStore restore/logout/cancel races и minified APK.

В будущем нужны проверки active-only list, wrong password, missing/disabled/null access, revocation между login/access, session restore/logout, сетевой сбой отдельно от bad password, cancellation/late completion, storage failure, отсутствие password в saved state/logs/backup, resource closure/IO/TLS и build/runtime с выбранным JDBC. Они не отмечены PASS и не заменены fixture-логином. Исходные семантики и P0/P1/P2 evidence сохранены.
