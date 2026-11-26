package org.fossify.gallery.activities

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.graphics.ColorUtils
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.models.FAQItem
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.ActivityFaqBinding
import org.fossify.gallery.extensions.applyThemedSelectionHandles
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.AppearancePalette

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
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(
            binding.faqAppbar,
            NavigationIcon.Arrow,
            topBarColor = getProperBackgroundColor(),
        )
    }

    private fun populateFaqItems() {
        @Suppress("DEPRECATION")
        val faqItems = (intent.getSerializableExtra(EXTRA_APP_FAQ) as? ArrayList<*>)
            ?.filterIsInstance<FAQItem>()
            .orEmpty()
        val highlightTitle = intent.getIntExtra(EXTRA_FAQ_HIGHLIGHT, -1)
        var highlightView: View? = null

        val accentColor = getProperPrimaryColor()
        val textColor = getProperTextColor()
        val palette = AppearanceManager.resolve(this, config)
        val cardBg = resolveCardBackground(palette.background, palette.surface, textColor)
        val cardBorder = ColorUtils.setAlphaComponent(textColor, CARD_BORDER_ALPHA)
        val titleColor = resolveCardTitleColor(accentColor, cardBg)
        val bodyColor = ColorUtils.setAlphaComponent(textColor, BODY_TEXT_ALPHA)
        val boldTypeface = Typeface.create("sans-serif", Typeface.BOLD)

        val smallMargin = resources.getDimensionPixelSize(org.fossify.commons.R.dimen.small_margin)
        val cardPaddingPx = dpToPx(CARD_PADDING_DP)
        val cardSpacingPx = dpToPx(CARD_SPACING_DP)
        val cardRadiusPx = dpToPx(CARD_RADIUS_DP).toFloat()
        val borderWidthPx = dpToPx(1f).coerceAtLeast(1)
        val lineSpacingExtraPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            LINE_SPACING_EXTRA_DP,
            resources.displayMetrics,
        )
        val selectionHighlightColor = ColorUtils.setAlphaComponent(titleColor, SELECTION_HIGHLIGHT_ALPHA)

        faqItems.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index < faqItems.lastIndex) {
                        bottomMargin = cardSpacingPx
                    }
                }
                setPadding(cardPaddingPx, cardPaddingPx, cardPaddingPx, cardPaddingPx)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = cardRadiusPx
                    setColor(cardBg)
                    setStroke(borderWidthPx, cardBorder)
                }
            }

            val titleView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                text = resolveText(item.title)
                setTextColor(titleColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_TEXT_SIZE_SP)
                typeface = boldTypeface
                setTextIsSelectable(true)
                highlightColor = selectionHighlightColor
                applyThemedSelectionHandles(titleColor)
            }
            card.addView(titleView)

            val bodyView = MyTextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = smallMargin
                }
                text = resolveText(item.text)
                setTextColor(bodyColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, BODY_TEXT_SIZE_SP)
                setLineSpacing(lineSpacingExtraPx, 1f)
                setTextIsSelectable(true)
                highlightColor = selectionHighlightColor
                applyThemedSelectionHandles(titleColor)
            }
            card.addView(bodyView)
            binding.faqHolder.addView(card)

            if (highlightTitle != -1 && item.title == highlightTitle) {
                highlightView = card
            }
        }

        highlightView?.let { view ->
            binding.faqScrollView.post {
                binding.faqScrollView.scrollTo(0, view.top)
            }
        }
    }

    private fun resolveCardBackground(background: Int, surface: Int, textColor: Int): Int {
        return if (surface != background) {
            surface
        } else {
            ColorUtils.blendARGB(background, textColor, SURFACE_FALLBACK_BLEND)
        }
    }

    private fun resolveCardTitleColor(accentColor: Int, cardBg: Int): Int {
        if (AppearancePalette.contrastRatio(accentColor, cardBg) >= MIN_TITLE_CONTRAST) {
            return accentColor
        }
        val target = if (ColorUtils.calculateLuminance(cardBg) < DARK_LUMINANCE_THRESHOLD) {
            Color.WHITE
        } else {
            Color.BLACK
        }
        var adjusted = accentColor
        for (step in 1..CONTRAST_BLEND_STEPS) {
            val candidate = ColorUtils.blendARGB(accentColor, target, step * CONTRAST_BLEND_STEP)
            adjusted = candidate
            if (AppearancePalette.contrastRatio(candidate, cardBg) >= MIN_TITLE_CONTRAST) {
                break
            }
        }
        return adjusted
    }

    private fun dpToPx(dp: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        dp,
        resources.displayMetrics,
    ).toInt()

    private fun resolveText(value: Any): String = when (value) {
        is Int -> getString(value)
        is String -> value
        else -> value.toString()
    }

    companion object {
        const val EXTRA_APP_FAQ = "app_faq"
        const val EXTRA_FAQ_HIGHLIGHT = "faq_highlight"
        private const val CARD_BORDER_ALPHA = 20
        private const val BODY_TEXT_ALPHA = 215
        private const val SELECTION_HIGHLIGHT_ALPHA = 60
        private const val CARD_PADDING_DP = 16f
        private const val CARD_SPACING_DP = 12f
        private const val CARD_RADIUS_DP = 16f
        private const val SURFACE_FALLBACK_BLEND = 0.06f
        private const val MIN_TITLE_CONTRAST = 4.5
        private const val DARK_LUMINANCE_THRESHOLD = 0.5
        private const val CONTRAST_BLEND_STEPS = 8
        private const val CONTRAST_BLEND_STEP = 0.08f
        private const val TITLE_TEXT_SIZE_SP = 16f
        private const val BODY_TEXT_SIZE_SP = 14f
        private const val LINE_SPACING_EXTRA_DP = 2f
    }
}
