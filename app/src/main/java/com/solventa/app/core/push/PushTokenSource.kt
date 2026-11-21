package com.solventa.app.core.push

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

fun interface PushTokenSource { suspend fun currentToken(): String? }

class FirebasePushTokenSource : PushTokenSource {
    override suspend fun currentToken(): String? = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull()
}

class NoPushTokenSource : PushTokenSource {
    override suspend fun currentToken(): String? = null
}
