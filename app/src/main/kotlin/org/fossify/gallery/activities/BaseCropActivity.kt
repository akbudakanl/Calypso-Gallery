package org.fossify.gallery.activities

import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import androidx.core.view.isInvisible
import androidx.lifecycle.lifecycleScope
import com.canhub.cropper.CropImageView
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fossify.gallery.helpers.media.IsolatedDecodeRequest
import org.fossify.gallery.helpers.media.IsolatedDecoderClient
import org.fossify.gallery.helpers.media.IsolatedFormatSniffer
import java.io.File

abstract class BaseCropActivity : SimpleActivity() {
    private var bitmapCroppingJob: Job? = null
    private var bitmapLoadingJob: Job? = null

    abstract val cropImageView: CropImageView

    abstract fun onImageCropped(bitmap: Bitmap?, error: Exception?)

    private fun setCropProgressBarVisibility(visible: Boolean) {
        cropImageView
            .findViewById<View>(com.canhub.cropper.R.id.CropProgressBar)
            ?.isInvisible = visible.not()
    }

    protected fun loadIsolatedImageIntoCropView(uri: Uri, onError: (() -> Unit)? = null) {
        bitmapLoadingJob?.cancel()
        bitmapLoadingJob = lifecycleScope.launch(Dispatchers.Main) {
            val flags = IsolatedDecodeRequest.FLAG_APPLY_EXIF_ORIENTATION or
                IsolatedDecodeRequest.FLAG_ALLOW_LARGE_OUTPUT
            val bitmap = withContext(Dispatchers.IO) {
                val path = uri.path
                if (uri.scheme == "file" && !path.isNullOrEmpty()) {
                    IsolatedDecoderClient.decodeFile(
                        context = applicationContext,
                        file = File(path),
                        maxWidth = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                        maxHeight = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                        flags = flags,
                    )
                } else {
                    IsolatedDecoderClient.decodeUri(
                        context = applicationContext,
                        uri = uri,
                        maxWidth = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                        maxHeight = IsolatedFormatSniffer.MAX_OUTPUT_DIMENSION,
                        flags = flags,
                    )
                }
            }
            if (isFinishing || isDestroyed) return@launch
            if (bitmap != null) {
                cropImageView.setImageBitmap(bitmap)
            } else {
                onError?.invoke()
            }
        }
    }

    protected fun cropImage() {
        setCropProgressBarVisibility(true)
        bitmapCroppingJob?.cancel()
        bitmapCroppingJob = lifecycleScope.launch(CoroutineExceptionHandler { _, t ->
            onImageCropped(bitmap = null, error = Exception(t))
        }) {
            val bitmap = withContext(Dispatchers.Default) {
                cropImageView.getCroppedImage()
            }
            onImageCropped(bitmap, null)
        }.apply {
            lifecycleScope.launch(Dispatchers.Main) {
                setCropProgressBarVisibility(false)
            }
        }
    }
}
