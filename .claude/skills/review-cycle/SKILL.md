---
name: review-cycle
description: Review a branch or pull request for correctness and cleanup, then land the fixes. Use when asked to review the current diff or a PR before it merges.
---

# Review cycle

- Review the actual diff against `origin/main` (`git diff origin/main...HEAD`, or
  `gh pr diff <n>`), not the whole tree. The built-in `/code-review` command is the fast
  way in; this skill is what to look for once you are reading the diff.
- Prefer real correctness bugs; report cleanup only when it clearly earns its place. Do
  not invent findings to hit a count.

## What to look for here

- **Logic that landed in a composable.** Rules belong in `game/`, measurements in
  `layout/`. Anything worth testing that sits in `ui/` is both untested and excluded
  from coverage, so it fails silently rather than loudly.
- **A hard-coded pixel.** The watch face is round and ships in several diameters;
  geometry is derived, and a constant that looks fine on one size clips on another.
- **A new user-facing string with no translation** in every `values-<lang>/strings.xml`.
- **A test that passes against the bug it claims to catch.** Break the behaviour the
  test names and watch it fail before trusting it.
- **Hygiene**: ASCII outside the translation resources, ktlint and detekt clean, no
  secrets or personal paths.

## Landing the fixes

- One commit per finding, each a one-line Conventional Commit with no attribution.
- Re-run the local gate, then ask before pushing and drive CI back to green (see the
  `shipping-a-change` skill).
