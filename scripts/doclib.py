"""Shared documentation ownership checks. No third-party packages."""

from __future__ import annotations

import json
import re
import subprocess
from pathlib import Path

WAIVER = "docs/impact-waiver.md"
RESOURCE_GLOB = "src/main/resources"


def repo_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_manifest(root: Path | None = None) -> dict:
    root = root or repo_root()
    return json.loads((root / "docs" / "manifest.json").read_text(encoding="utf-8"))


def glob_to_re(pattern: str) -> re.Pattern[str]:
    pattern = pattern.replace("\\", "/")
    parts: list[str] = ["^"]
    i = 0
    while i < len(pattern):
        if pattern.startswith("**", i):
            parts.append(".*")
            i += 2
            if i < len(pattern) and pattern[i] == "/":
                i += 1
        elif pattern[i] == "*":
            parts.append("[^/]*")
            i += 1
        else:
            parts.append(re.escape(pattern[i]))
            i += 1
    parts.append("$")
    return re.compile("".join(parts))


def matches(pattern: str, rel: str) -> bool:
    rel = rel.replace("\\", "/")
    return glob_to_re(pattern).match(rel) is not None


def owners_for(manifest: dict, rel: str) -> list[str]:
    found: list[str] = []
    for doc in manifest["documents"]:
        if any(matches(pattern, rel) for pattern in doc["paths"]):
            found.append(doc["document"].replace("\\", "/"))
    return found


def resource_yaml(root: Path) -> list[str]:
    base = root / "src" / "main" / "resources"
    return sorted(
        path.relative_to(root).as_posix()
        for path in base.glob("*.yml")
    )


def validate_manifest(root: Path | None = None) -> list[str]:
    root = root or repo_root()
    manifest = load_manifest(root)
    errors: list[str] = []
    if manifest.get("satisfy") != "any-owner":
        errors.append("manifest satisfy must be any-owner")

    known = {item.replace("\\", "/") for item in manifest.get("known_unloaded", [])}
    if "src/main/resources/cookware.yml" not in known:
        errors.append("cookware.yml must stay listed in known_unloaded")

    for excluded in manifest.get("generated_excludes", []):
        if not excluded.startswith("target"):
            errors.append(f"unexpected generated exclude: {excluded}")
        if any(matches(excluded, doc["document"]) for doc in manifest["documents"]):
            errors.append(f"generated exclude covers a document: {excluded}")

    for doc in manifest["documents"]:
        document = root / doc["document"]
        if not document.is_file():
            errors.append(f"missing document {doc['document']}")
        if doc["document"].replace("\\", "/").startswith("target/"):
            errors.append(f"document points at target: {doc['document']}")
        for pattern in doc["paths"]:
            if pattern.startswith("target"):
                errors.append(f"{doc['id']} owns generated path {pattern}")
            matched = [
                path.relative_to(root).as_posix()
                for path in root.rglob("*")
                if path.is_file() and "target" not in path.relative_to(root).parts
                and matches(pattern, path.relative_to(root).as_posix())
            ]
            if not matched:
                errors.append(f"{doc['id']} pattern matches nothing: {pattern}")

    mapped: set[str] = set()
    for rel in resource_yaml(root):
        if owners_for(manifest, rel):
            mapped.add(rel)
        elif rel not in known:
            errors.append(f"runtime yaml is not mapped or known_unloaded: {rel}")
    if "src/main/resources/cookware.yml" in mapped:
        errors.append("cookware.yml is unloaded and must not be an owned runtime config")
    return errors


def waiver_allows(root: Path, changed: set[str]) -> bool:
    if WAIVER not in changed:
        return False
    text = (root / WAIVER).read_text(encoding="utf-8")
    if "docs-impact: none" not in text:
        return False
    for line in text.splitlines():
        if line.lower().startswith("reason:"):
            reason = line.split(":", 1)[1].strip()
            return len(reason) >= 12 and reason.lower() != "none"
    return False


def impact_errors(root: Path, changed: set[str]) -> list[str]:
    manifest = load_manifest(root)
    changed = {item.replace("\\", "/") for item in changed}
    if waiver_allows(root, changed):
        return []
    errors: list[str] = []
    for rel in sorted(changed):
        if rel.startswith("target/"):
            errors.append(f"do not commit generated output: {rel}")
            continue
        owners = owners_for(manifest, rel)
        if not owners:
            continue
        if any(owner in changed for owner in owners):
            continue
        errors.append(
            f"{rel} needs one of: {', '.join(owners)} "
            f"(or a same-diff {WAIVER} with docs-impact: none and a reason)"
        )
    return errors


def git_changed(root: Path) -> list[str]:
    import os
    base = os.environ.get("GITHUB_BASE_REF", "").strip()
    if base:
        names = _git(root, ["diff", "--name-only", f"origin/{base}...HEAD"])
        if names is not None:
            return _split(names)
    if os.environ.get("GITHUB_EVENT_NAME") == "push":
        names = _git(root, ["diff", "--name-only", "HEAD~1", "HEAD"])
        if names is not None:
            return _split(names)
    names = _git(root, ["diff", "--name-only", "HEAD"])
    staged = _git(root, ["diff", "--name-only", "--cached"])
    untracked = _git(root, ["ls-files", "--others", "--exclude-standard"])
    files: set[str] = set()
    for blob in (names, staged, untracked):
        if blob:
            files.update(_split(blob))
    return sorted(files)


def _git(root: Path, args: list[str]) -> str | None:
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=root,
            check=False,
            capture_output=True,
            text=True,
        )
    except OSError:
        return None
    if result.returncode != 0:
        return None
    return result.stdout


def _split(blob: str) -> list[str]:
    return [line.strip().replace("\\", "/") for line in blob.splitlines() if line.strip()]
