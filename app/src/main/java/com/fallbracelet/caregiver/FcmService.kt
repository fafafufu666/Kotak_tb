package com.fallbracelet.caregiver

import com.fallbracelet.caregiver
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Firebase Cloud Messaging from the relay. Messages are data-only, so this runs even when the app
 * is in the background: an ESKALASI rings like it does over the live stream. Only used once the
 * app is built with a Firebase project (`google-services.json`).
 */
class FcmService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        caregiver.handlePush(message.data)
    }

    override fun onNewToken(token: String) {
        caregiver.onNewPushToken(token)
    }
}
