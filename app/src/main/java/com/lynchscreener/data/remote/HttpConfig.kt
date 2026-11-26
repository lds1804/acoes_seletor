package com.lynchscreener.data.remote

/**
 * Central place for endpoints, headers and rate-limit tuning.
 *
 * All scraping is direct (no server intermediary), so we throttle conservatively
 * to stay under each provider's free-tier limits.
 */
object HttpConfig {

    // SEC requires a real, identifying contact in the User-Agent (403 otherwise).
    const val SEC_USER_AGENT = "LynchScreenerApp/1.0 (lds1804@gmail.com)"
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // Fundamentus (B3)
    const val FUNDAMENTUS_RESULTADO = "https://www.fundamentus.com.br/resultado.php"
    const val FUNDAMENTUS_DETALHES = "https://www.fundamentus.com.br/detalhes.php?papel="

    // SEC EDGAR (US)
    const val SEC_COMPANY_FACTS = "https://data.sec.gov/api/xbrl/companyfacts/CIK%s.json"
    const val SEC_COMPANY_TICKERS = "https://www.sec.gov/files/company_tickers.json"

    // Prices
    const val YAHOO_CHART = "https://query1.finance.yahoo.com/v8/finance/chart/"
    const val STOOQ_CSV = "https://stooq.com/q/l/?s=%s&f=sd2t2ohlcv&h&e=csv"

    // CVM open data (official, free) — historical earnings for B3 companies.
    const val CVM_FCA_TICKERS = "https://dados.cvm.gov.br/dados/CIA_ABERTA/DOC/FCA/DADOS/fca_cia_aberta_%d.zip"
    const val CVM_FCA_TICKERS_CSV = "fca_cia_aberta_valor_mobiliario_%d.csv"
    const val CVM_DFP_ZIP = "https://dados.cvm.gov.br/dados/CIA_ABERTA/DOC/DFP/DADOS/dfp_cia_aberta_%d.zip"
    const val CVM_DFP_DRE_CSV = "dfp_cia_aberta_DRE_con_%d.csv"

    // Fiscal years of DFP packages to ingest. Each package contains the current
    // and previous fiscal year, so N packages cover N+1 fiscal years. Five
    // packages (2021..2025) cover FY2020..FY2025 → a true 5-year CAGR.
    // One-time download on Wi-Fi (~200 MB total); results are cached in Room.
    val CVM_DFP_YEARS = listOf(2025, 2024, 2023, 2022, 2021)

    // Fundamentus net-income account (DRE consolidado).
    const val CVM_NET_INCOME_ACCOUNT = "3.11"

    const val CVM_USER_AGENT = "LynchScreenerApp/1.0 (lds1804@gmail.com)"

    // Rate limiting (conservative).
    const val SEC_MIN_INTERVAL_MS = 350L                       // ~3 req/s
    const val FUNDAMENTUS_DETAIL_MIN_MS = 1500L                 // 1.5s
    const val FUNDAMENTUS_DETAIL_MAX_MS = 2000L                 // 2.0s
    const val PRICE_MIN_INTERVAL_MS = 500L

    // Queue sizing per WorkManager cycle.
    // 50 p/ enriquecer setor/nome/detalhes da B3 (223 tickers) em ~5 ciclos.
    const val B3_DETAILS_BATCH = 50
    const val SEC_BALANCE_BATCH = 60
    const val PRICE_BATCH = 60

    // SEC: only reprocess balances older than ~75 days.
    const val SEC_STALENESS_MS = 75L * 24L * 60L * 60L * 1000L

    // Minimum average daily liquidity (R$) to keep a B3 paper.
    const val MIN_B3_LIQUIDITY = 500_000.0
}
