
`docker run --rm -v "$PWD":/data colossus-utils CMDLINE`

## Html to Pdf Conversion

... `weasyprint input.html output.pdf`

## QR Code Generation

... `QR "input string" svg`   → raw SVG element on stdout
... `QR "input string" data`  → data:image/svg+xml;base64,... URL on stdout

## AP News Fetch

... `APNEWS [outputfile]`  → fetches articles from apnews.com posted in the last 24 hours and writes JSON to outputfile (default: apnews-articles.json)

## PDF Manipulation

... `qpdf [options] input.pdf output.pdf`  → inspect, transform, or repair PDFs (merge, split, encrypt, linearize, etc.)

## Jinja2 Template Rendering

... `minja_render TEMPLATE_FILE JSON_FILE [OUTPUT_FILE]`  → renders a Jinja2 template with JSON context; writes to OUTPUT_FILE or stdout if omitted

Binary was built on Ubuntu from `../minja-render` and copied in; rebuild there if the source changes.

## This Day in History Fetch

... `HISTORY [outputfile]`  → fetches articles from history.com for today; writes JSON to outputfile (default: history-articles.json)

## Playwright Browser Fetch

... `PLAYWRIGHT <url>`  → fetches the fully-rendered HTML of url via a headless Chromium browser and writes it to stdout

Behaves like a real browser (executes JS, sends realistic headers/user-agent). Useful for sites that block plain HTTP clients. Also available as a subprocess from within the image.

## Calendar Events Fetch

... `CALENDAR <output_json> <mailbox> <days_ahead> <calendar_id> [<calendar_id> ...]`

Fetches events from one or more Office365/Outlook calendars over a rolling window and merges in US federal holidays (public + unofficial). Writes structured JSON to `output_json` and prints a human-friendly listing to stdout.

Required environment variables (pass via `-e` or `with-secrets`):
- `CAL_TENANT_ID`
- `CAL_CLIENT_ID`
- `CAL_CLIENT_SECRET`


