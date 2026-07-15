FROM python:3.12-slim

# Create a non-root user to run commands as
RUN useradd -r -s /usr/sbin/nologin -m sandbox

# Install extra unix tools (jq, sqlite3); standard tools like grep/awk/sed
# are already present in the base image
RUN apt-get update && apt-get install -y --no-install-recommends \
    jq \
    sqlite3 \
	openscad \
  && rm -rf /var/lib/apt/lists/*

# environment variables to help scripts be less whiny
ENV MPLCONFIGDIR=/tmp/matplotlib

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
WORKDIR /tmp


