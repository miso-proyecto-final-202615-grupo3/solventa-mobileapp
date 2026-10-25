package com.solventa.app.feature.activation

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.solventa.app.data.activation.*
import com.solventa.app.core.device.SharedPreferencesInstallationIdProvider
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun ActivationScreen(email: String, repository: ActivationRepository, onApproved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val installationId = remember(context) { SharedPreferencesInstallationIdProvider(context).get() }
    var challenge by remember { mutableStateOf<LivenessChallenge?>(null) }
    var message by remember { mutableStateOf("Solicita el reto para comenzar.") }
    val frames = remember { mutableStateListOf<ByteArray>() }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { frames += it.jpeg() }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else message = "Debes permitir el uso de la cámara para continuar."
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Prueba de vida", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp)); Text(message)
        Spacer(Modifier.height(24.dp))
        if (challenge == null) Button(onClick = {
            scope.launch {
                when (val result = repository.start(email, installationId, "ANDROID", android.os.Build.MODEL, android.os.Build.VERSION.RELEASE)) {
                    is ActivationStartResult.ChallengeReady -> { challenge = result.challenge; message = "Mira la cámara y parpadea. Toma tres fotos: inicio, gesto y final." }
                    ActivationStartResult.DeviceAlreadyLinked -> onApproved()
                    else -> message = "No fue posible iniciar la prueba. Verifica el servicio local."
                }
            }
        }) { Text("Iniciar prueba") }
        else {
            Button(onClick = { permission.launch(android.Manifest.permission.CAMERA) }, enabled = frames.size < 3) { Text("Capturar foto ${frames.size + 1} de 3") }
            Spacer(Modifier.height(12.dp))
            Button(onClick = {
                scope.launch {
                    when (repository.verify(email, challenge!!, frames.toList())) {
                        ActivationVerificationResult.Approved -> { frames.clear(); onApproved() }
                        is ActivationVerificationResult.Rejected -> message = "La prueba fue rechazada. Intenta de nuevo."
                        ActivationVerificationResult.NetworkError -> message = "No se pudo conectar con el servicio."
                        ActivationVerificationResult.ServiceUnavailable -> message = "El servicio no pudo procesar la prueba."
                    }
                }
            }, enabled = frames.size == 3) { Text("Enviar prueba") }
        }
    }
}

private fun Bitmap.jpeg(): ByteArray = ByteArrayOutputStream().use { output ->
    compress(Bitmap.CompressFormat.JPEG, 80, output); output.toByteArray()
}
