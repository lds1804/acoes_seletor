# Lynch Screener

Aplicativo Android **100% offline-first** (zero backend) para coletar indicadores
fundamentalistas de ações do **Brasil (B3)** e dos **EUA (S&P 500)**, rodar
atualizações incrementais em background e ranquear as empresas segundo a
metodologia de **Peter Lynch**.

## Stack

Kotlin · Jetpack Compose (Material 3) · Navigation Compose · MVVM + Repository ·
Coroutines/StateFlow · Room · WorkManager · OkHttp + Jsoup · kotlinx-serialization ·
DI manual (AppContainer).

## Estrutura (Etapa 1)

```
app/src/main/java/com/lynchscreener/
├── LynchScreenerApp.kt
├── data/local/
│   ├── AppDatabase.kt
│   ├── entity/      (assets, fundamentals, portfolio_positions)
│   └── dao/         (AssetDao, FundamentalsDao, PortfolioPositionDao)
└── domain/
    ├── model/       (Market, LynchCategory, Financials, LynchScore, ScoreComponent)
    └── LynchScoringEngine.kt   ← 100% testável, sem dependências Android
```

## Construir

```bash
# dentro de lynch-screener/
./gradlew assembleDebug
./gradlew test          # roda os testes unitários do LynchScoringEngine
```

## Roadmap

- **Etapa 1:** gradle, entidades Room, DAOs, `LynchScoringEngine` + testes.
- **Etapa 2 (esta):** clientes de rede (Fundamentus + SEC EDGAR + Yahoo/Stooq),
  parsers defensivos e `CoroutineWorker`s incrementais com backoff exponencial.
- **Etapa 3:** ViewModels, ranking/médias por setor e as 3 telas em Compose.

## Etapa 2 — rede e background

```
data/remote/      HttpConfig, OkHttp helper, FundamentusClient, SecClient, PriceClient
data/remote/dto/  Sp500Entry, QuotePrice, SecFundamentals, B3TableRow/B3Details
data/remote/parser/ FundamentusParser, SecCompanyFactsParser, YahooChartParser, StooqCsvParser
data/repository/  AssetRepository (merge + scoring + filas) e SyncSettings
worker/           B3TableWorker, B3DetailsWorker, SecBalancesWorker, PriceRefreshWorker,
                  SeedUniverseWorker, SyncConstraints, SyncScheduler, LynchWorkerFactory
di/               AppContainer (DI manual)
```

### Fluxo de coleta (filas incrementais com checkpoint)

- **B3 tabela** (`B3TableWorker`, diário): 1 GET em `resultado.php`, parse com
  Jsoup, descarta papéis com liquidez < R$ 500 mil/dia.
- **B3 detalhes** (`B3DetailsWorker`, a cada 3h): 15 tickers mais desatualizados,
  delay 1,5–2,0s, checkpoint de `syncStatus` a cada papel.
- **SEC balanços** (`SecBalancesWorker`, a cada 6h): 20 empresas com balanço
  mais antigo que ~75 dias, ~3 req/s, JSON bruto descartado da memória.
- **Cotações** (`PriceRefreshWorker`, a cada 6h): Yahoo (primário) + Stooq
  (fallback), recalculando P/L, P/VP, Market Cap, DY e PEGY localmente.

### ⚠️ Antes do primeiro sync US

Rode o script para gerar o universo do S&P 500 (o `sp500_universe.json` vem vazio):

```bash
python tools/build_sp500_universe.py
```

## SEC User-Agent

O `User-Agent` da SEC exige um e-mail real identificável. Já configurado como
`LynchScreenerApp/1.0 (lds1804@gmail.com)` na constante `SEC_USER_AGENT`
(Etapa 2) e no script `tools/build_sp500_universe.py`.
Com `User-Agent` genérico/vazio a SEC responde **HTTP 403**.

## Convenções importantes

- **Ratios são frações:** `roe`, `roic`, `dividendYield`, `cagr5y` e
  `netCashToMarketCap` são armazenados como `0.15 == 15%`.
- **PEGY** = P/L ÷ (crescimento% + DY%), com denominador em pontos percentuais.
- **Bancos/seguradoras** não são penalizados por dívida (peso redistribuído para
  ROE, P/VP, P/L e DY).
