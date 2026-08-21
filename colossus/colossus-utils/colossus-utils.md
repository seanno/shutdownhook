
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



