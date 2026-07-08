#!/usr/bin/env python3
"""
chat-sync-script.py — Upload QQ chat export ZIPs to the Yingshi server.

Scans the QCE scheduled-exports directory, tracks processed files in
processed.json, and uploads any new / missed ZIPs via multipart POST.
"""

import argparse
import hashlib
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

try:
    import requests
except ImportError:
    print("ERROR: 'requests' package is required. Install with: pip install requests")
    sys.exit(1)


# ---------------------------------------------------------------------------
# Paths
# ---------------------------------------------------------------------------

SCRIPT_DIR = Path(__file__).resolve().parent
DEFAULT_CONFIG = SCRIPT_DIR / "config.json"
PROCESSED_FILE = SCRIPT_DIR / "processed.json"
DEFAULT_EXPORTS_DIR = Path(os.path.expandvars(r"%USERPROFILE%\.qq-chat-exporter\scheduled-exports"))


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


def _md5_prefix(path: Path, length: int = 8) -> str:
    """Return the first *length* hex chars of the MD5 of a file."""
    h = hashlib.md5()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()[:length]


def _load_json(path: Path, default):
    if path.exists():
        with open(path, "r", encoding="utf-8") as f:
            return json.load(f)
    return default


def _save_json(path: Path, data) -> None:
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _load_config(config_path: Path) -> dict:
    if not config_path.exists():
        print(f"ERROR: Config file not found: {config_path}")
        print("Copy chat-sync-config.template.json to config.json and fill in your values.")
        sys.exit(1)
    with open(config_path, "r", encoding="utf-8") as f:
        cfg = json.load(f)
    # Expand env vars in exportsDir
    exports_dir = cfg.get("exportsDir", "")
    cfg["exportsDir"] = os.path.expandvars(exports_dir)
    return cfg


# ---------------------------------------------------------------------------
# Core logic
# ---------------------------------------------------------------------------

def load_processed() -> dict:
    """Return the processed registry keyed by absolute file path."""
    return _load_json(PROCESSED_FILE, {})


def save_processed(data: dict) -> None:
    _save_json(PROCESSED_FILE, data)


def discover_zips(exports_dir: Path) -> list[Path]:
    """Return sorted list of .zip files in the exports directory."""
    if not exports_dir.exists():
        print(f"WARNING: Exports directory does not exist: {exports_dir}")
        return []
    zips = sorted(exports_dir.glob("*.zip"))
    return zips


def find_unprocessed(zips: list[Path], processed: dict) -> list[Path]:
    """Return ZIPs that have not been successfully processed yet."""
    unprocessed = []
    for zp in zips:
        key = str(zp)
        entry = processed.get(key)
        if entry is None:
            # Never seen before
            unprocessed.append(zp)
            continue
        # Check if file changed since last processing
        current_size = zp.stat().st_size
        current_mtime = zp.stat().st_mtime
        if (entry.get("size") != current_size
                or entry.get("mtime") != current_mtime
                or entry.get("result") != "success"):
            unprocessed.append(zp)
    return unprocessed


def upload_zip(
    zip_path: Path,
    server_url: str,
    auth_token: str,
    dry_run: bool = False,
) -> dict:
    """
    Upload a single ZIP to the server.

    Returns a dict with keys:
        result      — "success" | "error" | "dry-run"
        message     — human-readable summary
        stats       — dict with chats/messages/resources/mediaStored (or empty)
        duration_ms — upload duration in milliseconds
    """
    stat = zip_path.stat()
    file_size = stat.st_size
    md5_pre = _md5_prefix(zip_path)

    if dry_run:
        return {
            "result": "dry-run",
            "message": f"[DRY-RUN] Would upload {zip_path.name} ({file_size} bytes, md5:{md5_pre})",
            "stats": {},
            "duration_ms": 0,
        }

    url = f"{server_url.rstrip('/')}/api/chat/imported/upload-zip"
    headers = {}
    if auth_token:
        headers["Authorization"] = auth_token

    t0 = time.monotonic()
    try:
        with open(zip_path, "rb") as f:
            files = {"file": (zip_path.name, f, "application/zip")}
            resp = requests.post(url, files=files, headers=headers, timeout=300)
        elapsed = int((time.monotonic() - t0) * 1000)

        if resp.status_code == 200:
            body = resp.json() if resp.headers.get("content-type", "").startswith("application/json") else {}
            stats = {
                "chats": body.get("chats", 0),
                "messages": body.get("messages", 0),
                "resources": body.get("resources", 0),
                "mediaStored": body.get("mediaStored", 0),
            }
            return {
                "result": "success",
                "message": f"Uploaded {zip_path.name} — {stats['messages']} msgs, {stats['resources']} resources",
                "stats": stats,
                "duration_ms": elapsed,
            }
        else:
            return {
                "result": "error",
                "message": f"HTTP {resp.status_code}: {resp.text[:200]}",
                "stats": {},
                "duration_ms": elapsed,
            }
    except requests.exceptions.ConnectionError as exc:
        elapsed = int((time.monotonic() - t0) * 1000)
        return {"result": "error", "message": f"Connection error: {exc}", "stats": {}, "duration_ms": elapsed}
    except requests.exceptions.Timeout as exc:
        elapsed = int((time.monotonic() - t0) * 1000)
        return {"result": "error", "message": f"Timeout: {exc}", "stats": {}, "duration_ms": elapsed}
    except Exception as exc:
        elapsed = int((time.monotonic() - t0) * 1000)
        return {"result": "error", "message": f"Unexpected error: {exc}", "stats": {}, "duration_ms": elapsed}


def run_sync(dry_run: bool = False, config_path: Path = DEFAULT_CONFIG) -> list[dict]:
    """
    Main sync entry point. Returns a list of result dicts (one per ZIP processed).
    """
    cfg = _load_config(config_path)
    server_url = cfg.get("serverUrl", "http://localhost:8080")
    auth_token = cfg.get("authToken", "")
    exports_dir = Path(cfg.get("exportsDir", str(DEFAULT_EXPORTS_DIR)))

    processed = load_processed()
    zips = discover_zips(exports_dir)

    if not zips:
        print("No ZIP files found in exports directory.")
        return []

    unprocessed = find_unprocessed(zips, processed)
    if not unprocessed:
        print("All ZIP files are already processed. Nothing to do.")
        return []

    print(f"Found {len(unprocessed)} unprocessed ZIP(s) out of {len(zips)} total.")
    results = []

    for zp in unprocessed:
        stat = zp.stat()
        ts = _now_iso()
        print(f"  [{ts}] Processing {zp.name} ({stat.st_size} bytes) ...")

        result = upload_zip(zp, server_url, auth_token, dry_run=dry_run)

        # Update processed registry
        key = str(zp)
        processed[key] = {
            "path": str(zp),
            "filename": zp.name,
            "size": stat.st_size,
            "mtime": stat.st_mtime,
            "md5": _md5_prefix(zp),
            "uploadTime": ts,
            "result": result["result"],
            "message": result["message"],
            "duration_ms": result["duration_ms"],
            "stats": result["stats"],
        }
        results.append(processed[key])

        print(f"    -> {result['message']}")

    # Persist after all uploads so partial progress is saved even on crash
    save_processed(processed)
    print(f"\nDone. {len(results)} file(s) processed. Registry saved to {PROCESSED_FILE}")
    return results


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description="Upload QQ chat export ZIPs to the Yingshi server.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Preview which files would be uploaded without actually uploading.",
    )
    parser.add_argument(
        "--config",
        type=str,
        default=str(DEFAULT_CONFIG),
        help=f"Path to config.json (default: {DEFAULT_CONFIG})",
    )
    args = parser.parse_args()

    config_path = Path(args.config)
    run_sync(dry_run=args.dry_run, config_path=config_path)


if __name__ == "__main__":
    main()
