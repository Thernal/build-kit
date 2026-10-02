# build-kit — rules for agents

build-kit is a **kit**: reusable Compose Multiplatform code that applications copy — renamed into their own
package — with `skillctl.sh kit install build-kit` (the skill-manager skill, `Thernal/knowledge`), and later
merge changes from with `kit update`. What they copy is listed in `kit.yml`: the modules `build-logic`, `config/detekt`, `gradle/app-settings.gradle.kts`, `.githooks` ….
Everything committed to `main` reaches every app that takes the next update, so this file's first rule is
about delivery.

## Delivering a change

- **Nothing reaches `main` without a green `./gradlew build`** — every target, the tests on the JVM host
  and the iOS simulator, Detekt. Check Gradle's own exit code (`./gradlew build && git commit …`), never
  through a pipe: `./gradlew … | tail && git commit` checks `tail`. A failing test is fixed, never skipped.
- Git conventions, attribution (off here) and branches: `.agents/workspace.md`. A breaking change to what
  apps use is `feat!:`/`refactor!:` with a `Migration:` paragraph — apps merge the kit's files, never their
  own call sites.
- Detekt here works as it does in apps (D25): it never fails the build; the pre-commit hook blocks a
  finding the first time and records it. Fix what it reports rather than committing twice — the kit's
  own code is the example apps copy. The Detekt rules in `build-logic/detekt-rules` have their own tests,
  which do fail the build.

## Writing code here

- **No `api(...)`** (epic D24): a module declares everything it uses; nothing is re-exported.
- **Rename-safe.** An app's copy is renamed textually (`kit.yml`: package, alias). Write the
  package prefix whole, never split or as a regex fragment; `libs.plugins.<alias>.` literally; and keep the
  kit's name out of any string an install does not rename (resource names, authorities, cache paths).
- `api`/`impl` code lives in `data`, `domain` or `presentation` packages, and layers point inwards
  (the kit's own Detekt rules). `wiring` holds Metro binding containers only.
- Kotlin nests block comments: never write `/*` inside KDoc (`image/*`, `ios/*.swift`).
- Every module that changes what apps copy updates `kit.yml` in the same change: `code`, `surface`,
  `requires`.

## Documentation

| File | Holds | Update when |
|---|---|---|
| `README.md` | what the kit does, its layout, how it is built | anything a user of the kit sees changes |
| `build-logic/README.md` | why each part has its shape | a decision or trade-off changes |
| `skills/build-kit/references/setup.md` | how to use it, task by task | the contract changes |
| `skills/build-kit` | the same for an agent in an app that took the kit | the public surface changes — `kit status` flags a skill older than the surface (LAG) |
| `kit.yml` | what an app copies and what its build must provide | a module, file part or requirement changes |

The skill's frontmatter has to load in Claude Code and in Codex alike (knowledge `docs/SKILLS.md`): a
`description` of at most 1024 characters — 600–900 in practice, since an app's renamed package can
lengthen it and every installed skill shares one context budget — valid YAML (no `": "` or `" #"` in a
plain one-line description; write `—`), and only the keys `name` and `description`. skill-manager warns
in an app whose copy breaks this; the fix is made here.

Docs are read in place by apps (`knowledgectl.sh kit build-kit read <path>`), never copied. When `kit.yml`
or `README.md` changes, the knowledge card `kits/build-kit.md` in `Thernal/knowledge` needs its `card_sha`
bumped (`scripts/check-corpus.py --kits` says so).

## This kit is different

build-kit is the build every app starts from, so its own rules are the apps' rules: in apps, Detekt
**never fails the build** — the pre-commit hook blocks new findings once and annotates them (D25). This
repository proves the conventions on `fixture/` (two apps, a feature, an environment, an iOS export); it is
never copied into apps. Anything named after a flavor reads `app.flavors*` instead.
