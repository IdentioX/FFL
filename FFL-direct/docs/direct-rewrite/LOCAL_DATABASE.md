# Локальная DB-среда без рабочей БД

**Актуально 17.10.2026: 0.2.2-dev/code4, итоговый engineering audit.** Основной источник текущих результатов: [FINAL_TEST_RESULTS](../FINAL_TEST_RESULTS.md), [RELEASE_READINESS](../RELEASE_READINESS.md) и верхний раздел PROGRESS. Предыдущие counts/ограничения ниже относятся к историческим запускам, если расходятся с итоговым отчётом.843 JVM,29 public Android,11 restored DB,2 independent minified checks; physical/Windows/Doze остаются непроверенными. DRIVER_ROLLOUT=BLOCKED_SECURITY.

Для существующего owned state выполните `harden-grants` перед `prepare-restored/build`: SELECT только7таблиц, отдельный private read-only integrity auditor, test config обновляется prepare-restored. Новый baseline — `docs/evidence/final-audit/clean-restore-baseline.json`, независимый чистый импорт101таблица/359540строк. `check_restored_integrity.py --create-baseline` допускается только до seed; текущую seeded копию baseline не подменять. Paired BF fixture reset исправлен без изменения серверных triggers. Не удаляйте существующий state/volume ради смены профиля — выбирайте другой state/порт.


Актуализация после получения пяти ZIP-частей: блокер transfer limit снят. Все SHA проверены, полный SQL52,606,346bytes восстановлен в отдельной MariaDB10.6.25. Структура101таблица и исходные данные доступны; реальная проверка DTO/SQL на Android уже выполнена. В локальной копии исправлены только владельцы routines/triggers (исходный dump не меняется), добавлены зарезервированные fictional records. Текущие результаты и границы — PROGRESS.md / RESTORED_DATABASE.md; прежние разделы о недоступном дампе ниже исторические.

Стенд для Direct-проекта, без FastAPI. Требуются Python3.11+, Docker Engine/Desktop с Linux containers и OpenSSL (в Windows подходит OpenSSL из Git for Windows). Скрипт `tools/dev_database.py` использует официальный MariaDB10.6.25 image с закреплённым digest, совпадающий по основной версии с заголовком предоставленного дампа. Данные, CA/ключи, случайные пароли, private APK/generated sources и логи находятся вне checkout: Linux — `/workspace/.ffl/direct-dev` при текущем размещении проекта, Windows — `%LOCALAPPDATA%\FFLDriverDirect`. Альтернативный каталог задаётся `FFL_DEV_STATE_DIR`.

## Начало и повторный запуск

```bash
python3 tools/dev_database.py up
python3 tools/dev_database.py status
python3 tools/dev_database.py stop
python3 tools/dev_database.py start
```

На Windows замените `python3` на `python`. Первый `up` создаёт самостоятельную synthetic БД с вымышленными водителями97/98, датой2026-05-18 и заказами обеих таблиц. Это не копия вашей БД. Повторный `up`/`start` сохраняет существующие данные. Named volume не удаляется при stop. Если cloud не сохранил вообще никаких Docker-ресурсов, synthetic профиль создаётся заново; если сохранился только volume без container, скрипт отказывается автоматически его перезаписывать. Для restored-dump при утрате ресурсов требуется тот же private dump.

Порт привязан только к127.0.0.1:19065; TLS обязателен, явная CA, hostname localhost, без отключения verification. Приложение использует отдельный account с SELECT, EXECUTE name_v и UPDATE только st_vv/date_att_log. Root остаётся внутри этого локального контейнера и используется для bootstrap/import. Клиентские ограничения строк не заменяют серверное разграничение водителей.

`status` действительно выполняет SELECT1 и чтение трёх таблиц через TLS с hostname verification; одних PID/порта недостаточно. Он выводит только технический статус и количества, без имён/паролей/заказов. Скрипт проверяет ownership label перед stop/destroy; произвольный существующий контейнер не трогает.

## Настроенный Android runtime

Linux в этой среде перед каждой Gradle/adb командой:

```bash
source /workspace/.ffl/env.sh
cd /workspace/FFL-direct
python3 tools/dev_database.py build
adb devices
python3 tools/dev_database.py android --serial emulator-5558
```

Эмулятор должен быть запущен и иметь работающий package manager. В Windows используйте serial из `adb devices`, обычно emulator-5554. `android` настраивает adb reverse, устанавливает основной и test APK и запускает обычный MainActivity. Успех команды запуска ещё не доказывает отрисовку; её проверяют UI tests.

`build` передаёт конфигурацию через environment дочернего Gradle без значений в argv. `FFL_DIRECT_BUILD_DIR` отправляет generated sources/BuildConfig/APK в private state, не в обычный app/build. Основной APK содержит настоящий DIRECT_MYSQL BuildConfig и используется обычным DirectApplication; форма backend IP не появляется. Никакой fake/DI graph для обычного запуска не нужен. Этот private APK содержит извлекаемый одноразовый локальный пароль, не публикуйте его как готовую рабочую сборку. Публичный ранее выданный APK без конфигурации остаётся отдельным артефактом.

Synthetic тестовый водитель:97, пароль `driver-fixture-only` (заведомо вымышленный, не пароль DB). В настройках выберите2026-05-18. Запуск `test` делает это через UI.

```bash
python3 tools/dev_database.py test --serial emulator-5558
```

Этот режим разрешён только synthetic profile. Он очищает локальные данные только ru.ffl24.direct.dev на выбранном тестовом эмуляторе, устанавливает точный private APK, выполняет четыре отдельных instrumentation runs:

1. Обычные MainActivity/DirectApplication/BuildConfig без injected graph: active list, wrong password, login, рабочая дата через настройки, оба рейса/delivered, details, «Сдал», проверка MySQL, directory.
2. Между runs выполняет force-stop процесса приложения, затем проверяет восстановление persisted session/date и logout.
3. Настоящий OneTimeWorkRequest с production SecondRouteWorker: summary SQL/baseline, новое source ID, системное уведомление, dedup и нажатие PendingIntent во второй рейс. Это не измерение15мин/Doze.
4. Direct JDBC/TLS/SELECT1/auth/reads/summary, обе транзакции, injected failure после настоящего UPDATE/rollback, idempotency и owner/access guards.

Свежие XML/логи в private `test-results`; skips/failures и отсутствие OK не считаются PASS. Перед транзакционным тестом сбрасываются только два поля собственных synthetic row1. StartupTest с требованием пустого BuildConfig выполняется отдельно на обычном unconfigured APK, смешивать его с configured APK нельзя. Все tests на физическом телефоне/Windows/Doze отдельно от emulator/Linux.

## Импорт предоставленного дампа

Файл пользователя сейчас НЕ получен: downloader отказал из-за лимита32МиБ. Ни schema parity, ни restored-user-data E2E этим документом не объявляются выполненными. `.sql.gz` и `.zip` импортёр проверены на вымышленной SQL-выгрузке. Для полного дампа поддерживается также `.sql`; ZIP должен содержать ровно один SQL. Поток не извлекается в checkout; лимит распакованного SQL8GiB. Сжатый файл следует держать вне Git/публичных артефактов.

Чтобы использовать другой профиль, сначала сохраните нужные локальные результаты. Команда ниже удаляет ТОЛЬКО собственный development container/volume/state, не исходный dump и не production:

```bash
python3 tools/dev_database.py destroy --confirm-owned-data-loss
python3 tools/dev_database.py up --dump /private/path/database.sql.gz
python3 tools/dev_database.py status
python3 tools/dev_database.py build
python3 tools/dev_database.py android --serial emulator-5558
```

После импорта ожидается ровно одна non-system DB с name_v, __hr_typepos, reys/reys_den и нужными columns. Название выбирается из реально восстановленных DB, существующие dump credentials не переиспользуются. Несовпадение схемы/DEFINER/отсутствующие routines должны быть исследованы по private import log на настоящем файле; автоматически выдумывать структуру или ослаблять TLS скрипт не пытается. При прерванном импорте повтор не объявляет частичную базу готовой.

Импортёр запускает SQL только в принадлежащем этому стенду контейнере. SQL/root diagnostics могут содержать персональные данные, поэтому остаются private; не публикуйте их целиком. Автоматические synthetic write cases на restored profile не выполняются: сначала нужно изучить реальную схему и подготовить отдельные обезличенные test records. Production endpoint/grants/данные не используются.

## Windows PowerShell

Установите Python3.11+, Docker Desktop (Linux containers), Git for Windows, JDK21 и Android Studio SDK36.1/build-tools36.0.0/platform-tools; создайте AVD. Укажите свои JAVA_HOME/ANDROID_HOME и добавьте JDK/bin и platform-tools в PATH. Затем из корня Direct-проекта:

```powershell
python tools/dev_database.py up
python tools/dev_database.py build
adb devices
python tools/dev_database.py test --serial emulator-5554
python tools/dev_database.py android --serial emulator-5554
python tools/dev_database.py stop
python tools/dev_database.py start
```

Скрипт использует gradlew.bat и OpenSSL из PATH либо Git/usr/bin/openssl.exe. Windows-команды подготовлены по этому коду, но на Windows в облачной Linux-среде не выполнялись. TLS/Android/restore фактические результаты Linux — PROGRESS.md и evidence/db-integration. Не переносите Linux env.sh на Windows и не добавляйте private state/дамп/настроенный APK в репозиторий.
