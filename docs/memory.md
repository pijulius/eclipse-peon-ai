# Session-Stand (2026-09-13 — Release-Gate ui-config abgeschlossen, User macht Commit+Merge)

## Zyklus ui-config Delta — ✅ KOMPLETT (User macht finalen Commit + Merge)

Branch `core-cleanup-2026-09-11` @ `d643677`. Inc-1..5 committed, Da Dok-Reviews abgearbeitet,
Suite 189/0/0. Plan archiviert (planImplemented). **User übernimmt:** Commit der eigenen
WIP-Dateien + Merge (User-Entscheid).

**User-WIP im Working Tree (NICHT anfassen, User committet selbst):** `PoDelegateTool.java`
(6 Zeilen, im Editor offen), `docs/index.md`, `docs/memory.md` (diese Datei), untracked
`peon-plan/*`.

## Release-Cleanup — alle 3 Da-Dok-Befunde (§14) abgearbeitet

1. **C-5** Debug-Gate `AIChatView.handleDoneChatResponse:595-597` — re-enable verifiziert (User).
2. **Dead-Code-Sweep** — `d7a41d3` (5 Dateien): `ThreadSafeMemory.count` (0 Refs verifiziert),
   Import `AiCompressorAgentTest:18`, `sm` `AiDeveloperAgentTest:221`, Javadoc `AiAgent.compact`,
   stale Kommentar `AIChatView:498-499`. 189/0/0.
3. **Doc-Sync** — `d643677`: `context-message-concept.md` „Compact-Result genau einmal" ✅
   (`1f2d0b0`/`ce3483d`), R-ST4 Body-Marker ✅ (`a89cdc6`), Resume-Quote → IST
   `Session compacted:` (AbstractAgent.compact:290 — kein „Resume the task" im Code).
   AGENTS-DEV Zeilenref `:164-165`.

## Backlog (nicht release-blockend, nächster Zyklus)

- **Compact-Slot-Bug:** `ConfiguredChatModel.callBlocking` nutzt `getChatModel()` = BASE —
  COMPACT-Slot nur Anzeige-Name, nicht der tatsächliche Call. Fix-Idee: `AiCompressorAgent`
  muss das Compact-Modell für den HTTP-Call auflösen. User-WIP-Area, erst nach Release.
- **ApiRetry-Cancel-Bug** (memory #21) — Da Thinka-Calls während Retry gecancelt.
- **Compact-Delay ~4-5s** (provider-side Verdacht: onCompleteResponse spät → StreamingBridge-Poll
  in 1,5s-Quanta) — verify via Done-Zeilen-„(Xs)".
- R-A3 Copilot-Studie · Docs-Hygiene-Sweep (Scope-Confirm) · Stale-Guard-Follow-up-Test.
- Shell-Tool read-only für Review-Agent (open-points.md — Da Dok hat kein Git, Umweg über
  Diff-File bewährt sich als Pattern).
- **Branch `release-2026-09-06`** (3 Commits, von main, ungemerged) — Merge = User-Entscheid.
