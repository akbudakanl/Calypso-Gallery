package org.fossify.gallery.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import androidx.core.content.ContextCompat
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.models.FAQItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityAboutBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.SettingsCardUi
import org.fossify.gallery.helpers.findVaultRoot

class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        if (intent.getBooleanExtra(EXTRA_OPEN_FORGOT_FAQ, false)) {
            launchFaqActivity()
            finish()
            return
        }

        binding.aboutToolbar.title = getString(org.fossify.commons.R.string.about)
        binding.aboutVersion.text = getString(
            R.string.about_version,
            BuildConfig.VERSION_NAME,
            packageName,
        )

        setupColors()
        setupActions()
        setupEdgeToEdge(
            padTopSystem = listOf(binding.aboutAppbar),
            padBottomSystem = listOf(binding.aboutScrollView),
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            binding.aboutAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor(),
        )
        setupColors()
    }

    private fun setupColors() {
        val palette = AppearanceManager.resolve(this, config)
        val mutedColor = SettingsCardUi.mutedTextColor(palette)
        val density = resources.displayMetrics.density

        updateTextColors(binding.aboutHolder)

        binding.aboutHeroCard.background = SettingsCardUi.createCardBackground(this, palette, 18f)
        binding.aboutHeroCard.clipToOutline = true

        // The About emblem mirrors the real launcher icon: fixed dark background and the
        // drawable's own white mark. It must not follow the active theme's primary color.
        binding.aboutEmblemBg.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 13f * density
            setColor(ContextCompat.getColor(this@AboutActivity, R.color.app_icon_background))
        }
        binding.aboutEmblemIcon.imageTintList = null
        binding.aboutEmblemIcon.clearColorFilter()

        listOf(
            binding.aboutVersion,
            binding.aboutDescription,
            binding.aboutModificationNotice,
            binding.aboutCopyrightNotice,
            binding.aboutLicenseNotice,
            binding.aboutSourceNotice,
        ).forEach { it.setTextColor(mutedColor) }

        binding.aboutTitle.setTextColor(palette.text)

        listOf(
            binding.aboutGplLicense,
            binding.aboutForkSource,
            binding.aboutReleaseSources,
            binding.aboutForkIssues,
            binding.aboutUpstreamSource,
            binding.aboutUpstreamProject,
            binding.aboutFaq,
            binding.aboutThirdPartyLicenses,
        ).forEach { it.setTextColor(palette.text) }

        val chevronTint = ColorStateList.valueOf(mutedColor)
        listOf(
            binding.aboutGplLicenseChevron,
            binding.aboutForkSourceChevron,
            binding.aboutReleaseSourcesChevron,
            binding.aboutForkIssuesChevron,
            binding.aboutUpstreamSourceChevron,
            binding.aboutUpstreamProjectChevron,
            binding.aboutFaqChevron,
            binding.aboutThirdPartyLicensesChevron,
        ).forEach { it.imageTintList = chevronTint }

        SettingsCardUi.styleCardGroup(binding.aboutLicenseCard, palette, eyebrow = binding.aboutLicenseHeading)
        SettingsCardUi.styleCardGroup(binding.aboutSourceCard, palette, eyebrow = binding.aboutSourceHeading)
        SettingsCardUi.styleCardGroup(binding.aboutHelpCard, palette, eyebrow = binding.aboutHelpHeading)
    }

    private fun setupActions() {
        binding.aboutGplLicenseRow.setOnClickListener {
            startActivity(Intent(this, GplLicenseActivity::class.java))
        }
        binding.aboutForkSourceRow.setOnClickListener {
            launchViewIntent(FORK_REPOSITORY_URL)
        }
        binding.aboutReleaseSourcesRow.setOnClickListener {
            launchViewIntent(FORK_RELEASES_URL)
        }
        binding.aboutForkIssuesRow.setOnClickListener {
            launchViewIntent(FORK_ISSUES_URL)
        }
        binding.aboutUpstreamSourceRow.setOnClickListener {
            launchViewIntent(UPSTREAM_REPOSITORY_URL)
        }
        binding.aboutUpstreamProjectRow.setOnClickListener {
            launchViewIntent(UPSTREAM_PROJECT_URL)
        }
        binding.aboutFaqRow.setOnClickListener {
            launchFaqActivity()
        }
        binding.aboutThirdPartyLicensesRow.setOnClickListener {
            launchThirdPartyLicensesActivity()
        }
    }

    private fun launchFaqActivity() {
        startActivity(Intent(this, FaqActivity::class.java).apply {
            putExtra(FaqActivity.EXTRA_APP_FAQ, getFaqItems())
            putExtra(FaqActivity.EXTRA_FAQ_HIGHLIGHT, R.string.faq_forgot_password_title)
        })
    }

    private fun launchThirdPartyLicensesActivity() {
        startActivity(Intent(this, ThirdPartyLicensesActivity::class.java))
    }

    private fun getFaqItems(): ArrayList<FAQItem> {
        val faqItems = arrayListOf(
            FAQItem(R.string.faq_3_title, R.string.faq_3_text),
            FAQItem(R.string.faq_12_title, R.string.faq_12_text),
            FAQItem(R.string.faq_7_title, R.string.faq_7_text),
            FAQItem(R.string.faq_14_title, R.string.faq_14_text),
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(org.fossify.commons.R.string.faq_5_title_commons, org.fossify.commons.R.string.faq_5_text_commons),
            FAQItem(R.string.faq_5_title, R.string.faq_5_text),
            FAQItem(R.string.faq_4_title, R.string.faq_4_text),
            FAQItem(R.string.faq_6_title, R.string.faq_6_text),
            FAQItem(R.string.faq_8_title, R.string.faq_8_text),
            FAQItem(R.string.faq_10_title, R.string.faq_10_text),
            FAQItem(R.string.faq_11_title, R.string.faq_11_text),
            FAQItem(R.string.faq_13_title, R.string.faq_13_text),
            FAQItem(R.string.faq_15_title, R.string.faq_15_text),
            FAQItem(R.string.faq_2_title, R.string.faq_2_text),
            FAQItem(R.string.faq_18_title, R.string.faq_18_text),
            FAQItem(org.fossify.commons.R.string.faq_9_title_commons, org.fossify.commons.R.string.faq_9_text_commons),
        )

        val activePath = CryptoManager.findVaultRoot(this)?.absolutePath ?: config.vaultRootPath
        if (activePath.isNotEmpty()) {
            faqItems.add(
                0,
                FAQItem(
                    R.string.faq_forgot_password_title,
                    getString(R.string.faq_forgot_password_text, humanizePath(activePath)),
                ),
            )
        }

        if (isRPlus() && !isExternalStorageManager()) {
            faqItems.add(0, FAQItem(R.string.faq_16_title, "${getString(R.string.faq_16_text)} ${getString(R.string.faq_16_text_extra)}"))
            faqItems.add(1, FAQItem(R.string.faq_17_title, R.string.faq_17_text))
            faqItems.removeIf { it.text == R.string.faq_7_text }
            faqItems.removeIf { it.text == R.string.faq_14_text }
            faqItems.removeIf { it.text == R.string.faq_8_text }
        }

        return faqItems
    }

    companion object {
        const val EXTRA_OPEN_FORGOT_FAQ = "open_forgot_faq"
        private const val FORK_REPOSITORY_URL = "https://github.com/akbudakanl/Gallery-Cipher"
        private const val FORK_RELEASES_URL = "$FORK_REPOSITORY_URL/releases"
        private const val FORK_ISSUES_URL = "$FORK_REPOSITORY_URL/issues"
        private const val UPSTREAM_REPOSITORY_URL = "https://github.com/FossifyOrg/Gallery"
        private const val UPSTREAM_PROJECT_URL = "https://www.fossify.org"
    }
}
