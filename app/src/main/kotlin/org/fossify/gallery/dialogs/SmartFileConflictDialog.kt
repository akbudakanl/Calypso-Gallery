package org.fossify.gallery.dialogs

import org.fossify.gallery.extensions.secureVaultWindow

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.helpers.CONFLICT_KEEP_BOTH
import org.fossify.commons.helpers.CONFLICT_OVERWRITE
import org.fossify.commons.helpers.CONFLICT_SKIP
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.R
import org.fossify.gallery.databinding.DialogSmartFileConflictBinding
import org.fossify.gallery.helpers.CryptoManager
import org.fossify.gallery.helpers.EncryptedGlideOptions
import org.fossify.gallery.helpers.TYPE_VIDEOS
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.fossify.gallery.helpers.compareFilesForSmartConflict
import org.fossify.gallery.helpers.media.IsolatedDecoderClient
import org.fossify.gallery.helpers.verifyFileIntegrity

data class SmartConflictItem(
    val resolutionKey: String,
    val displayName: String,
    val sourcePath: String,
    val existingPath: String,
    val useFastLargeDuplicateCheck: Boolean = false
)

private data class SmartSideDisplayInfo(
    val isVideo: Boolean,
    val metaText: String,
    val integrityFailed: Boolean
)

private const val MAX_INLINE_MAC_VERIFY_BYTES = 64L * 1024L * 1024L

class SmartFileConflictDialog(
    val activity: Activity,
    val conflict: SmartConflictItem,
    val currentIndex: Int,
    val totalCount: Int,
    val callback: (resolution: Int, applyToAll: Boolean) -> Unit
) {
    private val dismissed = AtomicBoolean(false)
    private var previewDialog: AlertDialog? = null
    private var currentDifference: CryptoManager.SmartConflictDifference? = null

    init {
        val binding = DialogSmartFileConflictBinding.inflate(activity.layoutInflater)
        val textColor = activity.getProperTextColor()

        val baseMessage = String.format(
            activity.getString(org.fossify.commons.R.string.file_already_exists),
            conflict.displayName
        )
        binding.smartConflictMessage.apply {
            text = if (totalCount > 1) {
                activity.getString(R.string.smart_conflict_counter, baseMessage, currentIndex + 1, totalCount)
            } else {
                baseMessage
            }
            setTextColor(textColor)
        }

        binding.smartConflictSourceLabel.setTextColor(textColor)
        binding.smartConflictExistingLabel.setTextColor(textColor)
        binding.smartConflictSourceMeta.setTextColor(textColor)
        binding.smartConflictExistingMeta.setTextColor(textColor)
        binding.smartConflictPreviewHint.setTextColor(textColor)
        binding.smartConflictDifferenceBanner.setTextColor(textColor)

        binding.smartConflictKeepBoth.text = activity.getString(org.fossify.commons.R.string.keep_both)
        binding.smartConflictOverwrite.text = activity.getString(org.fossify.commons.R.string.overwrite)
        binding.smartConflictSkip.text = activity.getString(org.fossify.commons.R.string.skip)
        binding.smartConflictApplyToAll.apply {
            text = activity.getString(org.fossify.commons.R.string.apply_to_all)
            visibility = if (totalCount > 1) View.VISIBLE else View.GONE
        }

        loadSideThumbnail(
            path = conflict.sourcePath,
            imageView = binding.smartConflictSourceThumbnail,
            progressBar = binding.smartConflictSourceProgress,
            errorOverlay = binding.smartConflictSourceErrorOverlay
        )
        loadSideThumbnail(
            path = conflict.existingPath,
            imageView = binding.smartConflictExistingThumbnail,
            progressBar = binding.smartConflictExistingProgress,
            errorOverlay = binding.smartConflictExistingErrorOverlay
        )
        loadComparisonDetails(binding)

        binding.smartConflictSourcePreviewFrame.setOnClickListener {
            showFullPreview(
                path = conflict.sourcePath,
                title = "${activity.getString(R.string.smart_conflict_incoming_label)} — ${conflict.displayName}"
            )
        }
        binding.smartConflictExistingPreviewFrame.setOnClickListener {
            showFullPreview(
                path = conflict.existingPath,
                title = "${activity.getString(R.string.smart_conflict_existing_label)} — ${conflict.displayName}"
            )
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ ->
                dismissed.set(true)
                previewDialog?.dismiss()
                val resolution = when (binding.smartConflictRadioGroup.checkedRadioButtonId) {
                    R.id.smart_conflict_skip -> CONFLICT_SKIP
                    R.id.smart_conflict_overwrite -> CONFLICT_OVERWRITE
                    else -> CONFLICT_KEEP_BOTH
                }
                callback(resolution, binding.smartConflictApplyToAll.isChecked)
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel) { _, _ ->
                dismissed.set(true)
                previewDialog?.dismiss()
            }
            .setOnDismissListener {
                dismissed.set(true)
                previewDialog?.dismiss()
            }
            .apply {
                activity.setupDialogStuff(binding.root, this) {
                    val redColor = activity.getDestructiveRedColor()
                    binding.smartConflictSourceIntegrityWarning.setTextColor(redColor)
                    binding.smartConflictExistingIntegrityWarning.setTextColor(redColor)
                    applyDifferenceBanner(binding, currentDifference)
                }
            }
    }

    private fun applyDifferenceBanner(
        binding: DialogSmartFileConflictBinding,
        difference: CryptoManager.SmartConflictDifference?
    ) {
        val banner = binding.smartConflictDifferenceBanner
        if (difference == null) {
            banner.visibility = View.VISIBLE
            banner.text = activity.getString(R.string.smart_conflict_comparing)
            banner.setTextColor(activity.getProperTextColor())
            banner.setTypeface(null, Typeface.NORMAL)
            return
        }

        when (difference) {
            CryptoManager.SmartConflictDifference.CONTENT_DIFFERS -> {
                val isDarkBg = activity.getProperBackgroundColor().getContrastColor() == Color.WHITE
                banner.visibility = View.VISIBLE
                banner.text = activity.getString(R.string.smart_conflict_content_differs_warning)
                banner.setTextColor(if (isDarkBg) Color.parseColor("#FFB74D") else Color.parseColor("#E65100"))
                banner.setTypeface(null, Typeface.BOLD)
            }
            CryptoManager.SmartConflictDifference.DATE_TAKEN_DIFFERS -> {
                banner.visibility = View.VISIBLE
                banner.text = activity.getString(R.string.smart_conflict_date_taken_differs)
                banner.setTextColor(activity.getProperTextColor())
                banner.setTypeface(null, Typeface.NORMAL)
            }
            CryptoManager.SmartConflictDifference.MODIFIED_DATE_DIFFERS -> {
                banner.visibility = View.VISIBLE
                banner.text = activity.getString(R.string.smart_conflict_modified_date_differs)
                banner.setTextColor(activity.getProperTextColor())
                banner.setTypeface(null, Typeface.NORMAL)
            }
            CryptoManager.SmartConflictDifference.BOTH_DATES_DIFFER -> {
                banner.visibility = View.VISIBLE
                banner.text = activity.getString(R.string.smart_conflict_both_dates_differ)
                banner.setTextColor(activity.getProperTextColor())
                banner.setTypeface(null, Typeface.NORMAL)
            }
            CryptoManager.SmartConflictDifference.IDENTICAL_MATCH -> {
                banner.visibility = View.GONE
            }
        }
    }

    private fun buildSideDisplayInfo(
        file: File,
        size: Long,
        otherSize: Long,
        lastModified: Long,
        dateTaken: Long,
        showDateTaken: Boolean
    ): SmartSideDisplayInfo {
        val isEncrypted = CryptoManager.isEncryptedFile(file.name)
        val meta = if (isEncrypted) {
            CryptoManager.readMetadata(file, includeThumbnail = false, cacheResult = false)
        } else {
            null
        }
        val effectiveName = meta?.originalName ?: file.name
        val isVideo = if (meta != null) meta.mediaType == TYPE_VIDEOS else effectiveName.isVideoFast()

        val dimensions = if (!isEncrypted && !isVideo && !effectiveName.isSvg() && file.isFile) {
            IsolatedDecoderClient.probeFileBounds(activity, file)?.let { bounds ->
                "${bounds.width}×${bounds.height}"
            }
        } else {
            null
        }

        val integrityFailed = if (isEncrypted) {
            when {
                meta == null -> true
                meta.isMacAuthenticated && file.length() <= MAX_INLINE_MAC_VERIFY_BYTES -> {
                    !CryptoManager.verifyFileIntegrity(file, meta)
                }
                else -> false
            }
        } else {
            !file.exists() || !file.isFile
        }

        val metaLines = ArrayList<String>(4)
        if (file.exists() && size >= 0L) {
            val formattedSize = size.formatSize()
            val sizeLine = if (otherSize >= 0L && size != otherSize && formattedSize == otherSize.formatSize()) {
                "$formattedSize ($size B)"
            } else {
                formattedSize
            }
            metaLines.add(sizeLine)
        }
        if (dimensions != null) {
            metaLines.add(dimensions)
        }
        if (showDateTaken && dateTaken > 0L) {
            metaLines.add(activity.getString(R.string.smart_conflict_meta_date_taken, dateTaken.formatDate(activity)))
        }
        if (lastModified > 0L) {
            val modFormatted = lastModified.formatDate(activity)
            val modLine = if (showDateTaken) {
                activity.getString(R.string.smart_conflict_meta_date_modified, modFormatted)
            } else {
                modFormatted
            }
            metaLines.add(modLine)
        }

        return SmartSideDisplayInfo(
            isVideo = isVideo,
            metaText = metaLines.joinToString("\n"),
            integrityFailed = integrityFailed
        )
    }

    private fun loadComparisonDetails(binding: DialogSmartFileConflictBinding) {
        ensureBackgroundThread {
            val srcFile = File(conflict.sourcePath)
            val existingFile = File(conflict.existingPath)
            val comparison = CryptoManager.compareFilesForSmartConflict(
                sourceFile = srcFile,
                existingFile = existingFile,
                fastSampling = conflict.useFastLargeDuplicateCheck,
                context = activity.applicationContext
            )

            val showDateTaken = comparison.hasDistinctDateTaken ||
                comparison.difference == CryptoManager.SmartConflictDifference.DATE_TAKEN_DIFFERS ||
                comparison.difference == CryptoManager.SmartConflictDifference.BOTH_DATES_DIFFER

            val srcInfo = buildSideDisplayInfo(
                file = srcFile,
                size = comparison.sourceSize,
                otherSize = comparison.existingSize,
                lastModified = comparison.sourceLastModified,
                dateTaken = comparison.sourceDateTaken,
                showDateTaken = showDateTaken
            )
            val existingInfo = buildSideDisplayInfo(
                file = existingFile,
                size = comparison.existingSize,
                otherSize = comparison.sourceSize,
                lastModified = comparison.existingLastModified,
                dateTaken = comparison.existingDateTaken,
                showDateTaken = showDateTaken
            )

            activity.runOnUiThread {
                if (dismissed.get() || activity.isFinishing || activity.isDestroyed) {
                    return@runOnUiThread
                }
                currentDifference = comparison.difference
                applyDifferenceBanner(binding, comparison.difference)

                binding.smartConflictSourceVideoBadge.visibility = if (srcInfo.isVideo) View.VISIBLE else View.GONE
                binding.smartConflictSourceMeta.text = srcInfo.metaText
                if (srcInfo.integrityFailed) {
                    binding.smartConflictSourceIntegrityWarning.visibility = View.VISIBLE
                }

                binding.smartConflictExistingVideoBadge.visibility = if (existingInfo.isVideo) View.VISIBLE else View.GONE
                binding.smartConflictExistingMeta.text = existingInfo.metaText
                if (existingInfo.integrityFailed) {
                    binding.smartConflictExistingIntegrityWarning.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun loadSideThumbnail(
        path: String,
        imageView: ImageView,
        progressBar: ProgressBar,
        errorOverlay: View
    ) {
        if (activity.isFinishing || activity.isDestroyed) return
        progressBar.indeterminateTintList = ColorStateList.valueOf(activity.getProperPrimaryColor())
        val file = File(path)
        val isEncrypted = CryptoManager.isEncryptedFile(file.name)
        val effectiveName = if (isEncrypted) conflict.displayName else path
        val signature = ObjectKey("${path}_${file.lastModified()}_${System.nanoTime()}")

        val options = RequestOptions()
            .signature(signature)
            .skipMemoryCache(true)
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .format(DecodeFormat.PREFER_ARGB_8888)
            .dontAnimate()
            .fitCenter()

        if (isEncrypted) {
            options.set(EncryptedGlideOptions.PREFER_FULL_DECRYPTED_CONTENT, true)
        }

        Glide.with(activity.applicationContext)
            .asBitmap()
            .load(path)
            .apply(options)
            .listener(object : RequestListener<Bitmap> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Bitmap>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return true
                    progressBar.visibility = View.GONE
                    errorOverlay.visibility = View.VISIBLE
                    return true
                }

                override fun onResourceReady(
                    resource: Bitmap,
                    model: Any,
                    target: Target<Bitmap>,
                    dataSource: DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return true
                    progressBar.visibility = View.GONE
                    errorOverlay.visibility = View.GONE
                    return false
                }
            })
            .into(imageView)
    }

    private fun showFullPreview(path: String, title: String) {
        if (dismissed.get() || activity.isFinishing || activity.isDestroyed) return
        previewDialog?.dismiss()

        val container = FrameLayout(activity).apply {
            val pad = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
            setPadding(pad, pad, pad, pad)
        }
        val previewImage = ImageView(activity).apply {
            val heightPx = (activity.resources.displayMetrics.heightPixels * 0.55f).toInt().coerceAtLeast(360)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val progressBar = ProgressBar(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
        }
        val errorOverlay = MyTextView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.CENTER
            )
            text = activity.getString(R.string.smart_conflict_corrupted_unreadable)
            setTextColor(activity.getProperTextColor())
            visibility = View.GONE
        }
        container.addView(previewImage)
        container.addView(progressBar)
        container.addView(errorOverlay)

        loadSideThumbnail(path, previewImage, progressBar, errorOverlay)

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .apply {
                activity.setupDialogStuff(container, this, titleText = title) { alertDialog -> alertDialog.secureVaultWindow()
                    previewDialog = alertDialog
                }
            }
    }
}
