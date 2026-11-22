package org.fossify.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Constructor
import java.lang.reflect.Method

class CommonsPromptsNeutralizationTest {

    private fun locateAppFile(relativePath: String): File {
        val direct = File(relativePath)
        if (direct.exists()) return direct
        val underApp = File("app", relativePath)
        if (underApp.exists()) return underApp
        error("Could not locate $relativePath from working directory ${File(".").absolutePath}")
    }

    private fun Method.jvmDescriptor(): String {
        val params = parameterTypes.joinToString("") { it.jvmTypeDescriptor() }
        return "($params)${returnType.jvmTypeDescriptor()}"
    }

    private fun Constructor<*>.jvmDescriptor(): String {
        val params = parameterTypes.joinToString("") { it.jvmTypeDescriptor() }
        return "($params)V"
    }

    private fun Class<*>.jvmTypeDescriptor(): String = when {
        isPrimitive -> when (this) {
            Void.TYPE -> "V"
            Boolean::class.javaPrimitiveType -> "Z"
            Byte::class.javaPrimitiveType -> "B"
            Char::class.javaPrimitiveType -> "C"
            Short::class.javaPrimitiveType -> "S"
            Int::class.javaPrimitiveType -> "I"
            Long::class.javaPrimitiveType -> "J"
            Float::class.javaPrimitiveType -> "F"
            Double::class.javaPrimitiveType -> "D"
            else -> error("Unknown primitive $this")
        }
        isArray -> name.replace('.', '/')
        else -> "L${name.replace('.', '/')};"
    }

    @Test
    fun mainSourcesNeverCallCommonsPromptsOrDonationDialogs() {
        val mainKotlinDir = locateAppFile("src/main/kotlin")
        val forbiddenRefs = listOf(
            "org.fossify.commons.extensions.appLaunched",
            "org.fossify.commons.compose.extensions.appLaunchedCompose",
            "org.fossify.commons.extensions.showAutomaticSupportPromptIfEligible",
            "org.fossify.commons.extensions.SupportPromptKt",
            "org.fossify.commons.dialogs.DonateDialog",
            "org.fossify.commons.dialogs.RateAppDialog",
            "org.fossify.commons.dialogs.PurchaseThankYouDialog",
            "org.fossify.commons.dialogs.AppSideloadedDialog",
            "org.fossify.commons.dialogs.WhatsNewDialog",
            "org.fossify.commons.extensions.checkWhatsNew",
            "org.fossify.commons.views.PurchaseThankYouItem",
            "org.fossify.commons.extensions.launchPurchaseThankYouIntent",
            "org.fossify.commons.extensions.launchMoreAppsFromUsIntent",
            "org.fossify.commons.extensions.launchAppRatingPage"
        )

        for (src in mainKotlinDir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val content = src.readText()
            for (forbidden in forbiddenRefs) {
                assertFalse(
                    "${src.name} must not reference Fossify prompt/dialog API: $forbidden",
                    content.contains(forbidden)
                )
            }
        }

        val mainActivityText = locateAppFile("src/main/kotlin/org/fossify/gallery/activities/MainActivity.kt").readText()
        assertFalse(
            "MainActivity must not call Commons appLaunched()",
            Regex("""\bappLaunched\s*\(""").containsMatchIn(mainActivityText)
        )
        assertTrue(
            "MainActivity must initialize launch state locally via initAppLaunchState()",
            mainActivityText.contains("initAppLaunchState()")
        )
    }

    @Test
    fun sideloadCheckMarksSupportPromptsAsAlreadyShownOnStartup() {
        val sideloadCheckText = locateAppFile("src/main/kotlin/org/fossify/gallery/helpers/SideloadCheck.kt").readText()
        assertTrue(
            "SideloadCheck must mark appSideloadingStatus = SIDELOADING_FALSE",
            sideloadCheckText.contains("appSideloadingStatus = SIDELOADING_FALSE")
        )
        assertTrue(
            "SideloadCheck must permanently mark wasDonationPromptShown = true",
            sideloadCheckText.contains("wasDonationPromptShown = true")
        )
        assertTrue(
            "SideloadCheck must permanently mark wasRatePromptShown = true",
            sideloadCheckText.contains("wasRatePromptShown = true")
        )
    }

    @Test
    fun bytecodeInstrumentationTargetsMatchActualCommonsClassSignatures() {
        val gradleText = locateAppFile("build.gradle.kts").readText()

        val expectedMethodTargets = listOf(
            Triple(
                "org.fossify.commons.extensions.SupportPromptKt",
                "showAutomaticSupportPromptIfEligible",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "showModdedAppWarning",
                "(Lorg/fossify/commons/activities/BaseSimpleActivity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "showSideloadingDialog",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "checkAppSideloading",
                "(Landroid/app/Activity;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "isAppSideloaded",
                "(Landroid/app/Activity;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "launchPurchaseThankYouIntent",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "launchMoreAppsFromUsIntent",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ActivityKt",
                "launchAppRatingPage",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.ContextKt",
                "isThankYouInstalled",
                "(Landroid/content/Context;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.ContextKt",
                "isThankYouFontsSupported",
                "(Landroid/content/Context;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.ContextKt",
                "canAccessGlobalConfig",
                "(Landroid/content/Context;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.ContextKt",
                "hasThankYouUnlock",
                "(Landroid/content/Context;)Z"
            ),
            Triple(
                "org.fossify.commons.extensions.Context_stylingKt",
                "checkAppIconColor",
                "(Landroid/content/Context;)V"
            ),
            Triple(
                "org.fossify.commons.extensions.Context_stylingKt",
                "toggleAppIconColor",
                "(Landroid/content/Context;Ljava/lang/String;IIZ)V"
            ),
            Triple(
                "org.fossify.commons.dialogs.DonateDialogKt",
                "DonateAlertDialog",
                "(Lorg/fossify/commons/compose/alert_dialog/AlertDialogState;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V"
            ),
            Triple(
                "org.fossify.commons.dialogs.DonateDialogKt",
                "openDonationOptions",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.dialogs.DonateDialogKt",
                "access\$openDonationOptions",
                "(Landroid/app/Activity;)V"
            ),
            Triple(
                "org.fossify.commons.dialogs.PurchaseThankYouDialogKt",
                "PurchaseThankYouAlertDialog",
                "(Lorg/fossify/commons/compose/alert_dialog/AlertDialogState;Landroidx/compose/ui/Modifier;Landroidx/compose/runtime/Composer;II)V"
            ),
            Triple(
                "org.fossify.commons.dialogs.AppSideloadedDialogKt",
                "AppSideLoadedAlertDialog",
                "(Lorg/fossify/commons/compose/alert_dialog/AlertDialogState;Landroidx/compose/ui/Modifier;Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function0;Landroidx/compose/runtime/Composer;II)V"
            ),
            Triple(
                "org.fossify.commons.compose.extensions.ActivityExtensionsKt",
                "fakeVersionCheck",
                "(Landroid/content/Context;Lkotlin/jvm/functions/Function0;)V"
            ),
            Triple(
                "org.fossify.commons.compose.extensions.ComposeActivityExtensionsKt",
                "FakeVersionCheck",
                "(Landroidx/compose/runtime/Composer;I)V"
            )
        )

        for ((className, methodName, descriptor) in expectedMethodTargets) {
            val escapedMethodName = methodName.replace("$", "\\$")
            assertTrue(
                "app/build.gradle.kts must instrument $className#$methodName",
                gradleText.contains(className) &&
                    (gradleText.contains(methodName) || gradleText.contains(escapedMethodName)) &&
                    gradleText.contains(descriptor)
            )
            val clazz = Class.forName(className, false, javaClass.classLoader)
            val matching = clazz.declaredMethods.firstOrNull {
                it.name == methodName && it.jvmDescriptor() == descriptor
            }
            assertNotNull(
                "Commons class $className no longer has method $methodName with descriptor $descriptor; update NeutralizeCommonsFakeChecksVisitorFactory!",
                matching
            )
        }

        val expectedConstructorTargets = listOf(
            Pair(
                "org.fossify.commons.dialogs.DonateDialog",
                "(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V"
            ),
            Pair(
                "org.fossify.commons.dialogs.RateAppDialog",
                "(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;Lkotlin/jvm/functions/Function0;)V"
            ),
            Pair(
                "org.fossify.commons.dialogs.PurchaseThankYouDialog",
                "(Landroid/app/Activity;)V"
            ),
            Pair(
                "org.fossify.commons.dialogs.AppSideloadedDialog",
                "(Landroid/app/Activity;Lkotlin/jvm/functions/Function0;)V"
            )
        )

        for ((className, descriptor) in expectedConstructorTargets) {
            assertTrue(
                "app/build.gradle.kts must instrument constructor $className.<init>$descriptor",
                gradleText.contains(className) && gradleText.contains(descriptor)
            )
            val clazz = Class.forName(className, false, javaClass.classLoader)
            val matching = clazz.declaredConstructors.firstOrNull {
                it.jvmDescriptor() == descriptor
            }
            assertNotNull(
                "Commons class $className no longer has constructor with descriptor $descriptor; update NeutralizeCommonsFakeChecksVisitorFactory!",
                matching
            )
            assertEquals("java.lang.Object", clazz.superclass?.name)
        }
    }
}
