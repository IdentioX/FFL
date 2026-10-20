# Восстановленная база FFL — воспроизводимая тестовая копия

**Актуально 17.10.2026: 0.2.2-dev/code4, итоговый engineering audit.** Основной источник текущих результатов: [FINAL_TEST_RESULTS](../FINAL_TEST_RESULTS.md), [RELEASE_READINESS](../RELEASE_READINESS.md) и верхний раздел PROGRESS. Предыдущие counts/ограничения ниже относятся к историческим запускам, если расходятся с итоговым отчётом.843 JVM,29 public Android,11 restored DB,2 independent minified checks; physical/Windows/Doze остаются непроверенными. DRIVER_ROLLOUT=BLOCKED_SECURITY.

Для существующего owned state выполните `harden-grants` перед `prepare-restored/build`: SELECT только7таблиц, отдельный private read-only integrity auditor, test config обновляется prepare-restored. Новый baseline — `docs/evidence/final-audit/clean-restore-baseline.json`, независимый чистый импорт101таблица/359540строк. `check_restored_integrity.py --create-baseline` допускается только до seed; текущую seeded копию baseline не подменять. Paired BF fixture reset исправлен без изменения серверных triggers. Не удаляйте существующий state/volume ради смены профиля — выбирайте другой state/порт.


Получены kit00 и все5 ZIP-частей. Это части одного gzip, а не пять SQL-файлов. Проверены SHA каждого ZIP/сегмента, полного gzip и исходного SQL. Восстановленный SQL:52,606,346bytes, SHA256 `04e481bdfa30e286e0b568e999763fab8fcf0cc91b192f564f74a039ee9e8c55`. Исходные байты не изменены.

Private input `/workspace/.ffl/restore-source` содержит kit, части,52MB SQL и9MB gzip, directory700/files600. Эти файлы содержат персональные данные и пароли, не входят в Git, публичные evidence или source ZIP.

## Среда

Отдельный owner-labelled container/volume, официальный MariaDB10.6.25 с закреплённым digest, loopback127.0.0.1:19066, require-secure-transport и собственная CA/hostname localhost. Основной private state — `/workspace/.ffl/direct-restored`. Synthetic стенд19065 отдельный. Ни одного подключения к production не требуется и не выполняется.

Полный импорт содержит101таблицу:54водителя (27 access1),26,802reys и1,882reys_den до добавления test records. reys/reys_den, reys_log/reys_bf — InnoDB. Отдельный повторный импорт подтвердил воспроизводимость и дал fingerprint359,540исходных строк во всех101таблицах; вспомогательная копия удалена после измерения. В public evidence только количества/хеши/техническая схема, без plaintext rows.

```bash
source /workspace/.ffl/env.sh
cd /workspace/FFL-direct
export FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored
python3 tools/dev_database.py start
python3 tools/dev_database.py status
python3 tools/dev_database.py prepare-restored
python3 tools/dev_database.py build
python3 tools/dev_database.py test-restored --serial emulator-5558
```

`start` сохраняет существующие данные. Если Docker volume/container утрачены, восстановите из сохранённого private gzip:

```bash
python3 tools/dev_database.py up --dump /workspace/.ffl/restore-source/112.sql.gz --port 19066
python3 tools/dev_database.py prepare-restored
```

Не меняйте профиль существующего synthetic state и не удаляйте данные ради запуска. При неизвестном orphan volume скрипт откажется автоматически перезаписывать его. Все stop/destroy проверяют owner label; destroy не является обычным шагом запуска. Повторная инициализация при отсутствии volume заново создаёт только свой test scope; исходный dump остаётся вне state.

## Найденные отличия от упрощённой fixture

1. Четыре routines и пять UPDATE triggers имели DEFINER исходного сервера. Такой account отсутствует в новом контейнере: сначала name_v, затем UPDATE возвращали1449. Helper после полного исходного импорта перепривязывает эти объекты к локальному root-владельцу. SHOW CREATE/body/SQL mode/charset/attributes проверяются до/после; различается только DEFINER. Источник не редактируется. Root никогда не включается в APK; app account имеет SELECT и только нужные EXECUTE/UPDATE columns.
2. Триггеры ведут reys_log и могут DELETE/INSERT в reys_bf. Ограничение приложения «SET только st_vv/date_att_log» не означает отсутствия серверных side effects. Триггеры не отключаются. Отдельный paired test включает bf_vv, проверяет rollback созданной связанной строки и отсутствие повторного INSERT при идемпотентной сдаче.
3. В meneger_contr_ffl уникален nomer_z. Для неоднозначного match используются два допустимых raw значения с различием ведущего пробела, одинаковые после контрактного TRIM. Индекс не удалялся и структура не ослаблялась.
4. Реальная reys имеет дополнительные поля/NOT NULL, в частности p. Seed указывает их явно; оригинальные columns/indexes/engine остаются исходными.
5. На UI закрытии Activity обнаружен runtime crash SlotWriter/SlotTable в Compose1.10.4. BOM поднят до2026.03.01 (согласованные runtime/ui/foundation1.10.6) в той же patch-ветке, приложение0.2.1-dev. Итог новой runtime-проверки см. PROGRESS; сам dependency upgrade не считается доказательством исправления.

`localize-routines` повторяет локальную перепривязку существующей копии. Client delimiter ограничен длиной<=15bytes; ошибка длинного delimiter выявлена и исправлена, исходные definitions сохранены private. Объектные операции выполняются только после проверки принадлежности контейнера. Оригинальные production credentials не создаются и не переиспользуются.

## Тестовые записи и границы записи

`prepare-restored` сначала проверяет свободный ID range>=2,000,000,000. В настоящие восстановленные таблицы добавляются отдельные fictional driver/disabled/history, заказы обеих таблиц, контакты/manager/phone. Дата2099-12-31, имя для поиска `FFL E2E`, пароль водителя `ffl-e2e-only-42` — вымышленный. Для paired trigger отдельный заказ2000000020/date2000-01-01. Это не новые runtime tables и не использование реальных заказов для записи.

State хранит owner-scoped test manifest. Режим `test-restored` не равен generic `test`: он разрешает изменения только этих выделенных записей. Сбрасываются только их два delivery поля; derived BF строки удаляются только по зарезервированному fictional prefix. Original-row fingerprints сравниваются с независимым чистым импортом, а raw diagnostics с возможными именами/телефонами остаются private.

Основной APK собирается с реальными локальными BuildConfig host/port/DB/CA/account, MainActivity/DirectApplication не подменяются. Артефакты и generated sources — private state/android-build и state/artifacts. Отдельный Gradle project cache и копии APK защищают их от stale-output удаления обычной сборкой. `android --serial ...` устанавливает этот точный APK и настраивает adb reverse. Сертификат проверяется, plaintext fallback отсутствует.

## Проверки

Шесть отдельных instrumentation methods с текущим compiled configuration:

- Read-only реальные исходные записи: login/query внутри private памяти, DTO/source IDs/SQL order/summary/details/directory parity; имена/контакты/пароли не публикуются и не сохраняются в evidence.
- Обычный UI: поиск тестового водителя, wrong password/login, явная дата, оба рейса/delivered, детали/адрес, «Сдал» и проверка серверной строки, справочник.
- Force-stop между runs: восстановление session/date и logout.
- Controlled actual-schema cases: access0/owner gate, ambiguous resolver,5history, оба delivery commit/repeat и injected failure после настоящего UPDATE/rollback.
- Paired trigger: rollback новой BF строки, commit и repeat без её дублирования.
- Настоящий WorkManager OneTimeWorkRequest/SecondRouteWorker: summary→baseline→новый source ID→Android notification→dedup→реальный PendingIntent/MainActivity/второй рейс. Это не измерение периодического15мин/Doze/reboot.

Для PendingIntent используется отдельный plain instrumentation test: его handoff Activity/task не управляется ActivityScenario. Полезные assertions worker/SQL/notification/tap не удалены. Ранее ActivityScenario teardown застревал в PAUSED на software AVD; ошибки/ANR и неподтверждённые результаты сохранены отдельно. Любой crash/отсутствующий свежий XML/skip — не PASS. Raw logs/XML с возможным PII остаются в private test-results/history; публичный summary содержит только class/method/counts/commands/booleans.

Точные актуальные PASS/FAIL/BLOCKED, APK SHA и неизвестные — PROGRESS.md/evidence/restored-db. Старые831 JVM/28 synthetic Android checks не выдаются за этот actual-schema E2E. Physical device, Windows execution, minified runtime, реальные Яндекс/звонки и measured background отдельно от API30 Linux emulator.

## Windows

Требования и toolchain — LOCAL_DATABASE.md. Скачайте свои6архивов в private папку; распакуйте только kit00, прочитайте README, выполните `python assemble_dump.py`. Исходный SQL и gzip должны получить указанный выше SHA. После Docker Desktop/JDK21/SDK/adb/OpenSSL:

```powershell
cd C:\path\FFL-direct
$env:FFL_DEV_STATE_DIR = "$env:LOCALAPPDATA\FFLDriverDirectRestored"
python tools/dev_database.py up --dump C:\private\112.sql.gz --port 19066
python tools/dev_database.py prepare-restored
python tools/dev_database.py build
adb devices
python tools/dev_database.py test-restored --serial emulator-5554
python tools/dev_database.py android --serial emulator-5554
```

Не используйте cloud host как Windows MySQL address. Локальные Docker+adb reverse+собственная CA/config воспроизводят стенд без рабочей БД. Windows команды подготовлены, но на Windows не выполнены. Source ZIP не содержит вашу БД, поэтому нужны ваши private архивы. Для следующей сессии `start/status` сохраняют volume; пересоздание без dump не обещается. Полный direct-client security/серверное row isolation и массовый driver rollout этими тестами не сертифицированы.

## Сверка исходных данных

Read-only checker использует только TLS и печатает aggregate fingerprints, не строки. В этой среде библиотека PyMySQL1.2.3 уже есть в backend venv; backend сервис не запускается. Для другого Python установите `tools/requirements-db-check.txt`.

```bash
/workspace/.ffl/backend-venv/bin/python tools/check_restored_integrity.py --state /workspace/.ffl/direct-restored --baseline docs/direct-rewrite/evidence/restored-db/all-tables-baseline.json --output docs/direct-rewrite/evidence/restored-db/original-data-integrity.json
```

Baseline получен независимым полным импортом того же SHA SQL;101таблица/359,540строк. Для сравнения исключены только заранее свободный зарезервированный ID range и fictional prefix, отсутствующий в исходном dump; audit/BF связанные записи распознаются по этому prefix. Остальные исходные строки должны совпасть по числу и SHA. Проверка выполнена после Android-тестов, scoped privilege probes и stop/start.
