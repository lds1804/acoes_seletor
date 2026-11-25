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

- **Etapa 1 (esta):** gradle, entidades Room, DAOs, `LynchScoringEngine` + testes.
- **Etapa 2:** clientes de rede (Fundamentus + SEC EDGAR + Yahoo/Stooq), parsers
  defensivos e `CoroutineWorker`s incrementais com backoff exponencial.
- **Etapa 3:** ViewModels, ranking/médias por setor e as 3 telas em Compose.

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
