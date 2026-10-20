# Driver loaded explicitly; configuration parses property names through reflection.
-keep class org.mariadb.jdbc.Driver { public <init>(); *; }
-keep class org.mariadb.jdbc.Configuration { *; }
-keep class org.mariadb.jdbc.Configuration$Builder { *; }
# Preserve JDBC service provider names/constructors (no pooling/JMX/HTTP code is called).
-keep class * implements org.mariadb.jdbc.plugin.AuthenticationPluginFactory { public <init>(); }
-keep class * implements org.mariadb.jdbc.plugin.TlsSocketPlugin { public <init>(); }
-keep class * implements org.mariadb.jdbc.plugin.Codec { public <init>(); }

# Room opens WorkManager's generated database through a reflected no-arg
# constructor. R8 can otherwise retain the class name but remove the constructor,
# crashing InitializationProvider before MainActivity starts.
-keep class androidx.work.impl.WorkDatabase_Impl { <init>(); }

# Optional desktop paths in the official JDBC jar, unavailable/unused by this app:
# JNA sockets/Windows auth, JMX/pools/XA, LDAP CN fallback, GSS/Kerberos,
# PARSEC/BouncyCastle, SLF4J and AWS IAM. No synthetic stubs or trust bypass.
# The app accepts a single TCP endpoint, two password plugins, no pool and
# ffl-android TLS only. These directives suppress missing optional types, they
# do not establish Android support: actual JDBC/TLS tests are still required.
-dontwarn com.sun.jna.**
-dontwarn java.lang.management.ManagementFactory
-dontwarn javax.management.**
-dontwarn javax.naming.**
-dontwarn javax.security.auth.login.LoginContext
-dontwarn javax.sql.XAConnection
-dontwarn org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
-dontwarn org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn software.amazon.awssdk.**
-dontwarn waffle.windows.auth.**
# JDBC 4.2 overloads not used by SELECT 1 / JDBC int/string binding; Android's
# java.sql lacks SQLType. Calling these overloads is explicitly unsupported.
-dontwarn java.sql.SQLType
