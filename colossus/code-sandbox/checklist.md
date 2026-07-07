# Python Sandbox Setup: Docker

Single Docker image with a curated Python package set. The model passes a
shell command string to the tool; the orchestrator runs it inside the container
via `/bin/sh -c`. Two host directories are bind-mounted RW into every run —
a persistent data directory and a temp directory — using the same paths inside
and outside the container so the model never has to translate. Everything else
is read-only with no network access.

---

## Part 1: Verify Docker is Available

- [ ] Confirm Docker is running:
  ```bash
  docker info > /dev/null && echo "ok"
  ```

- [ ] Confirm your user can run Docker without sudo (colossus should already
  be in the `docker` group since Open WebUI is running):
  ```bash
  docker ps
  ```

  If not:
  ```bash
  sudo usermod -aG docker colossus
  # log out and back in for group change to take effect
  ```

---

## Part 2: Create the Dockerfile

- [ ] Create a working directory for the image definition:
  ```bash
  mkdir -p ~/llm-sandbox
  cd ~/llm-sandbox
  ```

- [ ] Create the Dockerfile:
  ```bash
  cat > Dockerfile << 'EOF'
  FROM python:3.12-slim

  # Create a non-root user to run commands as
  RUN useradd -r -s /usr/sbin/nologin -m sandbox

  # Install extra unix tools (jq, sqlite3); standard tools like grep/awk/sed
  # are already present in the base image
  RUN apt-get update && apt-get install -y --no-install-recommends \
      jq \
      sqlite3 \
    && rm -rf /var/lib/apt/lists/*

  # Install curated Python packages
  RUN pip install --no-cache-dir \
      numpy pandas scipy statsmodels sympy \
      matplotlib seaborn plotly \
      scikit-learn xgboost lightgbm \
      pillow openpyxl xlrd pyarrow \
      beautifulsoup4 lxml \
      pydantic jsonschema pyyaml toml \
      python-dateutil pytz regex \
      nltk rapidfuzz \
      tqdm more-itertools tabulate \
      requests

  USER sandbox
  WORKDIR /

  EOF
  ```

---

## Part 3: Build the Image

- [ ] Build it:
  ```bash
  docker build -t llm-sandbox ~/llm-sandbox
  ```

- [ ] Verify packages are present:
  ```bash
  docker run --rm llm-sandbox \
    python3 -c "import numpy, pandas, sklearn; print('ok')"
  ```

- [ ] Pin the exact package versions for future reference:
  ```bash
  docker run --rm llm-sandbox pip freeze \
    > ~/llm-sandbox/requirements.txt
  ```

---

## Part 4: Run a Command

The orchestrator passes a model-provided shell command string to `/bin/sh -c`.
The data and temp directories are mounted at identical host and container paths
so the model can reference the same paths in both file-tool calls and sandbox
commands.

```bash
# These come from your orchestrator's project context
DATA_DIR=/projects/foo/data
TEMP_DIR=/projects/foo/temp

# The model-provided command — any of these forms work:

# Reference a .py file written in a prior tool call
COMMAND="python3 /projects/foo/data/analyze.py"

# Inline python
COMMAND="python3 -c \"
import pandas as pd
df = pd.read_csv('/projects/foo/data/input.csv')
print(df.describe())
df.to_csv('/projects/foo/data/summary.csv')
\""

# Unix pipeline
COMMAND="cat /projects/foo/data/log.txt | grep ERROR | awk '{print \$3}' | sort | uniq -c"

# jq
COMMAND="jq '.results[] | select(.score > 0.9)' /projects/foo/data/results.json > /projects/foo/data/filtered.json"

# Run it
timeout 30s docker run --rm \
  --network none \
  --read-only \
  --memory 1g \
  --memory-swap 1g \
  --cpus 1 \
  --tmpfs /tmp:size=64m \
  -v "$DATA_DIR:$DATA_DIR" \
  -v "$TEMP_DIR:$TEMP_DIR" \
  llm-sandbox \
  /bin/sh -c "$COMMAND"
```

Flag reference:

| Flag | Purpose |
|---|---|
| `timeout 30s` | Wall-clock kill from the calling side (Docker has no native limit) |
| `--rm` | Remove container immediately on exit |
| `--network none` | No network interfaces at all |
| `--read-only` | Container root filesystem is read-only; bind mounts are exempt |
| `--memory 1g` | Hard memory cap |
| `--memory-swap 1g` | Same value disables swap |
| `--cpus 1` | Limit to one CPU |
| `--tmpfs /tmp:size=64m` | In-memory /tmp, size-capped, discarded on exit |
| `-v $DATA_DIR:$DATA_DIR` | Persistent project data, RW, same path inside and out |
| `-v $TEMP_DIR:$TEMP_DIR` | Temp dir, RW, same path inside and out |

### Shell injection guard

The model-provided command is passed directly to `/bin/sh -c`. Since the
RW mounts are real, add a simple blocklist in the orchestrator before
invoking Docker — reject commands containing any of:

```
rm, mkfs, dd, chmod, chown, mv, shred, truncate
```

The container limits the blast radius, but this prevents a confused model
from accidentally destroying project files.

---

## Part 5: Canary Tests

Run these before wiring up to the orchestrator.

- [ ] **No network access:**
  ```bash
  docker run --rm --network none --read-only \
    llm-sandbox \
    /bin/sh -c "python3 -c \"import socket; socket.create_connection(('1.1.1.1', 80))\""
  # expect: OSError / Network unreachable
  ```

- [ ] **Cannot write outside mounted directories:**
  ```bash
  docker run --rm --network none --read-only \
    --tmpfs /tmp \
    llm-sandbox \
    /bin/sh -c "echo evil > /etc/evil"
  # expect: Read-only file system error
  ```

- [ ] **Data directory is RW and path is identical inside and out:**
  ```bash
  mkdir -p /tmp/canary-data
  docker run --rm --network none --read-only \
    --tmpfs /tmp:size=64m \
    -v /tmp/canary-data:/tmp/canary-data \
    llm-sandbox \
    /bin/sh -c "echo hello > /tmp/canary-data/test.txt"
  cat /tmp/canary-data/test.txt
  # expect: hello
  rm -rf /tmp/canary-data
  ```

- [ ] **Memory limit is enforced:**
  ```bash
  docker run --rm --network none --read-only \
    --memory 1g --memory-swap 1g \
    --tmpfs /tmp \
    llm-sandbox \
    python3 -c "x = ' ' * (1200 * 1024 * 1024)"
  # expect: MemoryError or OOM kill
  ```

- [ ] **Unix tools are available:**
  ```bash
  docker run --rm --network none --read-only \
    --tmpfs /tmp \
    llm-sandbox \
    /bin/sh -c "grep --version && awk --version && jq --version && sqlite3 --version"
  # expect: all four print version strings
  ```

- [ ] **Time limit kills runaway commands:**
  ```bash
  timeout 5s docker run --rm --network none --read-only \
    --tmpfs /tmp \
    llm-sandbox \
    /bin/sh -c "while true; do :; done"
  echo "exit: $?"
  # expect: exits after ~5 seconds with code 124
  ```

---

## Part 6: Rebuilding / Updating Packages

When you want to add or update packages:

- [ ] Edit the `pip install` block in `~/llm-sandbox/Dockerfile`
- [ ] Rebuild: `docker build -t llm-sandbox ~/llm-sandbox`
- [ ] Regenerate the pinned list: `docker run --rm llm-sandbox pip freeze > ~/llm-sandbox/requirements.txt`
- [ ] Update the tool description (Part 7) to match

Old image layers are automatically replaced. Running containers are unaffected
until they exit; new invocations pick up the new image.

---

## Part 7: Tool Description (Keep in Sync with Dockerfile)

Use this as the basis for your orchestrator's tool description. Update the
package list whenever you rebuild the image.

```
Execute a shell command in a sandboxed Docker container.
The command string is passed directly to /bin/sh -c and may be:
  - A reference to a .py file: python3 /projects/foo/data/script.py
  - An inline python script: python3 -c "..."
  - A unix pipeline: grep ERROR /projects/foo/data/log.txt | sort | uniq -c
  - Any combination of available tools

Two directories are available for reading and writing, at the same
paths used by the file tools:
  /projects/foo/data  — persistent across sessions
  /projects/foo/temp  — cleared between sessions

Stdout and stderr are captured and returned.
No network access. All other paths are read-only.
Execution is time-limited to 30 seconds.

Available Python packages:
  numpy, pandas, scipy, statsmodels, sympy,
  matplotlib, seaborn, plotly,
  scikit-learn, xgboost, lightgbm,
  pillow, openpyxl, xlrd, pyarrow,
  beautifulsoup4, lxml,
  pydantic, jsonschema, pyyaml, toml,
  python-dateutil, pytz, regex,
  nltk, rapidfuzz,
  tqdm, more-itertools, tabulate,
  requests

Unix tools available:
  grep, sed, awk, sort, uniq, cut, tr, wc, head, tail,
  find, diff, cat, jq, sqlite3
```

> Tip: generate the package list from requirements.txt automatically so it
> never drifts:
> ```bash
> grep -v '^#' ~/llm-sandbox/requirements.txt \
>   | sed 's/=.*//' | sort | paste -sd ', '
> ```
