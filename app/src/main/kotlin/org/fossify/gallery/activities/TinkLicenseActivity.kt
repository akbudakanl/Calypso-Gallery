package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityTinkLicenseBinding

class TinkLicenseActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityTinkLicenseBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.tinkLicenseToolbar.title = getString(R.string.tink_license_title)
        binding.tinkLicenseText.text = resources.openRawResource(R.raw.tink_license)
            .bufferedReader()
            .use { it.readText() }
        binding.tinkLicenseText.setTextColor(getProperTextColor())

        setupEdgeToEdge(
            padTopSystem = listOf(binding.tinkLicenseAppbar),
            padBottomSystem = listOf(binding.tinkLicenseScrollView),
        )
        setupMaterialScrollListener(binding.tinkLicenseScrollView, binding.tinkLicenseAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.tinkLicenseAppbar, NavigationIcon.Arrow)
    }
}
