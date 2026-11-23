package com.solventa.app.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.solventa.app.SolventaApplication

class SolventaMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        (application as SolventaApplication).container.pushRegistrar.register(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ClaimNotifications.show(this, message.data)
    }
}
