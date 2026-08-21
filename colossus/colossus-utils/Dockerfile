# ── global ──────────────────────────────────────────────────────────────
# Generic Debian base for a multi-tool utility image.
FROM debian:trixie-slim

# Python + venv on PATH. A venv sidesteps PEP 668, so pip installs need no
# --break-system-packages and stay isolated from apt-managed packages.
RUN apt-get update && apt-get install --no-install-recommends -y \
        python3 \
        python3-venv \
    && rm -rf /var/lib/apt/lists/*

RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Shared working directory; map to your CWD at run time with -v "$PWD":/data
WORKDIR /data

# ── weasyprint ──────────────────────────────────────────────────────────
# Runtime libs (Pango stack; official list for v69+) plus base fonts so
# HTML that names common families renders instead of falling back to boxes.
RUN apt-get update && apt-get install --no-install-recommends -y \
        libpango-1.0-0 \
        libpangoft2-1.0-0 \
        libharfbuzz-subset0 \
        fonts-dejavu-core \
        fonts-liberation \
    && rm -rf /var/lib/apt/lists/*

# pip targets the venv, so no marker conflict and no flag needed.
RUN pip install --no-cache-dir weasyprint==69.0

# ── qr codes ────────────────────────────────────────────────────────────
RUN pip install --no-cache-dir qrcode

COPY QR /usr/local/bin/QR
RUN chmod +x /usr/local/bin/QR

# ── apnews ──────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install --no-install-recommends -y \
        nodejs \
        npm \
    && rm -rf /var/lib/apt/lists/*

COPY apnews/package.json apnews/package-lock.json /opt/apnews/
RUN npm ci --prefix /opt/apnews

COPY apnews/fetch-apnews.js /opt/apnews/
COPY APNEWS /usr/local/bin/APNEWS
RUN chmod +x /usr/local/bin/APNEWS

# ── qpdf ────────────────────────────────────────────────────────────────
RUN apt-get update && apt-get install --no-install-recommends -y \
        qpdf \
    && rm -rf /var/lib/apt/lists/*

# ── minja_render ─────────────────────────────────────────────────────────
# Jinja2-compatible template renderer. Binary built on Ubuntu from
# ../minja-render; only needs libstdc++6.
RUN apt-get update && apt-get install --no-install-recommends -y \
        libstdc++6 \
    && rm -rf /var/lib/apt/lists/*

COPY minja_render /usr/local/bin/minja_render_bin
RUN chmod +x /usr/local/bin/minja_render_bin && \
    printf '#!/bin/sh\nif [ "$#" -eq 3 ]; then\n    minja_render_bin "$1" "$2" > "$3"\nelse\n    minja_render_bin "$1" "$2"\nfi\n' \
      > /usr/local/bin/minja_render && \
    chmod +x /usr/local/bin/minja_render
