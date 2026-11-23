package com.solventa.app.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PendingDeepLink {
    private val pending = MutableStateFlow<String?>(null)
    val claimId: StateFlow<String?> = pending.asStateFlow()

    fun setClaim(id: String?) { if (!id.isNullOrBlank()) pending.value = id }
    fun consume(): String? = pending.value.also { pending.value = null }
}
