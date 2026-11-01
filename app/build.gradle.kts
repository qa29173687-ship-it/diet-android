import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
val ksProps = Properties().apply { val f = rootProject.file("keystore/keystore.properties"); if (f.exists()) f.inputStream().use { load(it) } }
android {
    namespace = "tw.dietbook"
    compileSdk = 35
    defaultConfig {
        applicationId = "tw.dietbook"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.3.1"
    }
    signingConfigs {
        if (ksProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file("keystore/" + ksProps["storeFile"])
            storePassword = ksProps["storePassword"] as String
            keyAlias = ksProps["keyAlias"] as String
            keyPassword = ksProps["keyPassword"] as String
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        disable += setOf("GradleDependency", "OldTargetApi", "SetTextI18n", "HardcodedText", "ViewConstructor", "ClickableViewAccessibility", "ObsoleteSdkInt", "AndroidGradlePluginVersion", "NewerVersionAvailable", "UnusedAttribute", "IconMissingDensityFolder")
    }
    testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.systemProperty("previewDir", rootProject.file("previews").absolutePath)
            it.systemProperty("demoFramesDir", rootProject.file("demo/frames").absolutePath) } }
    androidResources {
        // 保留 .gz 原樣，避免 AGP 解壓後檔名變成 trace-nutrition.jsonl 而程式開 .gz 失敗
        noCompress += "gz"
    }
    packaging { resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES") } }
}
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    val cx = "1.4.0"
    implementation("androidx.camera:camera-core:$cx")
    implementation("androidx.camera:camera-camera2:$cx")
    implementation("androidx.camera:camera-lifecycle:$cx")
    implementation("androidx.camera:camera-view:$cx")
    implementation("com.google.guava:guava:33.3.1-android")
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
