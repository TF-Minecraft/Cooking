"""Check relative Markdown links inside the cooking docs."""

import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from doclib import repo_root

LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def main() -> int:
    root = repo_root()
    files = [root / "README.md", root / "AGENTS.md", * (root / "docs").rglob("*.md")]
    errors: list[str] = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        for match in LINK.finditer(text):
            target = match.group(1).strip()
            if target.startswith(("http://", "https://", "mailto:")):
                continue
            target = target.split("#", 1)[0].strip()
            if not target:
                continue
            resolved = (path.parent / target).resolve()
            if not resolved.exists():
                errors.append(f"{path.relative_to(root).as_posix()} -> {target}")
    if errors:
        print("broken links:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"link check passed ({len(files)} files)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
