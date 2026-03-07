package com.dgp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.dgp.engine.DgpEngine
import com.dgp.engine.TestVectors
import com.dgp.security.BiometricHelper
import com.dgp.security.ConfigCrypto
import android.util.Base64
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
    val comment: String = ""
)

class MainActivity : FragmentActivity() {

    private val biometricHelper = BiometricHelper()
    private lateinit var dgpEngine: DgpEngine
    private lateinit var prefs: android.content.SharedPreferences

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
            DgpApp(dgpEngine, prefs, biometricHelper)
        }
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
                obj.optString("comment", "")
            ))
        }
    } catch (_: Exception) {}
    return list.sortedBy { it.name.lowercase() }
}

fun serializeServices(services: List<DgpService>): String {
    val arr = JSONArray()
    services.forEach {
        arr.put(JSONObject().apply {
            put("id", it.id)
            put("name", it.name)
            put("type", it.type)
            put("comment", it.comment)
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
        services = newList.sortedBy { it.name.lowercase() }
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
        SeedEntryDialog(
            error = seedError,
            onUnlock = { seed -> unlockWithSeed(seed) },
            onScanQr = { onResult -> scanQr(onResult) }
        )
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

    val filteredServices = services.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
        it.comment.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DGP") },
                actions = {
                    if (isSeeded) {
                        if (account.isNotEmpty()) {
                            IconButton(onClick = {
                                clearAccount()
                                showAccountPrompt = true
                            }) {
                                Icon(Icons.Default.Clear, "Clear Account")
                            }
                        } else {
                            IconButton(onClick = { showAccountPrompt = true }) {
                                Icon(Icons.Default.Person, "Set Account")
                            }
                        }
                        IconButton(onClick = {
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
                        }) {
                            Icon(Icons.Default.CheckCircle, "Test Vectors")
                        }
                        IconButton(onClick = {
                            authenticate { showSeedSettings = true }
                        }) {
                            Icon(Icons.Default.Settings, "Seed Settings")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (isSeeded) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Service")
                }
            }
        }
    ) { padding ->
        if (!isSeeded) {
            // Locked screen
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Enter seed to unlock", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { showSeedPrompt = true }) {
                    Text("Unlock")
                }
            }
        } else {
            // Unlocked - show service list
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    placeholder = { Text("Search services...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true
                )

                LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    items(filteredServices) { service ->
                        ListItem(
                            headlineContent = { Text(service.name) },
                            supportingContent = {
                                val subtitle = listOfNotNull(
                                    service.type,
                                    service.comment.ifEmpty { null }
                                ).joinToString(" - ")
                                Text(subtitle)
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { editingService = service }) {
                                        Icon(Icons.Default.Edit, "Edit")
                                    }
                                    IconButton(onClick = {
                                        selectedServiceForGen = service
                                        generatedPassword = engine.generate(
                                            masterSeed, service.name, service.type, account
                                        )
                                    }) {
                                        Icon(Icons.Default.VpnKey, "Generate")
                                    }
                                }
                            },
                            modifier = Modifier.clickable { editingService = service }
                        )
                        Divider()
                    }
                }
            }

            // Dialogs
            if (showAddDialog || editingService != null) {
                ServiceEditDialog(
                    service = editingService,
                    onDismiss = { showAddDialog = false; editingService = null },
                    onSave = { name, type, comment ->
                        val newList = services.toMutableList()
                        if (editingService != null) {
                            newList.removeIf { it.id == editingService!!.id }
                            newList.add(DgpService(editingService!!.id, name, type, comment))
                        } else {
                            newList.add(DgpService(name = name, type = type, comment = comment))
                        }
                        saveServices(newList)
                        showAddDialog = false
                        editingService = null
                    },
                    onDelete = {
                        if (editingService != null) {
                            val newList = services.filter { it.id != editingService!!.id }
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
                    onScanQr = { onResult -> scanQr(onResult) }
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeedEntryDialog(
    error: Boolean,
    onUnlock: (String) -> Unit,
    onScanQr: ((String) -> Unit) -> Unit
) {
    var seed by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text("Unlock DGP") },
        text = {
            Column {
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
                if (error) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Wrong seed — could not decrypt config",
                         color = MaterialTheme.colorScheme.error,
                         style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onScanQr { scanned -> seed = scanned } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan QR Code")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (seed.isNotEmpty()) onUnlock(seed) },
                enabled = seed.isNotEmpty()
            ) { Text("Unlock") }
        },
        dismissButton = {}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEditDialog(
    service: DgpService?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(service?.name ?: "") }
    var type by remember { mutableStateOf(service?.type ?: "alnum") }
    var comment by remember { mutableStateOf(service?.comment ?: "") }
    val types = listOf("alnum", "alnumlong", "hex", "hexlong", "base58", "base58long", "xkcd", "xkcdlong")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service == null) "Add Service" else "Edit Service") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Service Name") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = comment, onValueChange = { comment = it }, label = { Text("Comment") })
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
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotEmpty()) onSave(name, type, comment) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (service != null) {
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
    onScanQr: ((String) -> Unit) -> Unit
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
                Text("Caution: Changing your master seed will change all generated passwords.",
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
