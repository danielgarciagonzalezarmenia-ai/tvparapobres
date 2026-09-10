package com.tvparapobres.app

import android.content.Context
import android.content.res.Configuration

fun isTvDevice(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

fun isTouchDevice(context: Context): Boolean =
    context.resources.configuration.touchscreen == Configuration.TOUCHSCREEN_NOTOUCH