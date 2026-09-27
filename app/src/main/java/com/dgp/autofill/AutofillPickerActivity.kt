package com.dgp.autofill

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.autofill.Dataset
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.dgp.DgpService
import com.dgp.R
import com.dgp.engine.SiteMatcher
import com.dgp.session.DgpSession
import com.dgp.session.SessionUnlocker
import com.dgp.ui.theme.EditorialTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs when a DGP autofill suggestion is tapped. Unlocks the session if needed,
 * then fills the entry the suggestion named, or lets the user pick one. Picking an
 * entry that did not match adds this site to it, so next time it is offered directly.
 */
class AutofillPickerActivity : FragmentActivity() {

    private lateinit var passwordIds: List<AutofillId>
    private lateinit var target: SiteMatcher.Target

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        @Suppress("DEPRECATION")
        passwordIds = intent.getParcelableArrayListExtra<AutofillId>(EXTRA_PASSWORD_IDS) ?: emptyList()
        val web = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE)
        target = when {
            web != null -> SiteMatcher.Target.Web(web)
            pkg != null -> SiteMatcher.Target.App(pkg)
            else -> return cancel()
        }
        if (passwordIds.isEmpty()) return cancel()

        if (DgpSession.current != null) proceed()
        else SessionUnlocker.unlock(this) { ok -> if (ok) proceed() else cancel() }
    }

    private fun proceed() {
        val session = DgpSession.current ?: return cancel()
        if (session.account.isEmpty()) {
            Toast.makeText(this, "Open DGP and set your account first", Toast.LENGTH_LONG).show()
            return cancel()
        }
        val wanted = intent.getStringExtra(EXTRA_SERVICE_ID)
        if (wanted != null) {
            val svc = session.services.firstOrNull { it.id == wanted } ?: return cancel()
            return fill(svc)
        }
        val matches = SiteMatcher.match(session.services, target)
        val all = session.services.filter { !it.archived }
        setContent {
            EditorialTheme {
                Surface(Modifier.fillMaxSize()) {
                    PickerList(targetLabel(), matches, all) { fill(it) }
                }
            }
        }
    }

    private fun targetLabel() = when (val t = target) {
        is SiteMatcher.Target.Web -> SiteMatcher.normalizeHost(t.host)
        is SiteMatcher.Target.App -> t.packageName
    }

    private fun fill(service: DgpService) {
        val session = DgpSession.current ?: return cancel()
        lifecycleScope.launch {
            val secret = withContext(Dispatchers.Default) {
                DgpSession.secretFor(DgpSession.engine(this@AutofillPickerActivity), session, service)
            }
            if (secret == null) {
                Toast.makeText(this@AutofillPickerActivity, "Could not open ${service.name}", Toast.LENGTH_LONG).show()
                return@launch cancel()
            }
            rememberSite(service)
            val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
                setTextViewText(R.id.autofill_label, service.name)
            }
            @Suppress("DEPRECATION")
            val dataset = Dataset.Builder(presentation).apply {
                passwordIds.forEach { id ->
                    @Suppress("DEPRECATION")
                    setValue(id, AutofillValue.forText(secret), presentation)
                }
            }.build()
            setResult(RESULT_OK, Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset))
            finish()
        }
    }

    /** An entry picked for a site it doesn't list gets that site, so it matches next time. */
    private fun rememberSite(service: DgpService) {
        val session = DgpSession.current ?: return
        if (SiteMatcher.match(listOf(service), target).isNotEmpty()) return
        val site = SiteMatcher.siteFor(target)
        if (site.isEmpty()) return
        val updated = session.services.map {
            if (it.id == service.id) it.copy(sites = it.sites + site) else it
        }
        DgpSession.saveServices(this, updated)
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_PASSWORD_IDS = "com.dgp.autofill.PASSWORD_IDS"
        const val EXTRA_WEB_DOMAIN = "com.dgp.autofill.WEB_DOMAIN"
        const val EXTRA_PACKAGE = "com.dgp.autofill.PACKAGE"
        const val EXTRA_SERVICE_ID = "com.dgp.autofill.SERVICE_ID"

        fun intent(context: Context, form: ParsedForm, serviceId: String?): Intent =
            Intent(context, AutofillPickerActivity::class.java).putForm(form).apply {
                if (serviceId != null) putExtra(EXTRA_SERVICE_ID, serviceId)
            }
    }
}

@androidx.compose.runtime.Composable
private fun PickerList(
    target: String,
    matches: List<DgpService>,
    all: List<DgpService>,
    onPick: (DgpService) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) matches.ifEmpty { all }
                else all.filter { it.name.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Fill password for $target", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search entries") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        )
        LazyColumn {
            items(shown, key = { it.id }) { svc ->
                Column(Modifier.fillMaxWidth().clickable { onPick(svc) }.padding(vertical = 12.dp)) {
                    Text(svc.name, style = MaterialTheme.typography.bodyLarge)
                    if (svc.comment.isNotEmpty()) {
                        Text(svc.comment, style = MaterialTheme.typography.bodySmall)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}
