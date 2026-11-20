package org.fossify.gallery.dialogs

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.gallery.databinding.LayoutOnboardingAllFilesBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.OnboardingUiHelper

class AllFilesPermissionDialog(
    val activity: BaseSimpleActivity,
    @Suppress("UNUSED_PARAMETER") message: String = "",
    val callback: (result: Boolean) -> Unit,
    val neutralPressed: () -> Unit
) {
    private var dialog: Dialog? = null

    init {
        val binding = LayoutOnboardingAllFilesBinding.inflate(activity.layoutInflater)
        val palette = AppearanceManager.resolve(activity, activity.config)
        val pulseAnimator = OnboardingUiHelper.styleAllFilesPane(activity, binding, palette)

        OnboardingUiHelper.applyInsets(
            binding.onboardingAllFilesScroll,
            binding.onboardingAllFilesFooter
        )

        dialog = Dialog(activity, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar).apply {
            setContentView(binding.root)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(palette.background))
                OnboardingUiHelper.applySystemBars(this, palette)
            }
            setOnCancelListener {
                neutralPressed()
            }
            setOnDismissListener {
                pulseAnimator?.cancel()
            }
        }

        binding.onboardingAllFilesGrantBtn.setOnClickListener {
            dialog?.dismiss()
            callback(true)
        }

        binding.onboardingAllFilesSkipBtn.setOnClickListener {
            dialog?.dismiss()
            neutralPressed()
        }

        dialog?.show()
    }
}
