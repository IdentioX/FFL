# Connection layer P2 — developer-only

Только Direct-проект, без API, login, business reads/writes или P3. Итоговые результаты — PROGRESS.md. Отсутствие стенда/физического устройства блокирует закрытие P2 по плану.

## Конфигурация

BuildConfig получает строки из environment, затем из игнорируемого root `local.properties`. Gradle properties/CLI для secrets не используются. Обязательные имена:

- `DIRECT_MYSQL_HOST`: один DNS hostname/IPv4; URL, multi-host и IPv6 пока отклоняются.
- `DIRECT_MYSQL_PORT`: явно 1–65535, без неявного default.
- `DIRECT_MYSQL_DATABASE`: 1–64 ASCII буквы/цифры/`_$-`; другие имена требуют проверки.
- `DIRECT_MYSQL_USER`: ограниченный developer account, не root. Проверка имени не доказывает grants.
- `DIRECT_MYSQL_PASSWORD`: непустой пароль; пробелы и символы сохраняются буквально.
- `DIRECT_MYSQL_USE_SSL`: строго `true`. false/опечатка/пустое значение не включают plaintext.
- Дополнительно `DIRECT_MYSQL_CA_PEM`: явная публичная CA цепочка PEM стенда, в том числе для публичного центра. System trust/deferred identity не используются.

Пустая конфигурация → ConfigurationMissing, частичная/невалидная → ConfigurationInvalid, без сети. Повреждённая/просроченная/non-CA PEM отклоняется на IO до соединения. X.509 parsing/TLS не выполняются на Main.

Пароль передавать через secrets среды или защищённый локальный файл, без чата/CLI history. В Java properties multiline PEM записывается одной строкой с `\n`; backslash в пароле экранируется по синтаксису properties. Environment принимает исходные строки. Gradle экранирует Java string literal, но credentials извлекаются из APK/generated sources/build caches. Сконфигурированный APK и secret build output не публиковать.

## Driver и Android

Официальный `org.mariadb.jdbc:mariadb-java-client:3.5.10`, LGPL-2.1-or-later. URL, SHA256 и сверка опубликованного SHA1 jar/sources/POM двух кандидатов — `evidence/p2/driver-artifacts.json`. Vendor support Android не утверждается.

Также оценён `com.mysql:mysql-connector-j:26.7.0`, GPLv2 with Universal FOSS Exception, protobuf transitives/desktop management/time API. Оба jar преобразованы D8 min-api24 и загружены на ART API30; refused connection → SQLException. Ранний MySQL probe не включал resource bundle и падал: исправлен probe, итог — `driver-art-with-resources.log`. Это не доказательство live handshake/auth/SELECT. MariaDB выбрана как кандидат с меньшим dependency graph и TLS/plugin hooks; совместимость целевого MySQL остаётся gate.

Core library desugaring 2.1.5 нужен для Java time на API24/25. Android java.sql.Connection не имеет setNetworkTimeout; используется driver socketTimeout. SQLType/JDBC4.2 overloads, XA/JMX/pool, JNA/Unix/Windows, LDAP/GSS, IAM, PARSEC этим слоем не поддерживаются. Scoped R8 missing-type directives перечислены в app/proguard-rules.pro; они не доказывают доступность этих API. R8 не отключён. Minified artifact требует отдельного runtime proof.

Драйвер выбирается явно, без DriverManager discovery иных транспортов. Vendor logging отключается до driver classes. Auth allowlist: mysql_native_password,caching_sha2_password. Clear-password/GSS/PAM/другие plugins не разрешаются; RSA key retrieval/redirect отключены. Plugin реальной базы неизвестен.

TLS: verify-full, explicit CA и AndroidTlsSocketPlugin через ServiceLoader. Trust store строится только из указанной CA; layered SSLSocket использует исходное имя сервера и SSLParameters.endpointIdentificationAlgorithm=HTTPS. Платформа проверяет CA/hostname при startHandshake. Это заменяет отсутствующий на Android desktop LDAP CN parser, сохраняя проверку. Разрешены только TLS1.2/1.3, пересечение с поддерживаемыми платформой. mTLS пока не реализован. Manifest usesCleartextTraffic сам по себе не защищает JDBC; защита обеспечивается конфигурацией/плагином.

## IO, ресурсы, ошибки

Composition root валидирует строки без сети. UI запускает suspend probe из LaunchedEffect; blocking JDBC/CA/TLS идут через runInterruptible(Dispatchers.IO). Общий для graph/activity Semaphore ограничивает основные операции двумя соединениями. Connection не сохраняется singleton и не выходит из scope. ResultSet → PreparedStatement → Connection закрываются use при успехе/ошибке/отмене. Повтор только по кнопке; automatic retries отсутствуют.

Connect 3000ms, socket 5000ms, query 3s, coroutine operation budget 15000ms, включая очередь Semaphore. Нулевые/отрицательные timeout запрещены. Android DNS/native JDBC могут не прерываться от cancellation; budget не является обещанием жёсткой длительности. Слот удерживается до выхода/закрытия ресурса. Unit tests моделируют позднее возвращение Connection после отмены и обязательное закрытие; все native paths ими не доказаны.

Statement.setQueryTimeout драйвера на MySQL может открыть служебное соединение для KILL QUERY своего запроса. До двух основных и двух служебных соединений, без пула; это не business UPDATE. Реальные permissions/latency/query-timeout/close paths требуют стенда. Поведение частично открытого socket внутри драйвера и физической потери сети не доказано proxies. Никаких writes/unknown commit retry в P2 нет.

Единственный SQL приложения — SELECT 1, maxRows=1, readOnly connection. Arbitrary SQL callback не опубликован. Reachable означает только технический probe, не login/business grants/readiness. UI получает фиксированные строки; exceptions/causes/SQL/endpoint/account не передаются и не логируются. Caller cancellation сохраняется; локальный timeout возвращается безопасной категорией.

## Android checks без MySQL

ConnectionLayerTest использует локальный wire fixture: MySQL greeting → TLS → закрытие **до обработки auth/query**. tools/p2_tls_fixture.py не является MySQL, не выполняет SELECT и не читает app config. Ключи генерируются вне репозитория с umask077; tests содержат только публичную CA. Это проверка настоящего JDBC/TLS на ART без выдуманного live DB PASS.

Запустить fixture в отдельной сессии:

```bash
source /workspace/.ffl/env.sh
cd /workspace/FFL-direct
python3 tools/p2_tls_fixture.py --directory /workspace/.ffl/p2-fixture
```

После четырёх listening (эмулятор должен быть полностью загружен):

```bash
cp /workspace/.ffl/p2-fixture/ca.pem app/src/test/resources/fixture-ca.pem
cp /workspace/.ffl/p2-fixture/ca.pem app/src/androidTest/assets/fixture-ca.pem
adb -s emulator-5556 reverse tcp:19061 tcp:19061
adb -s emulator-5556 reverse tcp:19062 tcp:19062
adb -s emulator-5556 reverse tcp:19063 tcp:19063
adb -s emulator-5556 reverse tcp:19064 tcp:19064
bash gradlew --no-daemon --max-workers=2 :app:connectedDebugAndroidTest
```

Публичную CA копировать при регенерации fixture; production config не подменять. UI smoke suite требует APK без DIRECT_MYSQL_* config. Tests требуют работающего fixture и точной TLS ошибки; отсутствие fixture не считается PASS. StrictMode проверяет suspend probe из Main, без runBlocking на Main.

Для закрытия P2 нужны согласованная MySQL: real SELECT1, версия/engine/grants/auth plugin, TLS-negatives, timeouts/cancel/closure, лимит соединений на emulator и физическом устройстве. API24/target36 runtime и minified-runtime отдельно не доказаны. P3 не начинать; DRIVER_ROLLOUT=BLOCKED_SECURITY.
