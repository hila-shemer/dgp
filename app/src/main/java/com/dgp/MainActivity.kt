package com.dgp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dgp.engine.DgpEngine
import com.dgp.engine.TestVectors
import com.dgp.security.BiometricHelper
import com.dgp.security.ConfigCrypto
import com.dgp.ui.ListFilter
import com.dgp.ui.ServicesScreen
import com.dgp.ui.components.CopyToastState
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.UnlockScreen
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.security.MessageDigest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Scanner
import java.util.UUID

data class DgpService(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String = "alnum",
    val comment: String = "",
    val archived: Boolean = false,
    val pinned: Boolean = false,
    val tags: List<String> = emptyList(),
    // Base64(IV ‖ AES-256-GCM ciphertext). Only set for "vault" entries.
    // Key derivation: DgpEngine.deriveAesKey(seed, name, account).
    val encryptedSecret: String? = null,
)

class MainActivity : FragmentActivity() {

    private val biometricHelper = BiometricHelper()
    private lateinit var dgpEngine: DgpEngine
    private lateinit var prefs: android.content.SharedPreferences

    var importFileCallback: ((android.net.Uri?) -> Unit)? = null

    fun launchFilePicker(callback: (android.net.Uri?) -> Unit) {
        importFileCallback = callback
        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQUEST_IMPORT_FILE)
    }

    @Deprecated("Workaround: FragmentActivity rejects ActivityResultRegistry request codes (>= 0x10000)")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT_FILE) {
            importFileCallback?.invoke(if (resultCode == RESULT_OK) data?.data else null)
            importFileCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wordList = mutableListOf<String>()
        try {
            assets.open("english.txt").use { stream ->
                Scanner(stream).use { scanner ->
                    while (scanner.hasNextLine()) wordList.add(scanner.nextLine())
                }
            }
        } catch (e: Exception) {
            wordList.addAll(listOf("abandon", "ability", "able", "about", "above"))
        }

        dgpEngine = DgpEngine(wordList)

        prefs = getSharedPreferences("dgp_prefs", MODE_PRIVATE)

        setContent {
            EditorialTheme {
                DgpApp(dgpEngine, prefs, biometricHelper)
            }
        }
    }

    companion object {
        private const val REQUEST_IMPORT_FILE = 42
    }
}

fun parseServices(json: String): List<DgpService> {
    val list = mutableListOf<DgpService>()
    try {
        val arr = JSONArray(json)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(DgpService(
                obj.getString("id"),
                obj.getString("name"),
                obj.optString("type", "alnum"),
                obj.optString("comment", ""),
                obj.optBoolean("archived", false),
                obj.optBoolean("pinned", false),
                obj.optJSONArray("tags")?.let { ja -> (0 until ja.length()).map(ja::getString) } ?: emptyList(),
                if (obj.has("encryptedSecret") && !obj.isNull("encryptedSecret"))
                    obj.getString("encryptedSecret") else null
            ))
        }
    } catch (_: Exception) {}
    return list
}

fun serializeServices(services: List<DgpService>): String {
    val arr = JSONArray()
    services.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id)
            put("name", it.name)
            put("type", it.type)
            put("comment", it.comment)
            put("archived", it.archived)
            put("pinned", it.pinned)
            if (it.tags.isNotEmpty()) put("tags", JSONArray(it.tags))
            if (it.encryptedSecret != null) put("encryptedSecret", it.encryptedSecret)
        })
    }
    return arr.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DgpApp(engine: DgpEngine, prefs: android.content.SharedPreferences, biometricHelper: BiometricHelper) {
    val context = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    // Core state
    var masterSeed by remember { mutableStateOf("") }
    var isSeeded by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var services by remember { mutableStateOf(listOf<DgpService>()) }

    // Config export/import
    var showExportPinDialog by remember { mutableStateOf(false) }
    var showImportPinDialog by remember { mutableStateOf(false) }

    fun loadImportedJson(json: String) {
        val imported = parseServices(json)
        if (imported.isEmpty()) {
            android.widget.Toast.makeText(context, "No valid services found", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        services = imported
        prefs.edit()
            .putString("services_encrypted", ConfigCrypto.encrypt(json, masterSeed))
            .apply()
        android.widget.Toast.makeText(context, "Imported ${imported.size} services", android.widget.Toast.LENGTH_SHORT).show()
    }

    val mainActivity = context as MainActivity
    fun launchImportFilePicker() {
        mainActivity.launchFilePicker { uri ->
            if (uri != null) {
                try {
                    val json = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                    if (json != null) loadImportedJson(json)
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun exportServices(pin: String) {
        scope.launch {
            try {
                val json = serializeServices(services)
                val encrypted = withContext(Dispatchers.Default) {
                    ConfigCrypto.encryptExport(json, pin)
                }
                val file = java.io.File(context.cacheDir, "dgp-export.enc")
                file.writeText(encrypted)
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(android.content.Intent.createChooser(intent, "Export DGP Config"))
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Export failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun importEncryptedServices(pin: String) {
        scope.launch {
            try {
                val clip = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                if (clip.isNullOrEmpty()) {
                    android.widget.Toast.makeText(context, "Clipboard is empty", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                val json = withContext(Dispatchers.Default) {
                    ConfigCrypto.decryptExport(clip, pin)
                }
                if (json == null) {
                    android.widget.Toast.makeText(context, "Decryption failed — wrong PIN?", android.widget.Toast.LENGTH_LONG).show()
                    return@launch
                }
                loadImportedJson(json)
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // UI States
    var showSeedPrompt by remember { mutableStateOf(true) }
    var showAccountPrompt by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<DgpService?>(null) }
    var showSeedSettings by remember { mutableStateOf(false) }
    var showTestVectors by remember { mutableStateOf(false) }
    val testResults = remember { mutableStateListOf<TestVectors.SingleTestResult>() }
    var testRunning by remember { mutableStateOf(false) }
    var selectedServiceForGen by remember { mutableStateOf<DgpService?>(null) }
    var generatedPassword by remember { mutableStateOf("") }
    var seedError by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf<ListFilter>(ListFilter.All) }
    var copyToast by remember { mutableStateOf<CopyToastState>(CopyToastState.Idle) }

    fun loadServices(seed: String): Boolean {
        val encrypted = prefs.getString("services_encrypted", null)
        if (encrypted == null) {
            // First run or no data yet — migrate from old unencrypted format
            val oldJson = prefs.getString("services_list", null)
            if (oldJson != null) {
                services = parseServices(oldJson)
                // Re-save encrypted and remove old key
                val json = serializeServices(services)
                prefs.edit()
                    .putString("services_encrypted", ConfigCrypto.encrypt(json, seed))
                    .remove("services_list")
                    .apply()
            } else {
                services = emptyList()
            }
            return true
        }
        val json = ConfigCrypto.decrypt(encrypted, seed) ?: return false
        services = parseServices(json)
        return true
    }

    fun saveServices(newList: List<DgpService>) {
        services = newList
        val json = serializeServices(services)
        prefs.edit()
            .putString("services_encrypted", ConfigCrypto.encrypt(json, masterSeed))
            .apply()
    }

    fun clearAccount() {
        account = ""
        prefs.edit().remove("account_encrypted").apply()
    }

    fun authenticate(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setAllowedAuthenticators(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        val biometricPrompt = BiometricPrompt(context, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    clearAccount()
                }
            })
        biometricPrompt.authenticate(promptInfo)
    }

    fun saveSeedWithBiometric(seed: String) {
        try {
            val cipher = biometricHelper.getEncryptionCipher()
            val executor = ContextCompat.getMainExecutor(context)
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Save Seed")
                .setSubtitle("Authenticate to securely store your seed")
                .setNegativeButtonText("Skip")
                .build()
            val biometricPrompt = BiometricPrompt(context, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        val authedCipher = result.cryptoObject?.cipher ?: return
                        val (ciphertext, iv) = biometricHelper.encrypt(authedCipher, seed)
                        val encoded = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                                      Base64.encodeToString(ciphertext, Base64.NO_WRAP)
                        prefs.edit()
                            .putString("master_seed_encrypted", encoded)
                            .remove("master_seed")
                            .apply()
                    }
                })
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (_: Exception) {
            // Biometric not available or key invalidated — don't save
        }
    }

    fun loadSeedWithBiometric(onSuccess: (String) -> Unit) {
        val encoded = prefs.getString("master_seed_encrypted", null)
        // Migrate from old plaintext storage
        val plaintextSeed = prefs.getString("master_seed", null)

        if (encoded != null) {
            val parts = encoded.split(":")
            if (parts.size == 2) {
                try {
                    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
                    val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
                    val cipher = biometricHelper.getDecryptionCipher(iv)
                    val executor = ContextCompat.getMainExecutor(context)
                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock DGP")
                        .setSubtitle("Authenticate to access your seed")
                        .setNegativeButtonText("Enter manually")
                        .build()
                    val biometricPrompt = BiometricPrompt(context, executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                val authedCipher = result.cryptoObject?.cipher ?: return
                                val seed = biometricHelper.decrypt(authedCipher, ciphertext)
                                onSuccess(seed)
                            }
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                clearAccount()
                            }
                        })
                    biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
                    return
                } catch (_: Exception) {
                    // Key invalidated (e.g. new biometric enrolled) — fall through
                }
            }
        }

        if (plaintextSeed != null && plaintextSeed.isNotEmpty()) {
            // Old plaintext seed found — authenticate then migrate
            authenticate {
                onSuccess(plaintextSeed)
                saveSeedWithBiometric(plaintextSeed)
            }
        }
    }

    fun unlockWithSeed(seed: String, skipSave: Boolean = false) {
        if (loadServices(seed)) {
            masterSeed = seed
            isSeeded = true
            showSeedPrompt = false
            seedError = false
            val encryptedAccount = prefs.getString("account_encrypted", null)
            val decryptedAccount = encryptedAccount?.let { ConfigCrypto.decrypt(it, seed) }
            if (!decryptedAccount.isNullOrEmpty()) {
                account = decryptedAccount
            } else {
                showAccountPrompt = true
            }
            if (!skipSave) {
                saveSeedWithBiometric(seed)
            }
        } else {
            seedError = true
        }
    }

    fun generateForService(service: DgpService): String {
        if (service.type != "vault") {
            return engine.generate(masterSeed, service.name, service.type, account)
        }
        val blob = service.encryptedSecret ?: return "(no secret stored)"
        val key = engine.deriveAesKey(masterSeed, service.name, account)
        return ConfigCrypto.decryptWithRawKey(blob, key) ?: "(decryption failed)"
    }

    fun scanQr(onResult: (String) -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        scanner.startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { onResult(it) }
            }
    }

    // Clear account on reboot
    LaunchedEffect(Unit) {
        val bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val lastBootTime = prefs.getLong("last_boot_time", 0L)
        // Allow 5s tolerance for timing differences
        if (lastBootTime == 0L || kotlin.math.abs(bootTime - lastBootTime) > 5000) {
            clearAccount()
        }
        prefs.edit().putLong("last_boot_time", bootTime).apply()
    }

    // Try biometric unlock on first launch if seed is saved
    LaunchedEffect(Unit) {
        loadSeedWithBiometric { seed -> unlockWithSeed(seed, skipSave = true) }
    }

    // Seed entry prompt (shown when locked)
    if (showSeedPrompt && !isSeeded) {
        UnlockScreen(
            error = seedError,
            onUnlock = { seed -> unlockWithSeed(seed) },
            onScanQr = { onResult -> scanQr(onResult) },
            onBiometric = {
                loadSeedWithBiometric { seed -> unlockWithSeed(seed, skipSave = true) }
            },
            onResetConfig = {
                prefs.edit()
                    .remove("services_encrypted")
                    .remove("account_encrypted")
                    .apply()
                seedError = false
                android.widget.Toast.makeText(context, "Config cleared", android.widget.Toast.LENGTH_SHORT).show()
            },
        )
        return@DgpApp
    }

    // Account prompt (shown after seed unlock)
    if (showAccountPrompt && isSeeded) {
        AccountPromptDialog(
            onDismiss = { showAccountPrompt = false },
            onSave = { newAccount ->
                account = newAccount
                if (masterSeed.isNotEmpty()) {
                    prefs.edit().putString("account_encrypted", ConfigCrypto.encrypt(newAccount, masterSeed)).apply()
                }
                showAccountPrompt = false
            }
        )
    }

    ServicesScreen(
        services = services,
        account = account,
        searchQuery = searchQuery,
        onSearchChange = { searchQuery = it },
        activeFilter = activeFilter,
        onFilterChange = { activeFilter = it },
        onTapRow = { svc ->
            selectedServiceForGen = svc
            generatedPassword = generateForService(svc)
        },
        onChevronTap = { svc ->
            selectedServiceForGen = svc
            generatedPassword = generateForService(svc)
        },
        onLongPressRow = { /* Phase 7 */ },
        onAdd = { showAddDialog = true },
        onScan = {
            scanQr { _ ->
                editingService = null
                showAddDialog = true
            }
        },
        onLock = {
            masterSeed = ""
            isSeeded = false
            account = ""
            showSeedPrompt = true
        },
        onOpenAccount = {
            if (account.isNotEmpty()) clearAccount()
            showAccountPrompt = true
        },
        onOpenSettings = { authenticate { showSeedSettings = true } },
        onRunTestVectors = {
            testResults.clear()
            showTestVectors = true
            testRunning = true
            scope.launch {
                for (i in TestVectors.vectors.indices) {
                    val result = withContext(Dispatchers.Default) {
                        TestVectors.runOne(engine, i)
                    }
                    testResults.add(result)
                }
                testRunning = false
            }
        },
        copyToast = copyToast,
        onToastDismiss = { copyToast = CopyToastState.Idle },
        onToastUndo = { copyToast = CopyToastState.Idle },
    )

    if (showExportPinDialog) {
        PinDialog(
            title = "Export PIN",
            subtitle = "Encrypt config with a PIN for sharing",
            onDismiss = { showExportPinDialog = false },
            onConfirm = { pin ->
                showExportPinDialog = false
                exportServices(pin)
            }
        )
    }

    if (showImportPinDialog) {
        PinDialog(
            title = "Import PIN",
            subtitle = "Copy encrypted config to clipboard first",
            onDismiss = { showImportPinDialog = false },
            onConfirm = { pin ->
                showImportPinDialog = false
                importEncryptedServices(pin)
            }
        )
    }

    if (showAddDialog || editingService != null) {
        // Pre-decrypt existing vault secret so the user can edit it.
        // Failure → empty field + a warning shown in the dialog.
        val editing = editingService
        val existingBlob = editing?.encryptedSecret
        val (initialSecret, decryptFailed) = if (editing?.type == "vault" && existingBlob != null) {
            val key = engine.deriveAesKey(masterSeed, editing.name, account)
            val decrypted = ConfigCrypto.decryptWithRawKey(existingBlob, key)
            if (decrypted != null) decrypted to false else "" to true
        } else "" to false

        ServiceEditDialog(
            service = editing,
            initialVaultSecret = initialSecret,
            vaultDecryptFailed = decryptFailed,
            onDismiss = { showAddDialog = false; editingService = null },
            onSave = { name, type, comment, vaultSecret ->
                val encryptedSecret = if (type == "vault" && !vaultSecret.isNullOrEmpty()) {
                    val key = engine.deriveAesKey(masterSeed, name, account)
                    ConfigCrypto.encryptWithRawKey(vaultSecret, key)
                } else null
                val newList = services.toMutableList()
                if (editing != null) {
                    val idx = newList.indexOfFirst { it.id == editing.id }
                    if (idx >= 0) {
                        newList[idx] = editing.copy(
                            name = name, type = type, comment = comment,
                            encryptedSecret = encryptedSecret
                        )
                    }
                } else {
                    newList.add(DgpService(
                        name = name, type = type, comment = comment,
                        encryptedSecret = encryptedSecret
                    ))
                }
                saveServices(newList)
                showAddDialog = false
                editingService = null
            },
            onDelete = {
                if (editing != null) {
                    val newList = services.filter { it.id != editing.id }
                    saveServices(newList)
                    editingService = null
                }
            },
            onArchiveToggle = {
                if (editing != null) {
                    val newList = services.map {
                        if (it.id == editing.id) it.copy(archived = !it.archived) else it
                    }
                    saveServices(newList)
                    editingService = null
                }
            }
        )
    }

    if (showSeedSettings) {
        SeedSettingsDialog(
            currentSeed = masterSeed,
            onDismiss = { showSeedSettings = false },
            onSave = { newSeed ->
                // Re-encrypt services with new seed
                val json = serializeServices(services)
                prefs.edit()
                    .putString("services_encrypted", ConfigCrypto.encrypt(json, newSeed))
                    .apply()
                masterSeed = newSeed
                saveSeedWithBiometric(newSeed)
                showSeedSettings = false
            },
            onScanQr = { onResult -> scanQr(onResult) },
            onExport = { showExportPinDialog = true },
            onImportEncrypted = { showImportPinDialog = true },
            onImportJson = { launchImportFilePicker() }
        )
    }

    if (showTestVectors) {
        val passed = testResults.count { it.passed }
        val failed = testResults.count { !it.passed }
        val total = TestVectors.vectors.size
        AlertDialog(
            onDismissRequest = {
                showTestVectors = false
                testRunning = false
            },
            title = {
                Text(if (testRunning) "Running... ${testResults.size}/$total"
                     else "$passed passed, $failed failed / $total")
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(testResults.size) { i ->
                        val r = testResults[i]
                        Column(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(
                                text = (if (r.passed) "PASS " else "FAIL ") + r.label,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.passed) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                            if (r.passed) {
                                Text("  = ${r.actual}",
                                     style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("  expected: ${r.expected}",
                                     style = MaterialTheme.typography.bodySmall)
                                Text("  actual:   ${r.actual}",
                                     style = MaterialTheme.typography.bodySmall,
                                     color = MaterialTheme.colorScheme.error)
                            }
                        }
                        if (i < testResults.size - 1) Divider()
                    }
                    if (testRunning) {
                        item {
                            LinearProgressIndicator(
                                progress = testResults.size.toFloat() / total,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showTestVectors = false
                    testRunning = false
                }) { Text("Close") }
            }
        )
    }

    if (selectedServiceForGen != null) {
        AlertDialog(
            onDismissRequest = { selectedServiceForGen = null },
            title = { Text(selectedServiceForGen!!.name) },
            text = {
                Column {
                    Text("Type: ${selectedServiceForGen!!.type}",
                         style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(generatedPassword, style = MaterialTheme.typography.headlineMedium)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val clip = android.content.ClipData.newPlainText("DGP Password", generatedPassword)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        clip.description.extras = android.os.PersistableBundle().apply {
                            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                        }
                    }
                    clipboardManager.setPrimaryClip(clip)
                    selectedServiceForGen = null
                }) {
                    Text("Copy")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedServiceForGen = null }) { Text("Close") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEditDialog(
    service: DgpService?,
    initialVaultSecret: String,
    vaultDecryptFailed: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, type: String, comment: String, vaultSecret: String?) -> Unit,
    onDelete: () -> Unit,
    onArchiveToggle: () -> Unit
) {
    var name by remember { mutableStateOf(service?.name ?: "") }
    var type by remember { mutableStateOf(service?.type ?: "alnum") }
    var comment by remember { mutableStateOf(service?.comment ?: "") }
    var vaultSecret by remember { mutableStateOf(initialVaultSecret) }
    var vaultVisible by remember { mutableStateOf(false) }
    val types = listOf(
        "alnum", "alnumlong", "hex", "hexlong",
        "base58", "base58long", "xkcd", "xkcdlong", "vault"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service == null) "Add Service" else "Edit Service") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Service Name") },
                    modifier = Modifier.semantics { testTag = "service-name-input" })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") },
                    modifier = Modifier.semantics { testTag = "service-comment-input" })
                Spacer(modifier = Modifier.height(8.dp))
                Text("Password Type:", style = MaterialTheme.typography.labelMedium)
                types.chunked(2).forEach { row ->
                    Row {
                        row.forEach { t ->
                            FilterChip(
                                selected = type == t,
                                onClick = { type = t },
                                label = { Text(t) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
                if (type == "vault") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Stores an encrypted secret (legacy passwords, OTP seeds) " +
                        "tied to this seed + account + service name.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    if (vaultDecryptFailed) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Existing secret could not be decrypted — saving will overwrite it.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    TextField(
                        value = vaultSecret,
                        onValueChange = { vaultSecret = it },
                        label = { Text("Secret") },
                        modifier = Modifier.semantics { testTag = "vault-secret-input" },
                        visualTransformation = if (vaultVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { vaultVisible = !vaultVisible }) {
                                Icon(if (vaultVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotEmpty()) {
                    val secretOut = if (type == "vault") vaultSecret else null
                    onSave(name, type, comment, secretOut)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (service != null) {
                    TextButton(onClick = onArchiveToggle) {
                        Text(if (service.archived) "Unarchive" else "Archive")
                    }
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
fun AccountPromptDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter Account") },
        text = {
            TextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Account") },
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { visible = !visible }) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                    }
                },
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = { if (value.isNotEmpty()) onSave(value) },
                enabled = value.isNotEmpty()
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Skip") }
        }
    )
}

@Composable
fun SeedSettingsDialog(
    currentSeed: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onScanQr: ((String) -> Unit) -> Unit,
    onExport: () -> Unit,
    onImportEncrypted: () -> Unit,
    onImportJson: () -> Unit
) {
    var seed by remember { mutableStateOf(currentSeed) }
    var visible by remember { mutableStateOf(false) }
    var showFingerprint by remember { mutableStateOf(false) }

    fun seedFingerprint(s: String): String {
        if (s.isEmpty()) return "(no seed set)"
        val digest = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        return "SHA-256: " + digest.take(8).joinToString("") { "%02x".format(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Master Seed Settings") },
        text = {
            Column {
                Text("Caution: Changing your master seed will change all generated passwords " +
                     "and make vault-stored secrets unreadable.",
                     color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text("Master Seed") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onScanQr { scanned -> seed = scanned } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showFingerprint = !showFingerprint },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seed Fingerprint")
                }
                if (showFingerprint) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(seedFingerprint(seed), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Text("Service Config", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text("Export (encrypted)")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onImportEncrypted, modifier = Modifier.fillMaxWidth()) {
                    Text("Import (encrypted, from clipboard)")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(onClick = onImportJson, modifier = Modifier.fillMaxWidth()) {
                    Text("Import (plaintext JSON file)")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(seed) }) { Text("Update Seed") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PinDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null)
                        }
                    },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, enabled = pin.isNotEmpty()) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
