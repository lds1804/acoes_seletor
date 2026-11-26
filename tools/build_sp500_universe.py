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
import os
import urllib.request

# SEC requires a real, identifying User-Agent.
SEC_UA = "LynchScreenerApp/1.0 (lds1804@gmail.com)"

# Resolve o destino relativo à localização do próprio script.
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(SCRIPT_DIR, "..", "app", "src", "main", "assets", "sp500_universe.json")


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": SEC_UA})
    return urllib.request.urlopen(req, timeout=30).read()


def main() -> None:
    csv_text = fetch(
        "https://raw.githubusercontent.com/datasets/s-and-p-500-companies/master/data/constituents.csv"
    ).decode("utf-8")

    # SEC company_tickers.json é um dict {"0": {...}, "1": {...}, ...}
    sec_data = json.loads(fetch("https://www.sec.gov/files/company_tickers.json"))
    cik_map = {
        v["ticker"].upper(): str(v["cik_str"]).zfill(10)
        for v in sec_data.values()
    }

    universe = []
    for row in csv.DictReader(io.StringIO(csv_text)):
        ticker = row["Symbol"].strip().upper()
        csv_cik = row.get("CIK", "").strip()
        universe.append(
            {
                "ticker": ticker,
                "name": row["Security"].strip(),
                "cik": cik_map.get(ticker) or (csv_cik.zfill(10) if csv_cik else None),
                "sector": row.get("GICS Sector"),
            }
        )

    out = os.path.normpath(OUT)
    with open(out, "w", encoding="utf-8") as f:
        json.dump(universe, f, indent=2, ensure_ascii=False)
    print(f"Wrote {len(universe)} tickers to {out}")


if __name__ == "__main__":
    main()
