# AGENTS-PLAN.md — planning (Peon-Plan)

Project-specific additions for the plan phase — the method lives in the Jon skill, the build
rules in the base `AGENTS.md`.

You write **only the plan file** — never code, never `docs/` (owned by the PO). The plan is a
throwaway work file: every decision that survives lands in the docs by the docs owner. In a
single-agent harness you are the docs owner for this iteration (see the Jon skill).

Project constraints (details in the base `AGENTS.md`):

- **Layout:** a feature is a package with the same name across the bundles
  (`org.sterl.llmpeon.core` → `org.sterl.llmpeon` → `org.sterl.llmpeon.test`); no new module
  without a docs-owner decision.
- **Core staleness:** order the increments so core changes are built
  (`mvn clean verify` in core) before the plugin/test increments depend on them.
- **Concurrency:** plan the thread-safety, not just the logic.
- **OSGi test constraints:** plugin tests are JUnit 4, new test classes need user approval —
  prefer extending approved test classes.
- **User-visible changes** need the `homepage/` update in the same increment.
