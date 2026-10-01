# Working on Hex Duel

Hex for Wear OS watches, in Kotlin and Jetpack Compose: the connection game on a
rhombus of hexagons, where Red joins top to bottom and Blue joins left to right, and a
full board always holds exactly one crossing chain. Everything runs on the watch - no
phone, no network, no account. `README.md` says what the app is; this file is how work
is done here. Longer procedures live in `.agents/skills/`.

## How the code is laid out

One Gradle module, `:wear`, under `com.dchernykh.hex`:

| Package | Owns |
| --- | --- |
| `game/` | the rules: state, moves, scoring, generation, the saved format |
| `layout/` | geometry: where things sit on a round face, hit testing, chord maths |
| `ui/` | the Jetpack Compose screens and controls |
| `store/` | Context-backed persistence |
| `HexViewModel.kt` | the hub between the rules and the screen |

**The split is the point.** `game/` and `layout/` are plain Kotlin a JVM test reaches
without a watch. `ui/` and `store/` cannot be reached from one, so they are excluded
from coverage and a single instrumented test under `wear/src/androidTest/` walks the
app instead. Anything worth testing belongs in `game/` or `layout/`, never in a
composable.

## Rules

- **Commit messages are one line.** Conventional Commits, no body, no trailers, no
  `Co-Authored-By`, and no "generated with" footer in a pull request either.
  `commitizen` enforces the format, and release-please builds `CHANGELOG.md` from the
  subjects, so `feat` and `fix` produce a release and `chore`, `docs`, `test` and
  `style` do not.
- **Never commit to `main`.** Branch off `origin/main` and open a pull request.
- **Outward-facing actions need user authorization.** A request for a pull request
  authorizes its branch and pushes. Merging, tagging and releases need a separate
  explicit request. Reading needs no permission.
- **Source and config stay ASCII.** The `no-non-ascii` hook covers Kotlin, YAML,
  Markdown, TOML, shell and JSON. XML is deliberately absent, because the translations
  in `wear/src/main/res/values-<lang>/strings.xml` legitimately are not ASCII - every
  user-facing string goes there, never inline.
- **The screen is round.** A row near the top or bottom is limited by the chord of the
  circle at its own height, not by the screen width. Derive geometry in `layout/`;
  never hard-code a pixel in a composable.
- **Comments explain why, not what.** Match the density and voice of the file.

## The gate

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

That is exactly what CI runs. `koverVerify` holds coverage at `minBound(80)` over
everything outside `ui/` and `store/`. See the `local-gate` skill for the hooks that
fail a commit most often.

## Skills

- `shipping-a-change` - branch, commit, open the PR, watch CI to green.
- `local-gate` - the Gradle gate, coverage, and the pre-commit hooks that bite.
- `review-cycle` - review a branch or PR and land the fixes.

## Coding agent setup

`AGENTS.md` supplies the project instructions to Codex; task procedures live in
`.agents/skills/`. Claude Code keeps its separate `CLAUDE.md`, `.claude/skills/`
and `.claude/settings.json` configuration. Keep both sets of guidance in sync when
the project changes.

`.codex/hooks.json` runs the ASCII guard after `apply_patch`, `Edit` and `Write`.
Trust the project in Codex, then review and approve the exact hook definition in
`/hooks` before it can run. Hooks must remain enabled. The guard reads every added,
updated or moved file in a patch; translated XML and generated `CHANGELOG.md` are
exempt from the Claude guard, but the Codex guard follows the stricter pre-commit
policy and checks `CHANGELOG.md`. Translated XML remains allowed. Edits made through
a shell command do not trigger this guard; pre-commit remains the final check.

Claude permission allow-lists and its settings do not configure Codex. Codex uses
its own approval and sandbox controls; this repository does not silently grant
permissions or change global settings. A request to create or update a pull request
authorizes the pushes needed for that request. Merging and releases still require
the maintainer's explicit request.

Install Git hooks in each checkout with `pre-commit install --hook-type pre-commit
--hook-type commit-msg --hook-type pre-push`. Run the edit-guard tests with
`python3 -m unittest discover -s .codex/hooks -p 'test_*.py'`; CI runs these through
pre-commit as well.
