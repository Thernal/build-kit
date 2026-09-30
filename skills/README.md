# Agent skills

Skills for AI coding agents working in applications that **use** build-kit. Each skill is a directory
with a `SKILL.md` — YAML frontmatter (`name`, `description`) plus instructions — and the reference files
it points to, in the [Agent Skills](https://agentskills.io) layout.

| Skill | Use it when |
|---|---|
| [`build-kit`](build-kit/SKILL.md) | installing build-kit; adding modules, apps or flavors; environment values; the Detekt hook and its markers; module and stability reports; builds, version codes, deep-link tests and store lanes; debugging build-kit's errors |

```
build-kit/
├── SKILL.md                     the model, orientation, task router, verification
└── references/
    ├── setup.md                 installing: settings, root build, catalog, properties — a full example
    ├── flavors.md               flavors, the active flavor, Environment, flavor-scoped dependencies, apps
    ├── detekt.md                the rules, the hook, markers, detektFull, suppressing
    └── modules-and-reports.md   create, api(...), graph, stability pages, buildHealth, scripts, make, fastlane
```

An installed copy (`skillctl.sh kit install build-kit`) is renamed with the code, so the names in it are
already the application's.
