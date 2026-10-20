# План Direct-переработки P0–P11

Актуализация 17.10.2026: пользователь явно снял запрет продолжать без POSITIVE предыдущего этапа и поручил довести программу по нашему усмотрению. Прежние STOP-отчёты — история, они больше не блокируют developer-реализацию. Developer-приложение завершено: P4/P6/P7/P8/P9 функции, debug/R8 build,831 unit и28 Android tests, настоящий UI→JDBC/TLS→одноразовый синтетический MySQL8.4.11 (включая delivery/rollback) проверены. Рабочая legacy БД и physical device остаются непроверенными; фиктивного POSITIVE нет, разрешение на production write/распространение не подразумевается. Точные результаты и APK — PROGRESS.md/COMPLETION.md, прежние разделы ниже являются историей.


Уточнение scope P5: пользователь разрешил «пропускать эти блоки», поскольку проверки на реальных сейчас недоступны. P4 проверен: BLOCKED, его код не реализован. В пределах нового указания выполнена developer реализация P5 и локальные проверки; полный acceptance/gates не объявлены PASS. Порядок исторических этапов и их результаты не переписаны. P4 list/sections/order не реализуются внутри P5; необходимые OrderId и details navigation подготовлены для будущей интеграции. P6 автоматически не начинать. Детали — P5_IMPLEMENTATION.md/PROGRESS.md.

Новое указание пользователя после остановки P3: **«Пропускай все»**. Реализация P3 продолжена без ожидания P2 POSITIVE и без выполнения проверок; acceptance результаты не подменялись. P2/P3 verification остаются BLOCKED, P3 source-only changes описаны в P3_IMPLEMENTATION.md/PROGRESS.md. Это разрешение продолжить текущую реализацию, не автоматический переход к P4 и не фиктивный PASS gates ниже.

Актуализация P2 от 16.10.2026: по указанию пользователя connection layer реализован и проверен локально; **P2 BLOCKED** по отсутствию согласованной MySQL и физического устройства. 26 unit/11 API30 instrumentation PASS не заменяют real SELECT1/auth/grants. Полный gate ниже не изменён; P3 не начат. Точные evidence — начало PROGRESS.md и MYSQL_CONNECTION_LAYER.md. Следующий абзац отражает состояние после P1.

План составлен в P0. По отдельному указанию пользователя выполнен **P1**, результат POSITIVE и доказательства — в начале PROGRESS.md. P2 не начинался и требует нового указания. Пути этапов P2–P11 ниже остаются планируемыми. Корень нового проекта `/workspace/FFL-direct`, app — `app/src/main/java/ru/ffl24/direct/`; test/androidTest — соответствующие source sets. Эталон UI — текущая ветка FFL, подтверждено пользователем; недостающее согласуется отдельно.

## Общий порядок и gates

P0 → P1 → P2 → P3 → P4 → P5 → P6 → P7 → P8 → P9 → P10 → P11. Переставлять этапы молча нельзя. Продолжение только после POSITIVE текущего этапа и в пределах разрешённого пользователем scope; после текущего P0 — остановиться даже при POSITIVE. NEGATIVE/BLOCKED → остановиться, приложить доказательства; не переносить обязательную неуспешную проверку в «optional», чтобы открыть следующий этап.

Для всех этапов: сохранить старые проекты/пользовательские изменения, параметризовать SQL, не логировать секреты, только Direct runtime без API/JWT/HTTP fallback, без новых DB tables/DDL. Live DB_WRITE только после конкретного одобрения безопасного test order. Пока серверная изоляция водителей не доказана, DRIVER_ROLLOUT=BLOCKED_SECURITY. Это не мешает разрешённому developer-only проектированию, но не даёт разрешения распространять APK.

**Точный формат отчёта:** каждый этап обязан заполнить все поля шаблона из [DIRECT_QA_CHECKLIST.md](DIRECT_QA_CHECKLIST.md#шаблон-отчёта-каждого-этапа), включая изменённые файлы, executed/unrun counts, cwd/команды/exit codes, APK/БД, восемь отдельных статусов и строку `DIRECT_REWRITE_Pn_RESULT`. Ниже для каждого этапа указаны дополнения. Дата — Europe/Moscow; не переносить client timezone на business date/MySQL NOW без решения.

## P0 — аудит и архитектурное решение

- **Цель:** получить воспроизводимый контекст и scope, выбрать изоляцию, не менять приложение.
- **Файлы:** семь документов `docs/direct-rewrite/{MASTER_CONTEXT,DIRECT_REWRITE_PLAN,DIRECT_DB_CONTRACT,DIRECT_UI_CONTRACT,DIRECT_QA_CHECKLIST,SECURITY_RISKS,PROGRESS}.md`; точные копии report/master и `evidence/`.
- **Вход:** полный report пользователя, текущий Android/backend source и Git status; инструкции о Direct и сохранении rollback.
- **Обязательно:** прочитать report полностью; сверить SQL/source; изучить UI/DTO/ViewModels/Gradle/tests/docs; A/B/C comparison; reuse/exclusion matrix; unresolved conflicts; все P0–P11 gates.
- **Запрещено:** новый Android/JDBC runtime, изменение legacy бизнес-логики, запуск DB UPDATE/публикация APK.
- **Unit:** существующие suites только baseline evidence; не писать фиктивные тесты P0. Fresh execution и cached build результаты отличать.
- **Runtime:** не требуется для документационного P0; Android/DB остаются отдельно BLOCKED, source report не доказывает схему.
- **Acceptance:** семь завершённых согласованных документов; report сохранён; ссылки/хеши/чистота references проверены; неизвестные и риски явно названы; P1 не начат.
- **Stop conditions:** отсутствующий report, неразрешённое чтение обязательных источников, обнаруженное затирание пользовательских файлов либо невозможность закончить аудит. Отсутствие живой БД не блокирует сам аудит P0.
- **Отчёт:** общий шаблон + ADR-001, reuse/exclusion, 12 этапов с критериями, факты/предположения, P1 readiness. После отчёта остановиться.

## P1 — изолированный Android runtime

- **Цель:** отдельный минимальный developer app без унаследованного API startup.
- **Файлы:** новые `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, wrapper + checksum, `gradle/libs.versions.toml`, `.gitignore`, `app/build.gradle.kts`, manifest/resources, `MainActivity.kt`, `ui/theme/*`, composition root и state contracts. Старые projects не подключать как project dependency.
- **Вход:** POSITIVE P0 и новое указание пользователя. Отдельный dev applicationId; текущие tool versions как исходная совместимая база, без необоснованного обновления.
- **Обязательно:** один DIRECT_MYSQL режим, чистые interfaces и безопасное ConfigurationMissing состояние без реквизитов/URL-полей; новая storage namespace; разрешения только необходимые. Переиспользовать минимум чистого theme/resources с provenance. В Gradle dependency graph нет Retrofit/DriverApiService/backend JWT.
- **Запрещено:** JDBC/SQL-реализация, копирование MainActivity/factories старого app, API/Direct toggle, runtime зависимости на старый app, миграция JWT.
- **Unit:** startup state mapping и factory/interface wiring без подключения; tests не просто mirror constants. Проверить отсутствие неподдерживаемого состояния fallback статическим graph scan.
- **Runtime:** cold-start smoke без config на emulator/device при наличии; в этом этапе это диагностический результат, а не проверка DB. При отсутствии устройства явно BLOCKED направления, не выдавать JVM за ART.
- **Acceptance:** новый `:app:assembleDebug` и осмысленные unit checks exit=0; runtime dependency/manifest scan подтверждает изоляцию; reference hashes неизменны; никаких запросов/секретов. Полный UI и live JDBC не входят в P1.
- **Stop conditions:** Direct target не собирается, API transitives/startup утекли в runtime, reference затронут или нет разрешения начать P1.
- **Отчёт:** общий шаблон + dependency graph, package/variant/APK SHA, статическое доказательство single mode и явно unrun emulator/device.

## P2 — конфигурация и JDBC connection layer

- **Цель:** доказать Android-совместимое Direct-соединение с безопасной TLS и жизненным циклом ресурсов.
- **Файлы:** `data/mysql/{DirectMySqlConfig,MySqlConnectionFactory,DirectDbError}.kt`, dispatchers/concurrency policy, Gradle config injection и ignored local.properties; JDBC dependency/keep rules только в новом app; test fakes и androidTest connection checks.
- **Вход:** POSITIVE P1; официальный artifact/version/checksums/license; доступный согласованный MySQL test endpoint, CA/hostname/auth plugin, ограниченный dev account; схема/engine/grants read-only inspection с разрешением. Credentials вводятся безопасно, не в чат.
- **Обязательно:** оценить как минимум MySQL Connector/J и MariaDB Connector/J по Android/ART/API24, D8/R8, Java API/auth plugin/TLS; выбрать и закрепить только после проверки. Все шесть DIRECT_MYSQL_* properties, валидация отсутствия/port, никакого логирования values. Verified CA+hostname TLS, timeout connect/socket/query, bounded concurrency, IO dispatcher, cancellation/resource closure. Не путать Java21 host с Android runtime.
- **Запрещено:** vendor compatibility утверждение по desktop test, отключение cert/hostname checks, plaintext downgrade, admin account, connection singleton без scope, UI DB credentials, бизнес-write.
- **Unit:** missing/partial/invalid config, safe errors/redaction, close/cancel paths, timeout/retry policy, неутечка Connection между запросами.
- **Runtime:** установить конкретный APK на representative emulator и реальное устройство; реально выполнить read-only SELECT1/schema capability probe без PII; positive TLS и negative CA/hostname tests, offline/timeouts, StrictMode/no Main-thread JDBC. Это техническое чтение, не E2E orders/login.
- **Acceptance:** driver загружается на Android, сборка/unit положительны, connection/read/TLS и closure подтверждены на согласованном runtime, endpoint/grants evidence без секретов; runtime failures не маскируются API fallback. Поддерживаемые версии Android/сети явно перечислены.
- **Stop conditions:** нет стенда/device/CA/полномочий, verification/JDBC несовместимость или остаётся Main-thread/network leak. При обязательном runtime без устройства — BLOCKED P2, P3 не начинать.
- **Отчёт:** общий шаблон + driver version/license, MySQL/Android API/ABI, что именно SELECT доказал, TLS-negatives, журнал ресурсов; без host/credentials values.

## P3 — водители, login, local session, access

- **Цель:** войти без backend и сохранить сессию без пароля/JWT.
- **Файлы:** `domain/{Driver,DriverSession,AccessState}.kt`, `data/mysql/DirectMySqlDriverDataSource.kt`, `data/repository/AuthRepository.kt`, `session/AuthSessionManager.kt`, `presentation/SetupAuthViewModel.kt`, адаптированный auth UI/disabled state; tests.
- **Вход:** POSITIVE P2, актуальная схема __hr_typepos, test driver/password, согласованный сценарий «Недоступно» (C02). Не использовать bf.
- **Обязательно:** active-only список без parol, parameterized login posTypeId/parol/access; local id/name без password/JWT; access на старте и перед protected ops; missing/disabled/network ошибки раздельны. Очищение transient password и session-specific state, no driver override.
- **Запрещено:** fake credentials как live login proof, выбор паролей в driver-list DTO, persist password/saved-state, токен backend, data access до access gate.
- **Unit:** active/inactive/missing/blank name, wrong password, access изменился/не число/null, restart/logout, сетевой сбой не wrong-password.
- **Runtime:** реальный разрешённый driver list/login/restore, wrong password, согласованный test access-revocation scenario без самовольного UPDATE access. Inspection local storage/logcat не раскрывает пароль.
- **Acceptance:** live read/login подтверждены, UI не просит backend/DB config, disabled блокирует операции, password/JWT не сохраняются; phase tests/build положительны. Клиентский gate не объявлен серверной защитой.
- **Stop conditions:** нет test driver/password или безопасного access-test, неопределённый disabled UX, plaintext/legacy password semantics расходятся с БД, login/access checks провалены.
- **Отчёт:** общий шаблон + session lifecycle/access cases, реальные vs mocked reads и security limitations.

## P4 — заказы, разделы, локальный порядок

- **Цель:** достоверные заказы двух таблиц с уникальной identity и согласованными тремя разделами.
- **Файлы:** `domain/{OrderId,DriverOrder,OrderSection}.kt`, orders SQL/mappers, `data/repository/DriverOrdersRepository.kt`, `local/ManualOrderStore.kt`, `domain/DriverOrdersDateProvider.kt`, `presentation/DriverOrdersViewModel.kt`, minimal section UI/tests.
- **Вход:** POSITIVE P3; решения C01/C03/C10 по UI/дате/порядку, read-only test data обоих sources и DB typing.
- **Обязательно:** enum allowlist, parameterized date/id_v и readiness; exact mapping status/col_s/col/st_vv; delivered видимы в третьем разделе; local reorder driver/date/section без sort_r write; stable IDs при одинаковом numeric id; согласованный merge/scroll policy; baseline backend SQL order и local overlay различать.
- **Запрещено:** .toInt() source ID, infer readiness из количества, silent today/производственный query по test date без решения, drop delivered, SQL UPDATE sort_r, arbitrary table names.
- **Unit:** null/unknown mappings, filters, collisions/invalid IDs, SQL binding, empty sections, driver/date isolation, refresh/new/deleted/delivered order, deterministic sorting.
- **Runtime:** live read обоих tables на согласованную дату/driver; сверка безопасных агрегатов/контролируемых rows с reference, refresh и локальное восстановление. Никакой delivery write для создания fixture.
- **Acceptance:** domain/model/unit/build положительны, реальные reads проверены, три раздела и local ordering не меняют DB; дата/merge/новый UI согласованы. Не выдавать прежний единый список за сохранённый three-section UX.
- **Stop conditions:** дата/UI не определены, не совпали mapping/order/source identity, отсутствуют test rows/права, появилась лишняя запись.
- **Отчёт:** общий шаблон + resolved C01/C03/C10, schema/date scope без PII, collision и local-order evidence.

## P5 — детали, resolver, маршруты и контакты

- **Цель:** parity выбора адреса/деталей без выдачи чужого заказа и без произвольного выбора кандидата.
- **Файлы:** `data/mysql/OrderContactResolver.kt`, details/history queries/mappers, `domain/OrderDetails.kt`, `domain/ContactParsing.kt`, `presentation/OrderDetailsViewModel.kt`, navigation helpers и адаптированные details/contact UI/tests.
- **Вход:** POSITIVE P4, решения C02/C05: comment parsing, ручной маршрут, dial/copy UX; test data missing/one/multiple/tied candidates; apps/Intent capability на runtime.
- **Обязательно:** exact normalization/scoring/thresholds/cache semantics, TRIM nomer_z, 4 URI schemes; manual при ambiguous/нет достоверной link; текущий owner; same-source история<=5; time/comment parsing из report; raw text не менять. Phone parsing на Android после согласования; route-opened только ownership+no-op.
- **Запрещено:** выбрать первый match, substituting parsed name_c address за resolver, cross-table history, SQL/event writes, HTTP telemetry fallback, логирование адресов/телефонов.
- **Unit:** все cases resolver/regex/nullable fields из QA, URI scheme/Intent fallback, phones/raw comment preservation, owner/history filter, no-op route validation.
- **Runtime:** реальное read деталей/history/contacts на безопасном наборе; открытие Яндекс Карт/Навигатора, copy/dial и отсутствие handlers. Не совершать автоматические реальные звонки.
- **Acceptance:** parity fixtures+live read положительны, ambiguous safe, source ID сохранён до navigation, согласованный UI/телефоны работают; build положительный, DB read-only.
- **Stop conditions:** не согласованы отсутствующие UI/parser функции, live schema противоречит report, разные resolver результаты без объяснения, unsafe URI/owner leak.
- **Отчёт:** общий шаблон + golden comparison counts, URI/manual cases и подтверждение отсутствия route-opened persistence.

## P6 — markDelivered и транзакции

- **Цель:** одна контролируемая бизнес-запись с ownership/access и idempotency.
- **Файлы:** `data/mysql/DirectMySqlDeliveryDataSource.kt` или изолированный метод data source, `domain/DeliveryResult.kt`, repository, delivery VM state/confirm UI/tests; write tests отдельно от обычного suite.
- **Вход:** POSITIVE P5, C06/C07 решения по ready/countdown, engine/transaction/grants подтверждены, **явно одобренный конкретный безопасный test order**. Отдельное подтверждение live write не заменяется общим разрешением писать приложение.
- **Обязательно:** transaction, access, source allowlist, SELECT FOR UPDATE id/id_v, already-delivered no update, SET только st_vv/date_att_log, affected rows/post-read validation до commit, rollback/close, двойной tap/unknown commit policy. Учитывать access race и DB server isolation limitation.
- **Запрещено:** любые иные columns/tables/DDL, production write, auto-retry неизвестного commit, сообщение успеха до commit/postconditions, driverId из UI.
- **Unit:** commit/rollback/resource paths, zero affected rows, no owner, disabled, already delivered, concurrent request, исключение между UPDATE и commit, lost commit response.
- **Runtime:** на одобренном стенде before/after всех затрагиваемых columns, один разрешённый transition и повтор idempotent, проверка чужого заказа/disabled безопасным согласованным способом. До разрешения — никаких live writes даже при pending gate.
- **Acceptance:** тесты/сборка/реальная разрешённая запись подтверждены, только два поля изменены, invariant остальных доказан; C06/C07 закрыты. Unit mocks без approved live test не дают POSITIVE P6.
- **Stop conditions:** нет safe order/approval/grants, transactional guarantees не доказаны, лишнее изменение, неясный commit, дефект ownership/access. Стоп и отчёт, не переходить к P7.
- **Отчёт:** общий шаблон + ссылка на разрешение/test alias без PII, before/after proof, неизменённые поля, concurrency/idempotency/rollback и unknown result.

## P7 — телефонный справочник

- **Цель:** безопасное чтение payment card/важных телефонов/активных водителей с согласованным экраном.
- **Файлы:** `domain/PhoneDirectory.kt`, `data/mysql/DirectPhoneDirectoryDataSource.kt`, `data/repository/PhoneDirectoryRepository.kt`, VM и `ui/PhoneDirectoryScreen.kt`, copy/dial helpers/tests.
- **Вход:** POSITIVE P6, read permissions на nastroiki/phone/__hr_typepos, C02 UI/entrypoint/actions согласованы.
- **Обязательно:** karta/imya_karta, null handling, blank phones skip, active driver mobile→whatsapp, global directory после access; no silent split/dedupe; copy/dial согласно согласованному UX.
- **Запрещено:** UPDATE справочника/карты/водителей, публикация номеров в evidence, объявлять новый экран сохранённым существующим.
- **Unit:** mapper/query/access, missing card, null/blank, inactive/no-phone, raw phone strings, copy/dial models.
- **Runtime:** live read и безопасная сверка экрана/clipboard/dial на конкретном APK без фактического звонка/оплаты.
- **Acceptance:** source mapping и действия подтверждены, нет writes, UI согласован, tests/build/read positive.
- **Stop conditions:** нет данных/прав/дизайна, leak/parol в DTO, неправильные fallback или запись.
- **Отчёт:** общий шаблон + what read, global-vs-own scope, nullable/fallback и неприменимые действия.

## P8 — уведомления и background polling

- **Цель:** polling-based second-route notifications с корректными counters и tap flow.
- **Файлы:** summary SQL, `domain/NotificationSummary.kt`, `data/repository/NotificationRepository.kt`, background worker/scheduler/receivers, notification channel/actions, manifest/permissions, persisted seen-ID scope/tests.
- **Вход:** POSITIVE P7, C02 согласованное поведение interval/first observation/дедупликации/reset/tap, Android permission/Doze/background policy. Существующих receivers в reference нет.
- **Обязательно:** counters + source-aware active second-route IDs, access gate, logout/disabled cancellation, scope driver/date, no duplicates, correct PendingIntent identity/immutability, реальные ограничения chosen scheduler. WorkManager не обещает короткий exact interval; foreground service только после отдельного решения.
- **Запрещено:** называть polling push, silent always-on service/FCM/API fallback, polling чужой session, unlimited connections/retries или bypass permissions.
- **Unit:** summary edge cases/ID order, new IDs/delivery/date/driver transitions, disabled/logout, tap target и scheduler contract.
- **Runtime:** emulator и real device, notifications permission/channel, cold/warm tap, background/Doze/reboot по принятому дизайну; реальная summary read без подмены production updates.
- **Acceptance:** agreed polling поведение доказано с измеренным interval/delay, ограничения описаны, tests/build/live summary и runtime scenarios positive.
- **Stop conditions:** interval/UX не согласованы, scheduler не может дать требуемое поведение, нет устройств/permission tests, duplicate/foreign-driver leakage.
- **Отчёт:** общий шаблон + Android/API, scheduler constraints, наблюдённые timings, notifications не названы push.

## P9 — полный UI/UX и навигация

- **Цель:** закрыть visual/behavioral parity имеющегося UI и отдельно согласованных недостающих функций.
- **Файлы:** все новые ui screens/components/theme/resources, composition/navigation, state restoration, local settings, semantics и androidTest UI; только новые project files.
- **Вход:** POSITIVE P8, все C01/C02/C05/C06/C07 UI решения, current reference screenshots/запуск при возможности, согласованные новые sections/drag/contact/directory/notification UX.
- **Обязательно:** auth/orders/details/settings-theme/directory/disabled, три раздела, компактные rows и звонок, drag order/scroll state, dialog parity, contacts/time/history, loading/empty/error. Ни UI backend config, ни driver DB config. Source-aware deep links и session/access gate.
- **Запрещено:** redesign без согласования, исчезновение требований master, перенос old backend VM ради UI, неподтверждённые заявления visual parity по source alone.
- **Unit:** presentation state и restoration/navigation; Compose UI tests там, где поведение не покрывается чистым unit case.
- **Runtime:** сравнительные screenshots/manual paths для baseline и approved additions, phone font scale/rotation/process death/theme/back/refresh, notification navigation, no config fields.
- **Acceptance:** UI matrix со всеми строками закрыта actual evidence и согласованными additions; tests/build/runtime positive, старый UI не изменён.
- **Stop conditions:** есть unsolved product conflict, потеря scroll/order, missing screen/action, runtime падение/неподдержанная accessibility.
- **Отчёт:** общий шаблон + UI matrix expected/actual, approved difference links, APK/device и screenshot artifacts без PII.

## P10 — реальное E2E, developer APK и rollback

- **Цель:** доказать интеграцию готового developer Direct-клиента без backend, сохранить откат.
- **Файлы:** только new-app integration/androidTest/QA evidence, build/packaging config по необходимости, документация APK/rollback; старые repos/branch не удалять.
- **Вход:** POSITIVE P9, стенд/schema/grants, test driver/password, разрешённый safe order для write, emulator+real device, отдельные пакеты/signatures, договорённость о rollback.
- **Обязательно:** fresh login/restore/access denied, обе route tables/date/sections/сортировка, details/resolver/contacts/history, delivery/idempotency, directory, polling/tap, offline/TLS/config failure. Network inspection: нет FastAPI driver traffic. Собрать точный developer APK, signature/hash/package, installer/version evidence, сохранить reference HEAD/доступный APK и сценарий rollback без reset БД.
- **Запрещено:** production/admin APK, rollback через изменение БД/удаление source/force push, считать сборку E2E, автоматически распространять pilot.
- **Unit:** все meaningful suites и current XML; integration cases реально исполняются, skipped/disabled/unrun отдельно.
- **Runtime:** полный E2E на emulator и устройстве, live DB reads и ранее одобренные writes; backend выключен/недоступен и Direct функции не делают fallback. Rollback возвращает reference-app, не смешивает sessions/secrets и не обещает undo выполненной доставки.
- **Acceptance:** все требуемые сценарии actual pass, артефакт идентифицирован, rollback проверен/ограничения названы; runtime gaps не списаны на готовый APK. Только developer readiness, rollout ещё заблокирован до P11.
- **Stop conditions:** нет driver/order/device/access approval, E2E failure, schema mismatch, не доказана независимость backend/rollback, sensitive artifact.
- **Отчёт:** общий шаблон + test matrix, APK SHA/signature/cwd/exit, actual installs/DB evidence и rollback proof.

## P11 — security review и решение о pilot

- **Цель:** честная проверка server-enforced driver isolation и допустимости pilot.
- **Файлы:** SECURITY_RISKS/QA/PROGRESS, grants/procedure/identity design evidence без секретов; любые изменения серверной инфраструктуры требуют отдельного scope, не выполнять DDL в рамках аудита.
- **Вход:** POSITIVE P10; DBA/infra полномочия и безопасный стенд для независимых authz-negative tests; выбранный server-side identity design, правила distribution/revocation/network exposure.
- **Обязательно:** атакующий SQL-клиент с доступными APK credentials не читает/меняет чужие строки, parol protected, disabled/revoked identity не обходит запрет, column-grants/WHERE не названы row security. APK/logcat/storage/backup/TLS/dependency audit. Рассмотреть separate identities/procedures; minimal API только как отдельно одобренное изменение архитектуры.
- **Запрещено:** объявлять shared credential+VPN/TLS эквивалентом backend auth, подписывать production readiness по lack of logs, скрыто вводить API или distributing full-access APK.
- **Unit:** security-related regression tests полезны, но не заменяют независимый runtime/database permission test.
- **Runtime:** на разрешённом стенде actual malicious-client denied reads/writes, revocation, CA/hostname failures, inspected exact candidate APK. Положительная штатная работа не доказывает запреты.
- **Acceptance:** проверяемая серверная изоляция и lifecycle credentials доказаны, residual risks явно приняты уполномоченным пользователем; только тогда отдельное pilot decision. Никакой автоматической публикации.
- **Stop conditions:** нет полномочий/доказательств, общий account позволяет обход, пароли читаются, TLS/identity/revocation не доказаны → этап BLOCKED/NEGATIVE, `DRIVER_ROLLOUT: BLOCKED_SECURITY`. Developer-only код остаётся отдельно, без заявления pilot readiness.
- **Отчёт:** общий шаблон + threat matrix S01–S12, grants/negative evidence, residual risk owner, explicit rollout result. Не переходить к распространению без нового разрешения.

## Фактическое положение

P0 завершается только документацией; P1–P11 не начаты. Известные будущие внешние blockers: live schema/network/CA/account/grants, Android emulator/device, test driver/password, approved safe order и серверная driver isolation. Продуктовые решения C01/C02/C03/C05/C06/C07 сохранены и должны быть закрыты до указанных gates. Начать P1 можно после отдельного указания, не дожидаясь вымышленных DB credentials; выполнить P2/P3/P6/P10 без реальных prerequisites и объявить их POSITIVE нельзя.
