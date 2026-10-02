# Detekt

Config: `config/detekt/detekt.yml`. Rules: Detekt's own, ktlint, and `build-logic/detekt-rules`:

| Rule | Says |
|---|---|
| `LayerPackageRequired` | a file of an `api` or `impl` module lives in its `data`, `domain` or `presentation` package |
| `LayerPackageBoundary` | inside a module, `presentation` and `data` may use `domain`, never each other; `domain` uses neither |
| `ExpressionBodyNotAllowed` | block bodies: `{ return … }`, not `= …` |
| `MultilineConstructorRequired` | a primary constructor with two or more parameters puts each on its own line |
| `PreviewMustBePrivate` | `@Preview` functions are private |
| `UnsafeCollectionIndexAccess` | `list[i]` / `array[i]` can throw — use `getOrNull(i)` unless the bounds are proven |

`wiring` modules are exempt from the layer rules. Modules a kit installed (`part` lines in `kits.lock`, or `map module` in an older lock)
are not analysed at all — the kit's own build checks them, and markers in them would conflict with every
`kit update`.

## The workflow

Findings never fail the build. `.githooks/pre-commit` (installed by the first Gradle sync as
`core.hooksPath`):

1. runs `detektAnalysis` on the changed `.kt` files with ktlint auto-correct, re-staging what was staged;
2. merges the findings into `.misc/detekt/detekt-report.xml` and writes `// TODO: Detekt [Rule: message]`
   on each finding's line;
3. **blocks** if a file now has more findings of a rule than the report had before;
4. the same commit made again **passes** — the findings are recorded now.

So a blocked commit means: look at the listed findings. Fix them (the markers go away on the next run),
or commit again to record them for later. The report is local (`.misc/` is git-ignored); on a fresh clone
every finding in a touched file blocks once.

| Command | Does |
|---|---|
| `./gradlew detektFull` | clear markers, analyse everything, replace the report, re-annotate |
| `./gradlew :features:x:impl:detektAnalysis` | one module, report under `build/reports/detekt/analysis/` |
| `./gradlew detektClearTodos` | remove every marker (scoped with `-PdetektChangedFiles=…`) |

## A finding that is wrong

`@Suppress("RuleName")` with a comment saying why, on the smallest scope; or a change in `detekt.yml`
when the rule is wrong for the whole project. Never `--no-verify`, never hand-edited markers.

## A new rule

Add it under `build-logic/detekt-rules/src/main/kotlin/…`, register it in `ProjectRuleSetProvider`, give
it a test beside the others, enable it in `detekt.yml` under the project rule set, and run
`./gradlew test detektFull`. A rule added here is kit code: consider whether it belongs upstream in
build-kit (report it upstream: an issue in the build-kit repository, or `skillctl.sh report build-kit --kit --kind idea` with skill-manager).
