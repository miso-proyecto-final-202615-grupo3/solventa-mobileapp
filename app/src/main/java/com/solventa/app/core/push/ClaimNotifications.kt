package com.solventa.app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.solventa.app.MainActivity
import com.solventa.app.R
import com.solventa.app.data.claims.ClaimStatus
import com.solventa.app.feature.claims.list.statusRes

/** Datos válidos de un mensaje `SINIESTRO_ESTADO` (contrato §3.4 de HU4.2.2). */
data class ClaimStatusMessage(
    val claimId: String,
    val number: String,
    val status: ClaimStatus,
    /** HU4.2.3: `true` si el siniestro se creó automáticamente por un evento paramétrico. */
    val parametric: Boolean = false,
) {
    companion object {
        const val TYPE = "SINIESTRO_ESTADO"

        /** `null` si el mensaje no es de este tipo o le falta algún dato: no se muestra nada. */
        fun from(data: Map<String, String>): ClaimStatusMessage? {
            if (data["tipo"] != TYPE) return null
            val id = data["siniestro_id"]?.takeIf { it.isNotBlank() } ?: return null
            val number = data["numero"]?.takeIf { it.isNotBlank() } ?: return null
            val status = data["estado"]?.let(ClaimStatus::fromCode) ?: return null
            return ClaimStatusMessage(id, number, status, parametric = data["canal"] == "PARAMETRICO")
        }
    }
}

/** Canal "Siniestros" y notificación del cambio de estado; tocarla abre el detalle (HU4.2.2, CA2 y CA5). */
object ClaimNotifications {
    const val CHANNEL_ID = "siniestros"
    const val EXTRA_CLAIM_ID = "claim_id"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_claims),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = context.getString(R.string.notification_channel_claims_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Si el usuario no autorizó notificaciones (CA4) no se muestra nada; la app sigue funcionando. */
    fun canNotify(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun show(context: Context, data: Map<String, String>) {
        val message = ClaimStatusMessage.from(data) ?: return
        if (!canNotify(context)) return
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_CLAIM_ID, message.claimId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val requestCode = message.claimId.hashCode()
        val pending = PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val status = context.getString(statusRes(message.status))
        // HU4.2.3: el siniestro automático se anuncia como tal la primera vez (aprobado); los siguientes cambios, igual que siempre.
        val newParametric = message.parametric && message.status == ClaimStatus.APPROVED
        val title = if (newParametric) R.string.notification_parametric_title else R.string.notification_claim_status_title
        val body = if (newParametric) context.getString(R.string.notification_parametric_body) else context.getString(R.string.notification_claim_status_body, status)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(context.getString(title, message.number))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            // Un cambio nuevo del mismo siniestro reemplaza al anterior (mismo id).
            NotificationManagerCompat.from(context).notify(requestCode, notification)
        } catch (_: SecurityException) {
            // El usuario retiró el permiso entre la comprobación y el envío.
        }
    }
}
