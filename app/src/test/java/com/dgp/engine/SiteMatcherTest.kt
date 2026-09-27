package com.dgp.engine

import com.dgp.DgpService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Runs the case table shared with linux/tests (see SiteMatcher's doc comment). */
class SiteMatcherTest {

    private val fixture = JSONObject(File("../linux/tests/fixtures/sitematch-cases.json").readText())

    private val services = fixture.getJSONArray("services").let { arr ->
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val sites = o.getJSONArray("sites")
            DgpService(
                id = o.getString("id"),
                name = o.getString("name"),
                archived = o.optBoolean("archived", false),
                sites = (0 until sites.length()).map(sites::getString),
            )
        }
    }

    @Test
    fun sharedCases() {
        val cases = fixture.getJSONArray("cases")
        for (i in 0 until cases.length()) {
            val c = cases.getJSONObject(i)
            val input = c.getString("input")
            val target = if (c.getString("kind") == "web") SiteMatcher.Target.Web(input)
                         else SiteMatcher.Target.App(input)
            val expect = c.getJSONArray("expect").let { a -> (0 until a.length()).map(a::getString) }
            assertEquals("case $i ($input)", expect, SiteMatcher.match(services, target).map { it.id })
        }
    }

    @Test
    fun siteFor_webIsRegistrableDomain_appIsPackage() {
        assertEquals("example.co.uk", SiteMatcher.siteFor(SiteMatcher.Target.Web("https://login.example.co.uk/a")))
        assertEquals("com.foo.bar", SiteMatcher.siteFor(SiteMatcher.Target.App("com.Foo.bar")))
    }
}
