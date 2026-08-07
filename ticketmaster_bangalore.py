#!/usr/bin/env python3
"""
Ticketmaster Discovery API helper for events near Bangalore.

Uses only the Python standard library (urllib) so there is nothing to pip install.

API key resolution order (required — there is no built-in fallback):
  1. --apikey CLI flag
  2. TICKETMASTER_API_KEY environment variable

Examples:
  # All events near Bangalore
  python3 ticketmaster_bangalore.py

  # Dance classes (tries several dance keywords)
  python3 ticketmaster_bangalore.py --preset dance

  # Music classes
  python3 ticketmaster_bangalore.py --preset music

  # Custom keyword
  python3 ticketmaster_bangalore.py --keyword "dance"

  # Filter by day
  python3 ticketmaster_bangalore.py --day today
  python3 ticketmaster_bangalore.py --day weekend
  python3 ticketmaster_bangalore.py --day 2026-07-12

  # By category (Ticketmaster classification)
  python3 ticketmaster_bangalore.py --category music

  # Save output
  python3 ticketmaster_bangalore.py --preset dance --output json
  python3 ticketmaster_bangalore.py --preset music --day weekend --output csv
"""

import argparse
import csv
import json
import os
import sys
import time
from datetime import datetime, timedelta, timezone
from urllib.parse import urlencode
from urllib.request import urlopen, Request
from urllib.error import HTTPError, URLError

API_URL = "https://app.ticketmaster.com/discovery/v2/events.json"
DEFAULT_CITY = "Bangalore"

# Bangalore is IST = UTC+5:30 (no DST). Ticketmaster expects UTC datetimes.
IST = timezone(timedelta(hours=5, minutes=30))

PRESETS = {
    "dance": [
        "salsa", "bollywood", "kathak", "hip hop", "contemporary",
        "zumba", "ballet", "bharatanatyam", "western dance",
    ],
    "music": [
        "guitar", "singing", "carnatic", "piano", "violin",
        "drums", "keyboard", "western vocals", "music class",
    ],
}


def resolve_api_key(cli_key):
    key = cli_key or os.environ.get("TICKETMASTER_API_KEY")
    if not key:
        sys.exit(
            "No Ticketmaster API key. Pass --apikey or set TICKETMASTER_API_KEY.\n"
            "There is deliberately no hardcoded fallback: this file is public."
        )
    return key


def utc_range_for_day(local_date):
    """Return (startDateTime, endDateTime) in UTC ISO8601 'Z' for a full IST day."""
    start_local = datetime(local_date.year, local_date.month, local_date.day,
                           0, 0, 0, tzinfo=IST)
    end_local = datetime(local_date.year, local_date.month, local_date.day,
                         23, 59, 59, tzinfo=IST)
    fmt = "%Y-%m-%dT%H:%M:%SZ"
    return (start_local.astimezone(timezone.utc).strftime(fmt),
            end_local.astimezone(timezone.utc).strftime(fmt))


def resolve_day(day):
    """Turn a --day value into (startDateTime, endDateTime) UTC strings, or None."""
    if not day:
        return None
    today = datetime.now(IST).date()
    if day == "today":
        return utc_range_for_day(today)
    if day == "weekend":
        # Next Saturday 00:00 IST through Sunday 23:59 IST.
        days_until_sat = (5 - today.weekday()) % 7  # Mon=0 .. Sun=6; Sat=5
        saturday = today + timedelta(days=days_until_sat)
        sunday = saturday + timedelta(days=1)
        start, _ = utc_range_for_day(saturday)
        _, end = utc_range_for_day(sunday)
        return (start, end)
    # Explicit YYYY-MM-DD
    try:
        d = datetime.strptime(day, "%Y-%m-%d").date()
    except ValueError:
        sys.exit(f"Invalid --day value: {day!r}. Use today, weekend, or YYYY-MM-DD.")
    return utc_range_for_day(d)


def fetch_events(api_key, city, keyword=None, classification=None,
                 day_range=None, size=50):
    params = {"apikey": api_key, "city": city, "size": size, "sort": "date,asc"}
    if keyword:
        params["keyword"] = keyword
    if classification:
        params["classificationName"] = classification
    if day_range:
        params["startDateTime"], params["endDateTime"] = day_range

    url = f"{API_URL}?{urlencode(params)}"
    req = Request(url, headers={"Accept": "application/json"})
    try:
        with urlopen(req, timeout=30) as resp:
            payload = json.loads(resp.read().decode("utf-8"))
    except HTTPError as e:
        body = e.read().decode("utf-8", "replace")
        sys.exit(f"HTTP {e.code} from Ticketmaster: {body[:400]}")
    except URLError as e:
        sys.exit(f"Network error contacting Ticketmaster: {e.reason}")

    return payload.get("_embedded", {}).get("events", [])


def normalize(event):
    """Flatten a raw Ticketmaster event into the fields we care about."""
    dates = event.get("dates", {}).get("start", {})
    venues = event.get("_embedded", {}).get("venues", [{}])
    venue = venues[0] if venues else {}
    classifications = event.get("classifications", [{}])
    seg = classifications[0].get("segment", {}) if classifications else {}
    genre = classifications[0].get("genre", {}) if classifications else {}
    price = event.get("priceRanges", [{}])
    price0 = price[0] if price else {}

    return {
        "name": event.get("name"),
        "date": dates.get("localDate"),
        "time": dates.get("localTime"),
        "segment": seg.get("name"),
        "genre": genre.get("name"),
        "venue": venue.get("name"),
        "city": venue.get("city", {}).get("name"),
        "address": venue.get("address", {}).get("line1"),
        "price_min": price0.get("min"),
        "price_max": price0.get("max"),
        "currency": price0.get("currency"),
        "url": event.get("url"),
    }


def gather(args, api_key):
    """Run one or more queries depending on --preset/--keyword and return rows."""
    day_range = resolve_day(args.day)
    seen = set()
    rows = []

    if args.preset:
        keywords = PRESETS[args.preset]
    elif args.keyword:
        keywords = [args.keyword]
    else:
        keywords = [None]  # single broad query

    for kw in keywords:
        events = fetch_events(
            api_key, args.city, keyword=kw,
            classification=args.category, day_range=day_range,
        )
        for ev in events:
            eid = ev.get("id")
            if eid in seen:
                continue
            seen.add(eid)
            row = normalize(ev)
            row["matched_keyword"] = kw
            rows.append(row)
        if len(keywords) > 1:
            time.sleep(0.2)  # be polite to the API across multiple keyword calls

    return rows


def print_table(rows):
    if not rows:
        print("No events found for the given filters.")
        return
    print(f"\nFound {len(rows)} event(s):\n")
    for i, r in enumerate(rows, 1):
        when = " ".join(x for x in (r["date"], r["time"]) if x) or "date TBA"
        where = ", ".join(x for x in (r["venue"], r["city"]) if x) or "venue TBA"
        price = ""
        if r["price_min"] is not None:
            price = f" | {r['currency']} {r['price_min']}-{r['price_max']}"
        kw = f" [{r['matched_keyword']}]" if r.get("matched_keyword") else ""
        print(f"{i:>3}. {r['name']}{kw}")
        print(f"     {when} | {where}{price}")
        if r["url"]:
            print(f"     {r['url']}")
    print()


def save_output(rows, fmt, label):
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    base = f"ticketmaster_{label}_{stamp}"
    if fmt == "json":
        path = f"{base}.json"
        with open(path, "w", encoding="utf-8") as f:
            json.dump(rows, f, indent=2, ensure_ascii=False)
    else:  # csv
        path = f"{base}.csv"
        fields = ["name", "date", "time", "segment", "genre", "venue", "city",
                  "address", "price_min", "price_max", "currency",
                  "matched_keyword", "url"]
        with open(path, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=fields)
            w.writeheader()
            for r in rows:
                w.writerow({k: r.get(k) for k in fields})
    print(f"Saved {len(rows)} event(s) -> {path}")


def build_parser():
    p = argparse.ArgumentParser(
        description="Fetch events near Bangalore from the Ticketmaster Discovery API.")
    p.add_argument("--apikey", help="Ticketmaster API key (else env TICKETMASTER_API_KEY).")
    p.add_argument("--city", default=DEFAULT_CITY, help="City name (default: Bangalore).")
    p.add_argument("--preset", choices=sorted(PRESETS.keys()),
                   help="Run a bundle of keywords (dance, music).")
    p.add_argument("--keyword", help="Custom search keyword.")
    p.add_argument("--category", help="Ticketmaster classification, e.g. music, dance.")
    p.add_argument("--day", help="today | weekend | YYYY-MM-DD")
    p.add_argument("--output", choices=["json", "csv"],
                   help="Also save results to a json/csv file.")
    return p


def main():
    args = build_parser().parse_args()
    api_key = resolve_api_key(args.apikey)

    rows = gather(args, api_key)
    print_table(rows)

    if args.output:
        label = args.preset or args.keyword or args.category or "all"
        label = label.replace(" ", "_")
        save_output(rows, args.output, label)


if __name__ == "__main__":
    main()
