package org.fossify.gallery.activities

import android.content.Intent
import android.os.Bundle
import org.fossify.commons.activities.FAQActivity
import org.fossify.commons.activities.LicenseActivity
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.LICENSE_APNG
import org.fossify.commons.helpers.LICENSE_CROPPER
import org.fossify.commons.helpers.LICENSE_EXOPLAYER
import org.fossify.commons.helpers.LICENSE_FILTERS
import org.fossify.commons.helpers.LICENSE_GESTURE_VIEWS
import org.fossify.commons.helpers.LICENSE_GIF_DRAWABLE
import org.fossify.commons.helpers.LICENSE_GLIDE
import org.fossify.commons.helpers.LICENSE_PATTERN
import org.fossify.commons.helpers.LICENSE_PICASSO
import org.fossify.commons.helpers.LICENSE_REPRINT
import org.fossify.commons.helpers.LICENSE_RTL
import org.fossify.commons.helpers.LICENSE_SANSELAN
import org.fossify.commons.helpers.LICENSE_SUBSAMPLING
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.FAQItem
import org.fossify.gallery.BuildConfig
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityAboutBinding

class AboutActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.aboutToolbar.title = getString(R.string.about_title)
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
        setupMaterialScrollListener(binding.aboutScrollView, binding.aboutAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
    }

    private fun setupColors() {
        val textColor = getProperTextColor()
        val accentColor = getProperPrimaryColor()

        listOf(
            binding.aboutTitle,
            binding.aboutDescription,
            binding.aboutModificationNotice,
            binding.aboutLicenseNotice,
            binding.aboutSourceNotice,
        ).forEach { it.setTextColor(textColor) }

        listOf(
            binding.aboutLicenseHeading,
            binding.aboutSourceHeading,
            binding.aboutHelpHeading,
        ).forEach { it.setTextColor(accentColor) }

        listOf(
            binding.aboutGplLicense,
            binding.aboutForkSource,
            binding.aboutReleaseSources,
            binding.aboutForkIssues,
            binding.aboutUpstreamSource,
            binding.aboutUpstreamProject,
            binding.aboutFaq,
            binding.aboutThirdPartyLicenses,
        ).forEach { it.setTextColor(accentColor) }
    }

    private fun setupActions() {
        binding.aboutGplLicense.setOnClickListener {
            startActivity(Intent(this, GplLicenseActivity::class.java))
        }
        binding.aboutForkSource.setOnClickListener {
            launchViewIntent(FORK_REPOSITORY_URL)
        }
        binding.aboutReleaseSources.setOnClickListener {
            launchViewIntent(FORK_RELEASES_URL)
        }
        binding.aboutForkIssues.setOnClickListener {
            launchViewIntent(FORK_ISSUES_URL)
        }
        binding.aboutUpstreamSource.setOnClickListener {
            launchViewIntent(UPSTREAM_REPOSITORY_URL)
        }
        binding.aboutUpstreamProject.setOnClickListener {
            launchViewIntent(UPSTREAM_PROJECT_URL)
        }
        binding.aboutFaq.setOnClickListener {
            launchFaqActivity()
        }
        binding.aboutThirdPartyLicenses.setOnClickListener {
            launchThirdPartyLicensesActivity()
        }
    }

    private fun launchFaqActivity() {
        startActivity(Intent(this, FAQActivity::class.java).apply {
            putExtra("app_icon_ids", getAppIconIDs())
            putExtra("app_launcher_name", getAppLauncherName())
            putExtra("app_faq", getFaqItems())
        })
    }

    private fun launchThirdPartyLicensesActivity() {
        val licenses = LICENSE_GLIDE or LICENSE_CROPPER or LICENSE_RTL or LICENSE_SUBSAMPLING or
            LICENSE_PATTERN or LICENSE_REPRINT or LICENSE_GIF_DRAWABLE or LICENSE_PICASSO or
            LICENSE_EXOPLAYER or LICENSE_SANSELAN or LICENSE_FILTERS or LICENSE_GESTURE_VIEWS or
            LICENSE_APNG

        startActivity(Intent(this, LicenseActivity::class.java).apply {
            putExtra("app_icon_ids", getAppIconIDs())
            putExtra("app_launcher_name", getAppLauncherName())
            putExtra("app_licenses", licenses)
        })
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

        if (resources.getBoolean(org.fossify.commons.R.bool.is_google_play_build)) {
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_2_title_commons, org.fossify.commons.R.string.faq_2_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_6_title_commons, org.fossify.commons.R.string.faq_6_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_7_title_commons, org.fossify.commons.R.string.faq_7_text_commons))
            faqItems.add(FAQItem(org.fossify.commons.R.string.faq_10_title_commons, org.fossify.commons.R.string.faq_10_text_commons))
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
        private const val FORK_REPOSITORY_URL = "https://github.com/akbudakanl/Gallery-Cipher"
        private const val FORK_RELEASES_URL = "$FORK_REPOSITORY_URL/releases"
        private const val FORK_ISSUES_URL = "$FORK_REPOSITORY_URL/issues"
        private const val UPSTREAM_REPOSITORY_URL = "https://github.com/FossifyOrg/Gallery"
        private const val UPSTREAM_PROJECT_URL = "https://www.fossify.org"
    }
}
