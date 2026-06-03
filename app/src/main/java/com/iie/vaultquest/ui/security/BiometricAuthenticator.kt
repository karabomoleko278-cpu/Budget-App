package com.iie.vaultquest.ui.security

import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin, crash-safe wrapper around AndroidX [BiometricPrompt] used by the
 * Biometric App Lock (Custom Feature 1).
 *
 * Exposes two things the UI needs:
 *  - [canAuthenticate]: is fingerprint/face actually usable on this device?
 *  - [authenticate]:    show the system prompt with a password fallback.
 */
class BiometricAuthenticator(private val activity: FragmentActivity) {

    private val tag = "BiometricAuth"

    fun canAuthenticate(): Boolean {
        return try {
            BiometricManager.from(activity)
                .canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.e(tag, "canAuthenticate check failed: ${e.message}", e)
            false
        }
    }

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFallback: () -> Unit,
        onError: (String) -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)
            val prompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        Log.d(tag, "Biometric authentication succeeded")
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        // User tapped the negative button -> fall back to password.
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                            errorCode == BiometricPrompt.ERROR_USER_CANCELED
                        ) {
                            Log.d(tag, "Biometric cancelled -> password fallback")
                            onFallback()
                        } else {
                            Log.e(tag, "Biometric error [$errorCode]: $errString")
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        // A single non-matching attempt; the prompt stays open.
                        Log.w(tag, "Biometric attempt did not match")
                    }
                })

            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use password")
                .setAllowedAuthenticators(BIOMETRIC_WEAK)
                .build()

            prompt.authenticate(info)
        } catch (e: Exception) {
            Log.e(tag, "Failed to launch biometric prompt: ${e.message}", e)
            onFallback()
        }
    }
}
