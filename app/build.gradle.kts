plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 버전은 릴리스 워크플로우가 태그(v0.1.0)에서 계산해 -P 옵션으로 넘긴다.
// 로컬 빌드에서는 아래 기본값을 쓴다.
val appVersionName: String = (project.findProperty("appVersion") as String?) ?: "0.1.0"
val appVersionCode: Int = (project.findProperty("appVersionCode") as String?)?.toInt() ?: 100

// 서명 키는 환경변수로만 받는다. 키 파일을 저장소에 넣지 않는다.
val keystorePath: String? = System.getenv("KEYSTORE_PATH")

android {
    namespace = "com.kjt7942.multiscale"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kjt7942.multiscale"
        minSdk = 26
        targetSdk = 34
        versionCode = appVersionCode
        versionName = appVersionName
        // 앱이 새 버전을 찾아볼 GitHub 저장소 (owner/repo). 공개 저장소여야 한다.
        buildConfigField("String", "UPDATE_REPO", "\"kjt7942/multiscale\"")
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
