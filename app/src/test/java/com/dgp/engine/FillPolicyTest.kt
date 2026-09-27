package com.dgp.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FillPolicyTest {

    @Test
    fun nonBrowserClaimingAWebDomainIsMatchedAsItself() {
        assertEquals(
            SiteMatcher.Target.App("com.evil.app"),
            FillPolicy.target("com.evil.app", "google.com", "https"),
        )
    }

    @Test
    fun browserWebDomainIsBelieved() {
        assertEquals(
            SiteMatcher.Target.Web("accounts.google.com"),
            FillPolicy.target("com.android.chrome", "accounts.google.com", "https"),
        )
    }

    @Test
    fun browserWithoutDomainOrOnHttpGetsNothing() {
        assertNull(FillPolicy.target("com.android.chrome", null, null))
        assertNull(FillPolicy.target("com.android.chrome", "bank.com", "http"))
        assertEquals(
            SiteMatcher.Target.Web("localhost"),
            FillPolicy.target("com.android.chrome", "localhost", "http"),
        )
    }

    @Test
    fun spoofedDomainFromAppMatchesNoWebEntry() {
        val svc = listOf(com.dgp.DgpService(id = "g", name = "google.com", sites = listOf("google.com")))
        val t = FillPolicy.target("com.evil.app", "google.com", "https")!!
        assertEquals(emptyList<com.dgp.DgpService>(), SiteMatcher.match(svc, t))
    }
}
