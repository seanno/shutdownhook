#!/usr/bin/env python3
"""
Pull events from one or more Office365/Outlook calendars over a date window,
using Microsoft Graph with app-only (client credentials) auth, and merge in
US federal holidays computed locally (no calendar/API needed) via the
`holidays` package.

Writes a structured JSON file (for programmatic consumers such as a newsletter
generator) and always prints a human-friendly listing to stdout (handy for
debugging; discard it when you only want the JSON).

Credentials come from the environment:
    CAL_TENANT_ID
    CAL_CLIENT_ID
    CAL_CLIENT_SECRET

Usage:
    calendar_events.py <output_json> <mailbox> <days_ahead> <calendar_id> [<calendar_id> ...]

Example:
    calendar_events.py events.json you@yourdomain.com 14 AAMk...cal1 AAMk...cal2
"""

import datetime as dt
import json
import os
import sys

import holidays
import msal
import requests

GRAPH = "https://graph.microsoft.com/v1.0"
SCOPE = ["https://graph.microsoft.com/.default"]  # required for client creds
# Times returned in this zone instead of UTC (avoids all-day off-by-one).
TIMEZONE = "Pacific Standard Time"
# Tag applied to locally-computed holidays/observances so the newsletter can
# group/filter them apart from the family calendar events.
GENERAL_TAG = "general"
# US holiday categories to include. "public" = the 12 federal holidays;
# "unofficial" = soft observances (Valentine's, St. Patrick's, Mother's/
# Father's Day, Halloween, Easter, etc.). Both are merged under GENERAL_TAG.
US_HOLIDAY_CATEGORIES = ("public", "unofficial")


def die(msg, code=1):
    print(msg, file=sys.stderr)
    sys.exit(code)


def get_token(tenant_id, client_id, client_secret):
    app = msal.ConfidentialClientApplication(
        client_id,
        authority=f"https://login.microsoftonline.com/{tenant_id}",
        client_credential=client_secret,
    )
    result = app.acquire_token_for_client(scopes=SCOPE)
    if "access_token" not in result:
        die(f"Auth failed: {result.get('error_description', result)}")
    return result["access_token"]


def get_events(token, mailbox, calendar_id, start, end):
    """Return normalized (date_str, subject, [tags]) tuples from a calendar."""
    url = f"{GRAPH}/users/{mailbox}/calendars/{calendar_id}/calendarView"
    params = {
        "startDateTime": start.isoformat(),
        "endDateTime": end.isoformat(),
        "$select": "subject,start,end,categories,isAllDay",
        "$orderby": "start/dateTime",
        "$top": "100",
    }
    headers = {
        "Authorization": f"Bearer {token}",
        "Prefer": f'outlook.timezone="{TIMEZONE}"',
    }
    events = []
    while url:
        r = requests.get(url, headers=headers, params=params)
        if r.status_code != 200:
            die(f"Graph error {r.status_code} for calendar {calendar_id}: {r.text}")
        data = r.json()
        for e in data.get("value", []):
            day = e["start"]["dateTime"][:10]  # YYYY-MM-DD
            events.append((day, e.get("subject", "(no subject)"),
                           e.get("categories", [])))
        url = data.get("@odata.nextLink")  # follow pagination
        params = None                       # nextLink already has the query baked in
    return events


def get_holidays(start_date, end_date):
    """Return normalized (date_str, name, [GENERAL_TAG]) for US holidays and
    observances whose date falls within [start_date, end_date] inclusive.

    Merges the categories in US_HOLIDAY_CATEGORIES ("public" federal holidays
    plus "unofficial" observances) under a single tag. `holidays` computes
    each year's dates from rules, so future years work without any data
    refresh; years are derived from the window so a span crossing a year
    boundary is covered.
    """
    years = range(start_date.year, end_date.year + 1)
    us = holidays.US(years=years, categories=US_HOLIDAY_CATEGORIES)
    out = []
    for day, name in us.items():
        if start_date <= day <= end_date:
            out.append((day.isoformat(), name, [GENERAL_TAG]))
    return out


def main():
    args = sys.argv[1:]
    if len(args) < 4:
        die(
            "Usage: calendar_events.py <output_json> <mailbox> <days_ahead> "
            "<calendar_id> [<calendar_id> ...]"
        )

    output_json = args[0]
    mailbox = args[1]
    try:
        days_ahead = int(args[2])
    except ValueError:
        die(f"days_ahead must be an integer, got: {args[2]!r}")
    calendar_ids = args[3:]

    # Credentials from environment
    missing = [v for v in ("CAL_TENANT_ID", "CAL_CLIENT_ID", "CAL_CLIENT_SECRET")
               if not os.environ.get(v)]
    if missing:
        die(f"Missing required environment variable(s): {', '.join(missing)}")

    token = get_token(
        os.environ["CAL_TENANT_ID"],
        os.environ["CAL_CLIENT_ID"],
        os.environ["CAL_CLIENT_SECRET"],
    )

    now = dt.datetime.now(dt.timezone.utc)
    end = now + dt.timedelta(days=days_ahead)

    all_events = []
    for cal_id in calendar_ids:
        all_events.extend(get_events(token, mailbox, cal_id, now, end))

    # Merge in locally-computed US federal holidays over the same window.
    all_events.extend(get_holidays(now.date(), end.date()))

    # Sort by date string (ISO YYYY-MM-DD sorts chronologically as text).
    all_events.sort(key=lambda ev: ev[0])

    # Structured output for programmatic consumers.
    structured = [
        {"date": day, "subject": subject, "tags": cats}
        for day, subject, cats in all_events
    ]
    try:
        with open(output_json, "w", encoding="utf-8") as f:
            json.dump(structured, f, indent=2, ensure_ascii=False)
    except OSError as exc:
        die(f"Could not write {output_json}: {exc}")

    # Human-friendly listing always goes to stdout.
    for day, subject, cats in all_events:
        tags = ", ".join(cats) or "—"
        print(f"{day}  {subject}  [{tags}]")


if __name__ == "__main__":
    main()
