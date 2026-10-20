# Результаты итогового тестирования FFL Driver Direct

Дата: 17.10.2026. Версия 0.2.2-dev / code4. Стенд: Linux, JDK21, Gradle9.4.1, SDK36.1/build-tools36.0.0; выделенный software emulator API30 x86_64, 480×800. MariaDB10.6.25 в собственной Docker-копии полного пользовательского дампа; JDBC3.5.10, explicit CA/hostname verification. Production не использовался.

## Итог и границы доказательств

| Категория | Результат | Evidence |
| --- | --- | --- |
| JVM | PASS: 843 теста, 0 failures/errors/skipped | `evidence/final-audit/unit/`, свежий rerun |
| Credentials-free Android | PASS: 29 методов, 0 failures/errors/skipped | `android-ui/summary.json`, XML, screenshots |
| Android → восстановленная БД | PASS: 11 отдельных методов, 0 failures/errors/skipped | `android-restored.json`; raw XML/logs приватны |
| Minified Android | PASS: 2 метода, 0 failures/errors/skipped; UI и SQL post-state | `android-minified.json` |
| Debug, AndroidTest, R8 release build | PASS, exit0 | `build-delivery-command.json`, log; после keep-исправления — `release-room-fixed-command.json` |
| Runtime graph / lint | PASS: API dependencies отсутствуют; lint 0 errors, 13 warnings, 1 hint | `debug-runtime-components.txt`, `release-runtime-components.txt`, `lint-results-debug.xml` |
| Независимый чистый повторный импорт | PASS: 101 таблица, 359 540 исходных строк | `clean-restore-baseline.json`, `clean-restore-meta.json` |
| Финальная сохранность данных и reference | PASS: 101 таблица/359 540 строк; 104 reference файла, оба Git clean | `original-data-integrity.json`, `validation.json` |

Итого **42 отдельных выбранных Android methods**:29+11+2. R8 non-debuggable APK действительно obfuscated, установленный SHA совпал; оба UI smoke метода дополнительно проверили фактическую SQL запись. Raw JUnit приватен, safe attributes и точные команды опубликованы.

Все пути evidence в этом документе относятся к `docs/evidence/final-audit/`. Fixture UI, wire TLS, реальная DB integration и minified smoke — разные категории. 29 UI методов не являются 29 DB E2E. Один Android method содержит несколько assertions/сценариев; число сценариев в матрице не прибавляется к количеству методов. Отсутствие XML, skipped test или один exit0 не считается успехом.

## Матрица пользовательских сценариев

PASS означает проверку указанным методом и на указанном уровне. BLOCKED — нет нужного устройства/доступа; NOT TESTED — отдельный запуск не выполнялся. Найденные ранее FAIL сохранены в следующем разделе.

| Сценарий | Статус | Что именно проверено |
| --- | --- | --- |
| Чистая установка, DIRECT_MYSQL, отсутствие backend IP | PASS | StartupTest на credentials-free APK; обычный MainActivity configured APK |
| Отсутствие DB-конфигурации | PASS | Безопасный ConfigurationMissing, без setup/IP input и API fallback |
| Active driver list, поиск, выбор | PASS | Обычный UI с restored SQL; disabled fictional водитель отсутствует |
| Верный/неверный пароль, повторный вход | PASS | Ordinary UI и repository на настоящей DB |
| Пустой пароль, неизвестный ID, injection input | PASS | RestoredDatabaseFlowTest; PreparedStatement, login не проходит |
| Другой регистр/хвостовой пробел пароля | PASS | BINARY SQL: только точный пароль принимается; host и Android negatives |
| Session/date после force-stop, logout | PASS | Два последовательных отдельных процесса; disk hint, access recheck |
| Параллельные restore, logout/login races, offline hint | PASS | JVM deterministic tests и Android DataStore concurrent restore |
| access=0 / отзыв после входа | PASS | Host operator меняет только reserved driver; штатные reads/delivery блокируются, строка заказа неизменна |
| Нет соединения, TLS rejection, timeout, safe errors | PASS | JVM и Android wire fixture: реальный Connector/J; не полноценная WAN outage-проверка |
| Первый/второй рейс и сданные | PASS | Actual DB SQL/DTO parity, обычный UI; оба источника |
| date/id_v, source IDs с одинаковым numeric ID | PASS | Prepared SQL, реальные записи read-only, контролируемые обе таблицы и source-aware callbacks |
| Номер, col, NULL/blank, готовность/частичная готовность | PASS | JVM mapping/policy и UI fixtures; внутренние пробелы номера сохраняются |
| SQL sort_r и исходный порядок | PASS | Read-only parity на 9 исходных заказах и обоих источниках |
| Ручной порядок, scope driver/date/section, scroll persistence | PASS | WorkspaceUiTest: reorder callback и новое Preferences instance читает сохранённые значения |
| Длительный drag gesture и scroll после реального reboot | NOT TESTED | Наличие long-press drag в исходниках не заменяет этот runtime сценарий |
| Refresh, пустой список, error/disabled | PASS | UI fixtures и repo policy; protected content не остаётся при disabled/error |
| Реальное обновление SQL между запросами | PASS | Delivery refresh и новый second-route ID в worker test |
| Детали обеих таблиц, back | PASS | Обычный UI + SQL owner guard; NavigationTest отдельно |
| Однозначный/несколько/ambiguous/missing адрес | PASS | Resolver golden tests, обе таблицы в restored schema; ambiguity не выбирает случайный contact |
| Client/loader/manager phone, delivery time/comment | PASS | SQL/DTO и UI fixtures, кириллица, long/empty parsing |
| Последние 5 водителей | PASS | Restored fixtures, исторические inactive не отбрасываются; обе source tables |
| Телефоны из комментария | PASS | Golden/JVM и ART parsing; исходный комментарий сохраняется |
| Manual route, empty/invalid URL, copy | PASS | Compose dialogs, allowlisted URI, copy/callback и настоящий Android Intent |
| Реальные Яндекс Карты/Навигатор и возврат | BLOCKED | Приложения/полный внешний сценарий на физическом телефоне не доступны |
| Справочник: карта/важные/активные телефоны | PASS | Original read-only parity, ordinary directory UI, fixture missing-card/raw-phone/copy |
| Настоящий телефонный звонок | NOT TESTED | Проверяется ACTION_DIAL вместо CALL; звонок не совершался |
| «Сдал», confirm, перенос в сданные | PASS | Ordinary MainActivity → repository → TLS SQL; последующий SELECT и cold process |
| Пятисекундное подтверждение списка | PASS | UI fixture: открытие/раннее нажатие не вызывает delivery |
| Повторная/одновременная сдача | PASS | Независимые JDBC connections, обе таблицы: одна запись, один timestamp; повтор без UPDATE |
| Чужой заказ через штатный repository | PASS | Owner predicates/read+write guards, controlled record неизменен |
| SQL error, rollback и серверный BF trigger | PASS | Failure после настоящего UPDATE, BF rollback/commit/repeat; исходные trigger bodies сохранены |
| Обрыв после UPDATE, UNKNOWN | PASS | Connection закрывается после фактического UPDATE; сервер откатывает; остальные поля проверены |
| Обрыв точно во время COMMIT в реальной WAN | NOT TESTED | JVM uncertain-commit policy есть, физическая сеть/серверный crash не воспроизводились |
| Сериализация отзыва доступа и сдачи | PASS | Настоящий shared auth lock до commit; snapshot-дефект отдельно воспроизведён |
| Notification counts/source IDs | PASS | JVM policies и настоящие summary SELECT на restored DB |
| Worker → новая запись → notify → dedup → tap | PASS | Настоящий WorkManager OneTimeWorkRequest и PendingIntent во второй рейс, без fake graph |
| Concurrent seen persistence | PASS | Два экземпляра Preferences: один новый ID определяется новым ровно один раз |
| 15 минут polling, Doze/reboot/OEM background | NOT TESTED | OneTimeWorker не заменяет эти measurements; непрерывные push не обещаются |
| Android13+ notification permission | BLOCKED | API30 emulator; runtime permission на API33+ не проверялось |
| Small screen/light/dark/font1.6 | PASS | 320dp AuditLayoutTest; границы controls, целые слова разделов, contrast ≥4.5; screenshots просмотрены |
| Pixel-perfect старого и нового UI | NOT TESTED | Сверены исходники/контракт, одинаковый старый API runtime dataset не запускался |
| TLS, CA/hostname mismatch, запрет plaintext | PASS | Android wire negatives + настоящий SELECT/TLS на restored MariaDB |
| SQL injection и safe error disclosure | PASS | Prepared SQL/golden/error tests, отрицательный login; секреты не выводятся в error enum |
| Ограничение прав приложения семью таблицами | PASS | Unrelated SELECT запрещён; все11 DB Android methods повторены после harden-grants |
| Серверная изоляция водителей общим account | FAIL — архитектурное ограничение | Account читает parol/чужие строки и может UPDATE двух колонок чужого fictional заказа; probe откатан |
| Physical device/API24/API36/OEM | BLOCKED | Имеется один API30 software emulator |
| Windows execution | BLOCKED | Команды подготовлены, Windows runtime отсутствует |
| Soak/multi-device/WAN latency/heap leak/FPS | NOT TESTED | Не выдаётся за подтверждённое отсутствие утечек/ANR/тормозов |
| Отдельные synthetic-only DirectDatabaseFlowTest/DirectDatabaseUiTest | NOT TESTED в этом аудите | Новый итоговый прогон использует полную restored схему и11 выбранных DB methods; прежние synthetic результаты не прибавляются |
| Network CVE/advisory scan всех dependencies | NOT TESTED | Закреплены версии и просмотрен graph; аудит кода не является полным CVE scan |

## Запуски с ошибками и повторная проверка

| Попытка | Фактический результат | Что сделано |
| --- | --- | --- |
| Reproduction unit | 14 tests / 3 failures | Зафиксированы regression до исправления; XML сохранены |
| Reproduction session | 4 tests / 4 failures | Воспроизведены потеря clear/restore и конкуренция revision |
| Первый layout | 2 tests / 1 failure | Исправлена адаптивность, окончательная проверка учитывает прокрутку |
| Первый 29-case suite | 27 pass / 2 fail | Navigation display race и SystemUI ANR/clipboard; bounded displayed wait и восстановление своего AVD |
| Следующий 29-case suite | 29 pass, визуальные дефекты | После screenshot review исправлены разрывы слов и dark contrast, затем весь suite повторён |
| Финальный public suite | 29 pass / 0 fail | Точный installed APK SHA совпал, fresh XML |
| Первый DB прогон аудита | 4 метода pass, следующий не запускался | Root fixture reset встретил duplicate BF1062; исправлен reset, триггеры сохранены |
| Финальный DB прогон | 11 pass / 0 fail | Включая новые concurrency/access-loss/revoke/session cases на restricted role |
| Debug androidTest против R8 | Crash до теста | Оснастка несовместима с переименованными Kotlin classes; не ошибка штатного debug runtime |
| Независимый release harness до keep fix | Настоящий startup crash | R8 удалил конструктор WorkDatabase_Impl; узкое ProGuard rule |
| Release harness после keep fix, ранние попытки | Неуспешные | Click опережал enabled state входа или завершение перехода в детали. Добавлены ожидания enabled/visible, idle и уникального заголовка деталей; assertions сохранены |
| Финальный независимый R8 smoke | 2 pass / 0 fail / 0 skip | Login/date/details/confirm/delivery, затем отдельный cold process/session/date/delivered/logout; SQL post-state после каждого |
| Release cold restore до исправления оснастки | Вход/сдача pass, cold method fail | Сессия/date восстановились, settings/logout видимы; scrollTo ждал несуществующий scroll container, поскольку всё помещалось. Теперь ждёт visible target OR scrollable. Приватная hierarchy подтвердила причину |

Ошибки не стирались и не включены в число итоговых PASS. Метаданные репродукций лежат рядом с финальными; подробные DB/R8 failure logs приватны из-за возможных PII. SystemUI ANR относится к software AVD; он не доказывает и не опровергает стабильность физических устройств.

## Выполненные команды

Из `/workspace/FFL-direct`, перед Gradle/adb: `source /workspace/.ffl/env.sh`.

```bash
bash gradlew --offline --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest --rerun :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease
python3 tools/run_audit_android.py --serial emulator-5558
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py harden-grants
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py prepare-restored
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py build --include-release
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py test-restored --serial emulator-5558
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/run_minified_audit.py --serial emulator-5558 --debug-keystore /workspace/.ffl/android-user/debug.keystore
bash gradlew --offline --no-daemon --max-workers=2 :app:assembleRelease :app:verifyDirectRuntime
/workspace/.ffl/backend-venv/bin/python tools/check_restored_integrity.py --state /workspace/.ffl/direct-restored --baseline docs/evidence/final-audit/clean-restore-baseline.json --output docs/evidence/final-audit/original-data-integrity.json
python3 tools/verify_final_audit.py
```

Чистый reference создавался отдельно с другим state/портом19068: `dev_database.py up --dump .../112.sql.gz --port 19068`, затем `check_restored_integrity.py --create-baseline ...`. После сравнения удалена только эта owned reference-копия. Baseline создаётся до seed, проверяет отсутствие всего reserved scope и не исключает ни одной строки. Финальная проверка основной копии исключает только заранее свободный test scope, сохраняет все исходные records.

Для новых 29 public checks сначала запустить в отдельном управляемом терминале `python3 tools/p2_tls_fixture.py --directory /private/ffl-wire-fixture`. Дождаться четырёх listening states. Если CA создана заново, скопировать только публичный `ca.pem` в `app/src/androidTest/assets/fixture-ca.pem`, пересобрать public debug/AndroidTest. Не копировать private keys. Для портов19061,19062,19063,19064 выполнить `adb -s emulator-5558 reverse tcp:PORT tcp:PORT`. Затем runner с новым `--output /private/new-ui-evidence`. В текущем запуске использована сохранённая CA, совпадающая с asset; входящих DB данных эта fixture не обрабатывает. Останавливать после тестов только свой процесс fixture. На новом source это воспроизводит тесты, но создаёт другие test APK/SHA, которые надо зафиксировать заново.

Повторный UI collector требует новый output directory; текущие evidence не перезаписывайте очередным запуском. Configured runner очищает только package тестового эмулятора и сбрасывает только reserved fictional rows. R8 APK подписан локальным debug key исключительно для теста, non-debuggable и действительно obfuscated; production signing этим не готов.

## APK и конфиденциальность

Public debug SHA256: `fd644d1e249351db8c8952cb9efb23f50b56383ffd02a76fe931e05ebaef3fdf`.
Private configured debug SHA256: `3d103e035d631bee4d0a5a57a37b401d46574c868ed75a56671d4ab0c855d2d9`.
Private minified SHA256: `267824c3faa2b567f192246a6460ef6def129db78c55c0e6a7dd249b0ca5de50`; installed proof — `android-minified.json`.

Public APK не содержит конфигурации БД; configured APK действительно ходил в БД и остаётся в private state. Test-only integrity auditor не встроен в Main APK. Дамп, raw rows/logs, private keys/configured APK и DB passwords не входят в исходный архив. Финальные артефакты, размеры и SHA — `releases/ARTIFACTS-0.2.2.json` и `releases/SHA256SUMS-0.2.2.txt`.

## Диагностика запуска и памяти

После тестов отдельный `am force-stop`/`am start -W` minified APK: Status ok, TotalTime6121мс, WaitTime6286мс на software TCG. Однократный `dumpsys meminfo` сразу после старта: PSS32828KiB, RSS153596KiB. Это одна ранняя точка, не steady-state после загрузки всех данных и не heap-leak тест. Время Android Activity start не равно полному времени загрузки списка/деталей; отдельные Android screen timings не измерялись. Raw logs приватны; safe metadata — `startup-memory-diagnostic.json`. WAN/physical/soak/FPS остаются NOT TESTED.

После всех тестов остановлены только собственные emulator PID1645 и TLS wire fixture PID4452; процессы завершены/порты fixture закрыты (`cleanup.json`). Основной restored/synthetic state, volumes и private source сохранены. Повторный запуск требует старта выделенного AVD/fixture, живой процесс в snapshot не предполагается.
