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
