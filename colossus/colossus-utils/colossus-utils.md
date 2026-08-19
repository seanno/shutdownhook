
`docker run --rm -v "$PWD":/data colossus-utils CMDLINE`

## Html to Pdf Conversion

... `weasyprint input.html output.pdf`

## QR Code Generation

... `QR "input string" svg`   → raw SVG element on stdout
... `QR "input string" data`  → data:image/svg+xml;base64,... URL on stdout



