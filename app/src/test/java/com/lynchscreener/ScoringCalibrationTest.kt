package com.lynchscreener

import com.lynchscreener.data.remote.CvmPeriod
import com.lynchscreener.domain.LynchScoringEngine
import com.lynchscreener.domain.model.Financials
import com.lynchscreener.domain.model.LynchCategory
import com.lynchscreener.domain.model.LynchFlag
import com.lynchscreener.domain.model.Market
import com.lynchscreener.domain.model.RankedCompany
import com.lynchscreener.domain.model.RankingItem
import com.lynchscreener.ui.format.MarkdownExport
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Regression tests for the GARP rebalance (ranking v3) plus the Markdown export.
 *
 * The cases mirror the distortions found in the real B3 data: WIZC3 (broker
 * scored as a bank), CURY3 (P/L from a single quarter), DEXP3 (flat earnings with
 * a 15% yield) and ALLD3 (8% ROE with 80 points).
 */
class ScoringCalibrationTest {

    private lateinit var engine: LynchScoringEngine

    @Before
    fun setUp() {
        engine = LynchScoringEngine()
    }

    private fun base(
        ticker: String = "TEST3",
        sector: String? = "Comércio",
        subsector: String? = null,
    ) = Financials(
        ticker = ticker,
        market = Market.BR,
        sector = sector,
        subsector = subsector,
        price = 10.0,
        pe = 6.0,
        pb = 0.9,
        roe = 0.18,
        roic = 0.15,
        dividendYield = 0.06,
        earningsCagr5y = 0.12,
        earningsConsistent = true,
        netDebt = -100_000_000.0,
        equity = 1_000_000_000.0,
        netIncome12m = 180_000_000.0,
        marketCap = 900_000_000.0,
    )

    // ---------------------------------------------------------------
    // 1. Sector exception: banks/insurers only
    // ---------------------------------------------------------------

    @Test
    fun `bank is exempt from the leverage rule`() {
        assertTrue(LynchScoringEngine.isFinancialSector("Intermediários Financeiros", "Bancos"))
        assertTrue(LynchScoringEngine.isFinancialSector("Previdência e Seguros", "Seguradoras"))
    }

    @Test
    fun `insurance broker is NOT exempt (WIZC3 case)`() {
        assertFalse(
            LynchScoringEngine.isFinancialSector(
                "Previdência e Seguros",
                "Corretoras de Seguros",
            ),
        )
    }

    @Test
    fun `other financial segments keep the leverage rule`() {
        assertFalse(LynchScoringEngine.isFinancialSector("Serviços Financeiros Diversos", null))
        assertFalse(LynchScoringEngine.isFinancialSector("Exploração de Imóveis", "Incorporações"))
        assertFalse(LynchScoringEngine.isFinancialSector("Diversos", "Holdings"))
        assertFalse(LynchScoringEngine.isFinancialSector("Comércio", "Eletrodomésticos"))
    }

    @Test
    fun `broker does not receive the financial weight set`() {
        val broker = base(sector = "Previdência e Seguros", subsector = "Corretoras de Seguros")
        val keys = engine.score(broker).components.map { it.key }.toSet()
        assertTrue("esperava critério de saúde para corretora", "health" in keys)
        assertFalse("corretora não deve usar pesos de banco", "pb" in keys)
    }

    @Test
    fun `financial company has no net cash indicator`() {
        val bank = base(sector = "Intermediários Financeiros", subsector = "Bancos")
            .copy(totalDebt = 900_000_000_000.0, cashAndEquivalents = 800_000_000_000.0)
        // Deposits and float are raw material, so a "Caixa Líq." seal would lie.
        assertNull(engine.score(bank).netCashToMarketCap)
    }

    // ---------------------------------------------------------------
    // 2. PEGY dividend cap and growth floor
    // ---------------------------------------------------------------

    @Test
    fun `pegy denominator caps the dividend yield at MAX_DY_FOR_PEGY`() {
        // A 15% DY counts as 8%: 6 / (10 + 8) = 0.3333
        val score = engine.score(
            base().copy(pe = 6.0, earningsCagr5y = 0.10, dividendYield = 0.15),
        )
        assertEquals(6.0 / 18.0, score.pegy!!, 1e-9)
    }

    @Test
    fun `company without earnings growth cannot earn full pegy credit`() {
        val flat = base().copy(
            pe = 5.0,
            pb = 0.7,
            earningsCagr5y = 0.0,
            dividendYield = 0.15,
            roe = 0.18,
        )
        val pegy = engine.score(flat).components.first { it.key == "pegy" }
        assertTrue(
            "PEGY de empresa estagnada deve ser limitado",
            pegy.points <= pegy.maxPoints * 0.35 + 1e-9,
        )
    }

    @Test
    fun `negative growth flags the company`() {
        val shrinking = base().copy(earningsCagr5y = -0.05, earningsConsistent = true)
        assertTrue(LynchFlag.NEGATIVE_GROWTH in engine.score(shrinking).flags)
    }

    // ---------------------------------------------------------------
    // 3. Recent-earnings momentum
    // ---------------------------------------------------------------

    @Test
    fun `profit drop of more than 15 percent is penalised and flagged`() {
        val score = engine.score(base().copy(earningsYoy = -0.22))

        val momentum = score.components.firstOrNull { it.key == "momentum" }
        assertNotNull("queda de lucro deve gerar penalidade explícita", momentum)
        assertTrue(momentum!!.points < 0)
        assertTrue(LynchFlag.PROFIT_DROP in score.flags)
    }

    @Test
    fun `severe profit drop hurts more than a mild one`() {
        val mild = engine.score(base().copy(earningsYoy = -0.18))
        val severe = engine.score(base().copy(earningsYoy = -0.45))
        assertTrue(severe.totalScore < mild.totalScore)
    }

    @Test
    fun `growing company gets no momentum penalty`() {
        val score = engine.score(base().copy(earningsYoy = 0.18))
        assertNull(score.components.firstOrNull { it.key == "momentum" })
        assertFalse(LynchFlag.PROFIT_DROP in score.flags)
    }

    // ---------------------------------------------------------------
    // 4. Score ceilings and outlier caps
    // ---------------------------------------------------------------

    @Test
    fun `value trap is penalised and capped at 65`() {
        val trap = base().copy(
            pe = 4.0,
            pb = 0.7,
            roe = 0.20,
            dividendYield = 0.15,
            earningsCagr5y = -0.02,
            earningsConsistent = false,
            netDebt = -200_000_000.0,
        )
        val score = engine.score(trap)
        assertEquals(LynchCategory.VALUE_TRAP, score.category)
        assertTrue("score ${score.totalScore} deveria respeitar o teto", score.totalScore <= 65)
        assertTrue(score.isCapped)
    }

    @Test
    fun `low roe cannot reach the top even with cheap multiples`() {
        // ALLD3-like profile: cheap multiples and high growth, but only 10% ROE.
        val weak = base().copy(
            pe = 5.0,
            pb = 0.9,
            roe = 0.10,
            roic = 0.10,
            dividendYield = 0.08,
            earningsCagr5y = 0.20,
            earningsConsistent = true,
            netDebt = -200_000_000.0,
            marketCap = 900_000_000.0,
        )
        val score = engine.score(weak)
        assertTrue("base score deve passar do teto", score.baseScore > 68)
        assertEquals(68, score.totalScore)
        assertTrue(score.isCapped)
    }

    @Test
    fun `dividend yield above the atypical threshold earns no extra points`() {
        val normal = engine.score(base().copy(dividendYield = 0.12))
        val extreme = engine.score(base().copy(dividendYield = 0.30))
        val normalPoints = normal.components.first { it.key == "dividend" }.points
        val extremePoints = extreme.components.first { it.key == "dividend" }.points
        assertEquals(normalPoints, extremePoints, 1e-9)
        assertTrue(LynchFlag.ATYPICAL_DY in extreme.flags)
    }

    @Test
    fun `extreme roe is capped instead of rewarded`() {
        val normal = engine.score(base().copy(roe = 0.30, roic = 0.30))
        val extreme = engine.score(base().copy(roe = 0.90, roic = 0.90))
        val normalPoints = normal.components.first { it.key == "profitability" }.points
        val extremePoints = extreme.components.first { it.key == "profitability" }.points
        assertTrue(
            "ROE de 90% não pode pontuar mais que 30%",
            extremePoints <= normalPoints + 1e-9,
        )
        assertTrue(LynchFlag.HIGH_ROE in extreme.flags)
    }

    @Test
    fun `high quality grower is not capped`() {
        val quality = base().copy(
            pe = 7.0,
            pb = 4.57,
            roe = 0.65,
            roic = 0.55,
            dividendYield = 0.18,
            earningsCagr5y = 0.42,
            earningsConsistent = true,
            earningsYoy = 0.20,
            netDebt = -500_000_000.0,
        )
        val score = engine.score(quality)
        assertFalse("empresa de qualidade não deve levar teto", score.isCapped)
        assertTrue(score.totalScore > 68)
    }

    // ---------------------------------------------------------------
    // 5. Classification: Asset Play restricted, new categories
    // ---------------------------------------------------------------

    @Test
    fun `retailer below book value is not an asset play`() {
        val retailer = base(sector = "Comércio", subsector = "Eletrodomésticos").copy(
            pb = 0.41,
            roe = 0.14,
            earningsCagr5y = 0.14,
            pe = 5.0,
            netDebt = 200_000_000.0,
        )
        assertFalse(
            "varejo com P/VP < 1 por queda de preço não é Asset Play",
            engine.score(retailer).category == LynchCategory.ASSET_PLAY,
        )
    }

    @Test
    fun `real estate company below book value is an asset play`() {
        val re = base(sector = "Exploração de Imóveis", subsector = "Incorporações").copy(
            pb = 0.6,
            roe = 0.14,
            earningsCagr5y = 0.08,
            pe = 6.0,
        )
        assertEquals(LynchCategory.ASSET_PLAY, engine.score(re).category)
    }

    @Test
    fun `cash rich company below book value is an asset play`() {
        val cashRich = base(sector = "Comércio", subsector = "Eletrodomésticos").copy(
            pb = 0.5,
            roe = 0.14,
            earningsCagr5y = 0.10,
            netDebt = -400_000_000.0,
            marketCap = 900_000_000.0,
        )
        assertEquals(LynchCategory.ASSET_PLAY, engine.score(cashRich).category)
    }

    @Test
    fun `recovering company with a bad long record is a turnaround`() {
        val recovering = base().copy(
            pe = 9.0,
            earningsCagr5y = -0.06,
            earningsYoy = 0.45,
            earningsConsistent = true,
            roe = 0.14,
        )
        assertEquals(LynchCategory.TURNAROUND, engine.score(recovering).category)
    }

    @Test
    fun `low growth with sustained dividend is a slow grower`() {
        val slow = base().copy(
            pe = 11.0,
            pb = 1.2,
            roe = 0.14,
            earningsCagr5y = 0.02,
            dividendYield = 0.06,
        )
        assertEquals(LynchCategory.SLOW_GROWER, engine.score(slow).category)
    }

    // ---------------------------------------------------------------
    // 6. Breakdown reconciles with the ring
    // ---------------------------------------------------------------

    @Test
    fun `sum of integer components equals the displayed score`() {
        val samples = listOf(
            base(),
            base().copy(pe = 4.0, pb = 0.7, roe = 0.20, earningsConsistent = false),
            base().copy(roe = 0.10, pb = 0.41, dividendYield = 0.15, pe = 4.95),
            base().copy(earningsYoy = -0.35),
            base().copy(sector = "Intermediários Financeiros", subsector = "Bancos", pb = 1.3),
            base().copy(sector = "Exploração de Imóveis", subsector = "Incorporações", pb = 0.6),
        )
        for (f in samples) {
            val score = engine.score(f)
            val sum = score.components.sumOf { it.points.roundToInt() }
            assertEquals(
                "${f.ticker}: soma dos critérios deve fechar com o anel",
                score.totalScore,
                sum,
            )
        }
    }

    @Test
    fun `penalties are exposed as negative components`() {
        val trap = base().copy(
            pe = 4.0,
            pb = 0.7,
            roe = 0.20,
            earningsConsistent = false,
            earningsCagr5y = -0.02,
        )
        val score = engine.score(trap)
        val ceiling = score.components.firstOrNull { it.key == "CEILING" }
        assertNotNull("o teto deve aparecer no raio-X", ceiling)
        assertTrue("a linha do teto deve ser negativa", ceiling!!.points < 0)
        assertEquals(score.totalScore, score.components.sumOf { it.points.roundToInt() })
    }

    // ---------------------------------------------------------------
    // 7. P/L identity and CVM period helpers
    // ---------------------------------------------------------------

    @Test
    fun `pl pb roe identity holds for internally consistent figures`() {
        val marketCap = 8_129_380_000.0
        val netIncome = 1_158_000_000.0
        val equity = 1_778_940_000.0
        val pe = marketCap / netIncome
        val pb = marketCap / equity
        val roe = netIncome / equity
        assertEquals(pe, pb / roe, 1e-6)
    }

    @Test
    fun `three month row is not accepted as annual`() {
        assertFalse(CvmPeriod.isAnnual("2025-07-01", "2025-09-30"))
    }

    @Test
    fun `twelve month row is accepted as annual`() {
        assertTrue(CvmPeriod.isAnnual("2025-01-01", "2025-12-31"))
        assertFalse(CvmPeriod.isAnnual("2025-01-01", "2025-09-30"))
    }

    @Test
    fun `pickYearToDate prefers the accumulated 9 month row over the isolated quarter`() {
        data class Row(val start: String, val end: String, val label: String)

        val rows = listOf(
            Row("2025-07-01", "2025-09-30", "trimestre isolado"),
            Row("2025-01-01", "2025-09-30", "acumulado 9M"),
        )
        val chosen = CvmPeriod.pickYearToDate(rows, { it.start }, { it.end })
        assertEquals("acumulado 9M", chosen!!.label)
        assertEquals(9, CvmPeriod.monthsBetween(chosen.start, chosen.end))
    }

    @Test
    fun `cagr requires positive endpoints`() {
        assertNull(CvmPeriod.cagr(-100.0, 200.0, 5))
        assertNull(CvmPeriod.cagr(100.0, -200.0, 5))
        assertEquals(0.148698, CvmPeriod.cagr(100.0, 200.0, 5)!!, 1e-6)
    }

    // ---------------------------------------------------------------
    // 8. Markdown export
    // ---------------------------------------------------------------

    private fun ranked(
        ticker: String,
        score: Int = 88,
        flags: String? = null,
    ) = RankingItem(
        company = RankedCompany(
            ticker = ticker,
            market = "BR",
            name = "PORTO SEGURO",
            sector = "Previdência e Seguros",
            subsector = "Seguradoras",
            price = 43.03,
            marketCap = 30_000_000_000.0,
            pe = 7.3,
            pb = 1.6,
            roe = 0.221,
            roic = 0.18,
            dividendYield = 0.07,
            cagr5y = 0.10,
            pegy = 0.3,
            netDebt = null,
            netCashToMarketCap = null,
            equity = 18_000_000_000.0,
            netIncome12m = 3_500_000_000.0,
            sharesOutstanding = 700_000_000.0,
            dividendPerShare = 3.0,
            liquidity = 90_000_000.0,
            lynchScore = score,
            lynchCategory = "Stalwart",
            breakdownJson = null,
            earningsCagr5y = 0.132,
            earningsConsistent = true,
            earningsHistoryJson = "{}",
            earningsBasis = "CONTROLLERS",
            earningsYoy = 0.18,
            alertFlags = flags,
            priceUpdatedAt = 1_700_000_000_000L,
            fundamentalsUpdatedAt = 1_700_000_000_000L,
        ),
        overallRank = 1,
        sectorRank = 1,
    )

    @Test
    fun `markdown header carries market and filter`() {
        val lines = MarkdownExport.build(listOf(ranked("PSSA3")), Market.BR, "Todos").lines()
        assertEquals("# Lynch Screener — Brasil B3 | Filtro: Todos", lines[0])
        assertTrue(lines[1].startsWith("Data sync: "))
        assertTrue(lines[1].endsWith("| Exibindo Top 1 de 1"))
    }

    @Test
    fun `markdown table declares 15 columns in header and separator`() {
        val text = MarkdownExport.build(listOf(ranked("PSSA3")), Market.BR, "Todos")
        val table = text.lines().filter { it.startsWith("|") }
        assertEquals(3, table.size) // header + separator + 1 row
        assertEquals(15, table[0].split("|").count { it.isNotBlank() })
        assertEquals(15, table[1].split("|").count { it.isNotBlank() })
        assertEquals(15, table[2].split("|").count { it.isNotBlank() })
    }

    @Test
    fun `markdown row formats numbers in pt-BR`() {
        val text = MarkdownExport.build(listOf(ranked("PSSA3")), Market.BR, "Todos")
        val row = text.lines().last { it.startsWith("| 1 ") }
        assertTrue("ticker ausente", row.contains("PSSA3"))
        assertTrue("preço sem vírgula decimal: $row", row.contains("43,03"))
        assertTrue("PEGY ausente", row.contains("0,3"))
        assertTrue("ROE ausente", row.contains("22,1%"))
        assertTrue("DY ausente", row.contains("7,0%"))
        assertTrue("CAGR ausente", row.contains("13,2%"))
        assertTrue("YoY sem sinal: $row", row.contains("+18,0%"))
        assertTrue("sem alertas deveria mostrar travessão", row.endsWith("— |"))
    }

    @Test
    fun `markdown row shows alert labels`() {
        val text = MarkdownExport.build(
            listOf(ranked("DEXP3", flags = "PROFIT_DROP,ATYPICAL_DY")),
            Market.BR,
            "Químicos",
        )
        val row = text.lines().last { it.startsWith("| 1 ") }
        assertTrue(row.contains("Lucro em queda"))
        assertTrue(row.contains("DY atípico"))
        assertTrue(text.lines()[0].endsWith("| Filtro: Químicos"))
    }

    @Test
    fun `markdown respects the requested limit`() {
        val items = (1..50).map { ranked("T$it") }
        val text = MarkdownExport.build(items, Market.US, "Todos", limit = 30)
        val rows = text.lines().count { it.contains("PORTO SEGURO") }
        assertEquals(30, rows)
        assertTrue(text.lines()[1].endsWith("| Exibindo Top 30 de 50"))
        assertTrue(text.lines()[0].contains("EUA S&P 500"))
    }

    @Test
    fun `pipe inside a cell does not break the table`() {
        val dirty = ranked("PSSA3")
        val withPipe = dirty.copy(company = dirty.company.copy(name = "A | B"))
        val row = MarkdownExport.build(listOf(withPipe), Market.BR, "Todos")
            .lines().last { it.startsWith("| 1 ") }
        assertEquals(15, row.split("|").count { it.isNotBlank() })
    }

    @Test
    fun `flag codes round trip through storage`() {
        val flags = listOf(LynchFlag.PROFIT_DROP, LynchFlag.ATYPICAL_DY)
        val codes = LynchFlag.toCodes(flags)
        assertEquals("PROFIT_DROP,ATYPICAL_DY", codes)
        assertEquals(flags, LynchFlag.fromCodes(codes))
        assertNull(LynchFlag.toCodes(emptyList()))
        assertTrue(LynchFlag.fromCodes(null).isEmpty())
    }
}
