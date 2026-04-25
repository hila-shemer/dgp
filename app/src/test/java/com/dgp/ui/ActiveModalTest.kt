package com.dgp.ui

import com.dgp.DgpService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveModalTest {

    @Test
    fun activeModal_replacingEditingWithRevealing_yieldsRevealingOnly() {
        var modal: ActiveModal = ActiveModal.None
        val svc = DgpService(name = "github", type = "alnum")
        modal = ActiveModal.Editing(svc)
        modal = ActiveModal.Revealing(svc)
        assertTrue(modal is ActiveModal.Revealing)
        assertEquals(svc, (modal as ActiveModal.Revealing).service)
    }

    @Test
    fun activeModal_isExclusive_onlyOneActiveAtATime() {
        var modal: ActiveModal = ActiveModal.None
        assertTrue(modal is ActiveModal.None)

        val svc = DgpService(name = "github", type = "alnum")
        modal = ActiveModal.Editing(svc)
        assertTrue(modal is ActiveModal.Editing)

        modal = ActiveModal.Revealing(svc)
        assertTrue(modal is ActiveModal.Revealing)

        modal = ActiveModal.None
        assertTrue(modal is ActiveModal.None)

        modal = ActiveModal.ExportPin
        assertTrue(modal is ActiveModal.ExportPin)

        modal = ActiveModal.None
        assertTrue(modal is ActiveModal.None)

        modal = ActiveModal.ImportPin(null)
        assertTrue(modal is ActiveModal.ImportPin)

        modal = ActiveModal.None
        assertTrue(modal is ActiveModal.None)

        modal = ActiveModal.AddNew("abc")
        assertTrue(modal is ActiveModal.AddNew)
        assertEquals("abc", (modal as ActiveModal.AddNew).initialName)

        modal = ActiveModal.None
        assertTrue(modal is ActiveModal.None)
    }
}
