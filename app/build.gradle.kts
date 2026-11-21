import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.variant.FilterConfiguration
import org.apache.tools.ant.taskdefs.condition.Os
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
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
import javax.inject.Inject

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
        getByName("main").jniLibs.directories.add(
            layout.buildDirectory.dir("generated/rustJniLibs").get().asFile.absolutePath
        )
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
    implementation(libs.tink.android)
    testImplementation(libs.bouncycastle.bcprov)

    ksp(libs.glide.compiler)
    implementation(libs.zjupure.webpdecoder)

    implementation(libs.bundles.room)
    ksp(libs.androidx.room.compiler)
    detektPlugins(libs.compose.detekt)
    testImplementation("junit:junit:4.13.2")
}

fun resolveCargoExecutable(): String {
    val exeName = if (Os.isFamily(Os.FAMILY_WINDOWS)) "cargo.exe" else "cargo"
    val pathEntries = System.getenv("PATH").orEmpty().split(File.pathSeparator)
    for (entry in pathEntries) {
        if (entry.isBlank()) continue
        val candidate = File(entry, exeName)
        if (candidate.isFile) return candidate.absolutePath
    }
    val userCargo = File(System.getProperty("user.home"), ".cargo/bin/$exeName")
    if (userCargo.isFile) return userCargo.absolutePath
    error("Cargo executable not found in PATH or ${userCargo.absolutePath}")
}

fun resolveNdkPrebuiltBinDir(rootDir: File): String {
    val hostTag = when {
        Os.isFamily(Os.FAMILY_WINDOWS) -> "windows-x86_64"
        Os.isFamily(Os.FAMILY_MAC) -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    fun prebuiltBin(ndkDir: File): File = File(ndkDir, "toolchains/llvm/prebuilt/$hostTag/bin")

    val envNdk = listOf("ANDROID_NDK_HOME", "ANDROID_NDK_ROOT")
        .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) }
        .map(::File)
        .firstOrNull { prebuiltBin(it).isDirectory }
    if (envNdk != null) return prebuiltBin(envNdk).absolutePath

    val localPropsFile = File(rootDir, "local.properties")
    val localSdk = if (localPropsFile.isFile) {
        Properties().apply { FileInputStream(localPropsFile).use(::load) }.getProperty("sdk.dir")
    } else {
        null
    }
    val sdkDir = listOfNotNull(
        localSdk,
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT")
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("Android SDK directory not found; cannot locate Android NDK.")

    val ndkRoot = File(sdkDir, "ndk")
    val selectedNdk = ndkRoot.listFiles()
        ?.filter { it.isDirectory && prebuiltBin(it).isDirectory }
        ?.maxWithOrNull(compareBy { dir ->
            dir.name.split('.').map { it.toIntOrNull() ?: 0 }.let { parts ->
                val major = parts.getOrElse(0) { 0 }.toLong()
                val minor = parts.getOrElse(1) { 0 }.toLong()
                val patch = parts.getOrElse(2) { 0 }.toLong()
                (major shl 32) or (minor shl 16) or patch
            }
        })
        ?: error("No Android NDK found under ${ndkRoot.absolutePath} (expected prebuilt/$hostTag).")
    return prebuiltBin(selectedNdk).absolutePath
}

abstract class BuildRustArgon2AndroidTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSources: ConfigurableFileCollection

    @get:Input
    abstract val rustProjectPath: Property<String>

    @get:Input
    abstract val cargoExecutable: Property<String>

    @get:Input
    abstract val ndkPrebuiltBinDir: Property<String>

    @get:Input
    abstract val clangSuffix: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val cargo = cargoExecutable.get()
        val cargoBinDir = File(cargo).parentFile?.absolutePath
        val ndkBin = File(ndkPrebuiltBinDir.get())
        val suffix = clangSuffix.get()
        val rustDir = File(rustProjectPath.get())
        val outRoot = outputDir.get().asFile
        outRoot.deleteRecursively()
        outRoot.mkdirs()

        val abiTargets = listOf(
            Triple("arm64-v8a", "aarch64-linux-android", "aarch64-linux-android26-clang"),
            Triple("armeabi-v7a", "armv7-linux-androideabi", "armv7a-linux-androideabi26-clang"),
            Triple("x86", "i686-linux-android", "i686-linux-android26-clang"),
            Triple("x86_64", "x86_64-linux-android", "x86_64-linux-android26-clang")
        )

        for ((abi, triple, clangName) in abiTargets) {
            val clangFile = File(ndkBin, "$clangName$suffix")
            check(clangFile.exists()) {
                "Android NDK clang wrapper not found at ${clangFile.absolutePath}"
            }
            val envKey = triple.uppercase().replace('-', '_')
            execOperations.exec {
                workingDir = rustDir
                commandLine(cargo, "build", "--release", "--locked", "--target", triple)
                environment.remove("CARGO_BUILD_TARGET")
                if (!cargoBinDir.isNullOrBlank()) {
                    val curPath = System.getenv("PATH").orEmpty()
                    environment("PATH", "$cargoBinDir${File.pathSeparator}$curPath")
                }
                environment("CARGO_TARGET_${envKey}_LINKER", clangFile.absolutePath)
                environment("CARGO_TARGET_${envKey}_RUSTFLAGS", "-C link-arg=-Wl,-z,max-page-size=16384")
            }
            val builtSo = File(rustDir, "target/$triple/release/libcalypso_argon2.so")
            check(builtSo.exists()) {
                "Expected Rust library not produced at ${builtSo.absolutePath}"
            }
            val destDir = File(outRoot, abi).apply { mkdirs() }
            builtSo.copyTo(File(destDir, "libcalypso_argon2.so"), overwrite = true)
        }
    }
}

abstract class BuildRustArgon2HostTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val rustSources: ConfigurableFileCollection

    @get:Input
    abstract val rustProjectPath: Property<String>

    @get:Input
    abstract val cargoExecutable: Property<String>

    @get:Input
    abstract val windowsHost: Property<Boolean>

    @get:OutputFile
    abstract val outputLib: RegularFileProperty

    @TaskAction
    fun build() {
        val cargo = cargoExecutable.get()
        val cargoBinDir = File(cargo).parentFile?.absolutePath
        val rustDir = File(rustProjectPath.get())
        execOperations.exec {
            workingDir = rustDir
            commandLine(cargo, "build", "--release", "--locked")
            environment.remove("CARGO_BUILD_TARGET")
            if (!cargoBinDir.isNullOrBlank()) {
                val curPath = System.getenv("PATH").orEmpty()
                environment("PATH", "$cargoBinDir${File.pathSeparator}$curPath")
            }
            if (windowsHost.get()) {
                environment("CARGO_TARGET_X86_64_PC_WINDOWS_GNU_RUSTFLAGS", "-C link-arg=-static-libgcc")
            }
        }
        check(outputLib.get().asFile.exists()) {
            "Expected host Rust library not produced at ${outputLib.get().asFile.absolutePath}"
        }
    }
}

val rustArgon2Dir = rootProject.file("rust/calypso-argon2")
val rustArgon2Sources = files(
    File(rustArgon2Dir, "Cargo.toml"),
    File(rustArgon2Dir, "Cargo.lock"),
    fileTree(File(rustArgon2Dir, "src"))
)

val buildRustArgon2Android = tasks.register<BuildRustArgon2AndroidTask>("buildRustArgon2Android") {
    rustSources.from(rustArgon2Sources)
    rustProjectPath.set(rustArgon2Dir.absolutePath)
    cargoExecutable.set(providers.provider { resolveCargoExecutable() })
    ndkPrebuiltBinDir.set(providers.provider { resolveNdkPrebuiltBinDir(rootProject.rootDir) })
    clangSuffix.set(if (Os.isFamily(Os.FAMILY_WINDOWS)) ".cmd" else "")
    outputDir.set(layout.buildDirectory.dir("generated/rustJniLibs"))
}

val hostRustLibName = when {
    Os.isFamily(Os.FAMILY_WINDOWS) -> "calypso_argon2.dll"
    Os.isFamily(Os.FAMILY_MAC) -> "libcalypso_argon2.dylib"
    else -> "libcalypso_argon2.so"
}

val buildRustArgon2Host = tasks.register<BuildRustArgon2HostTask>("buildRustArgon2Host") {
    rustSources.from(rustArgon2Sources)
    rustProjectPath.set(rustArgon2Dir.absolutePath)
    cargoExecutable.set(providers.provider { resolveCargoExecutable() })
    windowsHost.set(Os.isFamily(Os.FAMILY_WINDOWS))
    outputLib.set(File(rustArgon2Dir, "target/release/$hostRustLibName"))
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(buildRustArgon2Android)
}

tasks.withType<Test>().configureEach {
    dependsOn(buildRustArgon2Host)
    val hostLibDir = File(rustArgon2Dir, "target/release").absolutePath
    val existingPath = System.getProperty("java.library.path")
    systemProperty(
        "java.library.path",
        if (existingPath.isNullOrBlank()) hostLibDir else "$hostLibDir${File.pathSeparator}$existingPath"
    )
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
