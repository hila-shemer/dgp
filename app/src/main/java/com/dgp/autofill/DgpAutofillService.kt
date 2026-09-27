package com.dgp.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import android.widget.RemoteViews
import com.dgp.R
import com.dgp.engine.SiteMatcher
import com.dgp.session.DgpSession

/**
 * Offers DGP entries to Chrome (via "Autofill using another service") and to apps.
 *
 * Every suggestion shows a name only and needs AutofillPickerActivity to run
 * before anything is filled: the password is derived there, on the user's tap,
 * never here. DGP fills and never saves, so there is no SaveInfo.
 */
class DgpAutofillService : AutofillService() {

    override fun onFillRequest(request: FillRequest, cancellationSignal: CancellationSignal, callback: FillCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure ?: return callback.onSuccess(null)
        val form = FormParser.parse(structure)
        if (form.passwordIds.isEmpty() || structure.activityComponent.packageName == packageName) {
            return callback.onSuccess(null)
        }

        val response = FillResponse.Builder()
        val session = DgpSession.current
        if (session == null) {
            response.addDataset(dataset(form, "Unlock DGP", serviceId = null))
        } else {
            SiteMatcher.match(session.services, form.target).take(MAX_MATCHES).forEach { svc ->
                response.addDataset(dataset(form, svc.name, svc.id))
            }
            response.addDataset(dataset(form, "Search DGP…", serviceId = null))
        }
        callback.onSuccess(response.build())
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        callback.onSuccess() // never asked for: no SaveInfo is ever set
    }

    private fun dataset(form: ParsedForm, label: String, serviceId: String?): Dataset {
        val presentation = RemoteViews(packageName, R.layout.autofill_item).apply {
            setTextViewText(R.id.autofill_label, label)
        }
        val intent = AutofillPickerActivity.intent(this, form, serviceId)
        val sender = PendingIntent.getActivity(
            this, nextRequestCode++, intent,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
        @Suppress("DEPRECATION")
        val builder = Dataset.Builder(presentation)
        form.passwordIds.forEach { id: AutofillId ->
            @Suppress("DEPRECATION")
            builder.setValue(id, null, presentation)
        }
        return builder.setAuthentication(sender).build()
    }

    companion object {
        private const val MAX_MATCHES = 4
        private var nextRequestCode = 1
    }
}

internal fun Intent.putForm(form: ParsedForm): Intent = apply {
    putParcelableArrayListExtra(AutofillPickerActivity.EXTRA_PASSWORD_IDS, ArrayList(form.passwordIds))
    when (val t = form.target) {
        is SiteMatcher.Target.Web -> putExtra(AutofillPickerActivity.EXTRA_WEB_DOMAIN, t.host)
        is SiteMatcher.Target.App -> putExtra(AutofillPickerActivity.EXTRA_PACKAGE, t.packageName)
    }
}
