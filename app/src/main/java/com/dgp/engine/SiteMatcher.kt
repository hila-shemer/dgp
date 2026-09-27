package com.dgp.engine

import com.dgp.DgpService

/**
 * Which entries autofill offers for a web page or an app.
 *
 * The rules are in docs/chrome-autofill-plan.md ("Matching") and are shared with
 * linux/dgp/sitematch.py; linux/tests/fixtures/sitematch-cases.json holds the case
 * table both test suites run. Change the rules on both sides or neither.
 */
object SiteMatcher {

    sealed class Target {
        data class Web(val host: String) : Target()
        data class App(val packageName: String) : Target()
    }

    private val SECOND_LEVEL = setOf("co", "com", "net", "org", "ac", "gov", "edu", "ltd", "plc")

    /** Host from a URL or bare host: lowercase, no scheme/path/port/trailing dot/leading www. */
    fun normalizeHost(input: String): String {
        var h = input.trim().lowercase()
        val scheme = h.indexOf("://")
        if (scheme >= 0) h = h.substring(scheme + 3)
        h = h.substringBefore('/').substringBefore('?').substringBefore('#')
        h = h.substringAfterLast('@')
        h = h.substringBefore(':')
        h = h.trimEnd('.')
        if (h.startsWith("www.")) h = h.removePrefix("www.")
        return h
    }

    /** Last two labels, or three under a two-letter TLD with a generic second level (b.co.il). */
    fun registrable(host: String): String {
        val labels = host.split('.').filter { it.isNotEmpty() }
        if (labels.size <= 2) return labels.joinToString(".")
        val tld = labels.last()
        val second = labels[labels.size - 2]
        val take = if (tld.length == 2 && second in SECOND_LEVEL) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun match(services: List<DgpService>, target: Target): List<DgpService> {
        val live = services.filter { !it.archived }
        val bySite = live.filter { svc -> svc.sites.any { siteMatches(it, target) } }
        val byName = live.filter { svc -> svc.sites.isEmpty() && nameMatches(svc.name, target) }
        return bySite + byName
    }

    /** The value autofill stores into `sites` when the user picks an entry for [target]. */
    fun siteFor(target: Target): String = when (target) {
        is Target.Web -> registrable(normalizeHost(target.host))
        is Target.App -> target.packageName.lowercase()
    }

    private fun siteMatches(site: String, target: Target): Boolean = when (target) {
        is Target.Web -> {
            val h = normalizeHost(target.host)
            h == site || h.endsWith(".$site")
        }
        is Target.App -> site == target.packageName.lowercase()
    }

    /**
     * Name fallback for entries with no sites: the name must be the whole registrable
     * domain ("github.com", not "github"), or an entry named "google" would be offered
     * on google.evil. Apps get no fallback: any app can take a "google" package label.
     */
    private fun nameMatches(name: String, target: Target): Boolean {
        val n = name.lowercase().replace(" ", "")
        return target is Target.Web && n.isNotEmpty() && n == registrable(normalizeHost(target.host))
    }
}
