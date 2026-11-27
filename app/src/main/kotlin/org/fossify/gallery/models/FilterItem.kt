package org.fossify.gallery.models

import android.graphics.Bitmap
import org.fossify.gallery.helpers.filters.PhotoFilter

data class FilterItem(var bitmap: Bitmap, val filter: PhotoFilter)
