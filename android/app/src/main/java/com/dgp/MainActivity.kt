package com.dgp

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.dgp.engine.DgpEngine
import com.dgp.engine.TestVectors
import com.dgp.security.BiometricHelper
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.Scanner
import java.util.UUID

data class DgpService(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val account: String = "",
    val type: String = "alnum"
)

class MainActivity : FragmentActivity() {

    private val biometricHelper = BiometricHelper()
    private lateinit var dgpEngine: DgpEngine
    private lateinit var encryptedPrefs: android.content.SharedPreferences

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

        val masterKey = MasterKey.Builder(this)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        encryptedPrefs = EncryptedSharedPreferences.create(
            this, "dgp_prefs", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        setContent {
            DgpApp(dgpEngine, encryptedPrefs)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DgpApp(engine: DgpEngine, prefs: android.content.SharedPreferences) {
    val context = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager

    var servicesJson by remember { mutableStateOf(prefs.getString("services_list", "[]") ?: "[]") }
    val services = remember(servicesJson) {
        val list = mutableListOf<DgpService>()
        try {
            val arr = JSONArray(servicesJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(DgpService(obj.getString("id"), obj.getString("name"), obj.getString("account"), obj.getString("type")))
            }
        } catch (e: Exception) {}
        list.sortedBy { it.name.lowercase() }
    }

    var searchQuery by remember { mutableStateOf("") }
    var isUnlocked by remember { mutableStateOf(false) }
    var masterSeed by remember { mutableStateOf(prefs.getString("master_seed", "") ?: "") }
    
    // UI States
    var showAddDialog by remember { mutableStateOf(false) }
    var editingService by remember { mutableStateOf<DgpService?>(null) }
    var showSeedSettings by remember { mutableStateOf(false) }
    var showTestVectors by remember { mutableStateOf(false) }
    var testVectorOutput by remember { mutableStateOf("") }
    var selectedServiceForGen by remember { mutableStateOf<DgpService?>(null) }
    var generatedPassword by remember { mutableStateOf("") }

    val filteredServices = services.filter { it.name.contains(searchQuery, ignoreCase = true) || it.account.contains(searchQuery, ignoreCase = true) }

    fun saveServices(newList: List<DgpService>) {
        val arr = JSONArray()
        newList.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("account", it.account)
                put("type", it.type)
            })
        }
        val json = arr.toString()
        prefs.edit().putString("services_list", json).apply()
        servicesJson = json
    }

    fun authenticate(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(context)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Authenticate")
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        val biometricPrompt = BiometricPrompt(context, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onSuccess()
            }
        })
        biometricPrompt.authenticate(promptInfo)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("DGP") },
                actions = {
                    IconButton(onClick = {
                        val result = TestVectors.run(engine)
                        testVectorOutput = result.output
                        showTestVectors = true
                    }) {
                        Icon(Icons.Default.CheckCircle, "Test Vectors")
                    }
                    IconButton(onClick = {
                        authenticate {
                            showSeedSettings = true
                        }
                    }) {
                        Icon(Icons.Default.Settings, "Seed Settings")
                    }
                    IconButton(onClick = { isUnlocked = !isUnlocked }) {
                        Icon(if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock, "Lock/Unlock")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, "Add Service")
            }
        }
    ) { padding ->
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
                        supportingContent = { if (service.account.isNotEmpty()) Text(service.account) },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingService = service }) {
                                    Icon(Icons.Default.Edit, "Edit")
                                }
                                IconButton(onClick = {
                                    if (isUnlocked) {
                                        selectedServiceForGen = service
                                        generatedPassword = engine.generate(masterSeed, service.name, service.type, service.account)
                                    } else {
                                        authenticate {
                                            isUnlocked = true
                                            selectedServiceForGen = service
                                            generatedPassword = engine.generate(masterSeed, service.name, service.type, service.account)
                                        }
                                    }
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
                onSave = { name, account, type ->
                    val newList = services.toMutableList()
                    if (editingService != null) {
                        newList.removeIf { it.id == editingService!!.id }
                        newList.add(DgpService(editingService!!.id, name, account, type))
                    } else {
                        newList.add(DgpService(name = name, account = account, type = type))
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
                    prefs.edit().putString("master_seed", newSeed).apply()
                    masterSeed = newSeed
                    showSeedSettings = false
                }
            )
        }

        if (showTestVectors) {
            AlertDialog(
                onDismissRequest = { showTestVectors = false },
                title = { Text("Test Vectors") },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(testVectorOutput, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTestVectors = false }) { Text("Close") }
                }
            )
        }

        if (selectedServiceForGen != null) {
            AlertDialog(
                onDismissRequest = { selectedServiceForGen = null },
                title = { Text(selectedServiceForGen!!.name) },
                text = {
                    Column {
                        Text("Type: ${selectedServiceForGen!!.type}", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(generatedPassword, style = MaterialTheme.typography.headlineMedium)
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val clip = android.content.ClipData.newPlainText("DGP Password", generatedPassword)
                        clipboardManager.setPrimaryClip(clip)
                        scope.launch {
                            kotlinx.coroutines.delay(15000)
                            if (java.util.Objects.equals(clipboardManager.primaryClip?.getItemAt(0)?.text, generatedPassword)) {
                                clipboardManager.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
                            }
                        }
                        selectedServiceForGen = null
                    }) {
                        Text("Copy (15s)")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { selectedServiceForGen = null }) { Text("Close") }
                }
            )
        }
    }
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
    var account by remember { mutableStateOf(service?.account ?: "") }
    var type by remember { mutableStateOf(service?.type ?: "alnum") }
    val types = listOf("alnum", "alnumlong", "hex", "hexlong", "base58", "base58long", "xkcd", "xkcdlong")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (service == null) "Add Service" else "Edit Service") },
        text = {
            Column {
                TextField(value = name, onValueChange = { name = it }, label = { Text("Service Name") })
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = account, onValueChange = { account = it }, label = { Text("Account/Secret") })
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
            Button(onClick = { if (name.isNotEmpty()) onSave(name, account, type) }) { Text("Save") }
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
fun SeedSettingsDialog(
    currentSeed: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var seed by remember { mutableStateOf(currentSeed) }
    var visible by remember { mutableStateOf(false) }

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


