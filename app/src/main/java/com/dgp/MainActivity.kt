package com.dgp

import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dgp.engine.DgpEngine
import com.dgp.engine.TestVectors
import com.dgp.security.BiometricHelper
import com.dgp.security.ConfigCrypto
import com.dgp.ui.EditEntryScreen
import com.dgp.ui.ListFilter
import com.dgp.ui.ReorderScreen
import com.dgp.ui.RevealSheet
import com.dgp.ui.ServicesScreen
import com.dgp.ui.SettingsScreen
import com.dgp.ui.components.CopyToastState
import com.dgp.ui.theme.EditorialMotion
import com.dgp.ui.theme.EditorialTheme
import com.dgp.ui.theme.LocalCompactRows
import com.dgp.ui.theme.ThemeMode
import com.dgp.ui.UnlockScreen
import com.dgp.ui.ActiveModal
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.security.MessageDigest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

        // Prevent passwords and seed from appearing in screenshots, screen
        // recordings, the Recents thumbnail, or Assistant captures.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )

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
            val configuration = LocalConfiguration.current
            val baseDensity = LocalDensity.current
            val clampedDensity = remember(configuration.fontScale, baseDensity) {
                Density(
                    density = baseDensity.density,
                    fontScale = minOf(configuration.fontScale, 1.3f),
                )
            }
            CompositionLocalProvider(LocalDensity provides clampedDensity) {
                // Edge-to-edge is on by default at targetSdk 35; pad the whole UI by
                // safeDrawing so nothing ends up behind the status or navigation bars.
                Box(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                ) {
                    DgpApp(dgpEngine, prefs, biometricHelper)
                }
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
    // Settings prefs state — must be outside EditorialTheme so themeMode is available for the wrapper
    var clipboardTimeoutSec by remember { mutableIntStateOf(prefs.getInt("clipboard_timeout_sec", 30)) }
    var clearOnLock by remember { mutableStateOf(prefs.getBoolean("clear_on_lock", true)) }
    var compactRows by remember { mutableStateOf(prefs.getBoolean("compact_rows", false)) }
    var themeMode by remember {
        mutableStateOf(
            when (prefs.getString("theme_mode", "auto")) {
                "light" -> ThemeMode.Light
                "dark" -> ThemeMode.Dark
                else -> ThemeMode.Auto
            }
        )
    }

    EditorialTheme(mode = themeMode) {
    CompositionLocalProvider(LocalCompactRows provides compactRows) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        DgpAppContent(engine, prefs, biometricHelper,
            clipboardTimeoutSec, onClipboardTimeoutChange = { sec ->
                clipboardTimeoutSec = sec
                prefs.edit().putInt("clipboard_timeout_sec", sec).apply()
            },
            clearOnLock, onClearOnLockChange = { v ->
                clearOnLock = v
                prefs.edit().putBoolean("clear_on_lock", v).apply()
            },
            themeMode, onThemeModeChange = { mode ->
                themeMode = mode
                prefs.edit().putString("theme_mode", when (mode) {
                    ThemeMode.Auto -> "auto"
                    ThemeMode.Light -> "light"
                    ThemeMode.Dark -> "dark"
                }).apply()
            },
            compactRows, onCompactRowsChange = { v ->
                compactRows = v
                prefs.edit().putBoolean("compact_rows", v).apply()
            },
        )
    }
    } // CompositionLocalProvider
    } // EditorialTheme
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DgpAppContent(
    engine: DgpEngine,
    prefs: android.content.SharedPreferences,
    biometricHelper: BiometricHelper,
    clipboardTimeoutSec: Int,
    onClipboardTimeoutChange: (Int) -> Unit,
    clearOnLock: Boolean,
    onClearOnLockChange: (Boolean) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    compactRows: Boolean,
    onCompactRowsChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    // Core state
    var masterSeed by remember { mutableStateOf("") }
    var isSeeded by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var services by remember { mutableStateOf(listOf<DgpService>()) }

    // Visual flag fingerprint of seed+account. fpBytes is the one expensive
    // derivation; recompute off the main thread whenever seed or account changes.
    var fpBytes by remember { mutableStateOf<ByteArray?>(null) }
    var flagNonce by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(masterSeed, account, isSeeded) {
        fpBytes = if (isSeeded && masterSeed.isNotEmpty()) {
            withContext(Dispatchers.Default) { engine.deriveFingerprintBytes(masterSeed, account) }
        } else {
            null
        }
    }

    val headerFlagIndex: Int? = fpBytes
        ?.takeIf { account.isNotEmpty() }
        ?.let { engine.flagIndexFor(it, flagNonce ?: 0) }

    var activeModal by remember { mutableStateOf<ActiveModal>(ActiveModal.None) }

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

    fun launchEncryptedImportFilePicker() {
        mainActivity.launchFilePicker { uri ->
            if (uri != null) {
                activeModal = ActiveModal.ImportPin(uri)
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

    fun importEncryptedServices(pin: String, uri: Uri? = null) {
        scope.launch {
            try {
                val encrypted = if (uri != null) {
                    context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
                } else {
                    clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
                }
                if (encrypted.isNullOrBlank()) {
                    val message = if (uri != null) "Encrypted file is empty" else "Clipboard is empty"
                    withContext(Dispatchers.Main.immediate) {
                        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val json = withContext(Dispatchers.Default) {
                    ConfigCrypto.decryptExport(encrypted, pin)
                }
                if (json == null) {
                    withContext(Dispatchers.Main.immediate) {
                        android.widget.Toast.makeText(context, "Decryption failed — wrong PIN?", android.widget.Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                withContext(Dispatchers.Main.immediate) {
                    loadImportedJson(json)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main.immediate) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                if (activeModal is ActiveModal.ImportPin) activeModal = ActiveModal.None
            }
        }
    }

    // UI States
    var showSeedPrompt by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val testResults = remember { mutableStateListOf<TestVectors.SingleTestResult>() }
    var testRunning by remember { mutableStateOf(false) }
    var seedError by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf<ListFilter>(ListFilter.All) }
    var copyToast by remember { mutableStateOf<CopyToastState>(CopyToastState.Idle) }
    var reordering by remember { mutableStateOf(false) }
    var flashedServiceId by remember { mutableStateOf<String?>(null) }
    var hasSavedSeed by remember {
        mutableStateOf(!prefs.getString("master_seed_encrypted", null).isNullOrEmpty())
    }
    val canUseSavedSeed = hasSavedSeed && biometricHelper.canAuthenticateForSavedSeed(context)

    LaunchedEffect(flashedServiceId) {
        if (flashedServiceId != null) {
            delay(EditorialMotion.saveFlashMs.toLong())
            flashedServiceId = null
        }
    }

    var clipboardClearJob by remember { mutableStateOf<Job?>(null) }

    fun copyPasswordToClipboard(password: String) {
        val clip = android.content.ClipData.newPlainText("DGP Password", password)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = android.os.PersistableBundle().apply {
                putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboardManager.setPrimaryClip(clip)
        clipboardClearJob?.cancel()
        val timeoutSec = clipboardTimeoutSec
        if (timeoutSec > 0) {
            clipboardClearJob = scope.launch {
                delay(timeoutSec * 1000L)
                val current = clipboardManager.primaryClip
                if (current?.getItemAt(0)?.text?.toString() == password) {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        clipboardManager.clearPrimaryClip()
                    } else {
                        clipboardManager.setPrimaryClip(
                            android.content.ClipData.newPlainText("", "")
                        )
                    }
                }
            }
        }
    }

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
            val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Save Seed")
                .setSubtitle("Authenticate to securely store your seed")
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                promptInfoBuilder.setAllowedAuthenticators(
                    androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
            } else {
                promptInfoBuilder.setNegativeButtonText("Skip")
            }
            val promptInfo = promptInfoBuilder.build()
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
                        hasSavedSeed = true
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
                    val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock DGP")
                        .setSubtitle("Authenticate to access your seed")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        promptInfoBuilder.setAllowedAuthenticators(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                    } else {
                        promptInfoBuilder.setNegativeButtonText("Enter manually")
                    }
                    val promptInfo = promptInfoBuilder.build()
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
                activeModal = ActiveModal.Account
            }
            flagNonce = prefs.getInt("flag_nonce", -1).takeIf { it >= 0 }
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

    // Pick the top-level screen. Dialogs and sheets below render on top of
    // whichever branch is active — that's why Settings-triggered dialogs
    // (export PIN, change-seed, etc.) stay visible instead of only appearing
    // after the user closes Settings.
    when {
        showSeedPrompt && !isSeeded -> {
            UnlockScreen(
                error = seedError,
                onUnlock = { seed -> unlockWithSeed(seed) },
                onScanQr = { onResult -> scanQr(onResult) },
                onBiometric = {
                    loadSeedWithBiometric { seed -> unlockWithSeed(seed, skipSave = true) }
                },
                hasSavedSeed = hasSavedSeed,
                biometricEnabled = canUseSavedSeed,
                onResetConfig = {
                    prefs.edit()
                        .remove("services_encrypted")
                        .remove("account_encrypted")
                        .remove("master_seed_encrypted")
                        .remove("master_seed")
                        .remove("flag_nonce")
                        .apply()
                    hasSavedSeed = false
                    seedError = false
                    android.widget.Toast.makeText(context, "Config cleared", android.widget.Toast.LENGTH_SHORT).show()
                },
            )
        }

        reordering && isSeeded -> {
            // Reorder only the items matching the active filter. Items outside
            // the filter (e.g. archived while filter=All) keep their absolute
            // positions in the JSON array.
            val visible = services.filter { svc ->
                when (val f = activeFilter) {
                    ListFilter.All -> !svc.archived
                    ListFilter.Pinned -> !svc.archived && svc.pinned
                    ListFilter.Archived -> svc.archived
                    is ListFilter.Tag -> !svc.archived && f.tag in svc.tags
                }
            }
            val visibleIds = visible.mapTo(HashSet()) { it.id }
            ReorderScreen(
                services = visible,
                onDone = { newOrder ->
                    val queue = ArrayDeque(newOrder)
                    val merged = services.map { original ->
                        if (original.id in visibleIds) queue.removeFirst() else original
                    }
                    saveServices(merged)
                    reordering = false
                },
                onCancel = { reordering = false },
            )
        }

        showSettings && isSeeded -> {
            SettingsScreen(
                clipboardTimeoutSec = clipboardTimeoutSec,
                onClipboardTimeoutChange = onClipboardTimeoutChange,
                clearOnLock = clearOnLock,
                onClearOnLockChange = onClearOnLockChange,
                compactRows = compactRows,
                onCompactRowsChange = onCompactRowsChange,
                seedFingerprint = remember(masterSeed) {
                    if (masterSeed.isEmpty()) "(no seed set)"
                    else {
                        val digest = java.security.MessageDigest.getInstance("SHA-256")
                            .digest(masterSeed.toByteArray())
                        "SHA-256: " + digest.take(8).joinToString("") { "%02x".format(it) }
                    }
                },
                onChangeSeed = { authenticate { activeModal = ActiveModal.ChangeSeed } },
                onRunTestVectors = {
                    testResults.clear()
                    activeModal = ActiveModal.TestVectors
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
                onExportConfig = { activeModal = ActiveModal.ExportPin },
                onImportEncryptedFile = { launchEncryptedImportFilePicker() },
                onImportEncrypted = {
                    activeModal = ActiveModal.ImportPin(null)
                },
                onImportPlaintext = { launchImportFilePicker() },
                onClearAll = {
                    saveServices(emptyList())
                },
                onLockAndQuit = {
                    if (clearOnLock) {
                        clipboardClearJob?.cancel()
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            clipboardManager.clearPrimaryClip()
                        }
                    }
                    masterSeed = ""
                    isSeeded = false
                    account = ""
                    flagNonce = null
                    fpBytes = null
                    (context as? android.app.Activity)?.finishAndRemoveTask()
                },
                onBack = { showSettings = false },
            )
        }

        else -> {
            ServicesScreen(
                services = services,
                account = account,
                flagIndex = headerFlagIndex,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                activeFilter = activeFilter,
                onFilterChange = { activeFilter = it },
                onTapRow = { svc ->
                    val password = generateForService(svc)
                    copyPasswordToClipboard(password)
                    copyToast = CopyToastState.Visible(svc.name)
                },
                onChevronTap = { svc -> activeModal = ActiveModal.Revealing(svc) },
                onLongPressRow = { reordering = true },
                onAdd = { initialName ->
                    activeModal = ActiveModal.AddNew(initialName)
                },
                onLock = {
                    if (clearOnLock) {
                        clipboardClearJob?.cancel()
                        if (android.os.Build.VERSION.SDK_INT >= 28) {
                            clipboardManager.clearPrimaryClip()
                        }
                    }
                    masterSeed = ""
                    isSeeded = false
                    account = ""
                    flagNonce = null
                    fpBytes = null
                    showSeedPrompt = true
                },
                onOpenAccount = {
                    if (account.isNotEmpty()) clearAccount()
                    activeModal = ActiveModal.Account
                },
                onOpenSettings = { showSettings = true },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                copyToast = copyToast,
                flashedServiceId = flashedServiceId,
                onToastDismiss = { copyToast = CopyToastState.Idle },
                onToastUndo = { copyToast = CopyToastState.Idle },
            )
        }
    }

    when (val m = activeModal) {
        is ActiveModal.None -> Unit
        is ActiveModal.Account -> {
            if (isSeeded) {
                AccountPromptDialog(
                    onDismiss = { activeModal = ActiveModal.None },
                    onSave = { newAccount ->
                        account = newAccount
                        if (masterSeed.isNotEmpty()) {
                            prefs.edit().putString("account_encrypted", ConfigCrypto.encrypt(newAccount, masterSeed)).apply()
                        }
                        activeModal = ActiveModal.None
                    }
                )
            }
        }
        is ActiveModal.ExportPin -> {
            PinDialog(
                title = "Export PIN",
                subtitle = "Encrypt config with a PIN for sharing",
                onDismiss = { activeModal = ActiveModal.None },
                onConfirm = { pin ->
                    activeModal = ActiveModal.None
                    exportServices(pin)
                }
            )
        }
        is ActiveModal.ImportPin -> {
            PinDialog(
                title = "Import PIN",
                subtitle = if (m.fileUri != null) {
                    "Choose the PIN for the encrypted file"
                } else {
                    "Copy encrypted config to clipboard first"
                },
                onDismiss = { activeModal = ActiveModal.None },
                onConfirm = { pin ->
                    val fileUri = m.fileUri
                    activeModal = ActiveModal.None
                    importEncryptedServices(pin, fileUri)
                }
            )
        }
        is ActiveModal.AddNew -> {
            EditEntryScreen(
                service = null,
                initialName = m.initialName,
                seed = masterSeed,
                account = account,
                engine = engine,
                onSave = { updated ->
                    val newList = services.toMutableList()
                    newList.add(updated)
                    saveServices(newList)
                    flashedServiceId = updated.id
                    activeModal = ActiveModal.None
                },
                onDelete = { },
                onClose = { activeModal = ActiveModal.None },
            )
        }
        is ActiveModal.Editing -> {
            EditEntryScreen(
                service = m.service,
                initialName = m.service.name,
                seed = masterSeed,
                account = account,
                engine = engine,
                onSave = { updated ->
                    val newList = services.toMutableList()
                    val idx = newList.indexOfFirst { it.id == m.service.id }
                    if (idx >= 0) newList[idx] = updated
                    saveServices(newList)
                    flashedServiceId = updated.id
                    activeModal = ActiveModal.None
                },
                onDelete = {
                    saveServices(services.filter { it.id != m.service.id })
                    activeModal = ActiveModal.None
                },
                onClose = { activeModal = ActiveModal.None },
            )
        }
        is ActiveModal.Revealing -> {
            RevealSheet(
                service = m.service,
                passwordProvider = { generateForService(m.service) },
                onCopy = {
                    val password = generateForService(m.service)
                    copyPasswordToClipboard(password)
                    copyToast = CopyToastState.Visible(m.service.name)
                    activeModal = ActiveModal.None
                },
                onEdit = { activeModal = ActiveModal.Editing(m.service) },
                onArchive = {
                    saveServices(services.map {
                        if (it.id == m.service.id) it.copy(archived = !it.archived) else it
                    })
                    activeModal = ActiveModal.None
                },
                onDismiss = { activeModal = ActiveModal.None },
            )
        }
        is ActiveModal.ChangeSeed -> {
            SeedSettingsDialog(
                currentSeed = masterSeed,
                onDismiss = { activeModal = ActiveModal.None },
                onSave = { newSeed ->
                    val json = serializeServices(services)
                    prefs.edit()
                        .putString("services_encrypted", ConfigCrypto.encrypt(json, newSeed))
                        .apply()
                    masterSeed = newSeed
                    prefs.edit().remove("flag_nonce").apply()
                    flagNonce = null
                    saveSeedWithBiometric(newSeed)
                    activeModal = ActiveModal.None
                },
                onScanQr = { onResult -> scanQr(onResult) },
            )
        }
        is ActiveModal.TestVectors -> {
            val passed = testResults.count { it.passed }
            val failed = testResults.count { !it.passed }
            val total = TestVectors.vectors.size
            AlertDialog(
                onDismissRequest = {
                    activeModal = ActiveModal.None
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
                        activeModal = ActiveModal.None
                        testRunning = false
                    }) { Text("Close") }
                }
            )
        }
    }
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
                    IconButton(
                        onClick = { visible = !visible },
                        modifier = Modifier.semantics {
                            contentDescription = if (visible) "Hide password" else "Show password"
                        },
                    ) {
                        Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
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
        title = { Text("Change Master Seed") },
        text = {
            Column {
                Text(
                    "Caution: Changing your master seed will change all generated passwords " +
                        "and make vault-stored secrets unreadable.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextField(
                    value = seed,
                    onValueChange = { seed = it },
                    label = { Text("Master Seed") },
                    // Multi-line so a pasted or scanned seed containing newlines
                    // is visible and fixable.
                    singleLine = false,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.semantics {
                                contentDescription = if (visible) "Hide password" else "Show password"
                            },
                        ) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                        }
                    },
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onScanQr { scanned -> seed = scanned } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { showFingerprint = !showFingerprint },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Fingerprint, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Seed Fingerprint")
                }
                if (showFingerprint) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(seedFingerprint(seed), style = MaterialTheme.typography.bodySmall)
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
                    modifier = Modifier.semantics { testTag = "pin-input" },
                    label = { Text("PIN") },
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(
                            onClick = { visible = !visible },
                            modifier = Modifier.semantics {
                                contentDescription = if (visible) "Hide password" else "Show password"
                            },
                        ) {
                            Icon(if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
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
