# build-logic — design

Why each part of build-kit has the shape it has. What it does and how to use it is in the
[README](../README.md).

## Capabilities, not layers

A module applies the capabilities it uses — `kmp.library`, `compose`, `injection` — rather than a
"feature data" or "feature presentation" bundle that also adds dependencies on named project modules.
Bundles like that encode one application's module graph into the build; conventions that only configure
tooling carry across applications unchanged, which is what a kit needs.

Quality is not a capability. Detekt is applied by every module convention and has no plugin id, so no
module can opt out of it by omission.

## No `api(...)`

`api` puts a dependency on every consumer's compile classpath. An ABI change there recompiles the whole
downstream graph, and a consumer's build file stops saying what it uses. Declaring a library again in
each module that uses its types costs a line; the other two cost build time and legibility for as long
as the code lives. The check runs at configuration time over every `api` bucket — `api`, `debugApi`,
`commonMainApi`, `iosMainApi` — including the ones conventions could add, so nothing reaches consumers
by accident.

The exception is structural, not a preference: Kotlin/Native `export(...)` accepts only `api`
dependencies, and a module whose types Swift must see has to be exported into the framework. Those are
named one by one in `app.api.allowed`; there is no pattern and no per-module switch.

## One flavor per invocation

The KMP Android library plugin has no product flavors, and iOS has no `BuildConfig`. Shared code is
therefore compiled for one environment per Gradle invocation, and the application modules — which do have
product flavors — must build the matching one. The detection order puts what a person types first
(`-Papp.env`), then what an IDE or CI asks for without typing it (variant task names, Xcode's
`CONFIGURATION`), then standing choices (`local.properties`, the default). Two flavors in one invocation
fail rather than build one of them wrong.

The flavor list is data. Nothing in the conventions names a flavor except by reading
`app.flavors.production`, so an application with two environments and one with five use the same code.
Names starting with `test` are refused because AGP reserves them for its own source sets and tasks.

`Environment` is generated rather than read at run time so that a missing key is a compile error in the
flavor that lacks it — every flavor exposes the union of keys, empty where a file does not set one, with
a build warning. It is a `const val` object in `commonMain` so that iOS and Android read it the same way.

## Signing

The debug keystore is shared and committed (`config/signing/debug/`) because Google sign-in, Firebase
and app links pin the debug signature; per-machine debug keys break them for everyone but the machine
that registered. Non-production release builds use it too, so a beta can be installed beside production
without a store. The production keystore never enters the repository; without it the production
release is built unsigned, unless `-PrequireReleaseSigning=true` says a release is being cut.

## Detekt: never fail, block at commit

A build that fails on findings is right for a codebase that starts clean and wrong for one that does
not: the first run on real code fails, and the fix is a baseline nobody reads. build-kit never fails on
findings. The pre-commit hook compares a run on the changed files with the persisted report and blocks
only findings that are new to it — having recorded them. The second attempt passes, and each finding
stays marked on its line, where the next person to touch the code sees it. Blocking once is the point:
the author has to look, but is never stuck.

Counting is per `file::rule` and by number, not by line, so reformatting does not turn old findings into
new ones and adding a second instance of a rule to a file still counts as new.

In a multiplatform module each source file is analysed exactly once, preferring a task that resolves
types: the Android compilations cover common and Android code, and source-set tasks cover only what no
Android compilation sees — the iOS source sets. An application module has no task that sees `src/main`
except the plain one, which it uses.

The root tasks and the hook installation live in build-logic rather than in `gradle/*.gradle.kts`
scripts, so they are copied and updated with the kit instead of drifting in each application.

## Reports are generated and merged, not edited

`report/` is derived from the build — the module report from build files, the stability pages from
compiler output — so a merge conflict in it is never a real disagreement. `.gitattributes` gives it a
driver that keeps one side, and the post-merge hook regenerates the module report from the merged build
files.

Stability is reported per module for the same reason: one page per Compose module means a branch that
changes one module changes one page, and each page is a cacheable task over that module's metrics alone.
The index is the only shared file, and it is small. A run without metrics keeps the committed pages —
an empty page would otherwise replace a real one whenever someone forgot the flag.

## Dependency analysis is advice

The analysis is right about unused project modules and often wrong about everything else in a
multiplatform build: Compose Multiplatform artifacts resolve to AndroidX ones on Android and look unused,
Metro binding containers are read off the classpath without a type reference, and "declare this
transitive dependency" asks for artifacts no catalog names. It runs only when the root build declares it,
reports at warning level, and ignores its `api` advice — `api(...)` is not used.

## Rename-safe by construction

An application's copy is renamed textually: the package prefix, `libs.plugins.<alias>.`, module paths.
So everything a rename must reach is written out whole — the Detekt rules' package prefix is a plain
string, not a regex fragment; generated build files use a literal `libs.plugins.buildkit.` prefix — and
everything that must *not* be renamed avoids those strings: the properties are `app.*`, never
`buildkit.*`.
