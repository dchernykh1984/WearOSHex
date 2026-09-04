#!/usr/bin/env python3
"""PostToolUse guard: keep non-ASCII characters out of the files pre-commit checks.

The no-non-ascii pre-commit hook rejects any byte above 0x7F in Kotlin, YAML, Markdown,
TOML, shell and JSON, so an accidental Cyrillic letter or a typographic dash otherwise
only surfaces at commit time. This catches it at the edit that introduced it: it reads
the hook payload on stdin and exits 2 with an explanation, which sends the message back
to the agent as a blocking error.

XML is deliberately absent, mirroring the pre-commit config: the translations under
res/values-<lang>/ are legitimately not ASCII.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

CHECKED_SUFFIXES = {".kt", ".kts", ".yml", ".yaml", ".md", ".toml", ".sh", ".json"}
# gradlew is vendored upstream code; CHANGELOG.md is generated from commit messages.
EXEMPT_NAMES = {"gradlew", "CHANGELOG.md"}
MAX_REPORTED_LINES = 5


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0  # JSONDecodeError subclasses ValueError; nothing to fail an edit over

    raw_path = (payload.get("tool_input") or {}).get("file_path")
    if not raw_path:
        return 0

    path = Path(raw_path)
    if path.suffix not in CHECKED_SUFFIXES or path.name in EXEMPT_NAMES:
        return 0
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return 0
    except UnicodeDecodeError:
        return 0

    offenders = []
    for number, line in enumerate(text.splitlines(), start=1):
        bad = sorted({ch for ch in line if ord(ch) > 0x7F})
        if bad:
            offenders.append((number, "".join(bad)))

    if not offenders:
        return 0

    shown = offenders[:MAX_REPORTED_LINES]
    detail = "; ".join(f"line {number}: {chars}" for number, chars in shown)
    if len(offenders) > len(shown):
        detail += f"; and {len(offenders) - len(shown)} more line(s)"
    print(
        f"{path.name} contains non-ASCII characters ({detail}). "
        "The no-non-ascii pre-commit hook will reject this file. Translated text "
        "belongs in res/values-<lang>/strings.xml, not in source or config.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
