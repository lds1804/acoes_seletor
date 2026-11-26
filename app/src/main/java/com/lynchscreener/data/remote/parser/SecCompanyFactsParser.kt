package com.lynchscreener.data.remote.parser

import com.lynchscreener.data.remote.dto.SecFundamentals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Parses the SEC XBRL company-facts JSON defensively using [JsonObject]
 * traversal instead of strict DTOs, so missing/renamed tags never throw.
 *
 * Structure: facts.us-gaap.<Metric>.units.<Currency>[ {start,end,val,form,fy,...} ]
 */
object SecCompanyFactsParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // Preferred us-gaap tag names (first match wins).
    private val NET_INCOME = listOf("NetIncomeLoss")
    private val EQUITY = listOf(
        "StockholdersEquity",
        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
    )
    private val CASH = listOf(
        "CashAndCashEquivalentsAtCarryingValue",
        "CashCashEquivalentsAndShortTermInvestments",
    )
    private val DEBT_LT = listOf("LongTermDebtNoncurrent", "LongTermDebt")
    private val DEBT_ST = listOf("DebtCurrent", "LongTermDebtCurrent")
    // Shares live under "dei" as EntityCommonStockSharesOutstanding for most filers.
    private val SHARES = listOf("EntityCommonStockSharesOutstanding", "CommonStockSharesOutstanding")
    private val DIVIDEND = listOf("CommonStockDividendsPerShareDeclared")

    fun parse(raw: String): SecFundamentals {
        val root = json.parseToJsonElement(raw).jsonObject
        val entityName = root["entityName"]?.jsonPrimitive?.contentOrNull

        val facts = root["facts"]?.jsonObject ?: return empty(entityName)
        val usGaap = facts["us-gaap"]?.jsonObject ?: JsonObject(emptyMap())
        val dei = facts["dei"]?.jsonObject ?: JsonObject(emptyMap())

        val netIncomeHistory = annualValues(usGaap, NET_INCOME)
        val equity = latestValue(usGaap, EQUITY)
        val cash = latestValue(usGaap, CASH)
        val lt = latestValue(usGaap, DEBT_LT)
        val st = latestValue(usGaap, DEBT_ST)
        val totalDebt = when {
            lt != null && st != null -> lt + st
            lt != null -> lt
            st != null -> st
            else -> null
        }
        // Shares are usually under "dei"; fall back to us-gaap.
        val shares = latestValue(dei, SHARES) ?: latestValue(usGaap, SHARES)
        val dividend = latestValue(usGaap, DIVIDEND)

        return SecFundamentals(entityName, netIncomeHistory, equity, cash, totalDebt, shares, dividend)
    }

    private fun empty(entityName: String?) = SecFundamentals(entityName, emptyList(), null, null, null, null, null)

    private fun findMetric(gaap: JsonObject, names: List<String>): JsonObject? {
        for (name in names) gaap[name]?.jsonObject?.let { return it }
        return null
    }

    private fun unitsEntries(metric: JsonObject): List<JsonObject> {
        val units = metric["units"]?.jsonObject ?: return emptyList()
        val result = mutableListOf<JsonObject>()
        for ((_, unitArray) in units) {
            for (element in unitArray.jsonArray) {
                element.jsonObject?.let { result += it }
            }
        }
        return result
    }

    /** Latest reported value for a metric, regardless of period. */
    private fun latestValue(gaap: JsonObject, names: List<String>): Double? {
        val metric = findMetric(gaap, names) ?: return null
        val latest = unitsEntries(metric)
            .filter { it["val"]?.jsonPrimitive?.contentOrNull != null }
            .maxByOrNull { it["end"]?.jsonPrimitive?.contentOrNull ?: "" }
        return latest?.get("val")?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()
    }

    /**
     * Annual (10-K) values, oldest first, deduplicated by fiscal year.
     * Used to derive a 5-year earnings CAGR.
     */
    private fun annualValues(gaap: JsonObject, names: List<String>): List<Double> {
        val metric = findMetric(gaap, names) ?: return emptyList()
        val annual = unitsEntries(metric)
            .filter { it["form"]?.jsonPrimitive?.contentOrNull == "10-K" }
            .filter { it["val"]?.jsonPrimitive?.contentOrNull != null }

        val byYear = LinkedHashMap<String, JsonObject>()
        for (entry in annual) {
            val fy = entry["fy"]?.jsonPrimitive?.contentOrNull
                ?: entry["end"]?.jsonPrimitive?.contentOrNull
                ?: continue
            val existing = byYear[fy]
            val end = entry["end"]?.jsonPrimitive?.contentOrNull ?: ""
            val existingEnd = existing?.get("end")?.jsonPrimitive?.contentOrNull ?: ""
            if (existing == null || end > existingEnd) byYear[fy] = entry
        }

        val sorted = byYear.values.sortedBy { it["end"]?.jsonPrimitive?.contentOrNull ?: "" }
        // Keep only the last 6 fiscal years → 5-year CAGR when used downstream.
        return sorted.mapNotNull { it["val"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull() }
            .takeLast(6)
    }
}
