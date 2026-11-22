#!/usr/bin/env python3
"""
Synchronize release title, description, APK metadata, and changelog across:
- metadata.json
- app/build.gradle.kts
- README.md (between AUTO_META and AUTO_CHANGELOG markers)
- .github/release_notes.md
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
META_FILE = ROOT / "metadata.json"
GRADLE_FILE = ROOT / "app" / "build.gradle.kts"
README_FILE = ROOT / "README.md"
RELEASE_NOTES_FILE = ROOT / ".github" / "release_notes.md"
DIST_DIR = ROOT / "dist-apk"


def run_git(args: list[str], default: str = "") -> str:
    try:
        out = subprocess.check_output(["git", *args], cwd=ROOT, text=True, stderr=subprocess.DEVNULL)
        return out.strip()
    except Exception:
        return default


def read_metadata() -> tuple[str, str]:
    if not META_FILE.exists():
        return "Ultimate Cleaner", "Pembersih & pengoptimal Android Root & Non-Root oleh Gendev."
    data = json.loads(META_FILE.read_text(encoding="utf-8"))
    name = str(data.get("name", "Ultimate Cleaner")).strip() or "Ultimate Cleaner"
    desc = str(data.get("description", "")).strip()
    return name, desc


def read_gradle_version() -> tuple[str, str]:
    if not GRADLE_FILE.exists():
        return "1.0", "1"
    content = GRADLE_FILE.read_text(encoding="utf-8")
    v_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    v_code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    v_name = v_name_match.group(1) if v_name_match else "1.0"
    v_code = v_code_match.group(1) if v_code_match else "1"
    return v_name, v_code


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def find_apk(variant: str) -> Path | None:
    search_dir = ROOT / "app" / "build" / "outputs" / "apk" / variant
    if not search_dir.exists():
        return None
    apks = sorted(search_dir.rglob("*.apk"))
    return apks[0] if apks else None


def replace_marker_block(text: str, start_marker: str, end_marker: str, new_body: str) -> str:
    pattern = re.compile(
        re.escape(start_marker) + r".*?" + re.escape(end_marker),
        flags=re.DOTALL,
    )
    replacement = f"{start_marker}\n{new_body.strip()}\n{end_marker}"
    if pattern.search(text):
        return pattern.sub(replacement, text, count=1)
    return text.rstrip() + f"\n\n{replacement}\n"


def main() -> None:
    app_name, app_desc = read_metadata()
    version_name, version_code = read_gradle_version()

    run_number = os.environ.get("GITHUB_RUN_NUMBER", "1").strip() or "1"
    repo = os.environ.get("GITHUB_REPOSITORY", "owner/repo").strip()
    server_url = os.environ.get("GITHUB_SERVER_URL", "https://github.com").strip().rstrip("/")
    sha_full = os.environ.get("GITHUB_SHA", run_git(["rev-parse", "HEAD"], "local")).strip()
    sha_short = sha_full[:7] if sha_full else "local"

    commit_subject = run_git(["log", "-1", "--pretty=%s"], "Pembaruan aplikasi")
    commit_body = run_git(["log", "-1", "--pretty=%b"], "")

    manual_title = os.environ.get("INPUT_TITLE", "").strip()
    manual_desc = os.environ.get("INPUT_DESCRIPTION", "").strip()

    update_title = manual_title if manual_title else commit_subject
    update_desc = manual_desc if manual_desc else (commit_body if commit_body else app_desc)

    full_version = f"{version_name}.{run_number}"
    release_tag = f"v{full_version}-{sha_short}"
    release_title = f"{app_name} v{full_version} — {update_title}"
    # GitHub Release title max safety trim
    if len(release_title) > 120:
        release_title = release_title[:117].rstrip() + "..."

    DIST_DIR.mkdir(parents=True, exist_ok=True)
    slug = re.sub(r"[^A-Za-z0-9]+", "", app_name) or "UltimateCleaner"

    debug_src = find_apk("debug")
    release_src = find_apk("release")

    apk_assets: list[tuple[str, Path, str, str]] = []
    if debug_src and debug_src.exists():
        dst = DIST_DIR / f"{slug}-v{full_version}-debug.apk"
        shutil.copy2(debug_src, dst)
        size_mb = dst.stat().st_size / (1024 * 1024)
        apk_assets.append(("Debug (Siap Install / Signature Konsisten)", dst, f"{size_mb:.2f} MB", sha256_file(dst)))

    if release_src and release_src.exists():
        dst_rel = DIST_DIR / f"{slug}-v{full_version}-release.apk"
        shutil.copy2(release_src, dst_rel)
        size_mb_rel = dst_rel.stat().st_size / (1024 * 1024)
        apk_assets.append(("Release (Signed Upload Key)", dst_rel, f"{size_mb_rel:.2f} MB", sha256_file(dst_rel)))

    now_utc = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    latest_release_url = f"{server_url}/{repo}/releases/latest"
    specific_release_url = f"{server_url}/{repo}/releases/tag/{release_tag}"

    # Recent commit history excluding bot [skip ci] commits
    raw_log = run_git(
        ["log", "-n", "12", "--grep=\\[skip ci\\]", "--invert-grep", "--pretty=format:%h|%cs|%s"],
        f"{sha_short}|{datetime.now(timezone.utc).strftime('%Y-%m-%d')}|{update_title}",
    )
    history_rows = []
    for line in raw_log.splitlines():
        parts = line.split("|", 2)
        if len(parts) == 3:
            c_hash, c_date, c_msg = parts
            safe_msg = c_msg.replace("|", "\\|")
            history_rows.append(f"| `{c_hash}` | {c_date} | {safe_msg} |")
    if not history_rows:
        history_rows.append(f"| `{sha_short}` | {now_utc} | {update_title} |")

    meta_block = "\n".join([
        f"# {app_name} — Root Edition (by Gendev)",
        "",
        f"> **{app_desc}**",
        "",
        f"[![Build & Release APK]({server_url}/{repo}/actions/workflows/build-apk.yml/badge.svg)]({server_url}/{repo}/actions/workflows/build-apk.yml) "
        f"[![Latest APK](https://img.shields.io/badge/Download_APK-v{full_version}-22C55E?style=flat-square&logo=android)]({latest_release_url})",
        "",
        f"- **Versi Rilis Terbaru:** `v{full_version}` (versionCode `{version_code}`, commit `{sha_short}`)",
        f"- **Judul Pembaruan Terakhir:** {update_title}",
        f"- **Download APK Terbaru:** [**Unduh dari GitHub Releases ({release_tag})**]({latest_release_url})",
    ])

    apk_table_lines = [
        "| Varian APK | Nama Berkas | Ukuran | SHA-256 |",
        "|---|---|---|---|",
    ]
    for label, path, sz, digest in apk_assets:
        apk_table_lines.append(f"| {label} | `{path.name}` | {sz} | `{digest[:16]}...` |")
    if len(apk_assets) == 0:
        apk_table_lines.append("| - | Belum ada berkas APK terdeteksi | - | - |")

    changelog_block = "\n".join([
        f"### Rilis Terbaru: `{release_tag}` ({now_utc})",
        "",
        f"- **Judul Pembaruan:** {update_title}",
        f"- **Deskripsi Pembaruan:** {update_desc}",
        f"- **Tautan Rilis:** [{specific_release_url}]({specific_release_url})",
        "",
        *apk_table_lines,
        "",
        "#### 10 Pembaruan Commit Terakhir",
        "",
        "| Commit | Tanggal | Ringkasan Pembaruan |",
        "|---|---|---|",
        *history_rows[:10],
    ])

    if README_FILE.exists():
        readme_text = README_FILE.read_text(encoding="utf-8")
        readme_text = replace_marker_block(readme_text, "<!-- AUTO_META_START -->", "<!-- AUTO_META_END -->", meta_block)
        readme_text = replace_marker_block(readme_text, "<!-- AUTO_CHANGELOG_START -->", "<!-- AUTO_CHANGELOG_END -->", changelog_block)
        README_FILE.write_text(readme_text, encoding="utf-8")

    release_notes_lines = [
        f"## {app_name} — `v{full_version}`",
        "",
        f"**Deskripsi Aplikasi:** {app_desc}",
        "",
        "### Ringkasan Pembaruan",
        f"- **Judul:** {update_title}",
        f"- **Deskripsi:** {update_desc}",
        f"- **Commit:** `{sha_short}` • **Tanggal Build:** `{now_utc}`",
        "",
        "### Berkas APK & Verifikasi",
        *apk_table_lines,
        "",
        "### Riwayat Perubahan Terkini",
        "| Commit | Tanggal | Perubahan |",
        "|---|---|---|",
        *history_rows[:6],
        "",
        f"Dokumentasi lengkap tersedia di [`README.md`]({server_url}/{repo}/blob/{os.environ.get('GITHUB_REF_NAME', 'main')}/README.md).",
    ]
    RELEASE_NOTES_FILE.parent.mkdir(parents=True, exist_ok=True)
    RELEASE_NOTES_FILE.write_text("\n".join(release_notes_lines) + "\n", encoding="utf-8")

    github_output = os.environ.get("GITHUB_OUTPUT")
    if github_output:
        with open(github_output, "a", encoding="utf-8") as out:
            out.write(f"release_tag={release_tag}\n")
            out.write(f"release_title={release_title}\n")
            out.write(f"apk_glob={DIST_DIR.as_posix()}/*.apk\n")

    print(f"Synced release metadata: tag={release_tag}, title={release_title}, apks={len(apk_assets)}")


if __name__ == "__main__":
    main()
