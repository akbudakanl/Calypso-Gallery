package org.fossify.gallery.activities

import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import org.fossify.commons.extensions.adjustAlpha
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.FAQItem
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityFaqBinding

/**
 * Fork-local replacement for Commons' FAQActivity so question titles follow the active
 * appearance palette rather than Commons' static XML colorPrimary.
 */
class FaqActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityFaqBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.faqToolbar.title = getString(R.string.about_faq)
        populateFaqItems()

        setupEdgeToEdge(
            padTopSystem = listOf(binding.faqAppbar),
            padBottomSystem = listOf(binding.faqScrollView),
        )
        setupMaterialScrollListener(
            binding.faqScrollView,
            binding.faqAppbar,
        )
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.faqAppbar, NavigationIcon.Arrow)
    }

    private fun populateFaqItems() {
        @Suppress("DEPRECATION")
        val faqItems = (intent.getSerializableExtra(EXTRA_APP_FAQ) as? ArrayList<*>)
            ?.filterIsInstance<FAQItem>()
            .orEmpty()

        val accentColor = getProperPrimaryColor()
        val textColor = getProperTextColor()
        val dividerColor = textColor.adjustAlpha(DIVIDER_ALPHA)
        val mediumTypeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val smallMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val mediumMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.medium_margin)
        val activityMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val dividerHeight = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.one_dp)
        val lineSpacingExtraPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            LINE_SPACING_EXTRA_DP,
            resources.displayMetrics,
        )

        faqItems.forEachIndexed { index, item ->
            val titleView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                text = resolveText(item.title)
                setTextColor(accentColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
                typeface = mediumTypeface
                setTextIsSelectable(true)
            }
            binding.faqHolder.addView(titleView)

            val bodyView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = smallMargin
                }
                text = resolveText(item.text)
                setTextColor(textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP)
                setLineSpacing(lineSpacingExtraPx, 1f)
                setTextIsSelectable(true)
            }
            binding.faqHolder.addView(bodyView)

            if (index < faqItems.lastIndex) {
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
                binding.faqHolder.addView(divider)
            }
        }
    }

    private fun resolveText(value: Any): String = when (value) {
        is Int -> getString(value)
        is String -> value
        else -> value.toString()
    }

    companion object {
        const val EXTRA_APP_FAQ = "app_faq"
        private const val DIVIDER_ALPHA = 0.2f
        private const val TITLE_TEXT_SIZE_SP = 16f
        private const val BODY_TEXT_SIZE_SP = 14f
        private const val LINE_SPACING_EXTRA_DP = 2f
    }
}
