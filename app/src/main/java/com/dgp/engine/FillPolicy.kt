package com.dgp.engine

/**
 * Decides who a fill request is really from.
 *
 * Any app can put any webDomain in its own view tree, so a web domain is believed
 * only from a known browser; every other app is matched by its package name. A
 * browser request with no domain (its own UI) or on plain http gets nothing.
 * Residual risk: a sideloaded app can take the package name of a browser that is
 * NOT installed; package names are unique per device, so it cannot while the real
 * browser is there.
 *
 * Below API 28 nothing is offered at all: there webScheme does not exist (so http
 * can't be refused) and the requester's activityComponent may be forged by the app
 * itself. Chrome's third-party autofill needs Android 14 anyway.
 */
object FillPolicy {

    val BROWSERS = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.chromium.chrome",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "com.brave.browser", "com.microsoft.emmx", "com.sec.android.app.sbrowser",
    )

    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "[::1]", "::1")

    const val MIN_SDK = 28

    /** The match target for a request, or null to offer nothing. */
    fun target(packageName: String, webDomain: String?, webScheme: String?, sdkInt: Int): SiteMatcher.Target? {
        if (sdkInt < MIN_SDK) return null
        if (packageName !in BROWSERS) return SiteMatcher.Target.App(packageName)
        val domain = webDomain?.takeIf { it.isNotBlank() } ?: return null
        if (webScheme.equals("http", ignoreCase = true) &&
            SiteMatcher.normalizeHost(domain) !in LOCAL_HOSTS) return null
        return SiteMatcher.Target.Web(domain)
    }
}
