import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.FilterConfiguration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.konan.properties.Properties
import org.objectweb.asm.AnnotationVisitor
import org.objectweb.asm.Attribute
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.TypePath
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.detekt)
}

val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

fun hasSigningVars(): Boolean {
    return providers.environmentVariable("SIGNING_KEY_ALIAS").orNull != null
            && providers.environmentVariable("SIGNING_KEY_PASSWORD").orNull != null
            && providers.environmentVariable("SIGNING_STORE_FILE").orNull != null
            && providers.environmentVariable("SIGNING_STORE_PASSWORD").orNull != null
}

val baseVersionCode = project.property("VERSION_CODE").toString().toInt()
val abiCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86" to 3, "x86_64" to 4)
val abiSplits = providers.gradleProperty("ABI_SPLITS").map(String::toBoolean).getOrElse(false)

base {
    archivesName = "gallery-$baseVersionCode"
}

android {
    compileSdk = project.libs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        applicationId = project.property("APPLICATION_ID").toString()
        minSdk = project.libs.versions.app.build.minimumSDK.get().toInt()
        targetSdk = project.libs.versions.app.build.targetSDK.get().toInt()
        versionName = project.property("VERSION_NAME").toString()
        versionCode = baseVersionCode
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            register("release") {
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        } else if (hasSigningVars()) {
            register("release") {
                keyAlias = providers.environmentVariable("SIGNING_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("SIGNING_KEY_PASSWORD").get()
                storeFile = file(providers.environmentVariable("SIGNING_STORE_FILE").get())
                storePassword = providers.environmentVariable("SIGNING_STORE_PASSWORD").get()
            }
        } else {
            logger.warn("Warning: No signing config found. Build will be unsigned.")
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists() || hasSigningVars()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    flavorDimensions.add("licensing")
    productFlavors {
        register("foss")
        register("gplay")
    }

    splits {
        abi {
            isEnable = abiSplits
            reset()
            include(*abiCodes.keys.toTypedArray())
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("main").java.directories.add("src/main/kotlin")
    }

    compileOptions {
        val currentJavaVersionFromLibs =
            JavaVersion.valueOf(libs.versions.app.build.javaVersion.get())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    dependenciesInfo {
        includeInApk = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true
    }

    tasks.withType<KotlinCompile> {
        compilerOptions.jvmTarget.set(
            JvmTarget.fromTarget(project.libs.versions.app.build.kotlinJVMTarget.get())
        )
    }

    namespace = project.property("APP_ID").toString()

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = false
        baseline = file("lint-baseline.xml")
        lintConfig = rootProject.file("lint.xml")
    }

    packaging {
        resources {
            excludes += "META-INF/library_release.kotlin_module"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
    }
}

androidComponents {
    onVariants { variant ->
        variant.instrumentation.transformClassesWith(
            NeutralizeCommonsFakeChecksVisitorFactory::class.java,
            InstrumentationScope.ALL
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COPY_FRAMES
        )
    }
}

if (abiSplits) {
    androidComponents {
        onVariants(selector().withBuildType("release").withFlavor("licensing" to "foss")) { variant ->
            variant.outputs.forEach { output ->
                val abi = output.filters.find {
                    it.filterType == FilterConfiguration.FilterType.ABI
                }?.identifier
                val versionCode = baseVersionCode + (abiCodes[abi] ?: 0)
                val abiSuffix = abi?.let { "-$it" }.orEmpty()
                val unsignedSuffix =
                    if (output.outputFileName.get().endsWith("-unsigned.apk")) "-unsigned" else ""
                output.versionCode.set(versionCode)
                output.outputFileName.set("gallery-$versionCode-foss${abiSuffix}-release${unsignedSuffix}.apk")
            }
        }
    }
}

detekt {
    baseline = file("detekt-baseline.xml")
    config.setFrom("$rootDir/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
}

configurations.configureEach {
    exclude(group = "com.github.tibbi", module = "reprint")
    exclude(group = "com.github.tibbi.reprint")
    exclude(group = "com.github.ajalt", module = "reprint")
    exclude(group = "com.github.ajalt.reprint")
}

dependencies {
    implementation(libs.fossify.commons) {
        exclude(group = "com.github.tibbi", module = "reprint")
        exclude(group = "com.github.tibbi.reprint")
    }
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.print)
    implementation(libs.android.image.cropper)
    implementation(libs.exif)
    implementation(libs.android.gif.drawable)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.sanselan)
    implementation(libs.androidphotofilters)
    implementation(libs.androidsvg.aar)
    implementation(libs.gestureviews)
    implementation(libs.subsamplingscaleimageview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.awebp)
    implementation(libs.apng)
    implementation(libs.avif)
    implementation(libs.avif.integration)
    implementation(libs.jxl.integration)
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.tink.android)

    ksp(libs.glide.compiler)
    implementation(libs.zjupure.webpdecoder)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
    testImplementation("junit:junit:4.13.2")
}

abstract class NeutralizeCommonsFakeChecksVisitorFactory :
    AsmClassVisitorFactory<InstrumentationParameters.None> {

    private enum class ReplacementKind {
        VOID_NOOP,
        BOOLEAN_FALSE
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className in TARGET_CLASSES
    }

    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        val api = instrumentationContext.apiVersion.get()
        val className = classContext.currentClassData.className
        return object : ClassVisitor(api, nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String,
                descriptor: String,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor? {
                val target = super.visitMethod(access, name, descriptor, signature, exceptions)
                    ?: return null
                val replacement = matchReplacement(className, name, descriptor) ?: return target
                val maxLocals = maxOf(1, Type.getArgumentsAndReturnSizes(descriptor) shr 2)
                return StubbingMethodVisitor(api, target, replacement, maxLocals)
            }
        }
    }

    private class StubbingMethodVisitor(
        api: Int,
        private val target: MethodVisitor,
        private val replacement: ReplacementKind,
        private val maxLocals: Int
    ) : MethodVisitor(api, null) {
        private var emitted = false

        override fun visitParameter(name: String?, access: Int) {
            target.visitParameter(name, access)
        }

        override fun visitAnnotation(descriptor: String?, visible: Boolean): AnnotationVisitor? {
            return target.visitAnnotation(descriptor, visible)
        }

        override fun visitTypeAnnotation(
            typeRef: Int,
            typePath: TypePath?,
            descriptor: String?,
            visible: Boolean
        ): AnnotationVisitor? {
            return target.visitTypeAnnotation(typeRef, typePath, descriptor, visible)
        }

        override fun visitAnnotableParameterCount(parameterCount: Int, visible: Boolean) {
            target.visitAnnotableParameterCount(parameterCount, visible)
        }

        override fun visitParameterAnnotation(
            parameter: Int,
            descriptor: String?,
            visible: Boolean
        ): AnnotationVisitor? {
            return target.visitParameterAnnotation(parameter, descriptor, visible)
        }

        override fun visitAttribute(attribute: Attribute?) {
            target.visitAttribute(attribute)
        }

        override fun visitCode() {
            emitted = true
            target.visitCode()
            if (replacement == ReplacementKind.BOOLEAN_FALSE) {
                target.visitInsn(Opcodes.ICONST_0)
                target.visitInsn(Opcodes.IRETURN)
                target.visitMaxs(1, maxLocals)
            } else {
                target.visitInsn(Opcodes.RETURN)
                target.visitMaxs(0, maxLocals)
            }
            target.visitEnd()
        }

        override fun visitEnd() {
            if (!emitted) {
                target.visitEnd()
            }
        }
    }

    companion object {
        private const val ACTIVITY_KT = "org.fossify.commons.extensions.ActivityKt"
        private const val COMPOSE_ACTIVITY_EXTENSIONS =
            "org.fossify.commons.compose.extensions.ActivityExtensionsKt"
        private const val COMPOSE_ACTIVITY_UI_EXTENSIONS =
            "org.fossify.commons.compose.extensions.ComposeActivityExtensionsKt"

        private val TARGET_CLASSES = setOf(
            ACTIVITY_KT,
            COMPOSE_ACTIVITY_EXTENSIONS,
            COMPOSE_ACTIVITY_UI_EXTENSIONS
        )

        private fun matchReplacement(
            className: String,
            name: String,
            descriptor: String
        ): ReplacementKind? {
            return when (className) {
                ACTIVITY_KT -> when {
                    name == "showModdedAppWarning" &&
                        descriptor == "(Lorg/fossify/commons/activities/BaseSimpleActivity;)V" ->
                        ReplacementKind.VOID_NOOP
                    name == "showSideloadingDialog" &&
                        descriptor == "(Landroid/app/Activity;)V" ->
                        ReplacementKind.VOID_NOOP
                    name == "checkAppSideloading" &&
                        descriptor == "(Landroid/app/Activity;)Z" ->
                        ReplacementKind.BOOLEAN_FALSE
                    name == "isAppSideloaded" &&
                        descriptor == "(Landroid/app/Activity;)Z" ->
                        ReplacementKind.BOOLEAN_FALSE
                    else -> null
                }
                COMPOSE_ACTIVITY_EXTENSIONS -> when {
                    name == "fakeVersionCheck" &&
                        descriptor == "(Landroid/content/Context;Lkotlin/jvm/functions/Function0;)V" ->
                        ReplacementKind.VOID_NOOP
                    else -> null
                }
                COMPOSE_ACTIVITY_UI_EXTENSIONS -> when {
                    name == "FakeVersionCheck" &&
                        descriptor == "(Landroidx/compose/runtime/Composer;I)V" ->
                        ReplacementKind.VOID_NOOP
                    else -> null
                }
                else -> null
            }
        }
    }
}
