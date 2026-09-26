package com.dastyar.app

import android.app.Application
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.notifications.NotificationHelper

class DastyarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        ServiceKeys.load(this)
    }
}
