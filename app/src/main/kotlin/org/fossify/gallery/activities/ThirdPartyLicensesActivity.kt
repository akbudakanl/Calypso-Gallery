package org.fossify.gallery.activities

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.StringRes
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.launchViewIntent
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityThirdPartyLicensesBinding

class ThirdPartyLicensesActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityThirdPartyLicensesBinding::inflate)

    private data class ThirdPartyLicenseItem(
        @StringRes val titleRes: Int,
        @StringRes val textRes: Int,
        @StringRes val urlRes: Int,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.thirdPartyLicensesToolbar.title = getString(R.string.about_third_party_licenses)
        populateLicenses()

        setupEdgeToEdge(
            padTopSystem = listOf(binding.thirdPartyLicensesAppbar),
            padBottomSystem = listOf(binding.thirdPartyLicensesScrollView),
        )
        setupMaterialScrollListener(
            binding.thirdPartyLicensesScrollView,
            binding.thirdPartyLicensesAppbar,
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.thirdPartyLicensesAppbar, NavigationIcon.Arrow)
    }

    private fun populateLicenses() {
        val accentColor = getProperPrimaryColor()
        val textColor = getProperTextColor()
        val dividerColor = textColor.adjustAlpha(DIVIDER_ALPHA)
        val selectableBackground = TypedValue().apply {
            theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
        }.resourceId
        val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val smallMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val mediumMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
        val activityMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val minTouchHeight = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.list_touch_target_min)
        val dividerHeight = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.one_dp)
        val lineSpacingExtraPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            LINE_SPACING_EXTRA_DP,
            resources.displayMetrics,
        )

        val licenses = getThirdPartyLicenses()
        licenses.forEachIndexed { index, item ->
            val titleView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                text = getString(item.titleRes)
                setTextColor(accentColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
                typeface = mediumTypeface
                gravity = Gravity.CENTER_VERTICAL
                minHeight = minTouchHeight
                setPadding(0, smallMargin, 0, smallMargin)
                if (selectableBackground != 0) {
                    setBackgroundResource(selectableBackground)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    launchViewIntent(getString(item.urlRes))
                }
            }
            binding.thirdPartyLicensesHolder.addView(titleView)

            val bodyView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = smallMargin
                }
                text = getString(item.textRes)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP)
                setLineSpacing(lineSpacingExtraPx, 1f)
                setTextIsSelectable(true)
            }
            binding.thirdPartyLicensesHolder.addView(bodyView)

            if (index < licenses.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dividerHeight,
                    ).apply {
                        topMargin = activityMargin
                        bottomMargin = mediumMargin
                    }
                    setBackgroundColor(dividerColor)
                }
                binding.thirdPartyLicensesHolder.addView(divider)
            }
        }
    }

    private fun getThirdPartyLicenses(): List<ThirdPartyLicenseItem> = listOf(
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.kotlin_title,
            org.fossify.commons.R.string.kotlin_text,
            org.fossify.commons.R.string.kotlin_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_fossify_commons_title,
            R.string.license_fossify_commons_text,
            R.string.license_fossify_commons_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_tink_title,
            R.string.license_tink_text,
            R.string.license_tink_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_rustcrypto_argon2_title,
            R.string.license_rustcrypto_argon2_text,
            R.string.license_rustcrypto_argon2_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.subsampling_title,
            org.fossify.commons.R.string.subsampling_text,
            org.fossify.commons.R.string.subsampling_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.glide_title,
            org.fossify.commons.R.string.glide_text,
            org.fossify.commons.R.string.glide_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_webpdecoder_title,
            R.string.license_webpdecoder_text,
            R.string.license_webpdecoder_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_jxl_coder_title,
            R.string.license_jxl_coder_text,
            R.string.license_jxl_coder_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.cropper_title,
            org.fossify.commons.R.string.cropper_text,
            org.fossify.commons.R.string.cropper_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.rtl_viewpager_title,
            org.fossify.commons.R.string.rtl_viewpager_text,
            org.fossify.commons.R.string.rtl_viewpager_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.pattern_title,
            org.fossify.commons.R.string.pattern_text,
            org.fossify.commons.R.string.pattern_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.gif_drawable_title,
            org.fossify.commons.R.string.gif_drawable_text,
            org.fossify.commons.R.string.gif_drawable_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.exoplayer_title,
            org.fossify.commons.R.string.exoplayer_text,
            org.fossify.commons.R.string.exoplayer_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.sanselan_title,
            org.fossify.commons.R.string.sanselan_text,
            org.fossify.commons.R.string.sanselan_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_exif_extended_title,
            R.string.license_exif_extended_text,
            R.string.license_exif_extended_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.filters_title,
            org.fossify.commons.R.string.filters_text,
            org.fossify.commons.R.string.filters_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_androidsvg_title,
            R.string.license_androidsvg_text,
            R.string.license_androidsvg_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.gesture_views_title,
            org.fossify.commons.R.string.gesture_views_text,
            org.fossify.commons.R.string.gesture_views_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.apng_title,
            org.fossify.commons.R.string.apng_text,
            org.fossify.commons.R.string.apng_url,
        ),
        ThirdPartyLicenseItem(
            org.fossify.commons.R.string.gson_title,
            org.fossify.commons.R.string.gson_text,
            org.fossify.commons.R.string.gson_url,
        ),
        ThirdPartyLicenseItem(
            R.string.license_material_icons_title,
            R.string.license_material_icons_text,
            R.string.license_material_icons_url,
        ),
    )

    companion object {
        private const val DIVIDER_ALPHA = 0.2f
        private const val TITLE_TEXT_SIZE_SP = 16f
        private const val BODY_TEXT_SIZE_SP = 14f
        private const val LINE_SPACING_EXTRA_DP = 2f
    }
}
