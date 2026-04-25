package com.dgp.ui

import android.net.Uri
import com.dgp.DgpService

sealed class ActiveModal {
    data object None : ActiveModal()
    data class Editing(val service: DgpService) : ActiveModal()
    data class Revealing(val service: DgpService) : ActiveModal()
    data object ExportPin : ActiveModal()
    data class ImportPin(val fileUri: Uri?) : ActiveModal()
    data object ChangeSeed : ActiveModal()
    data object TestVectors : ActiveModal()
    data object Account : ActiveModal()
    data class AddNew(val initialName: String) : ActiveModal()
}
