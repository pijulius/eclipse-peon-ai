# Offene Enden (2026-08-21)

- **Zyklus 2.6.3 (Standing-Orders-Umarbeitung) — Review + Fix-Kampagne abgeschlossen:**
  - Bauspezifikation + Review OK: issues 01 (NPE-Guard in `PeonAiService.get()`), 03+04
    (Static-Context-Re-Bake bei `updateConfig`/Reload-Callback), 05 (clear-Test), 06
    (Line-Separators + stray Quote) — Core 394/0, Plugin 91/91 grün. **Nicht committed** (Commit = User).
  - issue-02 (Slaven-Memory-Regression) **ABGELEHNT** — Halluzination: `AiPoAgent.setStaticContext`
    propgt das Memory an die Slaven; Repro-Test deterministisch grün, Datei gelöscht.
  - Docs ausgerichtet: [ADR-0031](adr/0031-static-context-env-plus-memory.md),
    context-architecture.md, ADR-0029-Superseded-Note, standing-orders-design.md → historisch.
- **Beobachten:** R2(a)-Rest-Race — nur relevant, falls der Live-Status nach Compact
  doch noch mal klebt (spät gelieferter Monitor-Callback, vgl. context-architecture.md R2).
- **Prompt Caching** ([caching.md](caching.md), 🚧): Neues Design (2026-08-21, User): Caching als
  **per-agent JSON extra body** in Advanced Settings (auch Custom Agents) + GPT/Claude-Beispiele
  in der UI + gilt für jeden Agenten mit Modell-Slot; llama.cpp: kein Snippet = kein Slot
  (Compact-Agent); Abgleich über Usage-Cache-Tokens. Dazu SOLL agent-spezifischer Config-Umbau
  (advanced-configuration.md). Ist-Hardcode in `AiProvider` (Anthropic flags, OpenAI-Claude
  cache_control) durch SOLL ersetzt. Umsetzung **nach der Issue-Runde**. Offene Fragen:
  Hardcode-Flatten, Merge-Semantik, Abgleich-Kanal.

# Geschlossen

- **Core-Fix-Kampagne (2026-08-16):** `ThreadSafeMemory`-Load-Pfad doppelte Division gestrichen
  (chars/9 → chars/3, konsistent mit `estimateTokens`); `ChatMessageUtil.toString()`-Workaround
  bleibt im Plugin ([ADR-0030](adr/0030-statictext-helper-frozen-chatmessageutil.md)).
- **Zyklus ADR-0029 (2026-08-16):** File-Context in der History gebaut + grün: po-agent-jon.md
  Marker ✅, EclipseFileContextItem + AgentsMdContextItem → Header-dedupKey, `itemsFor()` mit
  2 Items (R1+R2), Core-Delta `StandingOrdersBuilder.buildItems()` → `List<ContextItem>`,
  R2(a) Live-Status-Hide nach Replay in `doCompressContext`.
