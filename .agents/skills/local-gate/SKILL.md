---
name: local-gate
description: Running the same checks CI runs, and the ktlint, detekt, coverage and ASCII rules that fail a commit most often here. Use before every commit or push.
---

# The local gate

CI runs one Gradle line; run the same before pushing and there is nothing left to be
surprised by:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

While iterating, the fast subset is `./gradlew ktlintCheck detekt testDebugUnitTest`.

## Coverage

`koverVerify` enforces `minBound(80)` over what is left after the excludes, and the
excludes are the point: the `ui` package (Compose screens) and the `store` package
(Context-backed persistence) are unreachable from a JVM test, so they are excluded and
the single instrumented test under `wear/src/androidTest/` walks the app instead.

What remains - the rules in `game/`, the geometry in `layout/` and the view model - is
plain Kotlin with no excuse for being uncovered. Put logic there, not in a composable,
and coverage takes care of itself.

## pre-commit hooks that bite

- **`no-non-ascii`** rejects any byte above 0x7F in Kotlin, YAML, Markdown, TOML, shell
  and JSON, `gradlew` excluded. XML is deliberately absent from that list: translated
  text belongs in `wear/src/main/res/values-<lang>/strings.xml` and nowhere else. A
  degree sign or a typographic dash in a Kotlin string or a comment fails the commit.
- **Write files as UTF-8.** On Windows a PowerShell redirect, `Set-Content` or
  `Out-File` defaults to UTF-16, and the ASCII hook then rejects a file whose text looks
  perfectly plain in an editor: the bytes are the problem, not the characters.
  `file <path>` says which encoding you actually wrote.
- **`unescaped-apostrophe`** catches an unescaped `'` inside a `<string>` in a values
  file. That is an aapt2 error rather than a warning, and it otherwise surfaces only
  when the resources are compiled.
- `ktlint` and `detekt` run through Gradle, so they need the toolchain on PATH.
