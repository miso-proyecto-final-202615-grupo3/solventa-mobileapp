# HU3.1.1 · Arquitectura visual (app Android)

Complementa [HU3.1.1-plan-tecnico.md](HU3.1.1-plan-tecnico.md). Los diagramas de sistema (componentes, despliegue, secuencias entre servicios, estados de sesión e intento, modelo de datos) son canónicos en `solventa-backend/docs/planning/HU3.1.1-diagramas.md`. Aquí solo va lo propio de la app. Lo punteado es Didit (demostración, desactivado por defecto).

## 1. Navegación

```mermaid
stateDiagram-v2
    [*] --> LOGIN
    LOGIN --> ACCOUNT_NOT_FOUND: cuenta no existe
    LOGIN --> HOME: vinculado y sin oferta pendiente
    LOGIN --> BIOMETRIC_OFFER: vinculado, biometría sin configurar
    LOGIN --> BIOMETRIC_CONSENT: no vinculado o sin bloque cliente
    BIOMETRIC_CONSENT --> CONSENT_DECLINED: no acepta
    BIOMETRIC_CONSENT --> ACTIVATION_INTRO: acepta o ya había aceptado
    state activation {
        ACTIVATION_INTRO --> ACTIVATION_CAPTURE: sesión FRAMES + permiso de cámara
        ACTIVATION_INTRO --> LAUNCHING_SDK: sesión SDK
        ACTIVATION_CAPTURE --> ACTIVATION_RESULT: verificación
        LAUNCHING_SDK --> ACTIVATION_RESULT: verificación
        ACTIVATION_RESULT --> ACTIVATION_INTRO: reintentar (sesión nueva)
    }
    ACTIVATION_INTRO --> HOME: instalación ya vinculada
    ACTIVATION_RESULT --> BIOMETRIC_OFFER: aprobado
    ACTIVATION_RESULT --> LOGIN: agotado o sesión vencida
    BIOMETRIC_OFFER --> HOME
    CONSENT_DECLINED --> LOGIN
```

## 2. Clases

```mermaid
classDiagram
    direction LR

    class ActivationViewModel {
      +uiState StateFlow~ActivationUiState~
      +onStart()
      +onFaceSignals(signals)
      +onFrameCaptured(slot, bytes, instant)
      +onDiditResult(status)
      +onRetry()
      +onCancel()
    }
    class ActivationRepository {
      <<interface>>
      +startSession(token, device) SessionResult
      +verify(token, session, outcome) VerificationResult
    }
    class RemoteActivationRepository
    class MockActivationRepository
    class ActivationSession {
      +sessionId
      +expiresAt
      +remainingAttempts
      +capture CaptureType
    }
    class CaptureType {
      <<sealed>>
    }
    class Frames {
      +challenge
      +nonce
    }
    class Sdk {
      +sdkName
      +sessionToken
    }
    class CaptureLauncherFactory {
      +forType(type) LivenessCaptureLauncher
    }
    class LivenessCaptureLauncher {
      <<interface>>
      +supports(type) bool
    }
    class OwnCaptureLauncher
    class DiditCaptureLauncher {
      <<interface>>
      +launch(activity, token) SdkResult
    }
    class SimulatedDiditCaptureLauncher
    class SdkDiditCaptureLauncher {
      <<demo>>
    }
    class CaptureGuideEngine {
      +onFrame(signals) GuideState
      +onFrameCaptured(slot)
    }
    class MlKitFaceAnalyzer
    class CapturedFrames {
      +isComplete() bool
      +wipe()
    }
    class BiometricConsentRepository {
      <<interface>>
    }
    class MockBiometricConsentRepository
    class RemoteBiometricConsentRepository {
      <<pendiente HU3.1.3>>
    }

    ActivationViewModel --> ActivationRepository
    ActivationViewModel --> CaptureLauncherFactory
    ActivationViewModel --> CaptureGuideEngine
    ActivationViewModel --> CapturedFrames
    ActivationRepository <|.. RemoteActivationRepository
    ActivationRepository <|.. MockActivationRepository
    ActivationRepository --> ActivationSession
    ActivationSession --> CaptureType
    CaptureType <|-- Frames
    CaptureType <|-- Sdk
    CaptureLauncherFactory --> LivenessCaptureLauncher
    LivenessCaptureLauncher <|.. OwnCaptureLauncher
    LivenessCaptureLauncher <|.. DiditCaptureLauncher
    DiditCaptureLauncher <|.. SimulatedDiditCaptureLauncher
    DiditCaptureLauncher <|.. SdkDiditCaptureLauncher
    MlKitFaceAnalyzer --> CaptureGuideEngine : FaceSignals
    BiometricConsentRepository <|.. MockBiometricConsentRepository
    BiometricConsentRepository <|.. RemoteBiometricConsentRepository
```

## 3. Captura `FRAMES` dentro de la app

```mermaid
sequenceDiagram
    autonumber
    participant UI as LivenessCaptureScreen
    participant CX as CameraX ImageAnalysis
    participant MK as MlKitFaceAnalyzer
    participant E as CaptureGuideEngine
    participant VM as ActivationViewModel
    participant R as RemoteActivationRepository

    VM->>E: motor con el gesto del backend
    loop cada frame
        CX->>MK: ImageProxy
        MK->>E: FaceSignals
        E-->>UI: GuideState (instrucción y progreso)
        alt Capture(START | GESTURE | END)
            MK->>MK: FrameEncoder a JPEG en memoria
            MK->>VM: onFrameCaptured(slot, bytes, instant)
        end
        MK->>CX: imageProxy.close()
    end
    E-->>VM: Completed
    VM->>R: verify(sesión, 3 frames, nonce)
    R-->>VM: VerificationResult
    VM->>VM: frames.wipe() en finally
```

## 4. Estado de la pantalla de activación

```mermaid
stateDiagram-v2
    direction LR
    [*] --> INTRO
    INTRO --> STARTING: Empezar
    STARTING --> CAPTURE: Frames
    STARTING --> LAUNCHING_PROVIDER: Sdk
    STARTING --> RESULT: agotado, consentimiento, error
    CAPTURE --> SUBMITTING: 3 frames listos
    CAPTURE --> INTRO: falla local (no consume intento)
    LAUNCHING_PROVIDER --> SUBMITTING: COMPLETED
    LAUNCHING_PROVIDER --> INTRO: CANCELLED
    SUBMITTING --> RESULT
    RESULT --> STARTING: Reintentar (Frames siempre con sesión nueva)
    RESULT --> [*]: aprobado
```
