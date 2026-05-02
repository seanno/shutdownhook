# ============================================================
# O365 Email Exporter via Microsoft Graph API
# Exports matching emails as .eml files preserving all headers,
# attachments, BCC etc.
#
# Requirements:
#   pip install msal requests
#
# Setup:
#   1. Create an Azure AD App Registration
#   2. Add delegated permission: Mail.Read, User.Read
#   3. Set redirect URI to http://localhost:8400
#   4. Fill in CLIENT_ID and TENANT_ID below
# ============================================================

import os
import json
import base64
import requests
import msal
import time
from datetime import datetime, timezone
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText
from email.mime.base import MIMEBase
from email import encoders
from email.utils import formatdate
import email.utils

# ============================================================
# Configuration - edit these
# ============================================================

CLIENT_ID   = "CLIENT_ID"
TENANT_ID   = "TENANT_ID"
REDIRECT_URI = "http://localhost:8400"

# Date range (inclusive)
START_DATE  = "2025-02-01"
END_DATE    = "2026-02-01"  # set to tomorrow or beyond to include today

# Target addresses - emails must have at least one of these
# in any of from/to/cc/bcc to be included
TARGET_ADDRESSES = [
    "example@example.com"
]

# Target display names - matched in addition to addresses
# (handles cases where Outlook hides the SMTP address)
TARGET_NAMES = [
    "Example"
]

# Excluded domains - if ANY address in from/to/cc/bcc matches
# one of these domains, the email is skipped entirely
EXCLUDED_DOMAINS = [
    "@anotherexample.com"
]

# Output directory - one .eml file per matching email
OUTPUT_DIR = "./exported_emails"

# Folders to skip (case-insensitive, partial match)
SKIP_FOLDERS = [
    "deleted items",
    "recoverable items",
    "archive",
    "online archive",
    "junk",
]

# ============================================================
# Auth
# ============================================================

SCOPES = ["Mail.Read", "User.Read"]

def get_token():
    app = msal.PublicClientApplication(
        CLIENT_ID,
        authority=f"https://login.microsoftonline.com/{TENANT_ID}"
    )

    # Try silent first (cached token from previous run)
    accounts = app.get_accounts()
    if accounts:
        result = app.acquire_token_silent(SCOPES, account=accounts[0])
        if result and "access_token" in result:
            print("Using cached token.")
            return result["access_token"]

    # Device code flow
    flow = app.initiate_device_flow(scopes=SCOPES)
    if "user_code" not in flow:
        raise Exception(f"Device flow failed: {flow.get('error_description', 'unknown error')}")

    # This prints something like:
    # "To sign in, use a web browser to open https://microsoft.com/devicelogin
    #  and enter the code XXXXXXXX to authenticate."
    print(flow["message"])

    # Blocks here until you complete the auth in your browser
    result = app.acquire_token_by_device_flow(flow)

    if "access_token" not in result:
        raise Exception(f"Auth failed: {result.get('error_description', 'unknown error')}")

    print("Authenticated successfully.")
    return result["access_token"]

# ============================================================
# Graph API helpers
# ============================================================

GRAPH_BASE = "https://graph.microsoft.com/v1.0"

def graph_request_with_retry(method, url, headers, params=None, max_retries=5):
    """
    Execute a Graph API request with exponential backoff retry.
    Handles 429 (rate limit), 503 (service unavailable), and 504 (gateway timeout).
    """
    backoff = 2  # seconds, doubles each retry
    for attempt in range(max_retries):
        try:
            r = requests.request(method, url, headers=headers, params=params)
            
            # Success
            if r.status_code < 400:
                return r
            
            # Rate limited --- respect Retry-After header if present
            if r.status_code == 429:
                retry_after = int(r.headers.get("Retry-After", backoff))
                print(f"  Rate limited. Waiting {retry_after}s before retry {attempt + 1}/{max_retries}...")
                time.sleep(retry_after)
                continue
            
            # Transient server errors --- retry with backoff
            if r.status_code in (503, 504):
                print(f"  Transient error {r.status_code}. Waiting {backoff}s before retry {attempt + 1}/{max_retries}...")
                time.sleep(backoff)
                backoff = min(backoff * 2, 60)  # cap at 60s
                continue
            
            # Non-retryable error --- raise immediately
            r.raise_for_status()

        except requests.exceptions.ConnectionError as e:
            print(f"  Connection error: {e}. Waiting {backoff}s before retry {attempt + 1}/{max_retries}...")
            time.sleep(backoff)
            backoff = min(backoff * 2, 60)

    raise Exception(f"Failed after {max_retries} retries: {url}")


def graph_get(token, url, params=None):
    """Single GET with auth header, returns parsed JSON."""
    headers = {"Authorization": f"Bearer {token}"}
    r = graph_request_with_retry("GET", url, headers=headers, params=params)
    return r.json()


def graph_get_paged(token, url, params=None):
    """Yield all pages of a Graph API collection."""
    headers = {"Authorization": f"Bearer {token}"}
    next_url = url
    next_params = params
    while next_url:
        r = graph_request_with_retry("GET", next_url, headers=headers, params=next_params)
        data = r.json()
        yield from data.get("value", [])
        next_url = data.get("@odata.nextLink")
        next_params = None

# ============================================================
# Folder enumeration
# ============================================================

def get_all_folders(token):
    """Recursively enumerate all mail folders, skipping excluded ones."""
    folders = []
    root_url = f"{GRAPH_BASE}/me/mailFolders"
    _collect_folders(token, root_url, folders)
    return folders

def _collect_folders(token, url, result):
    for folder in graph_get_paged(token, url):
        name = folder.get("displayName", "")
        if any(skip in name.lower() for skip in SKIP_FOLDERS):
            print(f"  Skipping folder: {name}")
            continue
        result.append(folder)
        # Recurse into child folders
        child_url = f"{GRAPH_BASE}/me/mailFolders/{folder['id']}/childFolders"
        _collect_folders(token, child_url, result)

# ============================================================
# Matching logic
# ============================================================

def address_matches_target(addr, name):
    """Check if an address or display name matches any target."""
    addr_lower = (addr or "").lower()
    name_lower = (name or "").lower()
    for target in TARGET_ADDRESSES:
        if target.lower() in addr_lower:
            return True
    for target_name in TARGET_NAMES:
        if target_name.lower() in name_lower:
            return True
    return False

def address_matches_excluded(addr, name):
    """Check if an address or display name matches any excluded domain."""
    addr_lower = (addr or "").lower()
    name_lower = (name or "").lower()
    for domain in EXCLUDED_DOMAINS:
        if domain.lower() in addr_lower:
            return True
        if domain.lower() in name_lower:
            return True
    return False

def get_all_participants(msg):
    """Return list of (address, name) tuples for all from/to/cc/bcc."""
    participants = []

    # From
    sender = msg.get("from", {}).get("emailAddress", {})
    participants.append((sender.get("address", ""), sender.get("name", "")))

    # To
    for r in msg.get("toRecipients", []):
        ea = r.get("emailAddress", {})
        participants.append((ea.get("address", ""), ea.get("name", "")))

    # CC
    for r in msg.get("ccRecipients", []):
        ea = r.get("emailAddress", {})
        participants.append((ea.get("address", ""), ea.get("name", "")))

    # BCC
    for r in msg.get("bccRecipients", []):
        ea = r.get("emailAddress", {})
        participants.append((ea.get("address", ""), ea.get("name", "")))

    return participants

def message_matches(msg):
    """
    Returns True if:
    - At least one participant matches a target address/name
    - No participant matches an excluded domain
    """
    participants = get_all_participants(msg)

    # Check exclusions first (fast exit)
    for addr, name in participants:
        if address_matches_excluded(addr, name):
            return False

    # Check targets
    for addr, name in participants:
        if address_matches_target(addr, name):
            return True

    return False

# ============================================================
# EML export
# ============================================================

def fetch_full_message_mime(token, msg_id):
    """Fetch the raw MIME content of a message with retry."""
    url = f"{GRAPH_BASE}/me/messages/{msg_id}/$value"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "text/plain"
    }
    r = graph_request_with_retry("GET", url, headers=headers)
    return r.content

def safe_filename(subject, msg_id, received):
    """Generate a safe filename from subject + date + id fragment."""
    date_str = received[:10] if received else "nodate"
    safe = "".join(c if c.isalnum() or c in " -_" else "_" for c in (subject or "no_subject"))
    safe = safe[:60].strip()
    id_fragment = msg_id[-8:]
    return f"{date_str}_{safe}_{id_fragment}.eml"

def export_message(token, msg, output_dir):
    """Fetch and save a message as a raw .eml file."""
    msg_id = msg["id"]
    subject = msg.get("subject", "no_subject")
    received = msg.get("receivedDateTime", "")

    raw_mime = fetch_full_message_mime(token, msg_id)

    filename = safe_filename(subject, msg_id, received)
    filepath = os.path.join(output_dir, filename)

    with open(filepath, "wb") as f:
        f.write(raw_mime)

    return filename

# ============================================================
# Main
# ============================================================

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    print("Authenticating...")
    token = get_token()

    print("Enumerating folders...")
    folders = get_all_folders(token)
    print(f"Found {len(folders)} folders to search.")

    # Build date filter for Graph API query
    # This filters server-side before we even download message details
    date_filter = (
        f"receivedDateTime ge {START_DATE}T00:00:00Z "
        f"and receivedDateTime le {END_DATE}T00:00:00Z"
    )

    total_examined = 0
    total_skipped_excluded = 0
    total_matched = 0

    for folder in folders:
        folder_name = folder.get("displayName", "unknown")
        folder_id = folder["id"]

        # Query messages in this folder with date filter
        url = f"{GRAPH_BASE}/me/mailFolders/{folder_id}/messages"
        params = {
            "$filter": date_filter,
            "$select": "id,subject,receivedDateTime,sentDateTime,from,toRecipients,ccRecipients,bccRecipients",
            "$top": 50,
        }

        folder_count = 0
        folder_matched = 0

        for msg in graph_get_paged(token, url, params):
            total_examined += 1
            folder_count += 1

            if total_examined % 50 == 0:
                print(f"  ... examined {total_examined} messages so far")

            participants = get_all_participants(msg)

            # Check exclusions
            excluded = any(address_matches_excluded(a, n) for a, n in participants)
            if excluded:
                total_skipped_excluded += 1
                continue

            # Check targets
            matched = any(address_matches_target(a, n) for a, n in participants)
            if matched:
                filename = safe_filename(msg["subject"], msg["id"], msg.get("receivedDateTime", ""))
                filepath = os.path.join(OUTPUT_DIR, filename)
                
                # Skip if already exported (resume support)
                if os.path.exists(filepath):
                    print(f"  ~ Skipping (already exported): {filename}")
                    total_matched += 1
                    folder_matched += 1
                    continue
                    
                filename = export_message(token, msg, OUTPUT_DIR)
                total_matched += 1
                folder_matched += 1
                print(f"  + Exported: {filename}")

        if folder_matched > 0:
            print(f"  Folder '{folder_name}': {folder_count} examined, {folder_matched} exported")

    print()
    print("=== COMPLETE ===")
    print(f"Total examined:          {total_examined}")
    print(f"Skipped (excluded):      {total_skipped_excluded}")
    print(f"Exported:                {total_matched}")
    print(f"Output directory:        {os.path.abspath(OUTPUT_DIR)}")

if __name__ == "__main__":
    main()
