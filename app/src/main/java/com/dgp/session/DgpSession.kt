package com.dgp.session

import android.content.Context
import com.dgp.DgpService
import com.dgp.engine.DgpEngine
import com.dgp.security.ConfigCrypto
import com.dgp.serializeServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Scanner

/**
 * The unlocked seed, account and service list, held for the whole app process.
 *
 * Hila wants the phone app "unlocked most of the time", so an unlock outlives the
 * activity that did it: MainActivity and the autofill picker both read and write
 * this. It is memory only. Lock clears it, and a reboot or Android killing the
 * process ends it; the next use asks for a fingerprint again.
 */
object DgpSession {

    data class Unlocked(val seed: String, val account: String, val services: List<DgpService>)

    private val _state = MutableStateFlow<Unlocked?>(null)
    val state: StateFlow<Unlocked?> = _state

    val current: Unlocked? get() = _state.value

    fun set(seed: String, account: String, services: List<DgpService>) {
        _state.value = if (seed.isEmpty()) null else Unlocked(seed, account, services)
    }

    fun lock() {
        _state.value = null
    }

    /** Replace the service list and persist it the same way MainActivity.saveServices does. */
    fun saveServices(context: Context, services: List<DgpService>) {
        val s = _state.value ?: return
        prefs(context).edit()
            .putString("services_encrypted", ConfigCrypto.encrypt(serializeServices(services), s.seed))
            .apply()
        _state.value = s.copy(services = services)
    }

    /**
     * The account is cleared on every reboot. Whoever unlocks first after a boot
     * (MainActivity or the autofill picker) runs this; true means it was cleared.
     */
    fun clearAccountIfRebooted(prefs: android.content.SharedPreferences): Boolean {
        val bootTime = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()
        val lastBootTime = prefs.getLong("last_boot_time", 0L)
        // Allow 5s tolerance for timing differences
        val rebooted = lastBootTime == 0L || kotlin.math.abs(bootTime - lastBootTime) > 5000
        val edit = prefs.edit().putLong("last_boot_time", bootTime)
        if (rebooted) edit.remove("account_encrypted")
        edit.apply()
        return rebooted
    }

    fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("dgp_prefs", Context.MODE_PRIVATE)

    @Volatile private var engine: DgpEngine? = null

    /** One engine per process, with the real BIP-39 list from assets. */
    fun engine(context: Context): DgpEngine = engine ?: synchronized(this) {
        engine ?: run {
            val words = mutableListOf<String>()
            context.applicationContext.assets.open("english.txt").use { stream ->
                Scanner(stream).use { sc -> while (sc.hasNextLine()) words.add(sc.nextLine()) }
            }
            DgpEngine(words).also { engine = it }
        }
    }

    /** The secret for [service]: derived, or decrypted for vault entries. Null if a vault blob won't open. */
    fun secretFor(engine: DgpEngine, s: Unlocked, service: DgpService): String? {
        if (service.type != "vault") return engine.generate(s.seed, service.name, service.type, s.account)
        val blob = service.encryptedSecret ?: return null
        return ConfigCrypto.decryptWithRawKey(blob, engine.deriveAesKey(s.seed, service.name, s.account))
    }
}
