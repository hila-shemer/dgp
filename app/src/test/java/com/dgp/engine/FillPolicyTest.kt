package com.dgp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FillPolicyTest {

    @Test
    fun nonBrowserClaimingAWebDomainIsMatchedAsItself() {
        assertEquals(
            SiteMatcher.Target.App("com.evil.app"),
            FillPolicy.target("com.evil.app", "google.com", "https", 34),
        )
    }

    @Test
    fun browserWebDomainIsBelieved() {
        assertEquals(
            SiteMatcher.Target.Web("accounts.google.com"),
            FillPolicy.target("com.android.chrome", "accounts.google.com", "https", 34),
        )
    }

    @Test
    fun browserWithoutDomainOrOnHttpGetsNothing() {
        assertNull(FillPolicy.target("com.android.chrome", null, null, 34))
        assertNull(FillPolicy.target("com.android.chrome", "bank.com", "http", 34))
        assertEquals(
            SiteMatcher.Target.Web("localhost"),
            FillPolicy.target("com.android.chrome", "localhost", "http", 34),
        )
    }

    @Test
    fun spoofedDomainFromAppMatchesNoWebEntry() {
        val svc = listOf(com.dgp.DgpService(id = "g", name = "google.com", sites = listOf("google.com")))
        val t = FillPolicy.target("com.evil.app", "google.com", "https", 34)!!
        assertEquals(emptyList<com.dgp.DgpService>(), SiteMatcher.match(svc, t))
    }

    @Test
    fun belowApi28NothingIsOffered() {
        assertNull(FillPolicy.target("com.android.chrome", "github.com", "https", 27))
        assertNull(FillPolicy.target("com.github.android", null, null, 27))
    }
}
