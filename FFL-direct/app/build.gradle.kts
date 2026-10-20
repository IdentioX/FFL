import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Configured local-test APKs contain disposable credentials. Keep their generated
// sources, reports and APK outside the checkout and ordinary shareable outputs.
providers.environmentVariable("FFL_DIRECT_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}

// Only environment or ignored local.properties; never print values or accept URL options.
val directLocalProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
fun directValue(name: String): String = providers.environmentVariable(name)
    .orElse(providers.provider { directLocalProperties.getProperty(name, "") }).get()
fun javaString(value: String): String = "\"" + value.map { c ->
    when (c) {
        '\\' -> "\\\\"
        '"' -> "\\\""
        '\n' -> "\\n"
        '\r' -> "\\r"
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\u000c' -> "\\f"
        else -> if (c.code < 32) "\\u%04x".format(c.code) else c.toString()
    }
}.joinToString("") + "\""

android {
    namespace = "ru.ffl24.direct"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }
    defaultConfig {
        applicationId = "ru.ffl24.direct.dev"
        minSdk = 24
        targetSdk = 36
        versionCode = 6
        versionName = "0.3.1-dev"
        buildConfigField("String", "DATA_SOURCE_MODE", "\"DIRECT_MYSQL\"")
        listOf("HOST", "PORT", "DATABASE", "USER", "PASSWORD", "USE_SSL", "CA_PEM").forEach { suffix ->
            val name = "DIRECT_MYSQL_$suffix"
            buildConfigField("String", name, javaString(directValue(name)))
        }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk)
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.coroutines.android)
    implementation(libs.mariadb.jdbc)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Проверяется разрешённый runtime-граф, включая транзитивные зависимости.
tasks.register("verifyDirectRuntime") {
    group = "verification"
    doLast {
        val forbiddenGroups = listOf(
            "com.squareup.retrofit2", "com.squareup.okhttp3", "io.ktor",
            "io.jsonwebtoken", "com.auth0", "com.mysql"
        )
        for (variant in listOf("debug", "release")) {
            val components = configurations.getByName("${variant}RuntimeClasspath")
                .incoming.resolutionResult.allComponents.map { it.id.displayName }
            check(components.none { id -> forbiddenGroups.any { id.startsWith("$it:") } }) {
                "В runtime обнаружена запрещённая transport-зависимость"
            }
            check(components.count { it == "org.mariadb.jdbc:mariadb-java-client:3.5.10" } == 1) {
                "Ожидается только проверяемый закреплённый JDBC artifact"
            }
            check(components.none { it.contains("project :") && it != "project :app" }) {
                "Runtime должен быть независим от других проектов"
            }
            layout.buildDirectory.file("reports/direct-runtime/$variant-components.txt")
                .get().asFile.apply {
                    parentFile.mkdirs()
                    writeText(components.sorted().joinToString("\n", postfix = "\n"))
                }
            println("$variant: проверено ${components.size} runtime components, только Direct + pinned JDBC")
        }
    }
}
