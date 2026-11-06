import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}
val vMajor = (versionProps.getProperty("major") ?: "0").toInt()
val vMinor = (versionProps.getProperty("minor") ?: "1").toInt()
val vCode  = (versionProps.getProperty("code")  ?: "1").toInt()

android {
    namespace = "org.rigacci.olyshare"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.rigacci.olyshare"
        minSdk = 24
        targetSdk = 36
        versionCode = vCode
        versionName = "$vMajor.$vMinor"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register("bumpMajor") {
    description = "Increment the major version, reset minor to 0, bump the build code"
    doLast {
        val p = Properties()
        versionPropsFile.inputStream().use { p.load(it) }
        val major = (p.getProperty("major") ?: "0").toInt() + 1
        val code  = (p.getProperty("code")  ?: "1").toInt() + 1
        p.setProperty("major", major.toString())
        p.setProperty("minor", "0")
        p.setProperty("code", code.toString())
        versionPropsFile.outputStream().use { p.store(it, "major bumped") }
        println("Version bumped to $major.0 (build $code)")
    }
}

tasks.register("bumpVersion") {
    description = "Increment minor + build code in version.properties"
    doLast {
        val p = Properties()
        versionPropsFile.inputStream().use { p.load(it) }
        val major = (p.getProperty("major") ?: "0").toInt()
        val minor = (p.getProperty("minor") ?: "1").toInt() + 1
        val code  = (p.getProperty("code")  ?: "1").toInt() + 1
        p.setProperty("major", major.toString())
        p.setProperty("minor", minor.toString())
        p.setProperty("code",  code.toString())
        versionPropsFile.outputStream().use { p.store(it, "bumped") }
        println("Version bumped to $major.$minor (build $code)")
    }
}

tasks.register("bumpBuild") {
    description = "Increment only the build code (versionName unchanged)"
    doLast {
        val p = Properties()
        versionPropsFile.inputStream().use { p.load(it) }
        val code = (p.getProperty("code") ?: "1").toInt() + 1
        p.setProperty("code", code.toString())
        versionPropsFile.outputStream().use { p.store(it, "build bumped") }
        println("Build bumped to code $code (version ${p.getProperty("major")}.${p.getProperty("minor")})")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Intentionally empty: this skeleton uses only the Android platform
    // and the Kotlin standard library. No third-party dependencies,
    // no native (.so) libraries -> automatically 16 KB page-size compliant.
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
}
