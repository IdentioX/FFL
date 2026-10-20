# Готовность FFL Driver Direct 0.2.2-dev

**DEVELOPER_TEST_READY** — разработка и внутренние проверки на изолированной копии БД.

Приложение самостоятельно работает по цепочке Compose → repositories → JDBC/TLS → MariaDB. FastAPI, JWT, backend IP setup и HTTP fallback отсутствуют. Выполнены авторизация, оба рейса/сданные, детали/контакты/маршруты, подтверждение сдачи, справочник, локальные настройки и polling второго рейса.

Это не ONE_DEVICE_PILOT_READY: физический телефон и его сеть не проверены. Это не DRIVER_ROLLOUT_READY: общий DB account позволяет читать parol/чужие строки и менять разрешённые delivery columns вне штатного клиента. Найденное ограничение воспроизведено только на собственной копии и fictional rollback probe.

## Что исправлено и подтверждено

Исправлены гонки восстановления/выхода и superseded login, устаревший access snapshot внутри транзакции, неточное сравнение пароля из-за collation, N+1 контактов, классификация ошибок записи, concurrent notification seen, узкие экраны/крупный шрифт и контраст тёмной темы. Права SELECT приложения сужены до семи нужных таблиц. Реальный startup crash R8 из-за удалённого WorkDatabase_Impl constructor закрыт узким ProGuard rule.

Точные итоговые counts, SHA, свежие XML и ограничения приведены в [FINAL_TEST_RESULTS](FINAL_TEST_RESULTS.md) и `evidence/final-audit/validation.json`. Основной подтверждённый объём: 843 JVM-теста; отдельные Android категории public UI/TLS, restored DB и независимый R8 smoke. Неуспешные попытки сохранены. Все исходные 359 540 строк в 101 таблице сверяются с новым независимым чистым импортом; legacy Android/backend и 104 reference файла не изменяются.

## Какие артефакты использовать

| Артефакт | Назначение |
| --- | --- |
| `releases/FFL-Driver-Direct-0.2.2-dev.apk` | Публичный debug APK, без DB credentials. Можно установить для проверки запуска. Без собственной build-конфигурации показывает «Подключение не настроено», форму backend IP не показывает |
| `releases/FFL-Driver-Direct-0.2.2-dev-source.zip` | Полный переносимый исходный проект с wrapper, tools, tests, docs/evidence; без build cache/дампа/ключей/configured APK |
| `releases/ARTIFACTS-0.2.2.json`, `SHA256SUMS-0.2.2.txt` | Размеры, hashes и назначение артефактов |
| Private `state/artifacts/configured-local.apk` | Реально проверенная рабочая сборка для локального restored стенда. Содержит извлекаемый локальный DB password, в публичные файлы не включена |
| Private `state/artifacts/configured-local-minified-signed.apk` | Non-debuggable R8 сборка, локально подписана debug key исключительно для внутреннего теста |

Одинаковый applicationId `ru.ffl24.direct.dev` для этих вариантов: при замене APK сохраняйте нужные данные своего тестового приложения. Legacy package не подменяется. Production applicationId/key/distribution policy не назначены. Старые versioned артефакты сохранены.

## Запуск с нуля и повторная проверка

Исходники распаковать из source ZIP; использовать JDK21, SDK36.1/build-tools36.0.0 и wrapper. Установить Python3.11+, Docker Engine/Desktop и OpenSSL. Проверка целостности отдельно использует PyMySQL из `tools/requirements-db-check.txt`. Интернет нужен для первоначального verified dependency/image resolution; `--offline` годится при сохранённом cache.

Предоставленные ZIP-части хранить приватно. Собрать один gzip kit00 `assemble_dump.py`, сверить kit manifest и SQL SHA. В этой среде input уже сохранён в `/workspace/.ffl/restore-source`; повторное скачивание не требуется. Source ZIP намеренно не включает персональную БД. Если личные части потеряны, только исходного проекта недостаточно для восстановления настоящих данных.

```bash
cd /workspace/FFL-direct
source /workspace/.ffl/env.sh  # Только в подготовленной Linux-среде
export FFL_DEV_STATE_DIR=/workspace/.ffl/direct-restored
# Только если данного state/container/volume ещё нет:
python3 tools/dev_database.py up --dump /private/path/112.sql.gz --port 19066
# Для существующего owned state вместо up: start
python3 tools/dev_database.py harden-grants
python3 tools/dev_database.py status
python3 tools/dev_database.py prepare-restored
python3 tools/dev_database.py build --include-release
python3 tools/dev_database.py test-restored --serial emulator-5558
python3 tools/run_minified_audit.py --serial emulator-5558 --debug-keystore /workspace/.ffl/android-user/debug.keystore
```

Выделенный emulator должен загрузиться до работоспособных package/activity services. Команда запуска retained AVD и Windows PowerShell-инструкции — [RESTORED_DATABASE](direct-rewrite/RESTORED_DATABASE.md); живой процесс не предполагается сохранённым. На новом компьютере задайте свой SDK/JAVA_HOME и создайте AVD через SDK Manager; Linux пути не переносите буквально. Windows сценарий подготовлен, исполнение на Windows не заявлено.

Реквизиты/CA helper генерирует в private state и передаёт в Gradle environment без значений в argv. Импорт автоматически локализует отсутствующие DEFINER только в своей копии, сохраняя SQL bodies. Existing orphan volume helper не перезаписывает. Не удаляйте основной state ради смены профиля: используйте отдельный FFL_DEV_STATE_DIR и свободный loopback port.

Manual UI: `dev_database.py android --serial ...`; искать `FFL E2E`, пароль fictional `ffl-e2e-only-42`, в настройках дата `2099-12-31`. Helper tests очищают только package выделенного emulator, сбрасывают только заранее выделенные fictional rows. Настроенный APK не отправлять водителям.

Обычные public build/JVM/lint/runtime checks — README. Полные 29 public UI/TLS checks требуют отдельной loopback wire fixture и совпадающего test CA; порядок — FINAL_TEST_RESULTS. `verify_final_audit.py` проверяет собранные доказательства, не запускает тесты и не является универсальной приёмкой другой версии/машины. Изменения кода требуют новых тестов и нового output evidence, а не подмены hashes в старом отчёте.

## Перед пилотом и использованием водителями

Нужны серверно-принудительная изоляция водителей, закрытый parol и отзываемая сессия; минимальный вариант на имеющемся PHP/HTTPS-хостинге без отдельного VPS описан в [REMAINING_RISKS](REMAINING_RISKS.md). Возможности текущего хостинга ещё не подтверждены, gateway в Direct runtime не добавлен.

После этого: один конкретный телефон и сеть/CA, реальные Яндекс/набор номера, notification permission/Doze/reboot, проверка UNKNOWN и operational rollback; далее нагрузка, production signing и безопасное распространение. Production DB/credentials этим аудитом не использовались. Неизвестные не скрыты за общим функциональным PASS.

Полный инженерный разбор: [FINAL_ENGINEERING_AUDIT](FINAL_ENGINEERING_AUDIT.md). Файлы: `evidence/final-audit/files.md`. Текущий статус этапов: [PROGRESS](direct-rewrite/PROGRESS.md). Сохранённый environment draft требует отдельного review/save/publish в настройках среды; сохранение текста не означает публикацию или проверенный fresh-task restore.
