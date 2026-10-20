# Прогресс Direct-переработки

## Актуализация19.10.2026 — UI и runtime Direct MySQL0.3.1

Пользователь разрешил отдельный DB-редактор вместо прежнего BuildConfig-only flow. Реализованы утверждённый Compose-дизайн, пять действий панели, ручная локальная сортировка и административные check/save/TLS/Keystore/переключение профиля. Старые ограничения на DB-форму ниже исторические; API backend IP не возвращён.

## Итоговые проверки 0.3.1 — 19.10.2026

| Проверка | Результат |
| --- | --- |
| Debug APK + AndroidTest APK | BUILD SUCCESSFUL |
| JVM | 845 PASS,0 failures/errors/skipped |
| Lint / runtime dependencies |0 errors,45 warnings; DIRECT_MYSQL без HTTP/API transport |
| R8 release build | BUILD SUCCESSFUL; runtime0.3.1 release отдельно НЕ проверен |
| Основная Android/UI-регрессия |Первый прогон37/38;2 целевых повтора PASS, итоговое покрытие38 различных методов на точном APK |
| Малый экран320×640dp, шрифт1.6 и IME |2 повторных проверки PASS |
| Новый редактор подключения |3 UI PASS; публичные снимки только с вымышленными данными |
| Реальные runtime-профили MySQL |7 PASS, две отдельные MariaDB, SELECT1/ошибки/переключение/сохранение/перезапуск/кнопки |
| Восстановленная пользовательская MariaDB |Первый прогон11/12; целевой повтор уведомления PASS при scale1; итог12 различных сценариев PASS |
| Реальное нажатие OS-уведомления |PASS после завершения instrumentation, scale0, обычный APK и реальные SQL-данные |
| Сохранность |101таблица/359540 исходных строк без изменений;104 legacy-файла по SHA неизменны, оба Git clean |

APK SHA-256: `876a19ebeeb0741da62fff9d09cacf63a51528a55f3988e3ffd4362d7042409e`. Все текущие Android evidence относятся к этому APK; APK с DB-реквизитами не публикуются. Android11/API30 software emulator,360×800dp и320×640dp. Системный ANR timeout multiplier5 использован из-за медленного TCG; JDBC-таймауты не менялись. `am start -W` при холодном запуске вернул timeout; реальный экран подтверждён за28.28с, профиль и SELECT1 — отдельным PASS. Быстрый запуск на обычном устройстве этим не доказан.

Команды/выбранные методы/JUnit/SHA: [runtime evidence](../evidence/runtime-connection/summary.json), [покрытие38 методов](../evidence/runtime-connection/public-method-coverage.json) / [целевой повтор](../evidence/runtime-connection/public-regression/summary.json), [малый экран](../evidence/runtime-connection/small-regression/summary.json), [DB-регрессия](../evidence/runtime-connection/restored-regression.json), [сверка результатов](../evidence/runtime-connection/validation.json), [проверка исходных данных](../evidence/runtime-connection/original-data-integrity.json). `verify_runtime_connection.py` сверяет результаты, не запускает тесты.

[Галерея всех экранов](../evidence/ui-design/index.html), [APK](../../releases/FFL-Driver-Direct-0.3.1-dev.apk), [исходники](../../releases/FFL-Driver-Direct-0.3.1-dev-source.zip), [архив снимков](../../releases/FFL-Driver-Direct-0.3.1-screenshots.zip). Визуальные UI fixtures синтетические; DB E2E evidence отдельно, без персональных значений.

Отдельное ограничение стенда: внутри plain instrumentation при системном animator scale0 переход остаётся на loading, хотя ViewModel уже получила данные. Этот отрицательный [XML](../evidence/runtime-connection/notification-instrumented-zero-failure/instrumentation.xml) сохранён. Тот же инструментальный сценарий прошёл при стандартном scale1; отдельный host-тест завершил instrumentation, нажал настоящее OS-уведомление и подтвердил обычный APK/новый заказ при scale0. [Инструментальный повтор](../evidence/runtime-connection/notification-instrumented/summary.json) и [независимый OS-тест](../evidence/runtime-connection/native-notification/summary.json) опубликованы отдельно. Это разные доказательства, а не замена неудачного XML успешным. Исходники попытки изменить переход не вошли в результат: все76 production-файлов вернулись к зафиксированным SHA, APK остался тем же.

Физический телефон, API24/36, аппаратный Keystore, успешный системный PIN, Windows, Doze/перезапуск и периодический15мин polling остаются НЕ ПРОВЕРЕНЫ. Production не использовался. DRIVER_ROLLOUT=BLOCKED_SECURITY: общий DB account не обеспечивает серверную изоляцию строк водителей.


---

## Итоговый engineering audit — 17.10.2026 UTC

**DEVELOPER_TEST_READY — FFL Driver Direct 0.2.2-dev, code4.** Завершён аудит текущего Direct runtime с исправлениями и повторной регрессией. Исторические STOP/gates ниже отменены явным указанием пользователя, их статусы не повышались задним числом. **DRIVER_ROLLOUT=BLOCKED_SECURITY**, физический телефон/Windows/Doze не выданы за PASS.

| Направление | Итог | Доказательство `docs/evidence/final-audit/` |
| --- | --- | --- |
| JVM | PASS 843,0fail/error/skip | fresh rerun, `unit/` |
| Public Android UI/TLS | PASS 29,0fail/error/skip | `android-ui/summary.json`, XML,6screenshots |
| Android → полная restored MariaDB | PASS 11,0fail/error/skip | `android-restored.json`; ordinary MainActivity/BuildConfig, SQL/TLS, actual schema |
| Independent minified runtime | PASS 2,0fail/error/skip | `android-minified.json`; framework-only Java harness, non-debuggable R8/obfuscation, UI→SQL post-state |
| Build / runtime graph / lint | PASS debug/test/unsigned R8;0lint errors | `build-delivery-*`, `release-room-fixed-*`;13warnings/1hint сохранены |
| Новый независимый чистый импорт | PASS 101таблица/359540строк | `clean-restore-baseline.json`, `clean-restore-meta.json`; SQL bytes/SHA совпали |
| Оригинальные данные после E2E | PASS, все101/359540неизменны | `original-data-integrity.json`; reserved fictional scope исключён, отсутствовал в clean source |
| Legacy/reference/старые релизы | PASS | `validation.json`:104SHA совпали, оба Git clean, прежние versioned APK/ZIP SHA совпали |
| Security grants | Частично устранено, rollout FAIL | `restricted-grants.json`: SELECT7таблиц, отдельный read-only auditor. `security-db.json`: общий account всё ещё читает parol/чужие строки/может foreign fictional delivery; probe rollback |
| Physical/Windows/API24/36 | BLOCKED | Нет устройств/runtime; команды подготовлены |
| Measured15мин/Doze/reboot/WAN/soak/реальные Яндекс/звонки | NOT TESTED/BLOCKED | Подробная scenario matrix в FINAL_TEST_RESULTS; OneTimeWorker не объявлен push/continuous background |

### Исправления

Закрыты shared access-row snapshot race при сдаче, конкурентные restore/logout/login revision races, superseded login busy-state, case/trailing-space password acceptance (`BINARY` без изменения parol), N+1контактов (39queries→1, candidate parity), trim номера, definite/UNKNOWN errors, atomic seen persistence и worker final gates. UI: перенос toolbar/actions/разделов, целые слова при font1.6, dark contrast2.84→11.97, accessibility labels. Helper: вместо глобального SELECT только7таблиц; auditor не в Main BuildConfig; repeatable BF fixture reset без изменения triggers.

Независимый release harness обнаружил настоящий crash: R8 удалил WorkDatabase_Impl no-arg constructor. Узкое keep правило исправило startup; остальная минификация включена. После исправления два отдельных процесса проверили login/date/details/confirm/delivery, сохранение в MySQL, cold session/date restore/delivered/logout. Неуспешные первоначальные JVM/UI/R8/harness attempts сохранены и не прибавлены к итоговым PASS.

Исходная схема/индексы/данные и9SQL object bodies сохранены; production не использовался. Все write/fault/concurrent probes только в заранее свободном high-ID fictional scope собственной копии. Новый reference import19068 удалён после baseline, основные restored/synthetic volumes и private input сохранены. Runtime diagnostics: cold Activity TotalTime6121мс/PSS32828KiB на TCG — не physical benchmark/heap-leak доказательство.

### Команды, артефакты и файлы

Из `/workspace/FFL-direct`, Gradle/adb после `source /workspace/.ffl/env.sh`:

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

Финальные команды exit0. Следующий UI run требует новый output directory/retained matching TLS fixture — инструкции FINAL_TEST_RESULTS. Старые verify_completion/verify_restored_artifacts не запускались как новый аудит.

Public APK: `releases/FFL-Driver-Direct-0.2.2-dev.apk`,13974838bytes, SHA256 **fd644d1e249351db8c8952cb9efb23f50b56383ffd02a76fe931e05ebaef3fdf**. Семь пустых DB config fields, DIRECT_MYSQL, без backend IP, безопасный ConfigurationMissing. Source ZIP/ARTIFACTS/SHA256SUMS0.2.2 в releases; дампа/DB credentials/private keys/configured APK нет.

Реально проверенные private configured debug SHA256 **3d103e035d631bee4d0a5a57a37b401d46574c868ed75a56671d4ab0c855d2d9**, private configured minified SHA256 **267824c3faa2b567f192246a6460ef6def129db78c55c0e6a7dd249b0ca5de50**. Installed hashes совпали. Они содержат извлекаемый локальный DB password и остаются private; root и auditor не в Main APK. Public APK нельзя называть настроенным рабочим артефактом.

Полные русские отчёты: [FINAL_ENGINEERING_AUDIT](../FINAL_ENGINEERING_AUDIT.md), [FINAL_TEST_RESULTS](../FINAL_TEST_RESULTS.md), [REMAINING_RISKS](../REMAINING_RISKS.md), [RELEASE_READINESS](../RELEASE_READINESS.md). Список изменений — `docs/evidence/final-audit/files.md`, hashes/проверки — `validation.json`. Старые API проекты не тронуты; новый Direct Git/commit/push не создавался.

После проверок остановлены только собственные emulator и wire fixture; порты fixture закрыты, основные DB volumes/input сохранены (`cleanup.json`).

Startup-инструкции0.2.2 сохранены в environment draft, обновлён только start_skill; install/network/repositories/secrets сохранены. **requires_publish=true**: review/save/publish выполняются отдельно в настройках среды; публикация/fresh-task restore не заявлены. Реальные данные/ключи/настроенные APK сохраняются только в приватной среде.

---

## Восстановление полного дампа и Android E2E — 17.10.2026 12:59 MSK

**FFL_RESTORED_DB_RESULT: POSITIVE — изолированная developer-копия.** Нужный SQL получен из нового kit/5частей, БД восстановлена, приложение реально подключено и проверено. Прежний transfer-limit blocker снят; старые отчёты ниже — история. Это не production/physical-device/security rollout приёмка. **DRIVER_ROLLOUT=BLOCKED_SECURITY**: существование общего DB account позволяет обход клиентских owner/access checks; это отдельно подтверждено на fictional probe и откатано.

| Направление | Результат | Доказательство |
| --- | --- | --- |
| SQL restore | PASS | Все ZIP/payload/gzip/SQL SHA совпали,52,606,346bytes;101таблица; полный независимый повторный импорт |
| SQL integration | PASS | Настоящий Android JDBC/TLS, исходные записи + выделенные fictional records в исходной схеме; both-table commit/repeat/rollback и server trigger rollback |
| JVM tests | PASS |831 passed,0failures/errors/skipped, свежие XML `unit/` |
| Android APK runtime | PASS — API30 emulator | Обычные MainActivity/DirectApplication/BuildConfig, login/date/list/details/delivery/directory/session restore; configured installed SHA сверён |
| Android→restored MySQL E2E | PASS — test copy |6selected methods,6passed/0failed/0skipped; SQL/DTO/summary parity, actual WorkManager→notification→PendingIntent |
| UI regression fixtures | PASS — отдельная категория |8passed/0failed/0skipped на Compose1.10.6: sort/scroll/pull/confirm/copy/channel; это не8DB E2E |
| Исходные данные | PASS |359,540исходных строк/101таблица совпали с чистым повторным импортом; исключены только заранее выделенные test IDs/prefix |
| Сохранение среды | PASS current instance / draft saved | Own volume stop/start/status, исходные данные сохранены; start_skill saved, требует review/save/publish; fresh-task restoration не проверялась |
| Physical device / Windows execution | BLOCKED | Нет физического устройства/Windows runtime, подготовлены Windows команды |
| Measured15min/Doze/reboot / minified runtime / реальные карты и звонки | BLOCKED | Не выполнялись, OneTimeWorker не подменяет периодические/background measurements |
| Production DB / rollout | NOT ACCESSED / BLOCKED_SECURITY | Только собственные loopback Docker copies; серверного row isolation нет |

### Дамп, схема и исправления

Прочитаны README/manifest/PROMPT и scripts kit00; инструкции использованы в пределах пользовательского запроса. Проверенный assemble_dump.py собрал5byte-parts одного gzip; части не импортировались отдельно. SQL SHA256 **`04e481bdfa30e286e0b568e999763fab8fcf0cc91b192f564f74a039ee9e8c55`**, размер52,606,346bytes; gzip9,245,146bytes. Исходные байты неизменны, source/parts/реальные записи не опубликованы. Metadata — `restore-assembly.json`.

Отдельный контейнер/volume с owner labels, официальный MariaDB10.6.25 image pinned digest, loopback19066/TLS CA+hostname verification, require-secure-transport. Bootstrap/read/write role с новым случайным локальным паролем; root не входит в APK. До seed:54drivers/27active,26,802reys/1,882reys_den. Source tables/engines/indexes сохранены, InnoDB также для reys_log/reys_bf. Fullschema —101таблица; independent reimport baseline —359,540rows, только aggregate hashes. Reference clone после baseline удалена; основной restored и отдельный synthetic стенды сохранены.

Найдено и исправлено:

- Error1449 при name_v и UPDATE: исходные DEFINER отсутствовали. Helper локализует4routines+5triggers к локальному владельцу, SHOW CREATE/body/SQLmode/charset/attributes совпали кроме DEFINER (`sql-objects-localized.json`). Source dump не редактируется. При диагностике выявлен и исправлен client delimiter>15bytes; partialattempt сохранён private, процедуры при CREATE не исполнялись.
- Реальная reys содержит NOT NULL p и дополнительные поля; seed задаёт нужные columns, не меняет DDL. Contact nomer_z unique: ambiguous fixtures отличаются ведущим пробелом, совпадают после контрактного TRIM. Первая невалидная seed transaction откатилась; индекс не удалялся.
- Серверные UPDATE triggers аудитят и синхронизируют BF; они не отключены. App SET только st_vv/date_att_log, server side effects отдельно учитываются. Paired trigger case подтвердил: injected failure после реального UPDATE откатывает также созданную BF строку; commit создаёт1, repeat не создаёт дубликат. Обычные обе delivery таблицы/owner/access/idempotency проверены отдельно.
- Android UI crash `SlotWriter.moveSlotGapTo` при dispose Lazy composition на runtime1.10.4. Выбран официальный patch BOM2026.03.01/runtime+ui+foundation1.10.6 в существующей ветке; версия app0.2.1-dev/code3. Тот же ordinary UI flow после upgrade прошёл с закрытием Activity, затем ещё8UI regression checks. Не выдано за успех одно обновление зависимостей.
- PendingIntent handoff Activity/task вынесен в plain instrumentation test с теми же SQL/worker/notification/dedup/tap assertions. Ранее ActivityScenario teardown оставался PAUSED; software SystemUI ANR диагностировался/Wait восстанавливался. Ошибки сохранены, не подавлены. Конечный notification case прошёл без ActivityScenario ownership.
- Configured/default Gradle builds разделены buildDir/project-cache и immutable private APK copies: ранее stale-output cleanup удалял private APK. Повторная обычная сборка сохранила оба private outputs (`db-integration/build-cache-isolation.json`). JUnit initializationError из-за inferred non-void Kotlin test исправлен явным Unit; такой запуск не выдан за выполненные business tests.

### Выполненные проверки и команды

Основной проект `/workspace/FFL-direct`; перед Gradle/adb `source /workspace/.ffl/env.sh`. Секретов в argv/публичных логах нет.

```bash
# Проверенные пользовательские части собраны reviewed kit script в private folder:
python3 /workspace/.ffl/restore-source/assemble_dump.py
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py up --dump /workspace/.ffl/restore-source/112.sql.gz --port 19066
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py localize-routines
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py prepare-restored
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py build
FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored python3 tools/dev_database.py test-restored --serial emulator-5558
bash gradlew --offline --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease
/workspace/.ffl/backend-venv/bin/python tools/check_restored_integrity.py --state /workspace/.ffl/direct-restored --baseline docs/direct-rewrite/evidence/restored-db/all-tables-baseline.json --output docs/direct-rewrite/evidence/restored-db/original-data-integrity.json
python3 tools/verify_restored_artifacts.py
```

Все финальные команды exit0. Up первоначально импортировал source; после выявления triggers добавлен автоматический localize к последующим импортам. Independent fresh up/reimport уже проверил полный автоматический путь. Ранние ошибки и времена сохранены отдельно, финальные acceptance не смешаны с попытками.

`android-safe-summary.json`:6methods, каждый selected отдельным am instrument с CompletionRunListener, ожидаемым classname/method, свежим XML/OK(1 test),0skips. Raw логи/XML private из-за возможных PII. Первый method действительно прочитал9исходных orders в выбранном real driver/date, проверил source IDs/SQL order/summary/details/directory; реальные credentials/имена не сохранялись. Остальные methods используют prechecked high-ID fictional scope, actual schema/SQL. Между login и restore реально force-stop процесса. Настоящий compiled ordinary runtime, не injected fake graph. `fixture-regression.xml` отдельно8Compose fixture methods,0failures/skips, проверены pull/sort/full-source callbacks, local scopes/scroll/seen,5sec confirm, safe disabled/error, directory copy и Android channel/PendingIntent/scheduler/cancel.

`unit/`:831JVM checks,0failures/errors/skipped. `build-final-command.json`/log: build exit0, debug/AndroidTest/unsigned R8release. Lint0errors/13warnings/1hint; vendor warnings оставлены. Graph debug/release и DEX не содержат API/Retrofit/OkHttp/JWT/fake-test runtime, pinned JDBC3.5.10 и Compose1.10.6 подтверждены. Main APK не включает private test configuration/files; публичный BuildConfig имеет7пустых DIRECT_MYSQL_*.

`original-data-integrity.json`:359,540original rows/101tables совпали с independent reference reimport. Исключены только заранее свободный диапазон ID и reserved prefix, отсутствующий в исходном source; original rows ни одной таблицы не изменены. Это проверено повторно после negative grant probes и restart. Role отрицательно проверен UPDATE status/auth, INSERT/DELETE; direct-client foreign fictional UPDATE допустим общим account и откатан — конкретный незакрытый security риск, не production action.

### APK, файлы и воспроизводимость

Приватный настроенный и реально установленный APK: **SHA256 `53b78e9b9ad495b00b3d3980c7b77d91ceec28bdaa52af84b53c9278fc346ab1`** (`configured-apk.json`, signature exit0). Он содержит извлекаемый одноразовый local DB password и остаётся private. Использованы настоящие MainActivity/DirectApplication/BuildConfig→repositories→JDBC/TLS→восстановленная DB. Screenshot `controlled-second-route.png` проверен визуально: только fictional водитель/заказы, личных данных нет.

Публичный debug APK0.2.1-dev/code3: `releases/FFL-Driver-Direct-0.2.1-dev.apk`, **SHA256 `4ac15504c8d85fd0bd304ab723d3c5a1323b4f7d20af32d6cd46905e56f4bf34`**, 13,862,754bytes, без DB credentials; стартует безопасно с ConfigurationMissing. Рабочий локальный APK оператор создаёт helper build/android на своём стенде. Release unsigned R8 SHA256 `c960d00b7da8e3c4bd4d1dd20020d8d5a8a8c88b9a6c3777afe627d1aba7636a`, runtime unrun. Source ZIP и versioned SHA256SUMS в releases не содержат дамп/private state/keys/credentials.

Код/инфраструктура: `tools/dev_database.py`, `tools/fixtures/restored_e2e.sql`, `tools/check_restored_integrity.py`, requirements-db-check/verify_restored_artifacts; app build-dir override/version и BOM; androidTest OwnedTestDatabase, ConfiguredRuntimeTest/ConfiguredNotificationTest, RestoredDatabaseFlowTest и CA override двух existing fixture tests. Полный список и hashes — `artifact-validation.json`/`files.md`. Инструкции — RESTORED_DATABASE.md/LOCAL_DATABASE.md, обновлены README/MASTER_CONTEXT/SECURITY_RISKS. Исторические evidence не переписаны.

Основной restored volume/config/private gzip и отдельный synthetic state сохранены, stop/start/status прошли. Только собственный read-only emulator остановлен после14Android checks; installed config file удалён. Неизвестные/старые AVD и процессы не удалялись. Startup инструкции сохранены как полный start_skill в environment draft; install_script/network/secrets/repositories не менялись. Требуются review/save/publish в закрытой среде для применения draft; публикация и fresh-task restore не выполнялись. Windows инструкции подготовлены, на Windows не исполнялись. Исходная DB не нужна для дальнейшей локальной разработки.

### Ограничения и сохранность

Не проверены physical device/API24/36/minified runtime, реальные Maps/Navigator/dialer, measured15мин/Doze/reboot/force-stop reschedule и нагрузка многих concurrent devices. Реальные source данные проверены representative9-order read parity, не каждый DTO всех28,684orders. Серверное identity/row isolation не реализовано, общий account позволяет обход штатного клиента. Developer PASS не означает безопасность распространяемого Direct APK/production rollout.

Production не использовалась. FFL/FFL-backend HEAD/Git status сохранены чистыми,104reference hashes совпали. Пользовательские файлы/старый API runtime не менялись; Direct snapshot до продолжения сохранён. Reset/clean/git init/commit/push не выполнялись. Никаких реальных секретов/персональных rows в source/public artifacts. Старые prerequisite STOP отменены пользователем и не возобновлялись.

---

## Завершение developer-приложения после отмены gates — 17.10.2026 10:31 MSK

**DIRECT_REWRITE_DEVELOPER_RESULT: POSITIVE** — реализация, сборка и локально доступные проверки завершены. **LEGACY_DB_E2E: BLOCKED**, **REAL_DEVICE_RUNTIME: BLOCKED**, **DRIVER_ROLLOUT: BLOCKED_SECURITY**. Это разные направления: собственный настоящий тестовый MySQL проверен, рабочая БД пользователя недоступна. P0–P10 STOP-отчёты ниже сохранены как история, больше не являются prerequisite. Пользователь явно отменил требование предыдущего POSITIVE и поручил довести программу по усмотрению. Непройденные проверки не переименованы в PASS; полного POSITIVE P10/P11 нет, но код и APK готовы для дальнейшей проверки.

### Что реализовано и где

В независимом `/workspace/FFL-direct`, package `ru.ffl24.direct.dev`, versionCode2 / versionName0.2.0-dev, только DIRECT_MYSQL:

- Заказы: `domain/DriverWorkspace.kt`, `data/mysql/DirectWorkspaceDataSource.kt`, `data/repository/DirectWorkspaceRepository.kt`, `presentation/WorkspaceViewModel.kt`, `ui/DriverOrdersScreen.kt`. Обе таблицы, source-aware Long IDs, три постоянных раздела, точный SQL baseline, компактные строки, drag/↑↓, reset, pull/кнопка refresh, отдельные driver/date/section order и scroll.
- Сдал: `DirectDeliveryDataSource.kt`, repository и list/details UI. FOR UPDATE/id/id_v/access, SET только st_vv/date_att_log, commit/postconditions/rollback, идемпотентный повтор без смены timestamp, неизвестный commit без retry, сериализация writes. Ready guard,5секунд в списке и обычное подтверждение в деталях.
- Справочник: `PhoneDirectoryScreen.kt` + SQL/DTO: карта/владелец, важные телефоны, активные водители, raw copy/ACTION_DIAL. `domain/ContactParsing.kt` добавляет помеченные предположения телефонов из комментария; неоднозначный resolver не угадывает адрес.
- Уведомления: `notifications/SecondRouteNotifications.kt`, WorkManager2.10.1/manifest merger receivers, opt-in/permission, период>=15мин/network, baseline/dedup driver/date, private generic notification, immutable driver-specific PendingIntent, guarded tap во второй рейс, отмена при logout/отключении. Это не always-on service; расписание зависит от Android.
- Навигация/настройки: `DirectRuntime.kt`, `AppGraph.kt`, `MainActivity.kt`, `ui/DirectApp.kt`, `WorkspaceSettings.kt`, `local/WorkspacePreferences.kt`: вход→заказы→детали/справочник/настройки→выход, local theme/date/session; дата сегодня по умолчанию с явным выбором. Backend IP/DB credential form/API transport/JWT отсутствуют. Отдельный VM notification-entry предотвращает отмену чтения домашним экраном при переходе.
- Тесты: шесть новых JVM-файлов, Android WorkspaceUi/WorkspaceNavigation/DirectDatabaseUi/DirectDatabaseFlow/CompletionRunListener, обновлены только относящиеся к новому поведению Startup/OrderDetails fixtures. Fixture/fake runtime существуют только в androidTest.

Полный список изменённых файлов и source SHA — `evidence/completion/static-validation.json`, человекочитаемый список — `evidence/completion/files.md`. Решения/ограничения — COMPLETION.md, UI_PARITY.md; стенд — TEST_ENVIRONMENT.md. Прежние P0–P5 evidence не перезаписаны.

### Команды, сборка и артефакт

Рабочий каталог `/workspace/FFL-direct`; toolchain `source /workspace/.ffl/env.sh` (JDK21/SDK36.1/build-tools36.0.0). Выполнено:

```bash
bash gradlew --offline --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease
bash gradlew --offline --no-daemon --max-workers=2 :app:assembleDebugAndroidTest
python3 tools/completion_collect_android.py
python3 tools/verify_completion.py
python3 tools/verify_runtime_evidence.py
```

Обе финальные Gradle команды exit0; первая — `build-current-command.json`/`build-current.log`, вторая после улучшения изоляции/усиления Android tests — `android-test-build-current-command.json`/log. **831 JVM tests:831 passed,0 failures/errors/skipped**, свежие XML в `evidence/completion/unit`. Lint:0 errors,13 warnings,1 hint; vendor TrustAll warnings оставлены, не подавлены. Debug/release runtime graph содержит закреплённый mariadb-java-client3.5.10, не содержит Retrofit/OkHttp/Ktor/JWT/MySQL Connector/J/других project runtimes. DEX и generated BuildConfig отдельно проверены скриптом; main APK без test config/fakes.

Выдаваемый подписанный debug APK: `releases/FFL-Driver-Direct-0.2.0-dev.apk` (13,988,983bytes), копия `app/build/outputs/apk/debug/app-debug.apk`, **SHA256 `413bbdc09a289e5621704a46b76b23406abbcdd49eb45d2733f2b5fbf07ee10c`**. `apksigner verify --verbose --print-certs` exit0/v2/debug certificate; `aapt dump badging/xmltree` exit0. Установленный base.apk имеет **тот же hash** (`artifact-verification.json`, apk-*). Release R8 unsigned собран, SHA256 `ff8385e4776638dffc797bcabdcf5137ac56f5ab911c0b0537d250a3fb3350ef`; его runtime не проверялся и для установки он не выдаётся. В `releases` также ZIP исходников и SHA256SUMS.

### Emulator — POSITIVE в проверенном объёме

Android11/API30/x86_64, read-only software AVD, serial emulator-5558; точные свойства `android-environment.json`. Установка основного и test APK exit0. `adb ... shell am instrument -w -r -e listener ru.ffl24.direct.CompletionRunListener -e class <7 классов> ru.ffl24.direct.dev.test/androidx.test.runner.AndroidJUnitRunner`: полная команда/start/end/hash в `instrumentation-final-command.json`. **28 tests:28 passed,0 failures/errors/skipped**, свежий XML `instrumentation-final/instrumentation.xml` и консоль `OK (28 tests)`. Process exit0 сам по себе не использован как PASS.

Покрыты cold start без backend IP/DB inputs, безопасные missing/refused config и retry, theme/recreation, обе таблицы/details/resolver/ART parser, три раздела,5секунд confirmation, reorder/source ID, pull-to-refresh, disabled/error hiding, preferences scopes/scroll/seen, copy, реальные Android channel/PendingIntent/schedule/cancel, guarded notification-navigation, login/wrong-password/details/directory/settings/logout. Семь существующих Android connection tests проверяют фактический JDBC TLS positive/wrong-CA/wrong-host/timeout/IO/StrictMode. Wire fixture закрывается до auth и не считается БД.

После удаления test-only config выполнен отдельный запуск основного APK. `am start -W` exit0 вернул Status:timeout на медленном software emulator; это не PASS запуска. Последующий `uiautomator dump` exit0 действительно показал «Подключение не настроено», ни одного EditText, без ANR overlay; `standalone-render-check.json`, `standalone-window.xml` и `standalone-screen.png` подтверждают отображение.

PNG actual Compose roots: `orders-first.png`, `orders-second.png`, `orders-delivered.png`, `directory.png`, `mysql-orders.png`, `mysql-details.png` в `instrumentation-final/`. Содержат только вымышленные данные; это не pixel comparison с запущенным старым API приложением. UI_PARITY.md сравнивает каждый экран по reference source и новым runtime checks.

Попытки сохранены честно: первый прогон27/4failed (`instrumentation-initial` и восстановленный XML), второй28/1failed (`before-isolation-instrumentation-final`). Исправлены collision телефона raw comment/candidate в fixture, неверный charset synthetic SQL seed и изоляция persisted preferences; для copy устранён system UI ANR overlay выбором Wait, для cancellation — ожидание асинхронного Android результата. Тесты на candidate leak/clipboard/cancel не удалены. Изначальный запуск AVD имел stale-lock и network-stack/SystemUI failures; startup/logs/Wait evidence сохранены, одни boot_completed/am start exit не объявлены успехом. Последний прогон выше выполнен после исправлений на выдаваемом APK.

### Настоящая одноразовая MySQL — POSITIVE в проверенном объёме

Собственный Docker MySQL8.4.11/InnoDB, fictional schema/data, loopback+adb reverse, require_secure_transport=ON, явная CA/hostname, ограниченный fixture account. Никаких пользовательских/production данных. Официальный image digest/version/TLS и начальные трудности permissions/socket lock сохранены в evidence. Публичный seed `tools/fixtures/completion_mysql.sql` — только disposable fixture, не migration. Root применялся исключительно для создания этого стенда; в app его нет.

`DirectDatabaseFlowTest`: настоящий Android AppGraph→repositories→JDBC/TLS→MySQL: SELECT1, active list, wrong password, access0, login, три раздела/обе таблицы, details/Unicode time/resolver, foreign details и delivery denied, directory, counters/source IDs. После реального executeUpdate клиент получает injected SQLException; отдельный SELECT подтверждает rollback. Далее delivery commit/repeat для обеих таблиц, одинаковый timestamp повторно, status/col сохранены, итоговые counters и logout gate.

`DirectDatabaseUiTest`: настоящий экран→выбор водителя→вход→production repository→настоящий SQL→список→детали→«Сдал»/confirm→«Заказ сдан» и проверка строки серверным SELECT. `mysql-ui-write.json` подтверждает read/write сценарий. Перед следующим тестом сброшены только два поля своей fixture строки. Конфигурация передана через stdin в private target files, graph создан с ней только внутри androidTest. **Main BuildConfig содержит семь пустых DIRECT_MYSQL_*; не выдаём test injection за настроенную рабочую сборку.**

`mysql-before.json`/`mysql-after.json` и `mysql-invariants.json`: итоговые различия только st_vv/date_att_log у id1 в каждой таблице; остальные fields/rows, включая чужой id_v, неизменны. Это реальная запись/rollback в собственный тестовый сервер, не JDBC proxy; безопасность обхода клиентских guards внешним SQL-клиентом этим не доказана. `runtime-validation.json` проверяет итоговые XML/hash/invariants; credentials в evidence не включены. Task-owned container/volume/private config и процессы стенда убираются после сбора, запись cleanup — `cleanup.json`.

### Физическое устройство / рабочая legacy БД / ограничения — BLOCKED

Физического телефона нет (adb видит только собственный эмулятор), установка/runtime там unrun. Рабочая БД пользователя не настроена: её schema/version/collation/timezone/name_v/indexes/auth plugin/CA/grants, реальные объёмы/нагрузка и полное E2E по реальным заказам неизвестны. Production SELECT/UPDATE/DDL/grants не выполнялись. Тестовый MySQL не закрывает это направление. Текущий выдаваемый APK без credentials открывает «Подключение не настроено»; рабочий APK требует безопасного BuildConfig injection по MYSQL_CONNECTION_LAYER.md, без старого setup flow.

Не проверены API24/36, minified release runtime, реальные Яндекс-apps/номеронабиратель, measured15мин/Doze/reboot/force-stop, полная rotation/process-death/font-scale/accessibility matrix. Polling сохраняет seen до notify: при crash между ними уведомление может потеряться, повтор исключается; два summary SELECT не обещают один DB snapshot. Scroll debounce может потерять последние250ms. Shared DB credential в заполненном APK извлекается; клиентские owner/access checks и column grants не дают row-level security, это остаётся P11. Ограничения не останавливают завершённую developer-реализацию, но не позволяют назвать её проверенной рабочей/безопасной rollout-сборкой.

### Сохранность

FFL и FFL-backend Git status чистые, HEAD не изменены; все104 reference file SHA совпали. Снимок исходных326 файлов Direct до работы — `before.json`; полный текущий diff по hash записан, старые evidence сохранены. Старый API runtime/незакоммиченные пользовательские изменения не менялись, git reset/clean/commit/push/init не выполнялись. Случайные fixture secrets проверены по project files/APK без печати значений (`credential-scan.json`); в выдаваемых файлах их нет. Серверная изоляция и rollout не подменены скрытым API fallback.

---

## Запрос P10 — STOP на обязательном gate P9, 17.10.2026 06:32 MSK

**DIRECT_REWRITE_P10_RESULT: BLOCKED**

Полностью прочитаны семь обязательных документов: MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, прежний PROGRESS.md (585 строк), BACKEND_CONTRACT_REPORT.md (docs/direct-rewrite/, до EOF, включая последнюю строку без перевода строки). **P9 имеет BLOCKED, не POSITIVE**: его отчёт ниже и таблица этапов фиксируют остановку до реализации/сравнения экранов, 0 tests и отсутствие сборки/runtime P9. Каталога evidence/p9 нет; положительная приёмка не подтверждена. По прямому условию текущего запроса «Если нет — STOP» **P10 остановлен до установки APK и E2E**. Предыдущие результаты и отложенные реальные проверки не переписаны в PASS.

**Файлы/сохранность:** изменён только `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md` — этот отчёт и строка P10 в таблице. Код, тесты, Gradle, пользовательские файлы и прежние evidence не менялись. SHA256 снимок Direct-файлов вне build/.gradle/.kotlin/.git до/после отличается только этим документом; оба существующих APK неизменны. Все104 reference hashes совпали, Git status FFL/FFL-backend чистый; старый API runtime не тронут. Reset/clean/commit/push не выполнялись. **P11 не начат.**

**Команды:** cwd `/workspace/FFL-direct`, Git-проверки также из `/workspace`: `pwd`, `rg --files` для документов/AGENTS; `cat` MASTER_CONTEXT/PLAN/DB/UI/SECURITY; `sed -n` PROGRESS(1–100,101–200,201–300,301–400,401–500,501–585), BACKEND report(1–1000,1001–2000,2001–3000,3001–4000 до EOF) и шаблон DIRECT_QA_CHECKLIST; `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend. Inline `python3`: подсчёт строк/чтение P9 gate/evidence presence, SHA256 snapshot/reference/APK, точечная запись PROGRESS и проверка сохранности. Команды завершились exit=0; это документальная проверка prerequisites/сохранности, не software tests. Новых test/build/runtime логов P10 нет. Секреты/config values/PII не выводились.

**Тесты P10:** 0 executed; passed/failed/skipped/disabled неприменимы — suite не запускался, новых cases/XML нет. Все непройденные E2E направления ниже **BLOCKED**, не PASS. Не проверены fresh login/restore/wrong-password/access denied, обе таблицы/дата/разделы/порядок, details/resolver/контакты/история, доставка/idempotency, directory, polling/tap, offline/TLS/config failures и rollback. Исторические unit/emulator PASS других этапов не доказывают E2E.

**Сборка/артефакт:** Gradle/compile/assemble/signature/DEX/dependency checks не запускались, build exit отсутствует; нового APK P10 нет. На диске сохранён прежний P5 debug `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf`; это вычисленный hash локального файла, не installed hash и не E2E proof. Release `app/build/outputs/apk/release/app-release-unsigned.apk`, SHA256 `7b033750cf6462f7cca0c91f202724fa5f14730c852eb8605bb9613a6f05744a` также неизменён. Режим DIRECT_MYSQL и реальный путь UI→repository→JDBC→MySQL в этом запросе runtime-доказательствами не подтверждены.

### Emulator — BLOCKED

Установка/запуск APK и instrumentation не выполнялись: STOP на P9. Android API/ABI и installed APK SHA для P10 отсутствуют. Полный пользовательский сценарий, network inspection/отсутствие FastAPI traffic, независимость от backend и rollback не проверялись. Прежний P5 API30 subset не выдан за E2E P10.

### Real device — BLOCKED

Устройство не опрашивалось, APK не устанавливался; модель/API/installed SHA и fresh runtime evidence P10 отсутствуют. Все сценарии на физическом устройстве unrun. Доступность устройства после неуспешного prerequisite не переоценивалась.

### Live DB — BLOCKED

БД не использовалась: ни SELECT1/login/business reads/summary, ни transaction/UPDATE/DDL. DB endpoint/schema/grants/test identity не проверялись; UI→MySQL correlation отсутствует. Разрешение конкретной live-записи не запрашивалось, безопасный test order не назначался; production write не выполнялся. Fixture/proxy не использовались как подмена живой БД.

| Направление P10 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Только gate/сохранность; новый runtime/dependency scan не выполнен |
| UNIT_TEST | BLOCKED | 0 executed |
| BUILD | BLOCKED | Не выполнялась, APK P10 нет |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся, отдельный отчёт выше |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся, отдельный отчёт выше |
| LIVE_DB_READ | BLOCKED | Не выполнялось, отдельный отчёт выше |
| LIVE_DB_WRITE | BLOCKED | Не выполнялось, approved safe write отсутствует по сохранённым документам |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохранён |

**Блокер/неизвестные:** нет требуемого P9 POSITIVE с доказательствами. По сохранённым документам не завершены P4/P6/P7/P8 функции и P9 visual acceptance; локальная сборка P5 не делает клиент готовым к полному сценарию. Для будущего P10 остаются неподтверждёнными стенд/CA/account/schema/grants, test driver/date/orders и разрешённый safe write, устройства/handlers, точный установленный кандидат, реальный Direct traffic и rollback без смешивания sessions. Эти условия сейчас не переоценивались. При заданном gate нужен подтверждённый P9; предыдущие этапы этим отчётом не запускаются повторно, перехода к P11 нет.

---

## Запрос P9 — STOP на обязательном gate P8, 17.10.2026 06:30 MSK

**DIRECT_REWRITE_P9_RESULT: BLOCKED**

Полностью прочитаны MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, прежний PROGRESS.md (554 строки) и BACKEND_CONTRACT_REPORT.md (docs/direct-rewrite/, до EOF, включая последнюю строку без перевода строки). **P8 имеет BLOCKED, не POSITIVE**: отчёт P8 ниже и таблица этапов фиксируют отсутствие реализации notification-summary/polling/receivers/tap, 0 tests, отсутствие сборки/runtime P8. Каталога evidence/p8 нет; файлов с notification/polling/worker/receiver в имени в app/src не найдено (поиск имён не доказывает runtime-поведение). Доказательств успешной приёмки P8 нет. По прямому условию текущего запроса «Если нет — STOP» **P9 остановлен до реализации и сравнения экранов**. Прежние результаты и разрешение отложить реальные проверки сохранены; пропуски не стали PASS.

**Файлы:** изменён только `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md` — эта запись и строка P9. UI/components/navigation/theme/order/scroll/dialog/error implementation не менялись. Screen-by-screen comparison, visual parity и отсутствие API dependencies заново не подтверждались. Исторические P1/P5 проверки не выданы за приёмку P9. Снимок SHA256 всех Direct-файлов вне build/.gradle/.kotlin/.git до/после отличается только этим документом; debug/release APK сохранены. Все104 reference hashes совпали, Git status FFL и FFL-backend чистый. Старый API runtime, пользовательские файлы и прежние evidence не изменены; reset/clean/commit/push не выполнялись. **P10 не начат.**

**Команды:** cwd `/workspace/FFL-direct`, Git-проверки также из `/workspace`: `pwd`, `rg --files` для документов/AGENTS; `cat` MASTER_CONTEXT/PLAN/DB/UI/SECURITY; `sed -n` PROGRESS(1–100,101–200,201–300,301–400,401–500,501–554), BACKEND report(1–1000,1001–2000,2001–3000,3001–4000 до EOF) и шаблон DIRECT_QA_CHECKLIST. Inline `python3`: чтение/подсчёт строк, P8 result/evidence/file-name review, SHA256 snapshot/reference/APK, точечная запись PROGRESS и проверка сохранности. `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend. Команды завершились exit=0; это чтение документов и проверки сохранности, не software tests. Новых test/build логов P9 нет. Секреты и значения конфигурации не выводились.

**Тесты P9:** 0 executed; passed/failed/skipped/disabled неприменимы — suite не запускался, новых methods/XML нет. Не проверены screen matrix auth/orders/details/settings/directory/disabled, компактные строки/сортировка/drag/scroll, подтверждения, loading/empty/error/retry, back/deep links/notification tap, theme/font scale/rotation/process death/accessibility. Отсутствующим методам counts не присваиваются.

**Сборка:** Gradle/compile/assemble/dependency scan не запускались, exit code отсутствует, нового APK P9 нет. Прежний debug APK `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf` — артефакт P5, не доказательство P9. Release APK также неизменён.

**Runtime checks:** emulator/device не запускались, APK не устанавливался; сравнительные screenshots/manual paths не выполнялись, нового UI runtime evidence нет. **БД не использовалась:** SELECT/transaction/write отсутствуют; старый API runtime не запускался для сравнения.

| Направление P9 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены prerequisite/сохранность; новая UI/API dependency проверка не выполнена |
| UNIT_TEST | BLOCKED | 0 executed |
| BUILD | BLOCKED | Не выполнялась, APK P9 нет |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся |
| LIVE_DB_READ | BLOCKED | Не выполнялось |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохранён |

**Блокер:** нет обязательного P8 POSITIVE с доказательствами; уведомления не реализованы. По сохранённым контрактам для будущего P9 также открыты C01/C02/C05/C06/C07: новые три раздела/компактные строки/drag/scroll, справочник/уведомления/контактный UX, ручной маршрут и подтверждения. Текущая reference-ветка по аудиту не содержит компактных строк; они не объявлены «сохранёнными» без реализации/согласования. Сквозной list→details/delivery/directory/notification flow и визуальное соответствие на сопоставимых данных/устройствах не доказаны; наличие API transitives текущим запросом не переоценивалось. Эти ограничения не исправлялись молча. При заданном условии нужен подтверждённый P8; предыдущие этапы этим отчётом не запускаются повторно, перехода к P10 нет.

---

## Запрос P8 — STOP на обязательном gate P7, 17.10.2026 06:28 MSK

**DIRECT_REWRITE_P8_RESULT: BLOCKED**

Полностью прочитаны все семь обязательных документов: MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, прежний PROGRESS.md (523 строки), BACKEND_CONTRACT_REPORT.md (3 872 строки, находится в docs/direct-rewrite/). **P7 имеет BLOCKED, не POSITIVE:** сохранённый отчёт P7 ниже фиксирует остановку до реализации, 0 tests, отсутствие сборки/runtime; строка P7 в таблице это подтверждает. Каталога evidence/p7 и файлов с directory в имени в app/src нет; такой поиск не заменяет behavioral tests. Доказательств положительной приёмки P7 нет. Согласно прямому условию текущего запроса «Если нет — STOP» **реализация P8 не начата**. Историческое разрешение отложить реальные проверки сохранено; пропущенные проверки не превращены в PASS.

**Файлы:** изменён только `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md` — эта запись и строка P8 в таблице этапов. Notification-summary SQL/DTO, polling второго рейса, workers/scheduler/receivers, каналы/permissions/PendingIntent и tap navigation не добавлены. Код, тесты, Gradle, пользовательские файлы и прежние evidence сохранены. Снимок SHA256 Direct-файлов вне build/.gradle/.kotlin/.git до/после отличается только этим документом; оба существующих APK неизменны. Все104 reference hashes совпали, Git status FFL и FFL-backend чистый. Старый API runtime не тронут; reset/clean/commit/push не выполнялись. **P9 не начат.**

**Команды:** cwd `/workspace/FFL-direct` (Git-проверки также из `/workspace`): `pwd`, `rg --files` для документов/AGENTS, `wc -l`; `cat` MASTER_CONTEXT/PLAN/DB/UI/SECURITY; `sed -n` PROGRESS(1–100,101–200,201–300,301–400,401–523), BACKEND report(1–1000,1001–2000,2001–3000,3001–3872) и шаблон DIRECT_QA_CHECKLIST. Выполнены `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend; inline `python3` для чтения/проверки P7 результата и наличия evidence/имён файлов, SHA256 snapshot/reference/APK, точечной записи этого документа и проверки сохранности. Команды завершились exit=0. Это документальные проверки условия и сохранности, не software tests; нового test/build лога P8 нет. Секреты и значения конфигурации не выводились.

**Тесты P8:** 0 executed; passed/failed/skipped/disabled неприменимы — suite не запускался, новых test methods/XML нет. Не проверены counters/active second-route IDs/порядок, driver/date isolation, новые/исчезнувшие IDs и dedup/reset, logout/disabled cancellation, PendingIntent identity/immutability и tap routing. Исторические PASS других этапов за P8 не выдаются.

**Сборка:** Gradle/compile/assemble не запускались, exit code отсутствует; нового APK P8 нет. Существующий debug APK `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf` — прежний P5 артефакт, не доказательство P8. Release APK также сохранён.

**Runtime checks:** emulator/device не запускались, APK не устанавливался; permission/channel, cold/warm tap, background/Doze/reboot, фактические polling interval/delay не проверялись. **БД не использовалась:** ни SELECT summary, ни write/transaction. Расписание/службы/receivers не регистрировались.

| Направление P8 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены prerequisite/сохранность; P8 не реализован |
| UNIT_TEST | BLOCKED | 0 executed |
| BUILD | BLOCKED | Не выполнялась, APK P8 нет |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся |
| LIVE_DB_READ | BLOCKED | Не выполнялось |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохранён |

**Блокер:** нет требуемого P7 POSITIVE с доказательствами; справочник не реализован. Дополнительные неизвестные будущего P8: C02 (interval/first observation/dedup/reset/tap), рабочая дата/timezone и переходы driver/date, согласованность двух summary SELECT при изменениях данных (C10), реальная schema/read permissions, chosen scheduler и его Android background ограничения, permission/Doze/reboot/device behavior. Эти решения не приняты молча, доступность стенда/устройства здесь не переоценивалась. При заданном условии нужен подтверждённый P7; предыдущие этапы этим отчётом не запускаются повторно, перехода к P9 нет.

---

## Запрос P7 — STOP на обязательном gate P6, 16.10.2026 22:25 MSK

**DIRECT_REWRITE_P7_RESULT: BLOCKED**

Полностью прочитаны MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, прежний PROGRESS.md (484 строки) и BACKEND_CONTRACT_REPORT.md (3 872 строки, находится в docs/direct-rewrite/). Входное условие не выполнено: **P6 имеет BLOCKED, не POSITIVE**. Сохранённый отчёт P6 ниже прямо фиксирует отсутствие реализации transaction/idempotency/rollback/UI, 0 tests и отсутствие сборки/runtime. Строка P6 в таблице также BLOCKED. Файлов с именами delivery/delivered/PhoneDirectory в app/src не найдено; этот поиск сам по себе не заменяет проверку поведения. Положительных доказательств приёмки P6 нет. P5 phase_result/full_p5_result в сохранённых JSON также BLOCKED; это исторические metadata, не новый прогон.

В текущем запросе прямо указано «Если нет — STOP», поэтому **P7 остановлен до реализации**. Ранее отложенные реальные проверки остаются отложенными и не объявлены PASS; результаты предыдущих этапов не повышались. Это остановка по условию пользователя, не обнаруженный провал сборки P7.

### Файлы и сохранность

Изменён только `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md`: эта запись и строка P7 в таблице. Карта оплаты, важные телефоны, активные водители и copy/dial UI P7 не добавлены. Исходники, Gradle, тесты, пользовательские файлы и прежние evidence сохранены. SHA256 снимок всех Direct-файлов вне build/.gradle/.kotlin/.git до/после отличается только этим документом; debug/release APK неизменны. Все104 reference hashes совпали, Git status FFL и FFL-backend чистый. Старый API runtime не тронут; reset/clean/commit/push не выполнялись. **P8 не начат.**

### Команды и результаты

Cwd `/workspace` и `/workspace/FFL-direct`. Выполнены `pwd`, `rg --files` для документов/AGENTS, `wc -l`, `cat` MASTER_CONTEXT/PLAN/DB/UI/SECURITY; `sed -n` PROGRESS(1–100,101–200,201–300,301–400,401–484) и BACKEND report(1–1000,1001–2000,2001–3000,3001–3872). Прочитан шаблон DIRECT_QA_CHECKLIST. Выполнены `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend; inline `python3` для чтения сохранённых JSON, поиска имён файлов, SHA256/сохранности и точечного обновления PROGRESS. Команды чтения/сверки завершились exit=0; отдельного build/test лога P7 нет. Это документальная проверка условия и сохранности, не software tests. Setup skill прочитан; установка, сетевые проверки и изменение конфигурации среды для этой остановки не требуются. Секреты/config values/PII не выводились.

**Тесты P7:** 0 executed; passed/failed/skipped/disabled неприменимы — suite не запускался, новых cases/XML нет. Не проверены SQL/DTO parity, missing/null card, blank phones, inactive drivers, mobile→whatsapp fallback, raw copy/dial, access/session и UI/navigation. Число несуществующих test methods не выдумывается.

**Сборка P7:** Gradle/compile/assemble не запускались, exit code отсутствует, нового APK нет. Сохранён предыдущий debug APK P5 `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf`; это не build proof P7. Release APK также сохранён.

**Runtime checks P7:** emulator/device не запускались, APK не устанавливался, clipboard/номеронабиратель/экран справочника не проверялись. **БД не использовалась:** SELECT/transaction/write отсутствуют; реальные звонки и платежи не выполнялись.

| Направление P7 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены prerequisite и сохранность; реализация P7 не начата |
| UNIT_TEST | BLOCKED | 0 executed |
| BUILD | BLOCKED | Не выполнялась, нового APK нет |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся |
| LIVE_DB_READ | BLOCKED | Не выполнялось |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохранён |

### Неизвестные и блокеры

Основной блокер — **P6 не имеет POSITIVE с доказательствами** и не реализован. Для будущей приёмки P7 остаются неподтверждёнными read permissions/schema/data nastroiki/phone/__hr_typepos, C02 по входу и UI/actions справочника, access/session и настоящий clipboard/dial runtime. Доступность внешнего стенда в этом запросе не переоценивалась. Пропущенные проверки не стали успешными. При заданном условии следующий шаг — завершить P6 и подтвердить результат; предыдущие этапы этим отчётом не запускаются повторно. Перехода к P8 нет.

---

## Запрос P6 — STOP на обязательном gate P5, 16.10.2026 19:03 MSK

**DIRECT_REWRITE_P6_RESULT: BLOCKED**

Полностью прочитаны MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, прежний PROGRESS.md (439 строк) и BACKEND_CONTRACT_REPORT.md (3 872 строки; находится в docs/direct-rewrite/). Входное условие текущего запроса не выполнено: **P5 имеет BLOCKED, а не POSITIVE**. Сохранённые [static-validation.json](evidence/p5/static-validation.json) (phase_result=BLOCKED) и [runtime-validation.json](evidence/p5/runtime-validation.json) (full_p5_result=BLOCKED, live_db_used=false, physical_device=false) это подтверждают независимо от текста отчёта.

Предыдущее разрешение отложить реальные проверки сохранено: они не запускаются и не объявляются PASS. В текущем запросе пользователь снова прямо требует «Проверь, что P5 действительно имеет POSITIVE … Если нет — STOP». Поэтому остановка **до реализации P6**; P5 результат не переписан, новая трактовка «локальный PASS = полный POSITIVE» не вводилась. Это остановка по явному пользовательскому условию, не отказ из-за обнаруженной ошибки локальной сборки.

### Файлы и сохранность

Изменён **только** `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md`: эта запись и строка P6 в таблице этапов. Кнопка «Сдал», delivery transaction/idempotency/access/ownership write path и тесты P6 не добавлены. Исходники, Gradle, пользовательские незакоммиченные файлы, контракты и P0–P5 evidence сохранены. Сравнение SHA256 Direct-файлов до/после выявило только этот документ; оба существующих APK неизменны. Git status обоих старых проектов чистый;104 reference hashes совпали. Старый API runtime не тронут; reset/clean/commit/push не выполнялись. **P7 не начат.**

### Команды и evidence review

Cwd `/workspace` и `/workspace/FFL-direct`. Реально выполнены `pwd`, `rg --files` для обязательных документов/AGENTS, `wc -l`, `cat` документов и `sed -n` для PLAN(1–95/96–177), PROGRESS(1–110, повтор1–22 после усечения вывода,111–220,221–330,331–439), BACKEND report(1–1000,1001–2000,2001–3000,3001–3872). Дополнительно прочитан шаблон отчёта DIRECT_QA_CHECKLIST.md. Выполнены `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend; inline `python3` для чтения JSON/XML P5, сверки SHA256, точечного обновления PROGRESS и проверки сохранности. Эти shell-команды завершились с exit=0. Это чтение и проверка сохранённых доказательств, **не новый прогон tests/build/runtime**; отдельных P6 software-логов/XML нет. Секреты/config values/PII не читались и не выводились.

В сохранённых P5 XML подтверждены811 unit и8 API30 Android tests,0 failures/errors/skipped; build-final-command.json и connected-final-command.json содержат exit0. Они относятся к **P5**, в этом запросе не повторялись, не покрывают delivery/commit/rollback и не дают P5 полный POSITIVE. P5 acceptance остаётся незавершённым также из-за отсутствующей P4 integration/real reads/device/handlers и открытых продуктовых пунктов.

### Тесты, сборка, runtime и БД P6

**P6 tests:**0 executed; passed/failed/skipped/disabled неприменимы — suite не запускался и новых test cases/XML нет. Unrun: commit/rollback/resource closure, zero affected rows, id_v/access/disabled/not-found, already_delivered без повторного UPDATE, concurrent double tap, failure до commit, postcondition failure и unknown commit result. Отсутствующим test methods не присвоены вымышленные counts.

**Сборка:** Gradle/compile/assemble P6 не запускались, exit code отсутствует; нового APK P6 нет. Существующий debug APK P5 имеет SHA256 `60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf` и сохранён; это не P6 build proof. Release APK также не изменён.

**Runtime checks:** emulator/device не запускались, APK не устанавливался, кнопку/транзакцию/повторное нажатие не проверяли. **БД не использовалась:** ни SELECT, ни transaction, ни test/production UPDATE не выполнялись. Одобрение конкретной live-записи не запрашивалось и не подразумевалось общим запросом реализации; сохранён прямой запрет production write без разрешения.

| Направление P6 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены только prerequisite/evidence/сохранность; P6 код не начат |
| UNIT_TEST | BLOCKED |0 executed, transaction/idempotency/rollback unrun |
| BUILD | BLOCKED | Не выполнялась, APK P6 нет |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся |
| LIVE_DB_READ | BLOCKED | Не выполнялось |
| LIVE_DB_WRITE | BLOCKED | Не выполнялось; approved safe order отсутствует по сохранённым документам |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохранён |

### Неизвестные и блокеры

Основной блокер — **нет P5 POSITIVE с доказательствами**, требуемого текущим запросом. Отдельно для будущей приёмки P6 остаются не подтверждённые engine/transaction/FOR UPDATE/grants/access-revocation semantics, безопасный test order и явное разрешение его live write, а также решения C06/C07 по ready guard и подтверждению доставки. Эти вопросы и доступность стенда здесь не переоценивались. Идемпотентность, rollback, изменение только st_vv/date_att_log и поведение при неизвестном commit ещё не доказаны и не объявлены реализованными.

При заданном условии продолжение P6 возможно после фактического закрытия P5; этот отчёт не запускает предыдущие этапы повторно и не переходит к P7. Ни production-, ни тестовая DB запись не выполнены.

---

## P5 — детали, адреса, контакты; 16.10.2026 18:23 MSK

**DIRECT_REWRITE_P5_RESULT: BLOCKED — developer-код реализован и проверен локально; полная приёмка не подтверждена.**

Перед работой полностью прочитаны семь обязательных документов; BACKEND_CONTRACT_REPORT.md находится в этой директории, 3 872 строки. Проверено фактическое условие входа: **P4 BLOCKED, не POSITIVE; его реализация не начата**. Новый ввод пользователя: **«Ты пока пропускай эти блоки. У меня сейчас нету возможности проверять на реальных»** принят как явный waiver для реализации текущего P5 без ожидания внешних gates. Шаблонное STOP/P4 условие не скрыто: оно не выполнено, предыдущий результат не повышен до POSITIVE. P2/P3/P4 результаты сохранены. Реальные проверки отложены; это разрешение писать код, а не выдумывать PASS. **P6 не начат.**

### Реализовано и изменённые файлы

- Source-aware OrderId с enum reys/reys_den и positive Long; source сохраняется в navigation/history. Это необходимый общий тип для P5, не реализация P4 списка.
- DirectMySqlOrderDetailsDataSource: parameterized `id=? AND id_v=?` для обеих таблиц, без date-фильтра; current access и ownership, IO/TLS/timeouts/закрытие ресурсов через прежний executor. История той же source-table по исходным name_c/date, date DESC/id DESC, максимум5; mobile/whatsapp отдельно, без нового active-фильтра истории. NULL historical driver_id → safe data error, не вымышленный0.
- Resolver: точные normalization/scoring/stopwords/weights/cap/tie/delta rules backend. Несколько равных или недостаточно различимых кандидатов → manual/ambiguous с null address/contacts. Выбор первого отсутствует. Invalid link не уничтожает достоверные контакты; пустой номер не делает contacts query; решения заново вычисляются для текущего текста, глобального кеша выбранного адреса нет.
- Time/comment/title parsing, Unicode first80, raw note/client сохранены отдельно; plannedTime не теряет исходное нормализованное примечание, итоговый address только resolver. status/col_s/col/st_vv независимы.
- Repository использует текущую session, повторяет access gate и проверяет revision при завершении; поздние reads после logout/relogin не публикуются. Route-opened — ownership SELECT/no-op, без telemetry/write/HTTP. Перед внешним маршрутом данные и ownership проверяются заново.
- Compose details UI: данные/ошибки/disabled/not-found/retry, история, structured phones/copy/ACTION_DIAL, выбор Карт/Навигатора. При manual — копирование исходного текста и открытие выбранного приложения без destination. Без догаданного адреса/geocoding/автозвонка. Internal destination изолирован по driver+source-aware order ID; ON_STOP скрывает данные/отменяет job. Raw error/PII logging не добавлен.

Полный список **29 source/test/doc файлов** — [evidence/p5/files.md](evidence/p5/files.md), подробности — [P5_IMPLEMENTATION.md](P5_IMPLEMENTATION.md). Главные новые: domain/{OrderId,OrderDetails,OrderTextParsing}.kt, data/mysql/{DirectMySqlOrderDetailsDataSource,OrderContactResolver}.kt, data/repository/DirectOrderDetailsRepository.kt, presentation/OrderDetailsViewModel.kt, ui/OrderDetailsScreen.kt, navigation/{OrderDetailsDestination,OrderExternalActions}.kt; JVM/Android tests, fixture и два tools scripts. Существующие runtime-файлы изменены только AppGraph.kt/DirectApp.kt для P5 wiring/navigation. Обновлены MASTER_CONTEXT/PLAN/UI/SECURITY/PROGRESS. Gradle/dependencies и прежние auth/session/JDBC implementation files не менялись.

Старые `/workspace/FFL` и `/workspace/FFL-backend` чистые; все104 reference hashes совпадают. Снимок до работы — evidence/p5/before.json; пользовательские файлы вне scope не затирались, reset/clean/commit/push не выполнялись. Существующие P0–P3 evidence/report сохранены. Новый APK закономерно заменил предыдущий Direct build output; исторические SHA не выданы за текущий APK.

### Команды и фактические результаты

Cwd `/workspace/FFL-direct`; Gradle/SDK после `source /workspace/.ffl/env.sh`. Чтение — rg/cat/sed, Git status обоих references; inline Python для metadata/hash/snapshot/report, без вывода config values. Build/runner команды с точными аргументами/cwd/start/end/exit — [evidence/p5/commands.json](evidence/p5/commands.json).

| Команда/попытка | Exit | Результат / evidence |
| --- | --- | --- |
| `python3 tools/p5_generate_golden.py` | 0 | AST чистых функций неизменённого backend, без app/config/database import/SQL;119 resolver +644 text cases, golden.json |
| Первый `bash gradlew --offline ... :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime` | 1 | Не было cached Okio3.4.0 из существующего DataStore; build-initial.log. Compile/tests ещё не выполнены |
| Та же проверка online без offline | 0 | Зависимость получена обычным resolution, source/Gradle не подменялись; build-second-command.json |
| Финальный `bash gradlew --offline --no-daemon --max-workers=2 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease` | **0** | build-final-command.json/build-final.log;134 tasks,22 executed/112 up-to-date. Unit task реально выполнен, fresh XML |
| Первый `:app:connectedDebugAndroidTest` с выбранными8 cases | 1 | PropertyFetcher timeout/Unknown API Level до app tests; instrumentation-initial содержит framework failure, не8 проваленных app cases |
| Повтор с `-Dddmlib.getprop.timeout.sec=30` | 1 | Реально8 tests:7 PASS/1 FAIL — ART Unsupported flags:256. XML instrumentation-before-unicode-fix, connected-retry.log сохранены |
| Финальный `bash gradlew --offline --no-daemon --max-workers=2 -Dddmlib.getprop.timeout.sec=30 :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=...` | **0** | Выбор: OrderDetailsUiTest(6) +2 StartupTest cases; connected-final-command.json, свежий instrumentation XML |
| `python3 tools/verify_p5.py` | 0 | Source/DEX/graphs/config-empty/build/XML/reference hashes; static-validation.json. Не выполняет live SQL и не повышает gates |
| `apksigner verify --verbose`, `aapt dump badging`, `apkanalyzer manifest print` | 0 финально | artifact-checks-final.json; первоначально выбран несуществующий build-tools36.1.0 (127), исправлен путь на установленный36.0.0 |
| `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk`, pm path/sha256sum | 0 | После UTP cleanup переустановка; runtime-validation.json/install-final.log, установленный SHA совпал |

Отдельные setup/runtime попытки не спрятаны: PATH не содержал emulator executable (127), запуск исправлен существующим absolute-path helper. Software AVD boot сопровождался **system_server Lost network stack/DeadSystemException**; после восстановления activity/package services стали доступны. sys.boot_completed=1 отдельно не признан готовностью. Первые settings calls exit20 (service отсутствовал), затем0. Реальный getprop занял6.14s при default ddmlib timeout2s; поддержанный override найден в PropertyFetcher через javap. Это timeout runner, app SQL/network deadlines не менялись. После evidence остановлен только созданный для P5 emulator (cleanup.json).

### Тесты, APK и runtime

**JVM:** **811 executed/passed,0 failures/errors/skipped/disabled**. Из них **785 P5**:119 resolver golden,644 text golden,9 scripted JDBC SQL/mapping cases,8 session/repository cases,3 identity/URI/phone cases,2 resolver context/threshold cases;26 прежних graph/config/connection cases также выполнены заново. XML — evidence/p5/unit/*.xml, текущая сборка. Это synthetic golden/JDBC proxies, не live DB. Generation/check scripts не прибавлены к JUnit counts.

Android-прогон выявил настоящую несовместимость Pattern.UNICODE_CHARACTER_CLASS на ART. Флаг удалён, Python word-boundary семантика реализована явно через letters/numbers/underscore и Nd digits. Дополнительно независимый Python/JDK probe показал различие combining marks/connector punctuation; добавлены56 golden cases и Android assertions. Повтор положителен, assertions не ослаблялись. Исторический провал8/1 и более ранние положительные subsets не смешаны с финальными counts.

**Android:** **8 executed/passed,0 failures/errors/skipped/disabled**, API30/Android11 x86_64 software emulator-5556.6 новых P5 cases: ambiguous без ложного address/contact; manual choice/copy; second-route/history/comment/нет editable fields; scheme-compatible Yandex choice; реальные Android Intent package/action/scheme и ACTION_DIAL; Unicode parser на ART.2 baseline startup cases: config missing без backend IP/DB inputs, settings/theme/navigation/recreation. Подробные case names — [runtime-validation.json](evidence/p5/runtime-validation.json); XML — evidence/p5/instrumentation. Остальные9 старых instrumentation cases не запускались, это не полный Android suite. Старый P2 UI refused-connection case не объявлен актуальным auth-тестом P3.

**Debug APK:** `/workspace/FFL-direct/app/build/outputs/apk/debug/app-debug.apk`, package `ru.ffl24.direct.dev`,0.1.0-dev,minSdk24/target36, **13,376,890 bytes**, SHA256 **`60ada8215b13f1cf46b0599ebabc8bf91d6c6e7bd0f811b8e71b637e6c7b04cf`**. Подпись проверена; установленный base.apk SHA тот же. Собран без DB credentials/CA/config values; BuildConfig mode DIRECT_MYSQL. Debug132/release131 runtime components, без API transports/legacy project/classes. **Release:** app/build/outputs/apk/release/app-release-unsigned.apk, minified R8, SHA256 `7b033750cf6462f7cca0c91f202724fa5f14730c852eb8605bb9613a6f05744a`; unsigned, не установлен/не проверен runtime.

**Lint:**0 errors,12 warnings; полный lint.xml сохранён. Включены два прежних vendor TrustAllX509TrustManager warnings (не suppress/baseline), version/target warnings, unused legacy Direct strings, NotShrinkingResources/UseKtx. Это не security certification. Kotlin test-proxy nullable-cast warning также не скрыт. Native strip warnings сохранены в build log.

### Раздельные статусы и границы

| Направление P5 | Статус | Доказательство / предел |
| --- | --- | --- |
| STATIC_CHECK | POSITIVE локально | Graph/manifest/DEX/source/config-empty/lint/reference checks; warnings открыты |
| UNIT_TEST | POSITIVE |811 fresh PASS;785 P5 +26 shared, без live SQL |
| BUILD | POSITIVE | Debug/test APK + minified unsigned release, финальный exit0, debug signature |
| EMULATOR_RUNTIME | BLOCKED для полного P5 | Локальный UI/Intent/ART subset8 PASS; настоящий JDBC details/history не проверен |
| REAL_DEVICE_RUNTIME | BLOCKED — отложено пользователем | Физического устройства нет в проверке |
| LIVE_DB_READ | BLOCKED — отложено пользователем | Не выполнялись real details/resolver/history/name_v/schema reads |
| LIVE_DB_WRITE | BLOCKED — вне scope | Нет writes/разрешённого test order, P6 не начат |
| SECURITY_REVIEW | BLOCKED | P11/server-side driver isolation не доказаны, DRIVER_ROLLOUT=BLOCKED_SECURITY |

**Не реализовано/не подтверждено:** P4 list/three sections/reorder/date policy и переход из списка к деталям; есть только internal OrderDetailsDestination.open для будущего P4. Поэтому APK сейчас не показывает пользователю готовый список заказов/вход из него в детали. Сквозной login→list→details не проверен. Извлечение телефонов из свободного comment не придумано: backend его не содержит, C02 требует отдельного решения; comment целиком сохранён и копируется, structured DB phones реализованы. Новый developer UX/manual/copy/dial не объявлен завершённой visual/product приёмкой C02/C05.

Неизвестны реальные schema/types/collation/name_v/grants/данные обеих таблиц, актуальный login/access/session на стенде, real installed Maps/Navigator/номеронабиратель и clipboard behavior, API24/36/minified runtime/process death. Проверки Intent лишь конструировали реальные Android Intent и UI callback, **не открывали настоящие Яндекс-приложения и не совершали звонков**. Нет DB connection/SELECT1/business reads или writes в этом запросе; секреты/PII не выводились.

P5 код сделан в разрешённом developer scope, внешние проверки не задерживают его реализацию. Формальный полный результат BLOCKED сохраняет перечисленные ограничения; это не утверждение о проваленной текущей локальной сборке. Для полной приёмки потребуется P4 integration и реальные разрешённые read/runtime проверки, когда они станут доступны. **P6 автоматически не начинать.**

---

## Запрос P4 — STOP на обязательном gate P3, 16.10.2026 17:44 MSK

**DIRECT_REWRITE_P4_RESULT: BLOCKED**

Все семь обязательных документов прочитаны полностью: MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, этот PROGRESS.md и [BACKEND_CONTRACT_REPORT.md](BACKEND_CONTRACT_REPORT.md) (3 872 строки; report находится в docs/direct-rewrite/). Входное условие не выполнено: актуальный отчёт ниже содержит **DIRECT_REWRITE_P3_RESULT: BLOCKED**, а не POSITIVE. Независимое сохранённое доказательство — [evidence/p3/status.json](evidence/p3/status.json): implementation=ADDED_UNVERIFIED, verification=SKIPPED_PER_USER_INSTRUCTION, p3_result=BLOCKED; build_executed=false, build_exit=null, unit_tests_executed=0, instrumentation_tests_executed=0, live_db_queries_executed=false, new_p3_apk_created=false.

Предыдущее «Пропускай все» разрешало source-only продолжение P3; успешная приёмка тогда не заявлялась. Последний запрос P4 вновь прямо требует «Если нет — STOP». Поэтому **остановка до реализации P4**, без изменения результата P3 и без запуска исправлений/проверок предыдущего этапа.

### Файлы и границы

Изменён только `/workspace/FFL-direct/docs/direct-rewrite/PROGRESS.md`: эта запись и строка P4 в таблице этапов. Код, тесты, Gradle, существующие незакоммиченные Direct-файлы/P3 evidence и APK сохранены. До/после записи оба legacy Git дерева чистые; SHA256 всех 104 reference-файлов совпали. Сравнение SHA256 остальных Direct-файлов до/после не обнаружило изменений. Старый API runtime не затронут; commit/reset/clean/push не выполнялись.

**Не реализовано в P4:** reys/reys_den queries/mappers, DTO/SQL parity, source IDs, три раздела и delivered, сортировка, локальный порядок и восстановление прокрутки. Их реализация не начата из-за gate. **P5 не начат.**

### Команды, тесты, сборка и runtime

Чтение выполнялось из `/workspace/FFL-direct` (поиск — также из `/workspace`): `pwd`, `rg --files` для документов/AGENTS, `wc -l`, `cat` для MASTER_CONTEXT/PLAN/DB/UI/SECURITY и p3/status.json; `sed -n '1,110p'`, `'111,220p'`, `'221,327p'` для прежнего PROGRESS; `sed -n '1,1000p'`, `'1001,2000p'`, `'2001,3000p'`, `'3001,3872p'` для полного backend report. Прочитан также шаблон отчёта из DIRECT_QA_CHECKLIST.md. `git -C /workspace/FFL status --porcelain=v1` и аналогично FFL-backend проверили сохранность. Inline `python3` прочитал P3 metadata, сверил SHA256 references/Direct-файлов и точечно обновил этот документ; exit=0. Все перечисленные shell-команды чтения/сохранности завершились с exit=0; отдельные build/test логи P4 не создавались. Эти действия **не являются тестами приложения**.

**Тесты P4:** executed=0; passed/failed/skipped/disabled неприменимы, suite не запускался, новых test files/XML нет. Unrun: обе таблицы, delivered-раздел, mapping/status/source collisions, SQL binding/sorting, driver/date/section isolation, merge/local order/scroll. Количество несуществующих test cases не выдумывается. Исторические P2 PASS не доказывают новый P3 или P4.

**Сборка P4:** не выполнялась, Gradle/compile/assemble не запускались; exit code отсутствует, нового APK нет. Существующий APK — предыдущий P2-артефакт, не доказательство сборки P3/P4; в этом запросе не устанавливался. **Runtime checks P4:** emulator/device/API не запускались, UI/restore/SQL не проверялись. БД не использовалась: ни SELECT, ни business-write. Секреты/config values/PII не выводились.

| Направление P4 | Статус | Основание |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены только prerequisite и сохранность; P4 implementation не начата |
| UNIT_TEST | BLOCKED | 0 executed, остановка до реализации |
| BUILD | BLOCKED | Не выполнялась, APK P4 отсутствует |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся |
| LIVE_DB_READ | BLOCKED | Не выполнялось, DTO/SQL parity на живой БД не доказана |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось |
| SECURITY_REVIEW | BLOCKED | Не выполнялся; DRIVER_ROLLOUT=BLOCKED_SECURITY сохраняется |

### Неизвестные и блокеры

Главный блокер — **нет P3 POSITIVE с доказательствами**. У P3 не подтверждены даже сборка новых зависимостей/кода, active-only list/wrong-password/access=0, локальная сессия и её lifecycle; реальный auth/access на стенде не проверен. P2 также остаётся BLOCKED по сохранённым отчётам; доступность внешнего стенда/устройства в этом запросе не переоценивалась. Дополнительно контракты оставляют для P4 открытыми C01/C03/C10 (три раздела/scroll/local merge, дата и SQL order), typing/schema и контрольные данные обеих таблиц; решений по ним здесь не принималось.

Для продолжения при заданном gate необходимо завершить проверку P3 и зафиксировать фактический POSITIVE с evidence. Этот отчёт не запускает P3/P4 повторно и не переводит проект к P5.

---

## P3 — реализация после указания «Пропускай все», 16.10.2026 17:37 MSK

**DIRECT_REWRITE_P3_RESULT: BLOCKED — код добавлен, приёмка не проверена.**

После предыдущего STOP пользователь написал **«Пропускай все»**. Это принято как разрешение продолжить текущий P3 без ожидания P2 POSITIVE и без запуска проверок. Отдельно сообщено: работа только над P3, без P4; пропущенные проверки не становятся PASS. **P2 остаётся BLOCKED.** Остановка на gate снята для этой реализации, но успешность сборки/auth/runtime не выдумана. Ниже сохранён первоначальный STOP и исходные отчёты как история.

### Что добавлено в исходники

- `DirectMySqlDriverDataSource`: getDrivers только из __hr_typepos с access=1/id/name not null/name<>'', сортировка по имени и безопасные DTO без parol. Prepared login по id/parol/access=1, access до/после lookup; wrong password → InvalidCredentials, access!=1 → DriverDisabled, missing отделён. Ни bf, ни UPDATE/DDL/API/JWT нет.
- Общий `MySqlReadExecutor` выделен из P2 probe: IO, Semaphore(2), readOnly, connection/statement/result-set scope, safe errors, timeout и отсутствие auto-retry. Новые SQL используют тот же строгий TLS/config factory; SQL param values/driver errors не логируются. Рефакторинг P2 helper тоже не проверялся; прошлый PASS не распространяется на его новый код автоматически.
- `AuthRepository`/`DirectAuthRepository`, Driver/DriverSession/AccessState/AuthError. `checkAccess` использует текущий session id, а не override из protected UI. Network/TLS/DB-account/storage ошибки не выдаются за неверный пароль.
- Отдельный `ffl_direct_session_v1` DataStore: только version/id/name. `AuthSessionManager` с disk Mutex/revision для logout/late login/restore; старт restore сбрасывает memory grant, затем живой access check. Disabled/missing/logout очищают session. Пароль/JWT не сохраняются, старый JWT store не мигрируется. Disk failure оставляет безопасную ошибку и не объявляет logout успешным.
- Application-scoped graph и lifecycle SetupAuthViewModel. UI: активные водители/поиск/выбор, masked password dialog, неверный пароль, loading/retry, «Недоступно», имя после входа, проверка доступа/выход. Password input только remember, без saved state/StateFlow; transient CharArray очищается после завершения/отмены/background. Immutable JVM/IME copies гарантированно стереть нельзя.
- ON_STOP отменяет auth job/очищает ввод/скрывает authenticated state; ON_START снова проверяет сохранённую identity через access. Минимальный disabled UX реализован по рабочему допущению, без отдельной visual/product приёмки C02. Нет заказов/three sections/доставки — это не P4.

Точные файлы — [evidence/p3/files.md](evidence/p3/files.md). Главные новые: domain/{Driver,DriverSession,AccessState}.kt, data/mysql/{DirectMySqlDriverDataSource,MySqlReadExecutor}.kt, data/repository/DirectAuthRepository.kt, session/{DriverSessionStore,DataStoreDriverSessionStore,AuthSessionManager}.kt, presentation/SetupAuthViewModel.kt, ui/SetupAuthScreen.kt, DirectApplication.kt. Изменены только Direct composition/UI/manifest/resources/Gradle и документация, включая этот файл. Добавлены зависимости DataStore1.1.1 (baseline legacy) и Lifecycle2.9.4 (baseline текущего Direct graph); dependency resolution не запускался. Полное описание — [P3_IMPLEMENTATION.md](P3_IMPLEMENTATION.md).

Старые FFL/FFL-backend runtime и пользовательские файлы вне scope не изменялись, reset/clean/commit/push не выполнялись. Before snapshot — evidence/p3/before-implementation.json; P0/P1/P2 evidence и user report сохранены.

### Команды и проверки — что действительно выполнено

Cwd `/workspace`. Выполнены только чтение текущих файлов через rg/cat/sed, `git -C /workspace/FFL status --short` и аналогично FFL-backend (до редактирования чисто, exit=0), применение патчей и Python для snapshot/списка изменённых файлов/текста отчёта. Это операции работы с исходниками и сохранности, **не software tests или автоматическая верификация**. Пользовательские DB secrets/config values не выводились. Новых health/SELECT/login/network запросов не было.

По указанию пользователя **не запускались**: Gradle sync/dependency resolution/compile/assemble, unit tests, lint/static validators (включая tools/verify_p2.py), instrumentation, emulator, physical device, live MySQL. Команды проверки приложения не исполнялись — exit code отсутствует, не 0. Новых auth test files/XML нет, executed P3 tests=0; исторические 26 unit/11 Android P2 PASS не пересчитаны как результаты P3. Просмотр исходников не заменяет тесты.

**Сборка:** не выполнялась, нового APK P3 нет. Имеющийся app/build/outputs/apk/debug/app-debug.apk — старый артефакт P2, без нового auth flow. Его предыдущий SHA/подпись/установка из раздела P2 не доказывают новый код. Runtime checks P3 не выполнялись. Данные драйверов/пароли/test identity не выдумывались.

| Направление P3 | Статус | Причина |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED — skipped | Автопроверки/линт не запускались по указанию пользователя, только просмотр source |
| UNIT_TEST | BLOCKED — skipped | 0 executed, новых tests/XML нет |
| BUILD | BLOCKED — skipped | Compile/dependency resolution/assemble не запускались, P3 APK нет |
| EMULATOR_RUNTIME | BLOCKED — skipped | Эмулятор/приложение не запускались |
| REAL_DEVICE_RUNTIME | BLOCKED — skipped | На физическом устройстве не проверялось |
| LIVE_DB_READ | BLOCKED — skipped | getDrivers/login/access/SELECT1 реально не исполнялись |
| LIVE_DB_WRITE | BLOCKED — вне scope | Нет business-write кода/запросов/разрешения |
| SECURITY_REVIEW | BLOCKED | Не запускался; server-side isolation/P11 не доказаны |

### Неизвестные, ограничения и следующий scope

Неизвестны compile/runtime совместимость новых зависимостей и рефакторинга, MySQL schema/collation/parol/access coercion, права readOnly/session queries, auth success/failure/revocation на стенде, DataStore save/restore/logout/cancel/disk-failure/race/process-death, actual password absence в logs/saved-state/backup и API24/36/minified behavior. Некорректные/non-positive DB driver IDs дают data error; access не равный числу1 блокируется; пустой пароль не принимается. Эти правила требуют сверки с настоящими legacy данными. Disabled UI — developer-вариант, C02 не объявлен принятым.

Источник блокировки итоговой приёмки — **проверки сознательно пропущены**, а не обнаруженный успешный/провальный runtime. Новый код сделан в разрешённом scope и не оставлен в ожидании подтверждения. Для POSITIVE потребуются реальные build/test/auth/session/access evidence; waiver не превращает их в PASS. DRIVER_ROLLOUT=BLOCKED_SECURITY. **P4 не начат; автоматического перехода нет.**

---

## История: первоначальная остановка P3 до нового указания

## Запрос P3 — остановка на обязательном gate, 16.10.2026 17:14 MSK

**DIRECT_REWRITE_P3_RESULT: BLOCKED**

Обязательные MASTER_CONTEXT.md, DIRECT_REWRITE_PLAN.md, DIRECT_DB_CONTRACT.md, DIRECT_UI_CONTRACT.md, SECURITY_RISKS.md, PROGRESS.md и BACKEND_CONTRACT_REPORT.md прочитаны полностью (report: 3 872 строки). Проверка входного условия P3 не пройдена: в этом PROGRESS **DIRECT_REWRITE_P2_RESULT: BLOCKED**, а не POSITIVE. Сохранённые [P2 static-validation.json](evidence/p2/static-validation.json) и [runtime-validation.json](evidence/p2/runtime-validation.json) независимо указывают full_p2_result=BLOCKED, live_mysql_used=false, live_select_1_executed=false, physical_device=false. Согласно прямому условию пользователя — **STOP до реализации P3**. Никакого переопределения P2 и обхода gate.

### Файлы и выполненные действия

Изменён только `docs/direct-rewrite/PROGRESS.md`: этот отчёт и строка P3 в таблице этапов. Runtime-код, Gradle, тесты, APK, P0/P1/P2 evidence, исходные контракты и пользовательские файлы не изменены. Рабочие деревья `/workspace/FFL` и `/workspace/FFL-backend` чисты; SHA256 104 reference-файлов совпадают. Код getDrivers/login/checkAccess/local session не создавался. P4 не начат.

### Команды, тесты, сборка и runtime

Cwd `/workspace`. Реально выполнены: `rg --files` для обязательных документов/AGENTS; `cat` семи документов, для PROGRESS — `sed -n '1,120p'` и `sed -n '121,242p'`; для backend report — `sed -n '1,1000p'`, `'1001,2000p'`, `'2001,3000p'`, `'3001,3872p'`; `git -C /workspace/FFL status --porcelain=v1`, аналогично FFL-backend; inline `python3` для чтения JSON/XML P2 и сверки reference SHA256, затем точечного обновления этого файла с проверкой неизменности остальных project files. Эти команды завершены с exit=0; чтение/проверка метаданных не является прогоном тестов приложения.

**Тесты P3:** не запускались, 0 executed; suite/новых XML P3 нет. Сценарии active-only list, wrong password, access=0, local restore/logout не реализованы и не проверены. В сохранённых P2 XML есть 26 unit/11 API30 instrumentation PASS и build-final-command.json exit=0 — только исторические результаты P2, заново не исполнялись и за P3 PASS не выдаются. Они не доказывают доступ к настоящей MySQL или готовность auth/session.

**Сборка P3:** не выполнялась, Gradle не запускался, нового APK нет. Имеющийся P2 APK не пересобирался/не устанавливался. **Runtime P3:** эмулятор/телефон/БД не запускались, SQL/auth/access/storage checks не выполнялись. Новых LIVE_DB_READ/LIVE_DB_WRITE нет; секреты/PII не читались и не выводились.

| Направление P3 | Статус | Причина |
| --- | --- | --- |
| STATIC_CHECK | BLOCKED | Проверены только входной gate и сохранность файлов; source-check реализации P3 неприменим, код не начат |
| UNIT_TEST | BLOCKED | Не запускались: остановка до реализации |
| BUILD | BLOCKED | Не выполнялась: остановка до реализации |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся; прежний P2 subset не доказывает P3 |
| REAL_DEVICE_RUNTIME | BLOCKED | Не выполнялся; доказательства P2 не содержат physical-device PASS |
| LIVE_DB_READ | BLOCKED | У P2 нет real SELECT1; P3 driver list/login/access не проверялись |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось, разрешения на write нет |
| SECURITY_REVIEW | BLOCKED | Серверная driver isolation/P11 не доказаны; DRIVER_ROLLOUT=BLOCKED_SECURITY |

### Неизвестные и блокеры

Главный блокер — отсутствие **P2 POSITIVE с доказательствами**. Зафиксированные незакрытые условия P2: согласованный MySQL endpoint/CA/dev account/grants/auth plugin, реальный SELECT1 и runtime проверки на физическом устройстве. В этом запросе доступность стенда не переоценивалась и P2 не продолжался. Для будущего P3 также остаются не подтверждённые схема/collation/legacy parol semantics, тестовый водитель/пароль и согласованный disabled UX (C02); данные/секреты не выдумывались.

Допустимый следующий шаг — завершить P2 и зафиксировать POSITIVE с реальными доказательствами, после этого вернуться к разрешённому P3. P4 автоматически не начинать.

---

## Актуальный результат P2 — 16.10.2026, Europe/Moscow

**DIRECT_REWRITE_P2_RESULT: BLOCKED**

P1 gate подтверждён **до изменений**: PROGRESS POSITIVE, 2 unit/2 Android UI tests в сохранённых XML, фактический P1 APK SHA, 104 reference hashes и чистые Git HEAD обоих старых проектов. Доказательство — [evidence/p2/p1-gate.json](evidence/p2/p1-gate.json). Все семь обязательных документов прочитаны полностью, backend report — 3 872 строки. P0/P1 evidence и исходный report не перезаписывались.

Выполнена developer-only реализация P2 и доступные проверки. Закрыть P2 нельзя: **нет согласованной тестовой MySQL, её endpoint/CA/account/auth plugin/grants и физического устройства**. Шесть DIRECT_MYSQL properties отсутствуют и в environment, и в local.properties; это проверено без вывода значений. Реального соединения с MySQL/SELECT1/schema probe не было. Локальный TLS wire fixture не является БД. **P3 не начат. DRIVER_ROLLOUT=BLOCKED_SECURITY.**

### Реализовано и файлы

- BuildConfig: DIRECT_MYSQL_HOST/PORT/DATABASE/USER/PASSWORD/USE_SSL из environment или ignored local.properties, плюс обязательная явная DIRECT_MYSQL_CA_PEM. Корректное Java literal escaping; никакой передачи secrets в CLI/лог. Empty/partial/invalid config раздельны, false SSL/URL options/multi-host/root отклоняются без соединения. Никакого ввода backend IP или DB credentials в UI.
- JDBC-кандидат: официальный **MariaDB Connector/J 3.5.10**, LGPL-2.1-or-later, версия закреплена. Оценён также **MySQL Connector/J 26.7.0**, GPLv2 with Universal FOSS Exception. Jars/sources/POMs скачаны через HTTPS из Maven Central, опубликованные SHA1 сверены, SHA256 сохранены в driver-artifacts.json; cache jar совпал. SHA256 выбранного jar: `919b8c1c771d9ee3465811462f242c9543ab401e140c64988ddbf1d8abcb18b2`.
- Explicit CA + verify-full + AndroidTlsSocketPlugin: TrustManagerFactory только с явной CA; исходный hostname и JSSE HTTPS endpoint identification при TLS handshake. Только TLS1.2/1.3, без trust bypass/system/deferred identity/plaintext downgrade/redirect/RSA key retrieval. Auth allowlist mysql_native_password/caching_sha2_password; vendor logging выключен до driver classes.
- Blocking JDBC/CA/TLS: runInterruptible(Dispatchers.IO), общий Semaphore(2) между graph/activity, scope per operation, ResultSet/PreparedStatement/Connection use/close. Connect 3000ms, socket 5000ms, query 3s, coroutine budget 15000ms. Automatic retry=0; повтор только явно из UI. Connection не хранится singleton. Единственный SQL приложения SELECT 1, readOnly/maxRows=1. Safe enum errors, без Throwable/message/URL/credentials в UI.
- Compose startup: missing/invalid config, checking, безопасный отказ/повтор. Технический успех probe не запускает login или следующие этапы. Добавлен INTERNET для JDBC, API/JWT/HTTP fallback и старый setup отсутствуют. Core desugaring 2.1.5 и scoped keep/missing-optional-type rules, R8 включён для release.

Точные файлы: [evidence/p2/files.md](evidence/p2/files.md). Основные — app/build.gradle.kts, gradle/libs.versions.toml, app/proguard-rules.pro, manifest, AppGraph, DirectStartupRepository, domain/StartupRepository, ui/DirectApp, strings.xml, data/mysql/{DirectMySqlConfig,DirectDbError,ConnectionPolicy,MySqlConnectionFactory,MySqlConnectionProbe,AndroidTlsSocketPlugin}.kt, TLS service registration, unit/androidTest/public CA fixtures, tools/{p2_tls_fixture,verify_p2,verify_p2_buildconfig}.py. Удалён только P1 placeholder нового проекта data/UnconfiguredStartupRepository.kt. Обновлены README, MASTER_CONTEXT, план, SECURITY_RISKS и этот PROGRESS; добавлена [инструкция connection layer](MYSQL_CONNECTION_LAYER.md).

Старые `/workspace/FFL` и `/workspace/FFL-backend` не изменены, их 104 tracked files/HEAD/status проверены повторно. Незакоммиченные пользовательские изменения не сбрасывались/не затирались; Git/worktree/reset/commit/push/remote нового проекта не создавались.

### Команды и реальные результаты

Все Gradle-команды: cwd `/workspace/FFL-direct`, предварительно `source /workspace/.ffl/env.sh`. Логи без DB secrets — evidence/p2.

| Команда / попытка | Exit | Результат / evidence |
| --- | --- | --- |
| `git -C /workspace/FFL status --short`, аналогично FFL-backend; hash/HEAD/XML/APK gate Python check | 0 | Чистые reference trees, 104 файла неизменны, P1 доказан; p1-gate.json/before-files.json |
| Official HTTPS artifact fetch + SHA1/SHA256 check | 0 | 6 artifacts двух кандидатов; driver-artifacts.json |
| javac DriverProbe; D8 min-api24, затем adb dalvikvm с обоими JDBC jars | 0 после исправления probe | Обе classes загрузились на ART30, controlled refused loopback → SQLException; driver-art-with-resources.log. Это не DB connection PASS |
| `bash gradlew --no-daemon --max-workers=4 :app:assembleDebug :app:lintDebug :app:verifyDirectRuntime :app:assembleRelease` — первые две попытки | 1, 1 | Gradle import Properties и отсутствующий Android Connection.setNetworkTimeout исправлены; build-initial.log/build-second.log |
| Сборка + `:app:testDebugUnitTest` — третья попытка | 1 | 26 tests, 1 failed: timeout замаскирован proxy exception; исправлена проверка timed child context; build-third.log/unit-initial-failure.xml |
| Та же проверка — четвёртая попытка | 1 | **26 fresh unit PASS**, затем R8 missing optional desktop classes; build-fourth.log/r8-initial-missing-rules.txt |
| `bash gradlew --no-daemon --max-workers=4 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest :app:assembleRelease` — пятая попытка | Gradle 0 | Debug + unsigned minified release успешны; build-fifth.log. Shell wrapper сообщил exit1 при напечатанном Gradle exit0; эта неоднозначность не использована как единственный build proof, финальная команда ниже повторена с subprocess.returncode |
| `python3 tools/p2_tls_fixture.py --directory /workspace/.ffl/p2-fixture`, 4 adb reverse для tcp19061–19064 | сервис запущен / adb 0 | Loopback-only greeting/TLS, не MySQL; tls-fixture.log. Ключи вне repo, в tests только публичная CA |
| `bash gradlew --no-daemon --max-workers=2 :app:connectedDebugAndroidTest` | 0 | **11 fresh Android tests PASS**, 2m16s; connected-initial.log/instrumentation-tests.xml |
| `python3 tools/verify_p2_buildconfig.py` | 0 | Реальная генерация всех 7 fixture fields, javac/java сравнение кавычек/backslash/newline/tab/PEM; исходный config восстановлен, APK не подменён; buildconfig-injection-commands.json |
| Финальная полная Gradle-команда пятой попытки, через Python subprocess с сохранением кода | **0** | build-final-command.json/build-final.log; 134 tasks, 5 executed/129 up-to-date, APK SHA прежний. Unit task здесь UP-TO-DATE, не выдан за новый прогон |
| `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk`; pm path/sha256sum | 0 | Переустановка после UTP cleanup, установленный hash совпал; install.log/device.json |
| `adb ... shell am start -W -n ru.ffl24.direct.dev/ru.ffl24.direct.MainActivity` | 0, **Status: timeout** | Не PASS быстрого запуска. Последующий system/System UI ANR сохранён отдельными XML/PNG; после Wait actual rendered UI без backend input |
| `apksigner verify --verbose`, aapt badging, apkanalyzer manifest print | 0 | signature.txt/apk-badging.txt/apk-manifest.xml |
| `python3 tools/verify_p2.py` | 0 | Source/DEX/manifest/graphs/vendor checksum/XML/APK/reference hashes; static-validation.json |

### Тесты, сборка, runtime

**Unit:** 26 executed/passed, 0 failed/errors/skipped, disabled=0; fresh XML четвёртой попытки в evidence/p2/unit. Cases: все поля/missing/partial/invalid/port/options/TLS/CA/account/password preservation/redaction; safe type/state errors; IO dispatch, scoped close при success/query/mapper/prepare/close failure, one attempt, timeout, caller cancellation, late connection after uninterruptible open, bounded concurrency/no shared connection. Это JVM proxies, не live JDBC. Сохранена отрицательная ранняя попытка 25/26; она исправлена, assertions не ослаблялись. Дополнительно один host Gradle/javac injection check PASS (7 properties, не 7 Android tests).

**Android:** 11 executed/passed, 0 failed/errors/skipped, disabled=0. 4 UI tests: missing configuration/navigation/theme/recreation, отказ реального JDBC + safe message/retry, partial config, отсутствие editable/backend/DB fields. 7 connection tests: missing/partial config, actual driver classloading/refused connection, accepted trusted TLS с последующим intentional pre-auth close, wrong-host/unknown-CA отказ, stalled greeting timeout, вызов suspend probe из Main под StrictMode detectNetwork+penaltyDeath. XML свежий, API30 Android11 x86_64 software emulator-5556; физическим устройством это не считается.

Wire server зафиксировал `valid: tls_accepted_auth_not_processed`, `wrong-host/untrusted: tls_rejected`. Никакой fake MySQL auth/SELECT result fixture не выдаёт. Success + close SELECT1/resource paths проверялись **только unit proxies**; настоящая БД не подключалась, SELECT1 на Android не выполнен. Live tests unrun, не skipped/pass; отдельных live-test методов с выдуманными counts нет.

**APK debug:** `/workspace/FFL-direct/app/build/outputs/apk/debug/app-debug.apk`, package `ru.ffl24.direct.dev`, 0.1.0-dev, **12,513,910 байт**, SHA256 **`8b1aebe4620a78d70bb7a79215e33f24c2daa195b7674ee2a1a0c3751f1db185`**. Установленный base.apk hash тот же; v2 signature verified. `minSdk24/target36`, compile36.1. APK собран без credentials/test CA/test fixture strings; actual generated BuildConfig fields пустые. Runtime graph: debug116/release115 components, только выбранный JDBC, без старого API transport/project. Release minified unsigned: `app/build/outputs/apk/release/app-release-unsigned.apk`, SHA256 `328b69bc3d61b970399b5694e3d16aadb39e990a70a6150e03cffd3d193e1922`; **не установлен/не проверен runtime**, не production signature.

**Lint:** 0 errors, 8 warnings: OldTargetApi, GradleDependency, 2 NewerVersionAvailable, 2 TrustAllX509TrustManager в vendor jar, AutoboxingStateCreation, NotShrinkingResources. Trust-all warnings не скрыты: необязательный permissive trust manager присутствует в драйвере, app factory использует собственный explicit-CA plugin; Android TLS-negatives реально подтвердили выбранный путь. Это не security certification или доказательство отсутствия всех опасных vendor paths. Scoped R8 directives для unsupported optional types документированы, глобального ignorewarnings нет.

Software emulator без KVM остаётся нестабильным/медленным: после завершённых tests были system и System UI ANR. Скриншоты `post-test-system-anr.*`/`post-test-system-ui-anr.*` не выдаются за чистый app. После Wait получены `rendered-ui.xml/png` с «Подключение не настроено», FFL Driver Direct/Настройки, без inputs. Функциональные assertions основаны на завершённых instrumentation tests, не одном PID/launcher/exit0. Быстрый cold start/performance не доказан.

| Направление P2 | Статус | Область / ограничение |
| --- | --- | --- |
| STATIC_CHECK | POSITIVE | source/DEX/manifest/dependencies, checksum, reference integrity; lint warnings открыты |
| UNIT_TEST | POSITIVE | 26 fresh JVM tests + отдельный host injection check, без live SQL |
| BUILD | POSITIVE | Debug + minified unsigned release, финальный Gradle exit0; debug signature verified |
| EMULATOR_RUNTIME | BLOCKED для полного P2 | Локальный subset 11 PASS API30; real MySQL SELECT1/query-timeout/cancel/closure не проверены |
| REAL_DEVICE_RUNTIME | BLOCKED | Физического устройства нет |
| LIVE_DB_READ | BLOCKED | Нет стенда/CA/account/grants; ни SELECT1, ни schema/engine inspection не выполнялись |
| LIVE_DB_WRITE | BLOCKED — вне scope | Не выполнялось; разрешения на write нет, в P2 business-write кода нет |
| SECURITY_REVIEW | BLOCKED | P11/серверная driver isolation отсутствуют, vendor warnings/unsupported paths описаны |

### Неизвестные и блокеры

Нет endpoint/согласованной CA/dev identity и реальных MySQL version/schema/engine/grants/auth-plugin evidence. Не проверены real SELECT1, real TLS/auth compatibility/negatives, server queryTimeout/KILL QUERY permissions/служебное соединение, native DNS/timeouts/physical loss/partial-open socket cleanup, реальные ресурсы/нагрузка/ограничения сети. Coroutine budget не обещает hard deadline для непредсказуемого native DNS/JDBC; слот удерживается до закрытия. До двух основных и до двух служебных KILL QUERY connections у драйвера, без pool. mTLS, IPv6/нестандартные DB names и JDBC4.2/SQLType/XA/JMX/GSS/Windows/IAM/PARSEC не поддержаны. API24/36 runtime, minified runtime, физическое устройство не проверены. Shared credentials в APK не обеспечивают server-side driver isolation.

После сохранения evidence остановлены только созданные для P2 TLS fixture и AVD; reverse mappings удалены. Доказательства/установленный hash сохранены, повторный запуск описан в MYSQL_CONNECTION_LAYER.md. Cleanup — evidence/p2/cleanup.json.

Разрешённый следующий шаг — устранить внешние условия и **продолжить проверки P2** на согласованной тестовой MySQL и физическом устройстве; секреты вводятся безопасно, не в чат. Не обходить gate, не называть fixture живой БД, не начинать P3 автоматически.

---

## Исторический отчёт P1

## Актуальный результат P1 — 16.10.2026, Europe/Moscow

**DIRECT_REWRITE_P1_RESULT: POSITIVE**

P0 gate перед работой подтверждён: сохранённый POSITIVE, JSON evidence, HEAD и хеши 104 tracked reference files. Пользователь явно разрешил только P1. Создан отдельный Android-проект `/workspace/FFL-direct`, package `ru.ffl24.direct.dev`, версия `0.1.0-dev`. Рабочие деревья старых FFL/FFL-backend чистые и неизменны. P2 не начат.

Реализованы: новый composition root `AppGraph`, single-mode `RuntimeMode.DIRECT_MYSQL` из фиксированного BuildConfig, неизвестный mode отклоняется без fallback; интерфейс StartupRepository с безопасным ConfigurationMissing без реквизитов/запросов; MainActivity и Compose NavHost home/settings/back; настройка темы с восстановлением при recreation. На home — «Подключение не настроено», никакого поля backend IP. Нет старого login/setup/API flow. Независимый developer APK, собственный namespace/manifest/storage scope; INTERNET permission отсутствует. Из reference перенесены только wrapper/checksum, Color/Type и launcher resources. Theme интеграция написана отдельно без Activity cast.

Не реализованы в P1: JDBC/connection factory, чтение DIRECT_MYSQL_* properties, DB credentials, SQL, login/session, заказы и остальные P2–P11 функции. Тема пока сохраняется при recreation, но не записывается постоянно между холодными запусками. В новый runtime не добавлены Retrofit/OkHttp/DriverApiService/JWT/HTTP fallback и зависимости на старый app.

### Файлы и проверки P1

Полный список новых файлов: [evidence/p1/files.md](evidence/p1/files.md); машинный список — `evidence/p1/static-validation.json`. Основные: root Gradle files/wrapper/catalog, `app/build.gradle.kts`, manifest/resources, `AppGraph.kt`, `MainActivity.kt`, `domain/*`, `data/UnconfiguredStartupRepository.kt`, `ui/DirectApp.kt`, `ui/theme/*`, `AppGraphTest.kt`, `StartupTest.kt`, README. Обновлены этот PROGRESS и MASTER_CONTEXT. Новые файлы находятся в отдельной папке без Git remote; push/commit/публикация APK не выполнялись. Незакоммиченные пользовательские файлы не перезаписывались.

| cwd `/workspace/FFL-direct`, после `source /workspace/.ffl/env.sh` | Exit | Фактический результат |
| --- | --- | --- |
| `bash gradlew --no-daemon --max-workers=4 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | 1 | Первая компиляция Android-тестов: два неверных imports, исправлены; unit tests реально выполнились |
| `bash gradlew --no-daemon --max-workers=4 :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest` | 0 | После исправления imports сборка успешна |
| `bash gradlew --offline --no-daemon --max-workers=4 :app:assembleDebug :app:lintDebug :app:verifyDirectRuntime :app:assembleDebugAndroidTest` | 0 | Финальная упаковка после icons/backup rules; Lint: 0 errors, 3 version warnings |
| `bash gradlew --offline --no-daemon --max-workers=4 :app:verifyDirectRuntime :app:dependencies --configuration debugRuntimeClasspath` | 0 | Проверены transitives: debug 115, release 114 components, запрещённых transport/JDBC groups и других projects нет |
| `bash gradlew --offline --no-daemon --max-workers=2 :app:connectedDebugAndroidTest` | 1 | UTP dependency не была cached; тесты не стартовали, это не провал assertions |
| `bash gradlew --no-daemon --max-workers=2 :app:connectedDebugAndroidTest` | 0 | После загрузки UTP: 2 Android-теста реально выполнены, 0 failed/errors/skipped |
| `python3 docs/direct-rewrite/evidence/p1/verify_p1.py` | 0 | Source/DEX/manifest/dependency graph/хеши reference и actual APK проверены |
| `adb -s emulator-5556 install -r app/build/outputs/apk/debug/app-debug.apk` | 0 | APK установлен; после UTP cleanup установлен снова, hash в device.json совпадает с локальным |
| `apksigner verify --verbose app/build/outputs/apk/debug/app-debug.apk` | 0 | APK signature v2 проверена |

Unit: 2 executed/passed, 0 failed/errors/skipped (`AppGraphTest`), fresh XML `evidence/p1/unit-tests.xml`. После изменений только Android-test imports/resources повторные unit tasks были UP-TO-DATE; первоначальный текущий прогон исполнил оба теста реально. Checks: wiring настоящего graph без credentials/соединения и отказ неизвестного mode.

Android runtime: AOSP emulator `emulator-5556`, Android 11/API30, x86_64, 480×800, программная эмуляция без `/dev/kvm`. Реально прошли `coldStartAndNavigationHaveNoConnectionInput` и `recreationPreservesNavigationAndThemeWithoutSetupFlow`: home/config missing, ни одного editable поля, settings/back, theme toggle, восстановление navigation/theme после recreation. Fresh `instrumentation-tests.xml` и Gradle runner exit=0. Это реальные instrumentation tests, не mock JDBC и не desktop UI.

Установленный и проверенный APK: `/workspace/FFL-direct/app/build/outputs/apk/debug/app-debug.apk`, **SHA-256 `e24923d04f533cd6a13b1979ad6e0c0b5fb92b9b21a900c67e0fbc577bced2a7`**, 11 674 776 байт, debug package `ru.ffl24.direct.dev`. SHA-256 установленного base.apk получен через adb и совпал. Чистый снимок: `evidence/p1/verified-app-rendered.png`; фактическая UI hierarchy: `final-ui.xml`. В ней заголовок «Подключение не настроено», название приложения/Настройки, нет EditText/API URL input. Статические файлы app и APK DEX не содержат старых API classes; generated mode DIRECT_MYSQL есть в APK. Runtime assertions проверили обе доступные страницы.

### Ограничения и отрицательные попытки P1

Первый software boot занял около 397 секунд; при старте эмулятора были System UI ANR и задержки. Ранний `am start -W` вернул exit=0 с Error type 3, последующие — exit=0 со Status: timeout: **они не считались PASS**. Повторная зависшая установка остановлена SIGINT (exit=130). Успех runtime основан на последующих двух реально прошедших Android UI tests, отрисованной hierarchy/снимке и совпавшем installed APK hash. Быстрый cold-start/performance этими результатами не доказан. UTP удаляет тестовые приложения после прогона; после него app повторно установлен для отдельного screenshot/hash proof. Логи попыток сохранены, ошибочные/launcher/ANR снимки не выдаются за итоговый чистый экран.

Lint warnings: OldTargetApi, GradleDependency и NewerVersionAvailable. Сохранены baseline SDK36.1/target36 и Compose plugin 2.2.10; новые версии не внедрялись ради предупреждений. Ошибок Lint нет, assertions не отключались. Android24/36+, аппаратное ускорение и физический телефон не проверены.

| Направление P1 | Статус | Доказательство / ограничение |
| --- | --- | --- |
| STATIC_CHECK | POSITIVE | verifyDirectRuntime + source/DEX/manifest scan, 104 reference files unchanged |
| UNIT_TEST | POSITIVE | 2 fresh unit tests, 0 failed/errors/skipped |
| BUILD | POSITIVE | Независимые app-debug.apk и androidTest APK, финальная сборка exit=0; signature verified |
| EMULATOR_RUNTIME | POSITIVE | 2 fresh instrumentation UI tests и фактический отрисованный экран на API30; slow software cold start отдельно ограничен |
| REAL_DEVICE_RUNTIME | BLOCKED | Физического устройства нет; успех эмулятора не выдан за устройство |
| LIVE_DB_READ | BLOCKED | Не выполнялось; нет JDBC/SQL/config/стенда, это не scope P1 |
| LIVE_DB_WRITE | BLOCKED | Не выполнялось, разрешения на safe order нет |
| SECURITY_REVIEW | BLOCKED | P11 не выполнен; DRIVER_ROLLOUT=BLOCKED_SECURITY |

Доказательства P1: `evidence/p1/{commands.json,static-validation.json,runtime-validation.json,unit-tests.xml,instrumentation-tests.xml,lint.xml,device.json}`, build/instrumentation/install logs и PNG/XML. Исторический `evidence/verify_p0.py` фиксировал отсутствие runtime в P0 и после P1 повторно не запускался; gate P0 проверялся сохранёнными доказательствами, hash/HEAD/cleanliness.

Неизвестны совместимость JDBC, live schema/permissions/TLS/auth plugin/дата и остальные DB вопросы P0; они не исследовались и не выдумывались в P1. Блокеров завершения **текущего P1** после UI proof нет. Отдельное ограничение — медленный/нестабильный software emulator и отсутствие real-device проверки. **P2 автоматически не начинать: ждать следующего указания пользователя.**

---

## Исторический отчёт P0

Дата: **16.10.2026, Europe/Moscow**. Текущий scope: только P0. Последнее уточнение пользователя: текущая ветка — эталон UI, недостающие функции согласуются отдельно.

**DIRECT_REWRITE_P0_RESULT: POSITIVE** — анализ и семь документов завершены; итоговая статическая верификация прилагается в `evidence/p0-validation.json`. Это результат аудита, не готовности Direct-приложения. После отчёта остановиться и ждать следующего указания. P1 не начат.

## Этапы

Знак «—» означает, что этап не выполнялся и его результат ещё не оценивался; это не POSITIVE и не пропущенная проверка.

| Этап | Дата | Результат | Доказательства / следующий gate |
| --- | --- | --- | --- |
| P0 Audit/архитектура | 16.10.2026 | POSITIVE | Семь документов, точный report, source hashes, evidence; выбран B, references не изменены |
| P1 Изолированный runtime | 16.10.2026 | POSITIVE | Отдельный APK, single-mode, static/unit/build и 2 Android UI tests; evidence/p1 |
| P2 Config/JDBC | 17.10.2026 | LOCAL_PASS / physical unrun | Actual MariaDB10.6.25 schema/CA/JDBC/SELECT1/API30 проверены; детали restored-db |
| P3 Auth/session/access | 17.10.2026 | LOCAL_PASS | Active/wrong-password/access0/real login/cold process restore/logout на restored-schema и original-record read |
| P4 Orders/sections/order | 17.10.2026 | LOCAL_PASS | Both sources/3sections/high IDs, original9-order DTO/SQL order parity; current8fixture sort/pull/scroll checks |
| P5 Details/routes/contacts | 17.10.2026 | LOCAL_PASS / handlers unrun | Restored-schema ambiguity/5history/details и original reads; actual Yandex/dialer/physical не запускались |
| P6 Delivery transaction | 17.10.2026 | LOCAL_PASS | Both InnoDB tables+paired source trigger rollback/commit/idempotency; only reserved rows, original359540 unchanged |
| P7 Phone directory | 17.10.2026 | LOCAL_PASS / physical unrun | Actual restored phones/card/active drivers loaded privately, copy/dial fixtures8checks; no real calls |
| P8 Polling/notifications | 17.10.2026 | PARTIAL_PASS / timing BLOCKED | Actual WorkManager SQL/baseline/dedup/notification/PendingIntent tap; measured15min/Doze/reboot unrun |
| P9 Full UI/navigation | 17.10.2026 | LOCAL_PASS / full visual unrun | Ordinary actual-schema UI+8fixture regression, no API graph/DEX; no running-reference pixel comparison/physical audit |
| P10 E2E/APK/rollback | 17.10.2026 | LOCAL_PASS / physical unrun | Exact configured APK/API30+uploaded-dump schema/data,6actual DB methods; source/private artifacts and original359540 preserved |
| P11 Security/pilot | — | — | Нужны DBA/server isolation и независимые authz-negative tests; DRIVER_ROLLOUT=BLOCKED_SECURITY |

## Файлы P0

Созданы в `/workspace/FFL-direct/docs/direct-rewrite/`:

1. `MASTER_CONTEXT.md` — handoff, ADR-001, reuse/exclusion, C01–C11.
2. `DIRECT_REWRITE_PLAN.md` — P0–P11 со всеми обязательными полями/gates и форматом отчёта.
3. `DIRECT_DB_CONTRACT.md` — таблицы, mapping, auth/access, resolver, parsing, delivery transaction, directory/summary, connection requirements.
4. `DIRECT_UI_CONTRACT.md` — фактический reference UI, обязательные и отсутствующие функции, решения пользователя.
5. `DIRECT_QA_CHECKLIST.md` — критерии доказательств, unit/runtime cases и шаблон отчёта.
6. `SECURITY_RISKS.md` — предварительная модель угроз, server-isolation варианты и rollout gate.
7. `PROGRESS.md` — этот статус и фактические проверки.

Дополнительно: `BACKEND_CONTRACT_REPORT.md` и `sources/MASTER_INSTRUCTIONS.md` — точные копии вложений пользователя; `evidence/` — source hashes, command/verification metadata, логи baseline и XML. Старые tracked файлы не изменены. В новой папке нет Android/Gradle/JDBC runtime, не создана ветка/remote и не выполнен push.

## Что выполнено и что не выполнено

Выполнено: полное чтение 3 872 строк report, source-based аудит текущих Android моделей/UI/navigation/repos/session/ViewModels/Gradle/tests и документации; сверка backend auth/access/resolver/mappers/queries/transaction/directory. Выбрана изоляция B, сохранены минимальные candidates UI reuse, зафиксированы недостающие функции и риски. Реальные схемы/индексы/grants/auth plugin/CA/timezone не выведены из report.

Не выполнено: P1–P11, новый Android app/JDBC, connection tests, device install, новые UI/screens/receivers, любые изменения старой бизнес-логики, DB DDL/UPDATE, security certification, Git push или публикация APK. Report не заменялся собственным пересказом.

## Реально запущенные проверки

Все команды ниже выполнены в P0, кроме явно помеченного предыдущего lint. Логи не выводят значения DB/driver credentials. Метаданные команд/кодов и source audit — в evidence.

| cwd | Команда | Exit | Результат |
| --- | --- | --- | --- |
| `/workspace/FFL` | `git status --short` | 0 | До/после P0 чисто |
| `/workspace/FFL-backend` | `git status --short` | 0 | До/после P0 чисто |
| оба reference roots | `git log -1 --format='%H %s'` | 0 | HEAD совпадает с MASTER_CONTEXT |
| `/workspace` | `sed -n '1,260p'`, `sed -n '261,1300p'`, `sed -n '1301,2500p'`, `sed -n '2501,3872p'` для приложенного report | 0 каждый | Все строки прочитаны; копия byte-identical |
| `/workspace/FFL` | `source /workspace/.ffl/env.sh` затем `bash gradlew --offline --no-daemon --max-workers=4 :app:assembleDebug :app:testDebugUnitTest` | 0 | BUILD SUCCESSFUL, 44 tasks UP-TO-DATE; этот запуск **не** исполнил тест заново |
| `/workspace/FFL` | `source /workspace/.ffl/env.sh` затем `bash gradlew --offline --no-daemon --max-workers=4 :app:testDebugUnitTest --rerun` | 0 | test task реально выполнен: 1 шаблонный тест, 0 failed/errors/skipped; XML текущего запуска |
| `/workspace/FFL-backend` | `/workspace/.ffl/backend-venv/bin/python -m pytest -q --junitxml=/workspace/FFL-direct/docs/direct-rewrite/evidence/backend-tests.xml` | 0 | 102 executed/passed, 0 failed/errors/skipped; 1 dependency deprecation warning |
| `/workspace` | `python3 /workspace/FFL-direct/docs/direct-rewrite/evidence/verify_p0.py` | 0 | Семь docs/source hashes/links/phase fields/reference cleanliness/no runtime проверены; JSON результата сохранён |

Источник подсчётов — fresh XML, а не только консольный exit=0. Android test `addition_isCorrect` проверяет арифметику; он не покрывает login/orders/JDBC. Backend tests используют fixture и monkeypatch БД; они не доказывают live SQL/schema/TLS/права.

Ранее выполненный `:app:lintDebug`: exit=1, 4 Error / 18 Warning, сохранён в `evidence/legacy-lint-prior.xml`. В этом P0 lint **не перезапускался**, ошибки не исправлялись: три NewApi java.time при minSdk24, ContextCastToActivity в Theme. Это известный отрицательный legacy check, не заявляется как passing Direct static/build. `evidence/prior-legacy-checks.json` указывает прежний источник и отличие от fresh checks.

APK reference: `/workspace/FFL/app/build/outputs/apk/debug/app-debug.apk`, package `ru.ffl24.zakaz`, debug. Его SHA-256/размер и сохранённые HEAD — `evidence/p0-validation.json`. В P0 APK на emulator или устройство **не устанавливался**; runtime нового Direct APK не проверялся и такого APK нет.

## Отдельные статусы P0

| Направление | Статус | Область и доказательство |
| --- | --- | --- |
| STATIC_CHECK | POSITIVE | Документальный P0: полнота/ссылки/source mapping/изоляция/no code/source hashes. Legacy lint отдельно NEGATIVE |
| UNIT_TEST | POSITIVE (baseline) | Fresh: Android 1 template + backend 102. Unit tests Direct отсутствуют и не выдаются за выполненные |
| BUILD | POSITIVE (reference) | Старый Android APK build target exit=0 с reuse outputs. Новый Direct build — BLOCKED: runtime не создаётся в P0 |
| EMULATOR_RUNTIME | BLOCKED | Не выполнялся; emulator/аппаратное ускорение не подготовлены, Direct APK нет |
| REAL_DEVICE_RUNTIME | BLOCKED | Устройство не подключено/не тестировалось, Direct APK нет |
| LIVE_DB_READ | BLOCKED | Нет предоставленного endpoint/config/test identity/schema/grants. Никаких живых бизнес-чтений |
| LIVE_DB_WRITE | BLOCKED | Нет одобренного safe test order; live запись запрещена |
| SECURITY_REVIEW | BLOCKED | P11 не выполнен, server-enforced isolation не доказана; S01–S12 задокументированы |

**DRIVER_ROLLOUT: BLOCKED_SECURITY.** P0 POSITIVE не означает permission/готовность публиковать/устанавливать driver APK. Архитектура общего DB логина несёт подтверждённый риск обхода клиентских driver/access checks.

## Проверенные факты

- Обе reference-версии доступны и сохранены; hashes unchanged, рабочие деревья чистые.
- Report source-based, production introspection в нём не было; код backend совпадает с основными описанными SQL/mapping/auth правилами, nuances перечислены в C09/C10.
- Текущий Android полностью API-first: Retrofit factories/startup/me, JWT session, API URL в UI/settings. Direct source/flavor/module/JDBC dependency не найдены.
- Numeric Android IDs несовместимы с source-aware backend identity; DTO не несут новых route/section/contact полей. Не переносить API serializers.
- Current UI содержит search/password/orders cards/details/theme/confirm, но не реализует разделы/drag/directory/polling/contact parser. Пользователь подтвердил эту ветку как reference, недостающее будет согласовано отдельно.
- Дата `2026-05-18` действительно захардкожена; main readiness=status, quantity=col_s, invoice count=col; backend пишет delivery только в два поля.
- route-opened backend не пишет в MySQL; Direct-альтернатива в master — ownership check + no-op, без fallback.

## Неподтверждённые предположения и риски

Не подтверждены: production table definitions/indexes/engine, grants, name_v behavior/permission, legacy password encoding/collation, network/VPN/endpoint/CA/auth plugin, date timezone/NOW, Android compatibility конкретного JDBC driver, фактическая работоспособность старого APK с production, UI для отсутствующих функций, server-enforced driver isolation. Никакие значения secrets не изобретались. Driver release package/signature и окончательная distribution policy не выбраны.

## Готовность к P1

**Готовность документальная: да, для отдельного developer-only каркаса после нового указания пользователя.** Внешняя БД не нужна, чтобы спроектировать и собрать пустой изолированный каркас P1, но без необходимых runtime prerequisites нельзя закрывать P2/P3/P6/P10. Product conflicts не решаются скрыто и должны закрываться к указанным gates.

Следующий агент начинает с MASTER_CONTEXT и PROGRESS, проверяет Git/source hashes, учитывает ответ «текущая ветка — эталон». Не создавать worktree, не удалять reference/rollback, не начинать P1 автоматически, не выполнять DB UPDATE. На этом P0 остановлен по запросу пользователя.

Handoff P0 добавлен в start_skill черновика среды с указателем на MASTER_CONTEXT/PROGRESS и остановкой перед P1; прежние install_script, repositories и reference-инструкции сохранены. Draft save не является публикацией или проверкой восстановления в новой задаче. Документы пока не закоммичены и не отправлены в remote; их наличие доказано в текущем workspace. Для snapshot будущих задач требуется пользовательская публикация среды.

P1 handoff: обновлены install_script и start_skill черновика среды; прежние repositories/requirements сохранены. Полный script: /workspace/.ffl/install-all-p1.sh; Direct-only /workspace/.ffl/p1-install.sh повторно прошёл (exit=0). Service startup с readiness и функциональными проверками записан в start_skill, процессы не считаются переносимыми в новую задачу. Сохранение draft не исполняет script и не публикует среду; новая task restoration не проверена.

После сохранения runtime evidence эмулятор ffl-p1 остановлен для освобождения CPU; были остановлены только процессы этой проверки. APK остаётся в проекте; инструкции повторного запуска сохранены.
