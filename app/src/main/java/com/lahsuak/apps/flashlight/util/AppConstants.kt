package com.lahsuak.apps.flashlight.util

object AppConstants {
    const val WEBSITE = "https://zaap.bio/KaushalVasava"

    const val SETTING_DATA = "SETTING_DATA"
    const val HAPTIC_FEEDBACK = "haptic_feedback"
    const val SHOW_NOTIFICATION = "show_notification"
    const val TOUCH_SOUND = "sound_feedback"
    const val FLASH_ON_START = "flash_on_start"
    const val BIG_FLASH_AS_SWITCH = "big_flash_as_switch"
    const val SHAKE_TO_LIGHT = "shake_to_light"
    const val SHAKE_SENSITIVITY = "shake_sensitivity"
    const val MIN_TIME_BETWEEN_SHAKES_MILLIsECS = 1000
    const val CALL_NOTIFICATION = "call_notification"
    const val FLASH_EXIST = "flash_exist"
    const val UPDATE_REQUEST_CODE = 123
    const val TEL = "tel:"
    const val MAIL_TO = "mailto:"
    const val IMAGE_MIME_TYPE = "text/plain"
    const val SHARE_BY = "Share by"
    const val PACKAGE = "package"
    const val MARKET_DETAILS_ID = "market://details?id="

    //notification
    const val NOTIFICATION_CHANNEL_ID = "com.lahsuak.flashlight.FLASHLIGHT"
    const val NOTIFICATION_CHANNEL_NAME = "Flashlight Channel"
    const val NOTIFICATION_DETAILS = "Flashlight channel"

    //foreground service notification
    const val PLAY = "Play"
    const val PAUSE = "Pause"
    const val ACTION_NAME = "action_name"

    //blink delay
    const val BLINK_DELAY = 300L

    const val PHONE_NUMBER_PATTERN =
        "^\\s*(?:\\+?(\\d{1,3}))?[-. (]*(\\d{3})[-. )]*(\\d{3})[-. ]*(\\d{4})(?: *x(\\d+))?\\s*$"

    const val MAX_COUNTER_FOR_MORE_APPS = 5
    const val MORE_APPS_DELAY = 10000L
}

