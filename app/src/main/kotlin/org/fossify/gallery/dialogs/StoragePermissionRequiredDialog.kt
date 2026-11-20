package org.fossify.gallery.dialogs

import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databinding.LayoutOnboardingMediaPermissionBinding
import org.fossify.gallery.extensions.config
import org.fossify.gallery.helpers.AppearanceManager
import org.fossify.gallery.helpers.OnboardingUiHelper

class StoragePermissionRequiredDialog(
    val activity: SimpleActivity,
    val onOkay: () -> Unit,
    val onCancel: () -> Unit,
    val callback: (dialog: Dialog) -> Unit
) {

    init {
        val binding = LayoutOnboardingMediaPermissionBinding.inflate(activity.layoutInflater)
        val palette = AppearanceManager.resolve(activity, activity.config)
        val pulseAnimator = OnboardingUiHelper.styleMediaPermissionPane(activity, binding, palette)

        OnboardingUiHelper.applyInsets(
            binding.onboardingMediaScroll,
            binding.onboardingMediaFooter
        )

        val dialog = Dialog(activity, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar).apply {
            setContentView(binding.root)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            window?.apply {
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundDrawable(ColorDrawable(palette.background))
                OnboardingUiHelper.applySystemBars(this, palette)
            }
            setOnDismissListener {
                pulseAnimator?.cancel()
            }
        }

        binding.onboardingMediaSettingsBtn.setOnClickListener {
            dialog.dismiss()
            onOkay()
        }

        binding.onboardingMediaCloseBtn.setOnClickListener {
            dialog.dismiss()
            onCancel()
        }

        dialog.show()
        callback(dialog)
    }
}
