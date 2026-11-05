plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("app.cash.sqldelight").version("2.4.0")
}

kotlin {

    androidTarget()


    sourceSets {

        val sqldelight_version = "2.4.0"

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("app.cash.sqldelight:primitive-adapters:$sqldelight_version")
                implementation("app.cash.sqldelight:coroutines-extensions:$sqldelight_version")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("app.cash.sqldelight:android-driver:$sqldelight_version")
            }
        }

    }
}

android {
    namespace = "me.timeto.shared"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
    }
    compileOptions.sourceCompatibility = JavaVersion.VERSION_25
    compileOptions.targetCompatibility = JavaVersion.VERSION_25
}

sqldelight {
    databases {
        create("TimetomeDB") {
            packageName.set("me.timeto.appdbsq")
        }
    }
}
