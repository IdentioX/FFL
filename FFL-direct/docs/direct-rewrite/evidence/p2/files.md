# Изменённые файлы P2

- MODIFIED: `README.md`
- MODIFIED: `app/build.gradle.kts`
- ADDED: `app/proguard-rules.pro`
- ADDED: `app/src/androidTest/assets/fixture-ca.pem`
- MODIFIED: `app/src/androidTest/java/ru/ffl24/direct/StartupTest.kt`
- ADDED: `app/src/androidTest/java/ru/ffl24/direct/data/mysql/ConnectionLayerTest.kt`
- MODIFIED: `app/src/main/AndroidManifest.xml`
- MODIFIED: `app/src/main/java/ru/ffl24/direct/AppGraph.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/DirectStartupRepository.kt`
- REMOVED: `app/src/main/java/ru/ffl24/direct/data/UnconfiguredStartupRepository.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/AndroidTlsSocketPlugin.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/ConnectionPolicy.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/DirectDbError.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/DirectMySqlConfig.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/MySqlConnectionFactory.kt`
- ADDED: `app/src/main/java/ru/ffl24/direct/data/mysql/MySqlConnectionProbe.kt`
- MODIFIED: `app/src/main/java/ru/ffl24/direct/domain/StartupRepository.kt`
- MODIFIED: `app/src/main/java/ru/ffl24/direct/ui/DirectApp.kt`
- MODIFIED: `app/src/main/res/values/strings.xml`
- ADDED: `app/src/main/resources/META-INF/services/org.mariadb.jdbc.plugin.TlsSocketPlugin`
- MODIFIED: `app/src/test/java/ru/ffl24/direct/AppGraphTest.kt`
- ADDED: `app/src/test/java/ru/ffl24/direct/data/mysql/DirectDbErrorTest.kt`
- ADDED: `app/src/test/java/ru/ffl24/direct/data/mysql/DirectMySqlConfigTest.kt`
- ADDED: `app/src/test/java/ru/ffl24/direct/data/mysql/MySqlConnectionProbeTest.kt`
- ADDED: `app/src/test/resources/fixture-ca.pem`
- MODIFIED: `docs/direct-rewrite/DIRECT_REWRITE_PLAN.md`
- MODIFIED: `docs/direct-rewrite/MASTER_CONTEXT.md`
- ADDED: `docs/direct-rewrite/MYSQL_CONNECTION_LAYER.md`
- MODIFIED: `docs/direct-rewrite/PROGRESS.md`
- MODIFIED: `docs/direct-rewrite/SECURITY_RISKS.md`
- MODIFIED: `gradle/libs.versions.toml`
- ADDED: `tools/p2_tls_fixture.py`
- ADDED: `tools/verify_p2.py`
- ADDED: `tools/verify_p2_buildconfig.py`

Evidence: новые файлы в docs/direct-rewrite/evidence/p2; прежние P0/P1 evidence не изменены.
