---
name: shipping-a-change
description: Branch, commit, open a pull request, and drive CI to green in this repository. Use whenever you are about to commit, push, open a PR, or check CI status.
---

# Shipping a change

## Ask before anything outward-facing

Pushing, merging, tagging, deleting or force-pushing a branch, and cutting a release are
the user's call, every time. Show the exact command and wait for a plain "yes";
narrating what you are about to do is not consent. Reading - status, log, diff, fetch,
`gh ... view` - needs no permission.

## Branch

- Never commit to `main`. `git fetch origin && git switch -c <type>/<slug> origin/main`.
- Stage only the files you touched (`git add <path>`), never `git add -A`: the tree can
  carry edits that are not yours.

## Commit

- One line, Conventional Commits: `git commit -m "type(scope): summary"`. No body, no
  trailers, no `Co-Authored-By`, and no "generated with" footer in the pull request
  description either - strip one if a default adds it.
- `commitizen` validates the subject locally and in CI, and release-please builds
  `CHANGELOG.md` from these subjects, so the type matters: `feat` and `fix` produce a
  release, `chore`, `docs`, `test`, `style` and `refactor` do not.
- One logical change per commit, each green on its own.

## Before pushing

Run the local gate (see the `local-gate` skill). Everything CI checks runs on this
machine, so a red pipeline over ktlint or a failing unit test is avoidable.

## Pull request

`git push -u origin <branch>` then `gh pr create --base main --title "..." --body "..."`.
The body is prose about the change - what was wrong, what changed, how it is held in
place - and carries no attribution.

## Watch CI to green

The checks are `pre-commit`, `actionlint`, `commitizen` and `android` (the slow one:
ktlint, detekt, lint, unit tests, coverage and both assembles).

Take the verdict from the rollup, not from `gh pr checks`, whose per-check status lags
and can still say `pending` long after a job has finished - which reads like a hung
check:

```bash
gh pr view <n> --json statusCheckRollup \
  --jq '[.statusCheckRollup[] | {name:(.name//.context), s:(.conclusion//.state)}]'
```
