# Behavior Change Handover — static vs. turn context (2.6.2 → 2.6.3-SNAPSHOT)

**For:** Jon (Peon-PO) — findings from the `use-agentmd-static` branch, to plan the follow-up.
**Date:** 2026-08-21 · **Status:** handover, not yet a decision (no ADR)

## What the branch changed

1. **Static context now carries env info + workspace memory.**
   `PeonAiService.initStaticContext()` (PeonAiService.java:124) sets every agent's static context
   to `[StaticContextItem (OS/date/file-access rules), workspaceMemoryTool.get()]`. The static
   context is rendered into the **system prompt** (`AbstractAgent.buildSystemPrompt`,
   AbstractAgent.java:333) and **cached** until `clear()` or `setStaticContext()`
   (AbstractAgent.java:286, 305).
   - Before 2.6.2: the system prompt was 100% static (OS/date rules only, ADR-0029); the memory
     was folded into standing orders (turn context / chat) for the PO slaves.
2. **Turn context (`PeonAiService.get()`, PeonAiService.java:388)** — injected as chat (user)
   messages via `renderTurnContext` (AbstractAgent.java:357), deduped by `dedupKey`, re-injected
   only after compact or project switch: plan reference / handoff line, AGENTS.md items
   (`AgentsMdContextItem`), PO docs (`docs/memory.md`, `docs/index.md`), user context
   (selected project).
3. **`StandingOrdersBuilder` deleted** — its role is replaced by the static/turn split.
4. **`WorkspaceMemoryTool`** — no longer a singleton (`getInstance()` gone), no longer a
   `ContextItemProvider`.
5. **Planner prompt** — writes key know-how into the plan before compacting (`planner.txt`).
6. **PO agent built via `BuildPoAgentComponent`** (parts/ai/component).

## Intended behavior (confirmed by the user, 2026-08-21)

- **Memory in the system prompt is wanted, not a bug.** Memory changed mid-session
  (memoryAdd/memoryRemove/memoryReplace) is intentionally *not* reflected until the system prompt
  is rebuilt.
- **The system prompt is only rebuilt on `clear` or `compact`** — that should be fine.
- **Smoke test passed.**
- **Docs need to be aligned with this** (context-architecture.md, ADR-0029, memory.md) —
  planned as a follow-up, not part of this handover.

## To double-check (possible issues spotted while reading — verify in depth)

1. **All rebuild paths unverified.** Only `clear()` (AbstractAgent.java:305) and
   `setStaticContext()` (AbstractAgent.java:288) visibly reset the `systemMessage` cache. The
   compact path resetting it was *not* seen in the code read so far — check every path that
   should rebuild the system prompt (clear, compact, project switch, config reload).
2. **`updateConfig` → `agentService.refresh()`** is called from `setModel`, `withThinkSupported`
   and `ReloadConfigTool` — if `refresh` recreates agents, the static + turn context set once in
   the constructor (`initStaticContext`) would be lost. Check.
3. **Code smell, not an NPE**: `getProject().getFile(PlanTool.OVERVIEW_FILE)` in
   `PeonAiService.get()` / `preloadPlanIfNeeded` has no visible null-check — safety relies on
   `planTool.hasPlan()` guarding the path (invisible at the call site; the 2.6.2 version checked
   `project != null` explicitly). Consider making the guard explicit.
4. **Untested `clear()` cache reset** — existing TODO at AbstractAgent.java:305:
   "TODO test needed — AI forgot this reset case".
5. **`StaticContextItem.render()`** hardcodes `"\n"` instead of `System.lineSeparator()` —
   against the project's own line-separator rule (ADR-0014).

## Related

- [context-architecture.md](context-architecture.md) — R1/R2 context model
- [ADR-0029](adr/0029-file-context-in-history.md) — file context in history
- [memory.md](memory.md) — memory tool story + open ends
