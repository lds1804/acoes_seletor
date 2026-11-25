#!/usr/bin/env python3
"""Generate app/src/main/assets/sp500_universe.json (S&P 500 + CIK mapping).

Ticker list: public S&P 500 constituents CSV (datasets repo).
CIK mapping : SEC company_tickers.json (official, free).

Run from the repo root (lynch-screener/) once to produce the universe file
that gets packaged in the app assets for Etapa 2.
"""
import csv
import io
import json
import urllib.request

# SEC requires a real, identifying User-Agent.
SEC_UA = "LynchScreenerApp/1.0 (lds1804@gmail.com)"


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": SEC_UA})
    return urllib.request.urlopen(req, timeout=30).read()


def main() -> None:
    csv_text = fetch(
        "https://raw.githubusercontent.com/datasets/s-and-p-500-companies/master/data/constituents.csv"
    ).decode("utf-8")

    cik_map = {
        v["ticker"].upper(): str(v["cik_str"]).zfill(10)
        for v in json.loads(fetch("https://www.sec.gov/files/company_tickers.json"))
    }

    universe = []
    for row in csv.DictReader(io.StringIO(csv_text)):
        ticker = row["Symbol"].strip().upper()
        universe.append(
            {
                "ticker": ticker,
                "name": row["Name"].strip(),
                "cik": cik_map.get(ticker),
                "sector": row.get("Sector"),
            }
        )

    out = "app/src/main/assets/sp500_universe.json"
    with open(out, "w", encoding="utf-8") as f:
        json.dump(universe, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(universe)} tickers to {out}")


if __name__ == "__main__":
    main()
