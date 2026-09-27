package com.dgp.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import com.dgp.engine.FillPolicy
import com.dgp.engine.SiteMatcher

/** The password fields of a fill request, and who is asking (a web domain or an app). */
data class ParsedForm(
    val passwordIds: List<AutofillId>,
    val target: SiteMatcher.Target,
)

/** What the view tree claims. FillPolicy decides how much of it to believe. */
data class RawForm(
    val passwordIds: List<AutofillId>,
    val packageName: String,
    val webDomain: String?,
    val webScheme: String?,
)

object FormParser {

    /** Null when FillPolicy says to offer nothing. */
    fun parse(structure: AssistStructure): ParsedForm? {
        val raw = parseRaw(structure)
        val target = FillPolicy.target(raw.packageName, raw.webDomain, raw.webScheme) ?: return null
        return ParsedForm(raw.passwordIds, target)
    }

    private fun parseRaw(structure: AssistStructure): RawForm {
        val ids = mutableListOf<AutofillId>()
        var domain: String? = null
        var scheme: String? = null
        for (i in 0 until structure.windowNodeCount) {
            walk(structure.getWindowNodeAt(i).rootViewNode, null, null) { node, inheritedDomain, inheritedScheme ->
                val id = node.autofillId
                if (id != null && isPassword(node)) {
                    ids.add(id)
                    // The domain of the first password field wins, so an iframe from
                    // another site can't borrow the page's entries.
                    if (domain == null) { domain = inheritedDomain; scheme = inheritedScheme }
                }
            }
        }
        return RawForm(ids, structure.activityComponent.packageName, domain, scheme)
    }

    private fun walk(
        node: AssistStructure.ViewNode,
        parentDomain: String?,
        parentScheme: String?,
        visit: (AssistStructure.ViewNode, String?, String?) -> Unit,
    ) {
        val own = node.webDomain?.takeIf { it.isNotBlank() }
        val domain = own ?: parentDomain
        val scheme = if (own != null) {
            if (android.os.Build.VERSION.SDK_INT >= 28) node.webScheme else null
        } else parentScheme
        visit(node, domain, scheme)
        for (i in 0 until node.childCount) walk(node.getChildAt(i), domain, scheme, visit)
    }

    private fun isPassword(node: AssistStructure.ViewNode): Boolean {
        if (node.autofillType != View.AUTOFILL_TYPE_TEXT) return false
        val hints = node.autofillHints?.map { it.lowercase() } ?: emptyList()
        if (hints.any { it == View.AUTOFILL_HINT_PASSWORD.lowercase() || it == "current-password" || it == "new-password" }) {
            return true
        }
        val html = node.htmlInfo
        if (html != null && html.tag.equals("input", ignoreCase = true)) {
            if (html.attributes?.any { it.first == "type" && it.second.equals("password", ignoreCase = true) } == true) {
                return true
            }
        }
        val t = node.inputType
        val cls = t and InputType.TYPE_MASK_CLASS
        val variation = t and InputType.TYPE_MASK_VARIATION
        return (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
    }
}
