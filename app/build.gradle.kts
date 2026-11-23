@file:Suppress("UnstableApiUsage")

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val familySubscriptionProductId =
    (project.findProperty("myaac.familyProductId") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "family_subscription"

val familySubscriptionBasePlanId =
    (project.findProperty("myaac.familyBasePlanId") as? String)
        ?.trim()
        ?: ""

val enableProfileCapTestConfig =
    (project.findProperty("myaac.enableProfileCap") as? String)
        ?.toBooleanStrictOrNull()
        ?: false

val appVersionCode =
    (project.findProperty("myaac.versionCode") as? String)
        ?.trim()
        ?.toIntOrNull()
        ?: 1

val appVersionName =
    (project.findProperty("myaac.versionName") as? String)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "1.0"

val requireUploadSigning =
    (project.findProperty("myaac.requireUploadSigning") as? String)
        ?.toBooleanStrictOrNull()
        ?: false

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

fun resolveSigningValue(
    propertyKey: String,
    keystoreKey: String,
    envKey: String,
): String? {
    return (project.findProperty(propertyKey) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        ?: keystoreProperties.getProperty(keystoreKey)?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv(envKey)?.trim()?.takeIf { it.isNotEmpty() }
}

val uploadStoreFilePath = resolveSigningValue(
    propertyKey = "myaac.uploadStoreFile",
    keystoreKey = "storeFile",
    envKey = "MYAAC_UPLOAD_STORE_FILE",
)
val uploadStorePassword = resolveSigningValue(
    propertyKey = "myaac.uploadStorePassword",
    keystoreKey = "storePassword",
    envKey = "MYAAC_UPLOAD_STORE_PASSWORD",
)
val uploadKeyAlias = resolveSigningValue(
    propertyKey = "myaac.uploadKeyAlias",
    keystoreKey = "keyAlias",
    envKey = "MYAAC_UPLOAD_KEY_ALIAS",
)
val uploadKeyPassword = resolveSigningValue(
    propertyKey = "myaac.uploadKeyPassword",
    keystoreKey = "keyPassword",
    envKey = "MYAAC_UPLOAD_KEY_PASSWORD",
)
val resolvedUploadStoreFile = uploadStoreFilePath?.let { rawPath ->
    val direct = file(rawPath)
    if (direct.isAbsolute) direct else rootProject.file(rawPath)
}
val hasUploadSigning =
    resolvedUploadStoreFile?.isFile == true &&
        !uploadStorePassword.isNullOrEmpty() &&
        !uploadKeyAlias.isNullOrEmpty() &&
        !uploadKeyPassword.isNullOrEmpty()

if (requireUploadSigning && !hasUploadSigning) {
    throw GradleException(
        "Upload signing is required (-Pmyaac.requireUploadSigning=true), but upload keystore " +
            "settings were not found. Provide keystore.properties at repo root (storeFile, " +
            "storePassword, keyAlias, keyPassword) or MYAAC_UPLOAD_* env vars.",
    )
}

android {
    namespace = "com.kon.myaacapp"
    compileSdk = 37

    signingConfigs {
        if (hasUploadSigning) {
            create("upload") {
                storeFile = resolvedUploadStoreFile
                storePassword = uploadStorePassword
                keyAlias = uploadKeyAlias
                keyPassword = uploadKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.kon.myaacapp"
        minSdk = 26
        targetSdk = 37
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "FAMILY_SUBSCRIPTION_PRODUCT_ID",
            "\"$familySubscriptionProductId\"",
        )
        buildConfigField(
            "String",
            "FAMILY_SUBSCRIPTION_BASE_PLAN_ID",
            "\"$familySubscriptionBasePlanId\"",
        )
    }

    buildTypes {
        debug {
            buildConfigField(
                "boolean",
                "ENFORCE_PROFILE_CAP",
                enableProfileCapTestConfig.toString(),
            )
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasUploadSigning) {
                signingConfig = signingConfigs.getByName("upload")
            }
            // Keep production cap disabled until Play Console & backend verification are approved.
            buildConfigField("boolean", "ENFORCE_PROFILE_CAP", "false")
        }
        create("internalTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            signingConfig = if (hasUploadSigning) {
                signingConfigs.getByName("upload")
            } else {
                signingConfigs.getByName("debug")
            }
            buildConfigField("boolean", "ENFORCE_PROFILE_CAP", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    compileSdkMinor = 0

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.coil.compose)
    implementation(libs.android.image.cropper)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.play.feature.delivery)
    implementation(libs.play.billing.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.play.services.tasks)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
