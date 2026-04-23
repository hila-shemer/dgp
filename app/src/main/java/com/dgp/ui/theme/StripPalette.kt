package com.dgp.ui.theme

import androidx.compose.ui.graphics.Color

fun EditorialColors.stripFor(type: String): Color = when (type) {
    "alnum" -> stripAlnum
    "alnumlong" -> stripAlnumLong
    "hex" -> stripHex
    "hexlong" -> stripHexLong
    "base58" -> stripBase58
    "base58long" -> stripBase58Long
    "xkcd" -> stripXkcd
    "xkcdlong" -> stripXkcdLong
    "vault" -> stripVault
    else -> inkMuted
}
