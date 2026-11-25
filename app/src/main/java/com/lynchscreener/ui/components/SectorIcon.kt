package com.lynchscreener.ui.components

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PrecisionManufacturing
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Terrain

/**
 * Maps a sector (GICS/B3 name, in pt-BR or en) to a rounded Material icon.
 * Falls back to [Business].
 */
fun sectorIcon(sector: String?): ImageVector {
    val s = (sector ?: "").lowercase()
    return when {
        "banco" in s || "bank" in s || "financeir" in s || "financ" in s ->
            Icons.Rounded.AccountBalance
        "segur" in s || "insurance" in s ->
            Icons.Rounded.Shield
        "constru" in s || "imobili" in s || "real estate" in s || "propriedade" in s ->
            Icons.Rounded.Apartment
        "industr" in s || "bens industriais" in s || "autope" in s || "maquin" in s ->
            Icons.Rounded.PrecisionManufacturing
        "tecnolog" in s || "technology" in s || "semicond" in s || "software" in s || "hardware" in s ->
            Icons.Rounded.Memory
        "consumo" in s || "varejo" in s || "retail" in s || "consumer" in s ->
            Icons.Rounded.ShoppingBag
        "saúde" in s || "saude" in s || "health" in s || "farmac" in s || "hospital" in s ->
            Icons.Rounded.MedicalServices
        "energia" in s || "energy" in s || "utilidade" in s || "utility" in s || "elétri" in s || "eletri" in s ->
            Icons.Rounded.Bolt
        "materia" in s || "miner" in s || "metal" in s || "siderurg" in s || "agro" in s ->
            Icons.Rounded.Terrain
        "telecom" in s || "comunica" in s || "communication" in s || "telefon" in s ->
            Icons.Rounded.CellTower
        else -> Icons.Rounded.Business
    }
}
