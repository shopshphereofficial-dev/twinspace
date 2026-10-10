package com.pixelkaart.twinspace

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** A single launchable app shown in the list. */
data class AppInfo(
    val label: String,
    val packageName: String,
    val icon: Drawable,
    val system: Boolean,
    val component: ComponentName,
    val frozen: Boolean = false
)
