# FFL Driver Direct0.3.1-dev

Независимое Android-приложение `ru.ffl24.direct.dev`: Compose→JDBC/TLS→MySQL/MariaDB, без FastAPI/JWT/API fallback и экрана backend IP. Старые FFL/FFL-backend сохранены без изменений.

Реализованы вход/access gate/local session, первый и второй рейс/сданные, source-aware IDs, детали/адреса/контакты/5водителей, сортировка и scroll, транзакционная «Сдал», справочник, тема/дата, opt-in уведомления второго рейса. Дата по умолчанию сегодня; для тестов используется явно выбранная дата. Polling WorkManager>=15мин, без обещания точного времени в Doze.

**DEVELOPER_TEST_READY.** Текущая версия: [APK0.3.1](releases/FFL-Driver-Direct-0.3.1-dev.apk), [исходники](releases/FFL-Driver-Direct-0.3.1-dev-source.zip), [галерея](docs/evidence/ui-design/index.html). Подключение теперь настраивается в отдельном защищённом редакторе без пересборки: [инструкция](docs/DB_CONNECTION_SETTINGS.md). Утверждённый дизайн, пять действий панели и сортировка: [отчёт](docs/UI_IMPLEMENTATION_REPORT.md).

Исторический итоговый аудит0.2.2: [инженерный отчёт](docs/FINAL_ENGINEERING_AUDIT.md), [тесты](docs/FINAL_TEST_RESULTS.md), [риски](docs/REMAINING_RISKS.md), [готовность и запуск с нуля](docs/RELEASE_READINESS.md). Исходники: [source ZIP](releases/FFL-Driver-Direct-0.2.2-dev-source.zip). Распространение водителям блокируется отсутствием серверной изоляции общего DB account.

## Восстановленная тестовая БД

Предоставленный полный SQL собран из5ZIP-частей, все SHA проверены. Поднята отдельная MariaDB10.6.25 с101таблицей и исходными данными; production не используется. Настоящие MainActivity/DirectApplication/BuildConfig работают с этой копией. После импорта локализованы DEFINER routines/triggers с проверкой неизменности их кода. Server triggers не отключаются; rollback проверяется вместе с связанной BF записью.

[Восстановление, запуск и Windows](docs/direct-rewrite/RESTORED_DATABASE.md), [общая локальная среда](docs/direct-rewrite/LOCAL_DATABASE.md), [результаты и evidence](docs/direct-rewrite/PROGRESS.md).

Для обычного пользователя: на чистой установке откройте «Настроить подключение», введите параметры отдельного DB-пользователя и доверенный CA, проверьте соединение и сохраните профиль. Для последующего изменения требуется системный PIN/пароль устройства. Пароль хранится зашифрованным Android Keystore; новый профиль требует повторного входа водителя.

В подготовленной Linux-среде прежний приватный BuildConfig-стенд по-прежнему доступен:

```bash
source /workspace/.ffl/env.sh
export FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored
python3 tools/dev_database.py start
python3 tools/dev_database.py harden-grants
python3 tools/dev_database.py status
python3 tools/dev_database.py prepare-restored
python3 tools/dev_database.py build
# На запущенном выделенном тестовом эмуляторе:
python3 tools/dev_database.py test-restored --serial emulator-5558
```

Если Docker-ресурсы не сохранились, `up --dump /private/path/112.sql.gz --port 19066` восстанавливает собственную копию заново. Не переключайте/не удаляйте существующий профиль автоматически. Source ZIP не содержит личную БД: используйте собственные предоставленные архивы и kit00. На Windows Python3.11+/Docker Desktop/OpenSSL/JDK21/Android SDK, свои пути/serial; Linux env.sh на Windows не используется. Команды Windows подготовлены, но не запускались на Windows.

Тестовые записи: поиск `FFL E2E`, fictional password `ffl-e2e-only-42`, дата2099-12-31. Они изолированы высоким диапазоном ID и marker; исходные строки сверяются с независимым чистым импортом. Raw персональные данные/реальные пароли/настроенный APK/private logs находятся вне checkout, не включаются в публичные артефакты.

## Сборка без конфигурации

JDK21, Android SDK36.1/build-tools36.0.0, Gradle wrapper. Compose BOM2026.03.01 (runtime/ui/foundation1.10.6), Kotlin2.2.10, pinned MariaDB Connector/J3.5.10.

```bash
bash gradlew --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease
```

[Credentials-free APK](releases/FFL-Driver-Direct-0.2.2-dev.apk) устанавливается отдельно от старого приложения. Без DB BuildConfig он показывает «Подключение не настроено». Настроенная рабочая тестовая сборка создаётся `dev_database.py build/android`; её generated sources/cache/APK вынесены в private state, реквизиты через environment, без значений в argv. Она содержит извлекаемый одноразовый локальный пароль, не публикуйте её. Root и production credentials в APK не используются.

Release R8 собирается unsigned; отдельно private minified APK подписан локальным debug key и проверен независимой Java instrumentation. Узкое правило WorkDatabase_Impl сохраняет constructor, удаление которого воспроизводило startup crash. Debug подписан Android debug key. Точные hash и installed APK proof — PROGRESS/evidence. [Ручная конфигурация](docs/direct-rewrite/MYSQL_CONNECTION_LAYER.md) содержит исторические ограничения P2; текущие business functions и проверки — RESTORED_DATABASE/PROGRESS.

## Проверки и ограничения

Итоговый набор:843 JVM,29 public Android UI/TLS,11 Android→restored DB и2 independent minified runtime methods. Категории проверены отдельно, без подмены настоящей БД fixtures. Ordinary MainActivity login/date/orders/details/delivery/directory/session и WorkManager→SQL→notification→PendingIntent реально запускались. Дополнительно: concurrent delivery обеих таблиц, auth-row lock, connection loss после UPDATE/rollback, concurrent restore и operator access revoke. Counts/первые ошибки/повторные прогоны/installed SHA — FINAL_TEST_RESULTS. При crash/missing XML/skip успех не объявляется.

Роль приложения читает только7контрактных таблиц, вызывает name_v, обновляет только st_vv/date_att_log обеих source tables, требует TLS. Отдельный read-only auditor целостности находится вне Main BuildConfig/APK. Общий account всё ещё читает parol/чужие строки внутри разрешённых таблиц; клиентский access/owner guard не заменяет server authorization.

Оригинальные359,540строк во всех101таблицах проверяются `tools/check_restored_integrity.py`; этот read-only checker требует PyMySQL из tools/requirements-db-check.txt и публикует только hashes/counts. Базовый Docker/Android helper работает без PyMySQL.

Physical device, Windows runtime, реальные Яндекс/звонки и measured15мин/Doze/reboot не проверены. Общий DB account не обеспечивает server row isolation; функциональный test PASS не разрешает driver rollout. [SECURITY_RISKS](docs/direct-rewrite/SECURITY_RISKS.md), [UI comparison](docs/direct-rewrite/UI_PARITY.md). DRIVER_ROLLOUT=BLOCKED_SECURITY. Исторические verify_p2/verify_p5/verify_completion относятся к прежним этапам; не запускайте их как итоговый валидатор восстановленной БД.

Текущий evidence validator: `python3 tools/verify_final_audit.py` (проверяет уже собранные результаты, не запускает тесты). Результаты версии0.2.2 в `docs/evidence/final-audit/`; исторические0.2.0/0.2.1 сохранены.
