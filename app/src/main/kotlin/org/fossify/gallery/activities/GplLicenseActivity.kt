package org.fossify.gallery.activities

import android.os.Bundle
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityGplLicenseBinding

class GplLicenseActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityGplLicenseBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.gplLicenseToolbar.title = getString(R.string.gpl_license_title)
        binding.gplLicenseText.text = resources.openRawResource(R.raw.gpl_3_0)
            .bufferedReader()
            .use { it.readText() }
        binding.gplLicenseText.setTextColor(getProperTextColor())

        setupEdgeToEdge(
            padTopSystem = listOf(binding.gplLicenseAppbar),
            padBottomSystem = listOf(binding.gplLicenseScrollView),
        )
        setupMaterialScrollListener(binding.gplLicenseScrollView, binding.gplLicenseAppbar)
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.gplLicenseAppbar, NavigationIcon.Arrow)
    }
}
