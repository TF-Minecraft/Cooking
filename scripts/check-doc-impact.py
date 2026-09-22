"""Fail when owned code or config changes without a system doc or a waiver."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from doclib import git_changed, impact_errors, repo_root


def main() -> int:
    root = repo_root()
    changed = git_changed(root)
    errors = impact_errors(root, set(changed))
    if errors:
        print("documentation impact check failed:")
        for error in errors:
            print(f"  {error}")
        return 1
    print(f"documentation impact check passed ({len(changed)} changed paths)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
