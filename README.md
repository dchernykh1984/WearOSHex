# WearOS Hex

**Hex Duel** for **Wear OS** watches, in Kotlin and Jetpack Compose.

Hex is a connection game played on a rhombus of hexagons. Red owns the top and
bottom edges, Blue the left and right ones, and each player is trying to be the
first to join its own two sides with an unbroken chain of its own stones. Stones
are only ever added, never moved or taken, and a full Hex board always contains
exactly one crossing chain - so the game can never be drawn.

Play it against the watch on three levels, or pass the watch back and forth with a
friend. Everything runs on the watch: no phone, no network, no account.

This is a port of [AmazfitHex](https://github.com/dchernykh1984/AmazfitHex), the
same game as a Zepp OS mini app. The rules, the opponent, the layout proportions
and the eleven translations are carried over unchanged; the implementation is new.

## Playing it

- **Tap** an empty hexagon to place a stone. The cells along each edge are tinted
  in that player's colour, and the four corners carry an edge of each player, which
  is exactly how Hex counts them. A dot marks the stone played last.
- **Drag** to move the board around. A cell is a fixed, fingertip-sized hexagon on
  every board, so 5x5 fits the screen whole while 7x7 and 9x9 are bigger than it
  and are dragged into view. A drag never places a stone, and a tap that wobbles a
  little still counts as a tap.
- **Swap on / Swap off** sets the pie rule. With it on, the second player may take
  the opening stone instead of answering it - which is what keeps Hex fair, since
  the player who moves first is otherwise proven to have the advantage. Against the
  watch, the watch decides for itself whether to take yours, and the screen says
  **Sides swapped** when it does, because nothing on the board moves when it
  happens: the stone stays where it is and keeps its colour, and all that changes
  is whose it is.
- **Levels** - Easy drops a stone anywhere once it has been offered a win it never
  misses. Normal and Hard search: an alpha-beta over a handful of candidate moves,
  deepened until a budget of leaf evaluations runs out. The budget is divided by
  the board area, so a nine-cell board costs the watch about the same wall clock as
  a five-cell one.
- **Back** goes to the menu.
- **Languages** - English, Russian, German, French, Italian, Spanish, Portuguese,
  Dutch, Polish, Czech and Kazakh. The watch's own language is followed, and all
  eleven are offered individually in the system per-app language list - so Kazakh,
  which Zepp OS had no device-language code for and could never select, finally
  reaches the people it was translated for.

## How the opponent works

Three things make a search affordable on a watch CPU:

- **Candidates.** Every cell is rated by how long a crossing running through it
  would be, for both players at once, and only the best handful are searched. Four
  distance passes rate the whole board, which is cheaper than evaluating even one
  move per cell.
- **Tactics before search.** A crossing that can be finished this move, by either
  side, is read straight off those same passes, so no level ever spends search on
  the one move that obviously has to be played.
- **A budget, not a depth.** The search deepens two plies at a time until it runs
  out of leaf evaluations, keeping the best move of the last iteration that
  finished. Two plies and not one because the evaluation says nothing about whose
  turn it is.

The position itself is judged by the classic Hex **two-distance**: how many stones
a player still has to place before it owns a connection the opponent cannot cut.

## Devices

Round watches, **Wear OS 3 (API 30) and newer**. Built and tested against a
**OnePlus Watch 2R** (466x466 round, Wear OS 5).

## Setup

```bash
git clone https://github.com/dchernykh1984/WearOSHex.git
cd WearOSHex
```

A JDK 17 and the Android SDK (compileSdk 36) are all that is needed; Gradle comes
with the repository through the wrapper. Point the build at your SDK with a
`local.properties` holding `sdk.dir=/path/to/Android/sdk`, or export `ANDROID_HOME`.

## Develop

```bash
./gradlew testDebugUnitTest   # the JVM unit tests
./gradlew koverVerify         # unit tests + the coverage floor
./gradlew ktlintCheck         # formatting
./gradlew detekt              # static analysis
./gradlew lintDebug           # Android Lint, including the Wear OS checks
./gradlew assembleDebug       # build the APK
./gradlew connectedDebugAndroidTest   # instrumented tests (needs a watch or emulator)
./gradlew installDebug        # install on a watch over ADB
```

The whole pull-request gate in one line, which is exactly what CI runs:

```bash
./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```

### Layout of the code

```
wear/
  src/main/AndroidManifest.xml         watch-only, standalone, no permissions
  src/main/java/com/dchernykh/hex/
    MainActivity.kt                    the single activity
    HexViewModel.kt                    the state the screen draws
    game/Board.kt                      the shape of a board, built once per size
    game/Game.kt                       the rules, with union-find connectivity
    game/Evaluate.kt                   the two-distance the search is judged on
    game/MoveOrder.kt                  which cells are worth searching at all
    game/Ai.kt                         the search, and the pie-rule decision
    game/Level.kt                      how hard the watch plays
    game/Settings.kt                   mode, board size and the pie rule
    layout/HexLayout.kt                where the rhombus sits, and what a tap hit
    layout/Panning.kt                  how far it may be dragged
    layout/RoundGeometry.kt            chord maths that keeps content off the bezel
    store/SettingsStore.kt             the settings, on Preferences DataStore
    ui/                                the Compose screens
  src/main/res/values*/strings.xml     the screen strings, a table per language
  src/test/                            JVM unit tests, the opponent included
  src/androidTest/                     instrumented tests - what needs a device
tools/make-launcher-icons.sh           regenerates the icon from the Zepp OS one
config/detekt/detekt.yml               static-analysis overrides
gradle/libs.versions.toml              every dependency and plugin version
```

The rule that shapes it: anything a test can reach without a device - the rules,
the opponent, the board geometry, the panning - is a plain Kotlin class outside the
Compose layer, and `koverVerify` holds it to a floor of 80. Only what genuinely
needs a device is exempt, and each exemption is written down where it is made, with
the instrumented test that covers it instead.

The search runs on a background dispatcher. The hard level is allowed ninety
thousand leaf evaluations, which is a visible pause on a watch CPU, and a pause on
the main thread is a frozen screen rather than a thinking one.

## Pre-commit hooks (contributors)

```bash
uv tool install pre-commit   # or: pipx install pre-commit
pre-commit install
pre-commit install --hook-type commit-msg --hook-type pre-push
```

On commit: whitespace and line endings, YAML/TOML/XML well-formedness, a non-ASCII
guard on source and config (translations in `res/values-*/` are exempt - that is
what they are for), and a check that apostrophes in string resources are escaped,
which is an aapt2 error rather than a warning. On the commit message: Conventional
Commits. On push: ktlint, detekt and the unit tests.

## Continuous integration and releases

Every pull request must pass: pre-commit, `actionlint`, commitizen, the Gradle gate
above, a CodeQL analysis, an OSV dependency scan and the instrumented tests on two
Wear OS emulators.

Releases are automated with `release-please`: it maintains a version-bump PR from
the Conventional Commits and, when merged, tags a GitHub Release. The release build
then produces a **signed APK**, verifies its signature, records a build-provenance
attestation and attaches the APK and its R8 mapping file to the release.

Verify a published APK came from this repository:

```bash
gh attestation verify wearos-hex-<version>.apk --repo dchernykh1984/WearOSHex
```

### Dependency locking

`wear/gradle.lockfile` pins every transitive version. After changing a dependency,
regenerate it with the **Update lockfiles** workflow (or
`./gradlew :wear:dependencies --write-locks`) and commit the result.

## License

Released under the [MIT License](LICENSE).
