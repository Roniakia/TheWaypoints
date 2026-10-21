import com.google.gson.JsonParser

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.androidx.room)
}

val localBuild = providers.gradleProperty("localBuild").map(String::toBoolean).getOrElse(false)
// A candidate must never resolve its update metadata through GitHub's stable /latest/ alias.
val releaseTag = providers.gradleProperty("APP_RELEASE_TAG").orNull?.takeIf { it.isNotBlank() }
require(releaseTag == null || releaseTag.matches(Regex("v[0-9]+\\.[0-9]+(?:-beta\\.[0-9]+)?"))) {
    "APP_RELEASE_TAG must be a versioned release tag"
}
require(releaseTag == null || releaseTag.removePrefix("v").substringBefore("-beta.") ==
    providers.gradleProperty("APP_VERSION_NAME").get()) {
    "APP_RELEASE_TAG must match APP_VERSION_NAME"
}
val releaseAssetBase = "https://github.com/Roniakia/TheWaypoints/releases/" +
    (releaseTag?.let { "download/$it" } ?: "latest/download")
val releaseApkName = releaseTag?.let { "waypoints-${it.removePrefix("v")}.apk" } ?: "app-release.apk"

android {
    namespace = "de.dimskiy.waypoints"
    compileSdk = 35

    defaultConfig {
        applicationId = project.properties["APPLICATION_ID"].toString()
        minSdk = 23
        targetSdk = 35
        versionCode = project.properties["APP_VERSION_CODE"].toString().toInt()
        versionName = project.properties["APP_VERSION_NAME"].toString()
        manifestPlaceholders["karooManifestUrl"] = "$releaseAssetBase/manifest-karoo.json"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "no_file")
            storePassword = System.getenv("KEYSTORE_PASSWORD").orEmpty()
            keyAlias = System.getenv("KEY_ALIAS").orEmpty()
            keyPassword = System.getenv("KEY_PASSWORD").orEmpty()
        }
    }

    buildTypes {
        debug {
            if (localBuild) applicationIdSuffix = ".local"
        }
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        create("profiling") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isJniDebuggable = true
            isPseudoLocalesEnabled = true
        }

        create("stage") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("test").resources.srcDir("src/main/assets")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
        providers.gradleProperty("poiCompatibilityAssets").orNull?.let {
            getByName("androidTest").assets.srcDir(it)
        }
    }

    packaging {
        resources {
            excludes += "META-INF/gradle/incremental.annotation.processors"
            merges += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md")
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) { variant ->
        if (localBuild && variant.buildType != "debug") variant.enable = false
    }
}

afterEvaluate {
    val generateKarooAssetsManifest by tasks.registering {
        val readmeFile = file("$rootDir/README.md")
        if (!readmeFile.exists()) {
            error("'${readmeFile.name}' not found - cannot generate Karoo manifest")
        }

        val manifestTemplateFile = file("$rootDir/manifest-karoo-template.json")
        if (!manifestTemplateFile.exists()) {
            error("'${manifestTemplateFile.name}' not found - cannot generate Karoo manifest")
        }

        val outputDir = layout.buildDirectory.dir("outputs/apk/release/assets")
        val outputFile = outputDir.map { it.file("manifest-karoo.json") }

        inputs.file(manifestTemplateFile)
        inputs.file(readmeFile)
        inputs.property("applicationId", project.properties["APPLICATION_ID"].toString())
        inputs.property("versionCode", project.properties["APP_VERSION_CODE"].toString())
        inputs.property("versionName", project.properties["APP_VERSION_NAME"].toString())
        inputs.property("releaseAssetBase", releaseAssetBase)
        inputs.property("releaseApkName", releaseApkName)
        outputs.file(outputFile)

        val getSectionText: (String) -> String = { sectionName ->
            val readmeLines = readmeFile.readLines()
            val description = StringBuilder()

            var isDescriptionStarted = false
            for (line in readmeLines) {
                val lineTrimmed = line.trim()
                when {
                    lineTrimmed == sectionName -> isDescriptionStarted = true
                    isDescriptionStarted && lineTrimmed.contains("## ") -> break
                    isDescriptionStarted -> description.appendLine(line)
                    else -> {}
                }
            }
            description.toString()
        }

        doLast {
            val versionCode = project.properties["APP_VERSION_CODE"].toString().toInt()
            val versionName = project.properties["APP_VERSION_NAME"].toString()
            val packageName = project.properties["APPLICATION_ID"].toString()
            val description = getSectionText("## Description")

            val jsonContent = manifestTemplateFile.readText()
            val jsonElement = JsonParser.parseString(jsonContent)
            val jsonObject = jsonElement.asJsonObject

            jsonObject.addProperty("latestVersionCode", versionCode)
            jsonObject.addProperty("latestVersion", versionName)
            jsonObject.addProperty("packageName", packageName)
            jsonObject.addProperty("description", description)
            jsonObject.addProperty("iconUrl", "$releaseAssetBase/app_icon.webp")
            jsonObject.addProperty("latestApkUrl", "$releaseAssetBase/$releaseApkName")

            outputFile.get().asFile.parentFile.mkdirs()
            outputFile.get().asFile.writeText(jsonObject.toString())

            println("Karoo manifest generated: ${outputFile.get()}")
        }
    }

    val copyAppIconToAssets by tasks.registering {
        doLast {
            val iconFile = file("src/main/res/mipmap-xxhdpi/ic_launcher.webp")
            val destinationDir =
                layout.buildDirectory.dir("outputs/apk/release/assets").get().asFile

            if (!iconFile.exists()) {
                error("No app icon found: '${iconFile.absolutePath}'")
            }

            destinationDir.mkdirs()
            val destinationFileName = "app_icon.${iconFile.extension}"
            iconFile.copyTo(File(destinationDir.path, destinationFileName), overwrite = true)

            println("App icon '${iconFile.name}' copied to '${destinationDir.path}' as '$destinationFileName'")
        }
    }

    tasks.matching { it.name == "assembleRelease" }.configureEach {
        finalizedBy(generateKarooAssetsManifest)
        finalizedBy(copyAppIconToAssets)
    }
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    // Align the SDK's serialization runtime with Room's migration test helper.
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.core.ktx)
    implementation(libs.bundles.androidx.lifeycle)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.startup)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.work)
    implementation(libs.google.tink)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.bundles.androidx.compose)

    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.android)
    ksp(libs.androidx.hilt.compiler)

    implementation(libs.bundles.androidx.room)
    ksp(libs.androidx.room.compiler)

    implementation(libs.google.gson)
    implementation(libs.swipeview.compose)
    implementation(libs.timber)

    debugImplementation(libs.test.compose.manifest)
    testImplementation(libs.bundles.test.core)
    androidTestImplementation(libs.test.junit)
    androidTestImplementation(libs.test.androidx.core)
    androidTestImplementation(libs.test.androidx.junit.ext)
    androidTestImplementation(libs.test.coroutines)
    androidTestImplementation(libs.test.work)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.bundles.test.instrumented.extras)
}
