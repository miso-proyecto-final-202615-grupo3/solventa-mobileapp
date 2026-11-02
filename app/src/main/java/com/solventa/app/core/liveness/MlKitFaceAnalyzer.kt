package com.solventa.app.core.liveness

import android.graphics.Rect
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.solventa.app.core.camera.FrameEncoder
import kotlin.math.abs

/**
 * Adapter: traduce los rostros de ML Kit a [FaceSignals] y, cuando la guía lo pide,
 * codifica ese mismo fotograma a JPEG en memoria (nunca a disco).
 *
 * @param onSignals procesa las señales y devuelve la captura que hay que tomar de este fotograma, si aplica.
 * @param onFrameCaptured recibe el JPEG y la hora (ms) en que se tomó.
 */
class MlKitFaceAnalyzer(
    private val onSignals: (FaceSignals) -> FrameSlot?,
    private val onFrameCaptured: (FrameSlot, ByteArray, Long) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
) : ImageAnalysis.Analyzer {

    // Modelo empaquetado en el APK: funciona sin Google Play Services.
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setMinFaceSize(0.15f)
            .build(),
    )

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val rotation = imageProxy.imageInfo.rotationDegrees
        val timestamp = clock()
        val input = InputImage.fromMediaImage(mediaImage, rotation)
        val (width, height) = if (rotation == 90 || rotation == 270) {
            imageProxy.height to imageProxy.width
        } else {
            imageProxy.width to imageProxy.height
        }
        detector.process(input)
            .addOnSuccessListener { faces ->
                val slot = onSignals(toSignals(faces, width, height, timestamp))
                if (slot != null) {
                    FrameEncoder.encode(imageProxy)?.let { jpeg -> onFrameCaptured(slot, jpeg, timestamp) }
                }
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    fun close() = detector.close()

    companion object {
        internal fun toSignals(faces: List<Face>, width: Int, height: Int, timestamp: Long): FaceSignals {
            if (faces.size != 1) return FaceSignals(faceCount = faces.size, timestampMillis = timestamp)
            val face = faces.first()
            val box: Rect = face.boundingBox
            val area = (width * height).coerceAtLeast(1)
            val centered = abs(box.exactCenterX() - width / 2f) < width * 0.2f &&
                abs(box.exactCenterY() - height / 2f) < height * 0.2f
            return FaceSignals(
                faceCount = 1,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
                headEulerY = face.headEulerAngleY,
                faceBoxRatio = (box.width() * box.height()).toFloat() / area,
                faceCentered = centered,
                timestampMillis = timestamp,
            )
        }
    }
}
