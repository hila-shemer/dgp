package com.dgp.engine

import com.dgp.DgpService

/**
 * Import from a Google Password Manager CSV export (Chrome, desktop or Android).
 *
 * Hila's rule (2026-09-27): every Chrome password DGP did not generate becomes a
 * vault entry. A row whose password DGP already derives for a matching entry only
 * adds the site to that entry. The rules match docs/chrome-autofill-plan.md and
 * linux/dgp's import-chrome. Passwords are never logged; a Row is dropped as soon
 * as the plan is built.
 */
object ChromeImport {

    data class Row(val site: String, val isApp: Boolean, val username: String, val password: String)

    data class Plan(
        val services: List<DgpService>,
        val generated: Int,
        val added: List<String>,
        val skipped: Int,
    )

    /** Rows of the CSV. Columns go by header name; only `url` is required. */
    fun parseCsv(text: String): List<Row> {
        val records = csvRecords(text.removePrefix("﻿"))
        if (records.isEmpty()) return emptyList()
        val header = records[0].map { it.trim().lowercase() }
        val iUrl = header.indexOf("url")
        require(iUrl >= 0) { "not a Chrome password export: no url column" }
        val iName = header.indexOf("name")
        val iUser = header.indexOf("username")
        val iPass = header.indexOf("password")
        return records.drop(1).mapNotNull { r ->
            fun col(i: Int) = if (i in r.indices) r[i] else ""
            val url = col(iUrl).trim().ifEmpty { col(iName).trim() }
            if (url.isEmpty()) return@mapNotNull null
            val isApp = url.startsWith("android://")
            val site = if (isApp) url.substringAfter('@').trimEnd('/').lowercase()
                       else SiteMatcher.normalizeHost(url)
            if (site.isEmpty()) null else Row(site, isApp, col(iUser), col(iPass))
        }
    }

    /**
     * Works out the new service list. [secretOf] derives or decrypts an existing
     * entry; [encrypt] vault-encrypts a password under an entry name.
     */
    fun plan(
        rows: List<Row>,
        existing: List<DgpService>,
        secretOf: (DgpService) -> String?,
        encrypt: (name: String, password: String) -> String,
    ): Plan {
        val services = existing.toMutableList()
        val secrets = HashMap<String, String?>()
        fun secret(s: DgpService) = secrets.getOrPut(s.id) { secretOf(s) }
        var generated = 0
        var skipped = 0
        val added = mutableListOf<String>()

        for (row in rows.distinct()) {
            if (row.password.isEmpty()) { skipped++; continue }
            val target = if (row.isApp) SiteMatcher.Target.App(row.site) else SiteMatcher.Target.Web(row.site)

            val gen = SiteMatcher.match(services, target)
                .firstOrNull { it.type != "vault" && secret(it) == row.password }
            if (gen != null) {
                if (row.site !in gen.sites) {
                    val i = services.indexOfFirst { it.id == gen.id }
                    services[i] = gen.copy(sites = gen.sites + row.site)
                }
                generated++
                continue
            }

            val already = services.any {
                it.type == "vault" && row.site in it.sites && secret(it) == row.password
            }
            if (already) { skipped++; continue }

            val name = freeName(services, row)
            services.add(DgpService(
                name = name,
                type = "vault",
                comment = row.username,
                tags = listOf(TAG),
                sites = listOf(row.site),
                encryptedSecret = encrypt(name, row.password),
            ))
            secrets[services.last().id] = row.password
            added.add(name)
        }
        return Plan(services, generated, added, skipped)
    }

    const val TAG = "chrome-import"

    private fun freeName(services: List<DgpService>, row: Row): String {
        fun taken(n: String) = services.any { it.name.equals(n, ignoreCase = true) }
        if (!taken(row.site)) return row.site
        val base = if (row.username.isNotEmpty()) "${row.site} (${row.username})" else row.site
        if (!taken(base)) return base
        var k = 2
        while (taken("$base #$k")) k++
        return "$base #$k"
    }

    /** RFC 4180: quoted fields, doubled quotes, CRLF or LF, newlines inside quotes. */
    internal fun csvRecords(text: String): List<List<String>> {
        val out = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') { field.append('"'); i++ } else quoted = false
                } else field.append(c)
            } else when (c) {
                '"' -> quoted = true
                ',' -> { row.add(field.toString()); field.clear() }
                '\r' -> {}
                '\n' -> { row.add(field.toString()); field.clear(); out.add(row); row = mutableListOf() }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); out.add(row) }
        return out.filter { r -> r.any { it.isNotEmpty() } }
    }
}
