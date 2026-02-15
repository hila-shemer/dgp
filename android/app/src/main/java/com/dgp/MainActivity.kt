package com.dgp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dgp.engine.DgpEngine
import com.dgp.security.BiometricHelper
import kotlinx.coroutines.launch
import java.util.Scanner

class MainActivity : FragmentActivity() {

    private val biometricHelper = BiometricHelper()
    private lateinit var dgpEngine: DgpEngine
    private lateinit var encryptedPrefs: android.content.SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Load wordlist from assets
        val wordList = mutableListOf<String>()
        try {
            val stream = assets.open("english.txt")
            val scanner = Scanner(stream)
            while (scanner.hasNextLine()) {
                wordList.add(scanner.nextLine())
            }
            scanner.close()
        } catch (e: Exception) {
            e.printStackTrace()
            wordList.addAll(listOf("abandon", "ability", "able", "about", "above"))
        }
        
        dgpEngine = DgpEngine(wordList)

        // Setup EncryptedSharedPreferences for configuration
        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            this,
            "dgp_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        setContent {
            DgpApp(dgpEngine, biometricHelper, encryptedPrefs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DgpApp(engine: DgpEngine, biometricHelper: BiometricHelper, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current as FragmentActivity
    val executor = ContextCompat.getMainExecutor(context)
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    var serviceName by remember { mutableStateOf(prefs.getString("last_service", "") ?: "") }
    var accountName by remember { mutableStateOf(prefs.getString("last_account", "") ?: "") }
    var result by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var masterSeed by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("DGP - Local & Secure") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (!isUnlocked) {
                Text("DGP Vault is locked.", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock DGP Vault")
                        .setSubtitle("Authenticate to access your master seed")
                        .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                        .build()

                    val biometricPrompt = BiometricPrompt(context, executor, object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(authResult: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(authResult)
                            // Retrieve Master Seed securely (demo version uses prefs)
                            masterSeed = prefs.getString("master_seed", "default_seed") ?: "default_seed"
                            isUnlocked = true
                        }
                    })
                    biometricPrompt.authenticate(promptInfo)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("Unlock with Fingerprint/PIN")
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                Text("Note: After unlock, your generated passwords never leave the device.", style = MaterialTheme.typography.bodySmall)
            } else {
                TextField(
                    value = serviceName, 
                    onValueChange = { 
                        serviceName = it
                        prefs.edit().putString("last_service", it).apply()
                    }, 
                    label = { Text("Service Name (e.g. google.com)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = accountName, 
                    onValueChange = { 
                        accountName = it 
                        prefs.edit().putString("last_account", it).apply()
                    }, 
                    label = { Text("Account/Secret (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        result = engine.generate(masterSeed, serviceName, "alnum", accountName)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate Password")
                }
                
                if (result.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Generated Password:", style = MaterialTheme.typography.labelMedium)
                            Text(result, style = MaterialTheme.typography.headlineMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = {
                                val clip = android.content.ClipData.newPlainText("DGP Password", result)
                                clipboardManager.setPrimaryClip(clip)
                                
                                // Auto-clear after 15 seconds
                                scope.launch {
                                    kotlinx.coroutines.delay(15000)
                                    if (java.util.Objects.equals(clipboardManager.primaryClip?.getItemAt(0)?.text, result)) {
                                        clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                                    }
                                }
                            }) {
                                Text("Copy & Clear in 15s")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Button(onClick = { isUnlocked = false; masterSeed = ""; result = "" }, 
                      colors = ButtonDefaults.filledTonalButtonColors(),
                      modifier = Modifier.fillMaxWidth()) {
                    Text("Lock Vault")
                }
            }
        }
    }
}
