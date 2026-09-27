package com.dgp.session

import android.os.Build
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dgp.parseServices
import com.dgp.security.BiometricHelper
import com.dgp.security.ConfigCrypto

/**
 * Unlocks DgpSession from the biometric-wrapped seed, for callers other than
 * MainActivity (the autofill picker). Only the Keystore-wrapped seed is supported;
 * a first unlock, or the old plaintext-seed migration, still goes through the app.
 */
object SessionUnlocker {

    fun unlock(activity: FragmentActivity, onDone: (Boolean) -> Unit) {
        val prefs = DgpSession.prefs(activity)
        val parts = prefs.getString("master_seed_encrypted", null)?.split(":")
        if (parts == null || parts.size != 2) return onDone(false)
        val helper = BiometricHelper()
        val ciphertext: ByteArray
        val cipher = try {
            ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            helper.getDecryptionCipher(Base64.decode(parts[0], Base64.NO_WRAP))
        } catch (_: Exception) {
            return onDone(false) // key invalidated by a new enrollment; the app handles that
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock DGP")
            .setSubtitle("Authenticate to fill a password")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            info.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            info.setNegativeButtonText("Cancel")
        }

        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val authed = result.cryptoObject?.cipher ?: return onDone(false)
                    val seed = helper.decrypt(authed, ciphertext)
                    val encServices = prefs.getString("services_encrypted", null)
                    val services = if (encServices == null) emptyList() else {
                        val json = ConfigCrypto.decrypt(encServices, seed) ?: return onDone(false)
                        parseServices(json)
                    }
                    DgpSession.clearAccountIfRebooted(prefs)
                    val account = prefs.getString("account_encrypted", null)
                        ?.let { ConfigCrypto.decrypt(it, seed) } ?: ""
                    DgpSession.set(seed, account, services)
                    onDone(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Same rule as the app: a failed or cancelled prompt clears the account.
                    prefs.edit().remove("account_encrypted").apply()
                    onDone(false)
                }
            })
        prompt.authenticate(info.build(), BiometricPrompt.CryptoObject(cipher))
    }
}
