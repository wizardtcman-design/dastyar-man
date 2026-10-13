package com.dastyar.app

import android.app.Application
import com.dastyar.app.ai.ServiceKeys
import com.dastyar.app.notifications.NotificationHelper
import com.dastyar.app.update.UpdateCheckWorker
import com.dastyar.app.update.UpdateManager

class DastyarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
        UpdateManager.createChannel(this)
        ServiceKeys.load(this)
        // A once-a-day background check, so a new version is heard about even if
        // the app is only opened briefly. Harmless if it never finds anything.
        UpdateCheckWorker.schedule(this)
    }
}
