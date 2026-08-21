# Prompt Caching

**Status:** 🚧 in design (2026-08-21) — Designprinzip fest: **per-agent JSON extra body**;
Implementierung Backlog (nach der Issue-Runde), zusammen mit dem agent-spezifischen Config-Umbau
(→ [Advanced Configuration](advanced-configuration.md)).

## Purpose

Provider-Prompt-Caching (KV-Cache: stabiler Präfix = System-Prompt/Tools wird gecacht) wird
**pro Agent vom User konfigurierbar** — statt Provider-spezifischem Hardcode im Code.
Mechanik: ein **JSON extra body** je Agent, der in den LLM-Request-Body gemerged wird.
Gilt für jeden Agenten, bei dem ein Modell eingestellt werden kann (Built-in + Custom).

## SOLL (2026-08-21, User-Entscheidung)

- **R1 ❌ Per-agent "extra body" (JSON):** In den Advanced Settings kann **jeder Agent — auch
  Custom Agents** — ein JSON-Snippet definieren, das in den Request-Body gemerged wird.
  Caching ist ein Anwendungsfall dieser Mechanik (GPT-/Claude-Snippets), kein Built-in-Flag.
- **R2 ❌ Config-UI mit Beispielen:** Unter dem JSON-Input werden die **GPT- und Claude-Beispiele
  fürs Caching** angezeigt (copybar, ohne dass sie gesetzt sind).
- **R3 ❌ Geltungsbereich:** alle Agenten mit Modell-Slot (base/plan/search/compact/PO + Custom).
- **R4 ❌ Abgleich:** verifizieren, dass der Cache greift — Cache-Tokens aus der Usage-Antwort
  (`cache_read`/`cache_creation`) auslesen und im UI melden (an [token-usage](token-usage.md)
  andocken).
- **R5 ❌ Lokal-Provider / "no cache" (llama.cpp):** begrenzter Cache-Slots — ein Agent, der
  **nicht** cachen soll (z. B. Compact-Agent mit langem, statischem Kontext), bekommt einfach
  **kein Caching-Snippet** konfiguriert → belegt keinen Slot. (Ersetzt das frühere
  "per-agent no-cache-Flag"-Design: Absence = no cache.)

## Ist (gebaut 2026-08-21, durch SOLL ersetzt)

- `ANTHROPIC`: `cacheSystemMessages(true)` + `cacheTools(true)` beim Build (`AiProvider.buildModel`).
- `OPEN_AI` (Claude über OpenAI-kompatiblen Endpoint): `cache_control: ephemeral` als
  Custom-Parameter, wenn das Modell mit `claude` startet.
- Code-TODOs: per-Agent-`prompt_cache_key` (`OPEN_AI` GPT + `openAiOfficialParameters`) —
  im neuen Design legt der User den Key selbst im JSON extra body fest.

## Open Questions

1. **Hardcode-Flatten:** bleiben die bestehenden Anthropic-Cache-Flags als Default, oder läuft
   **alles** über das JSON extra body (Code neutral, Beispiele nur in der UI)? — Empfehlung:
   alles extra body; das Hardcode wird zu UI-Beispielen.
2. **Merge-Semantik:** deep merge mit Schutz für `model`/`messages` (Agent-Snippet darf diese
   Felder nicht überschreiben) oder offener Merge? — Empfehlung: Schutz + Doku.
3. **Abgleich-Kanal:** nur Debug-Log oder permanent im Token-Header (↑/↓ + cache-reads)?

## BDD (Entwürfe)

```
GIVEN ein Agent hat ein extra-body-JSON konfiguriert (Claude-Cache-Beispiel)
WHEN der Agent einen Call macht
THEN das JSON-Snippet ist in den Request-Body gemerged
AND der Request enthält die Cache-Marker

GIVEN ein Lokal-Provider (llama.cpp) mit begrenzten Cache-Slots
AND der Compact-Agent hat KEIN Caching-Snippet konfiguriert
WHEN der Compact-Agent einen Call macht
THEN keine Cache-Flags im Request — kein Slot belegt
AND andere Agenten mit Caching-Snippet bekommen den Slot

GIVEN der Cache greift (Provider meldet cache-reads)
WHEN der Abgleich die Usage-Antwort prüft
THEN die Cache-Tokens werden im UI gemeldet
```

## Relationship

- [Advanced Configuration](advanced-configuration.md) — agent-spezifischer Config-Umbau (SOLL)
- [Session Token Usage](token-usage.md) — Reporting-Anker für R4
- [Per-Agent Think Support](per-agent-think.md) — Muster: per-Agent-Request-Werte über
  `newRequestParameters`
- [ADR-0031](adr/0031-static-context-env-plus-memory.md) — stabiler System-Prompt-Präfix;
  Re-Bake bei Config-Change bricht den Provider-Cache einmal
