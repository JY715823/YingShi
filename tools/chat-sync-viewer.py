#!/usr/bin/env python3
"""
chat-sync-viewer.py — Lightweight desktop viewer for chat sync history.

Flask web app that reads processed.json and serves a dashboard at
http://localhost:5199 with upload history, summary stats, and a
"Sync Now" button.
"""

import json
import os
import sys
import threading
from datetime import datetime, timezone
from pathlib import Path

try:
    from flask import Flask, jsonify, render_template_string
except ImportError:
    print("ERROR: 'flask' package is required. Install with: pip install flask")
    sys.exit(1)

# Import sync logic from the companion script
SCRIPT_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(SCRIPT_DIR))

try:
    from importlib import import_module
    sync_module = import_module("chat-sync-script")
    run_sync = sync_module.run_sync
    PROCESSED_FILE = sync_module.PROCESSED_FILE
    DEFAULT_CONFIG = sync_module.DEFAULT_CONFIG
except Exception as e:
    print(f"WARNING: Could not import chat-sync-script: {e}")
    print("The 'Sync Now' feature will not be available.")
    run_sync = None
    PROCESSED_FILE = SCRIPT_DIR / "processed.json"
    DEFAULT_CONFIG = SCRIPT_DIR / "config.json"


# ---------------------------------------------------------------------------
# Flask app
# ---------------------------------------------------------------------------

app = Flask(__name__)

# Track whether a sync is currently running
_sync_lock = threading.Lock()
_sync_status = {"running": False, "message": ""}


def _load_processed() -> dict:
    if PROCESSED_FILE.exists():
        with open(PROCESSED_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {}


def _format_size(size_bytes: int) -> str:
    if size_bytes < 1024:
        return f"{size_bytes} B"
    elif size_bytes < 1024 * 1024:
        return f"{size_bytes / 1024:.1f} KB"
    else:
        return f"{size_bytes / (1024 * 1024):.1f} MB"


def _format_duration(ms: int) -> str:
    if ms <= 0:
        return "-"
    elif ms < 1000:
        return f"{ms}ms"
    else:
        return f"{ms / 1000:.1f}s"


def _build_entries(processed: dict) -> list[dict]:
    """Build sorted list of entry dicts for the template."""
    entries = []
    for key, rec in processed.items():
        stats = rec.get("stats", {})
        entries.append({
            "filename": rec.get("filename", Path(key).name),
            "size": _format_size(rec.get("size", 0)),
            "uploadTime": rec.get("uploadTime", "-"),
            "messages": stats.get("messages", 0),
            "resources": stats.get("resources", 0),
            "chats": stats.get("chats", 0),
            "mediaStored": stats.get("mediaStored", 0),
            "result": rec.get("result", "unknown"),
            "message": rec.get("message", ""),
            "duration": _format_duration(rec.get("duration_ms", 0)),
        })
    # Sort by upload time descending (newest first)
    entries.sort(key=lambda e: e["uploadTime"], reverse=True)
    return entries


def _build_summary(entries: list[dict]) -> dict:
    total_messages = sum(e["messages"] for e in entries)
    total_resources = sum(e["resources"] for e in entries)
    total_imports = len(entries)
    success_count = sum(1 for e in entries if e["result"] == "success")
    last_upload = entries[0]["uploadTime"] if entries else "Never"
    return {
        "totalImports": total_imports,
        "successCount": success_count,
        "totalMessages": total_messages,
        "totalResources": total_resources,
        "lastUpload": last_upload,
    }


# ---------------------------------------------------------------------------
# HTML template
# ---------------------------------------------------------------------------

HTML_TEMPLATE = r"""
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Chat Sync Viewer</title>
<style>
  :root {
    --bg: #0f1117;
    --surface: #1a1d27;
    --border: #2a2d3a;
    --text: #e1e4ed;
    --text-dim: #8b8fa3;
    --accent: #6c8cff;
    --accent-hover: #8ba4ff;
    --success: #4ade80;
    --error: #f87171;
    --dry: #facc15;
  }
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background: var(--bg);
    color: var(--text);
    line-height: 1.6;
    padding: 2rem;
    max-width: 1200px;
    margin: 0 auto;
  }
  h1 {
    font-size: 1.5rem;
    font-weight: 600;
    margin-bottom: 1.5rem;
    display: flex;
    align-items: center;
    gap: 0.75rem;
  }
  h1 .badge {
    font-size: 0.7rem;
    background: var(--border);
    padding: 2px 8px;
    border-radius: 4px;
    color: var(--text-dim);
    font-weight: 400;
  }

  /* Summary cards */
  .summary {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
    gap: 1rem;
    margin-bottom: 1.5rem;
  }
  .card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 1rem 1.25rem;
  }
  .card .label {
    font-size: 0.75rem;
    color: var(--text-dim);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    margin-bottom: 0.25rem;
  }
  .card .value {
    font-size: 1.5rem;
    font-weight: 700;
  }

  /* Toolbar */
  .toolbar {
    display: flex;
    align-items: center;
    gap: 1rem;
    margin-bottom: 1rem;
  }
  .btn {
    background: var(--accent);
    color: #fff;
    border: none;
    padding: 0.5rem 1.25rem;
    border-radius: 6px;
    font-size: 0.85rem;
    font-weight: 500;
    cursor: pointer;
    transition: background 0.15s;
  }
  .btn:hover { background: var(--accent-hover); }
  .btn:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
  .sync-status {
    font-size: 0.8rem;
    color: var(--text-dim);
  }

  /* Table */
  .table-wrap {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: 8px;
    overflow-x: auto;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.85rem;
  }
  th {
    text-align: left;
    padding: 0.75rem 1rem;
    border-bottom: 1px solid var(--border);
    color: var(--text-dim);
    font-weight: 500;
    font-size: 0.75rem;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    white-space: nowrap;
  }
  td {
    padding: 0.6rem 1rem;
    border-bottom: 1px solid var(--border);
    white-space: nowrap;
  }
  tr:last-child td { border-bottom: none; }
  tr:hover td { background: rgba(108, 140, 255, 0.04); }

  .result-badge {
    display: inline-block;
    padding: 1px 8px;
    border-radius: 4px;
    font-size: 0.75rem;
    font-weight: 500;
  }
  .result-success { background: rgba(74, 222, 128, 0.15); color: var(--success); }
  .result-error   { background: rgba(248, 113, 113, 0.15); color: var(--error); }
  .result-dry-run { background: rgba(250, 204, 21, 0.15); color: var(--dry); }

  .empty {
    text-align: center;
    padding: 3rem;
    color: var(--text-dim);
  }

  /* Footer */
  .footer {
    margin-top: 1.5rem;
    font-size: 0.75rem;
    color: var(--text-dim);
    text-align: center;
  }
</style>
</head>
<body>

<h1>
  Chat Sync Viewer
  <span class="badge">auto-refresh 30s</span>
</h1>

<!-- Summary -->
<div class="summary">
  <div class="card">
    <div class="label">Total Imports</div>
    <div class="value">{{ summary.totalImports }}</div>
  </div>
  <div class="card">
    <div class="label">Total Messages</div>
    <div class="value">{{ "{:,}".format(summary.totalMessages) }}</div>
  </div>
  <div class="card">
    <div class="label">Total Resources</div>
    <div class="value">{{ "{:,}".format(summary.totalResources) }}</div>
  </div>
  <div class="card">
    <div class="label">Last Upload</div>
    <div class="value" style="font-size:0.95rem">{{ summary.lastUpload }}</div>
  </div>
</div>

<!-- Toolbar -->
<div class="toolbar">
  <button class="btn" id="syncBtn" onclick="triggerSync()">Sync Now</button>
  <span class="sync-status" id="syncStatus">{{ sync_msg }}</span>
</div>

<!-- Table -->
<div class="table-wrap">
  <table>
    <thead>
      <tr>
        <th>Time</th>
        <th>Filename</th>
        <th>Size</th>
        <th>Chats</th>
        <th>Messages</th>
        <th>Resources</th>
        <th>Duration</th>
        <th>Status</th>
      </tr>
    </thead>
    <tbody>
      {% for e in entries %}
      <tr>
        <td>{{ e.uploadTime }}</td>
        <td title="{{ e.message }}">{{ e.filename }}</td>
        <td>{{ e.size }}</td>
        <td>{{ e.chats }}</td>
        <td>{{ "{:,}".format(e.messages) }}</td>
        <td>{{ "{:,}".format(e.resources) }}</td>
        <td>{{ e.duration }}</td>
        <td>
          <span class="result-badge result-{{ e.result }}">
            {{ e.result }}
          </span>
        </td>
      </tr>
      {% endfor %}
      {% if not entries %}
      <tr><td colspan="8" class="empty">No uploads yet. Click "Sync Now" to start.</td></tr>
      {% endif %}
    </tbody>
  </table>
</div>

<div class="footer">
  Processed registry: {{ processed_path }}
</div>

<script>
function triggerSync() {
  const btn = document.getElementById('syncBtn');
  const status = document.getElementById('syncStatus');
  btn.disabled = true;
  btn.textContent = 'Syncing...';
  status.textContent = 'Sync in progress...';

  fetch('/api/sync', { method: 'POST' })
    .then(r => r.json())
    .then(data => {
      status.textContent = data.message || 'Sync complete.';
      btn.disabled = false;
      btn.textContent = 'Sync Now';
      // Reload page to show updated data
      setTimeout(() => location.reload(), 1000);
    })
    .catch(err => {
      status.textContent = 'Error: ' + err.message;
      btn.disabled = false;
      btn.textContent = 'Sync Now';
    });
}

// Auto-refresh every 30 seconds
setTimeout(() => location.reload(), 30000);
</script>

</body>
</html>
"""


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/")
def index():
    processed = _load_processed()
    entries = _build_entries(processed)
    summary = _build_summary(entries)
    sync_msg = _sync_status["message"] if _sync_status["message"] else "Ready."
    return render_template_string(
        HTML_TEMPLATE,
        entries=entries,
        summary=summary,
        sync_msg=sync_msg,
        processed_path=str(PROCESSED_FILE),
    )


@app.route("/api/sync", methods=["POST"])
def api_sync():
    if run_sync is None:
        return jsonify({"error": "Sync module not available"}), 500

    if not _sync_lock.acquire(blocking=False):
        return jsonify({"message": "Sync already in progress."})

    def _run():
        try:
            _sync_status["message"] = "Sync started..."
            results = run_sync(dry_run=False, config_path=DEFAULT_CONFIG)
            success = sum(1 for r in results if r.get("result") == "success")
            errors = sum(1 for r in results if r.get("result") == "error")
            _sync_status["message"] = (
                f"Sync complete: {success} succeeded, {errors} failed "
                f"at {datetime.now(timezone.utc).strftime('%H:%M:%S UTC')}"
            )
        except Exception as e:
            _sync_status["message"] = f"Sync error: {e}"
        finally:
            _sync_lock.release()

    t = threading.Thread(target=_run, daemon=True)
    t.start()
    return jsonify({"message": "Sync started in background."})


@app.route("/api/data")
def api_data():
    """JSON endpoint for programmatic access."""
    processed = _load_processed()
    entries = _build_entries(processed)
    summary = _build_summary(entries)
    return jsonify({"entries": entries, "summary": summary})


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Chat Sync Viewer")
    parser.add_argument("--port", type=int, default=5199, help="Port to listen on (default: 5199)")
    parser.add_argument("--host", type=str, default="localhost", help="Host to bind (default: localhost)")
    args = parser.parse_args()

    print(f"Chat Sync Viewer starting at http://{args.host}:{args.port}")
    print(f"Reading processed data from: {PROCESSED_FILE}")
    app.run(host=args.host, port=args.port, debug=False)


if __name__ == "__main__":
    main()
