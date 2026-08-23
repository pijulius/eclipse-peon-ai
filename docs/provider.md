# Provider (AiProvider)

**Status:** 🚧 in design (2026-08-21) — Refactoring-SOLL: `AiProvider` wird eine eigene
Komponente mit je einer Klasse pro Provider; diese Seite ist die Provider-Doku (Architektur,
Fähigkeiten). Caching als Feature lebt in [caching.md](caching.md).

## Purpose

Die Provider-Domäne (Modell-Build, per-Request-Parameter, Modell-Listen, Extra-Body-Fähigkeit)
ist heute ein Enum (`AiProvider`) mit 10 Konstanten à 3 Methoden + statischen Helpers. Mit dem
Extra-Body-Feature (caching.md) wächst sie — sie wird eine eigene Komponente mit je einer
Klasse pro Provider (Repo-Layout: Feature = **Package** über die drei fixen OSGi-Bundles, kein
neues Bundle).

## SOLL

- **P1 ❌ Eigene Komponente:** neues Package in `org.sterl.llmpeon.core` (z. B.
  `org.sterl.llmpeon.provider`); Tests in core, UI-Anbindung im Plugin.
- **P2 ❌ Je Provider eine Klasse:** gemeinsames Interface
  (`buildModel`, `newRequestParameters`, `listAiModels`, `supportsExtraBody()`) + eine Klasse
  pro Provider (`OllamaProvider`, `OpenAiProvider`, `AnthropicProvider`, …). `AiProvider` bleibt
  (falls) nur als Name-Registry/`parse`, Auflösung über eine Factory.
- **P3 ❌ Extra-Body-Fähigkeit pro Provider:** `supportsExtraBody()`-Boolean + die Merge-Logik
  (caching.md R1, Merge-Semantik siehe dort) gehört **je Provider-Klasse** — nur dort, wo
  langchain4j extra-body-Parameter (`customParameters` o.ä.) kann, wird es geboten (UI-Gate in
  caching.md).
- **P4 ❌ Verhaltenstreu:** reines Refactoring — bestehende Provider-Tests bleiben grün,
  Request-Byte-Stream unverändert (außer den bewusst entfernten Cache-Hardcodes, caching.md R1).

## Ist

- `org.sterl.llmpeon.ai.AiProvider` (Enum): OLLAMA, OPEN_AI, OPEN_AI_OFFICIAL, LM_STUDIO,
  GOOGLE_GEMINI, MISTRAL, ANTHROPIC, GITHUB_MODELS, GITHUB_COPILOT — je `buildModel` /
  `newRequestParameters` / `listAiModels`; statics: `applyBase`, `effortFor`,
  `anthropicThinkingType`, `openAiOfficialParameters`, `MODEL_TIMEOUT`, `parse`.
- Extra-body-Fähigkeit heute ad hoc: `customParameters` bei OPEN_AI (Claude `cache_control`),
  LM_STUDIO (`reasoning`); Anthropic-Flags build-time.

## Offene Punkte

- Provider-Unterstützung Extra-Body: pro Provider in der aktuellen langchain4j-Version
  verifizieren → `supportsExtraBody()` befüllen (OpenAI-Familie bereits nachweisbar).
- Interface-Name / Package-Name endgültig fixieren bei der Umsetzung.

## Relationship

- [Prompt Caching](caching.md) — Extra-Body-Feature, das die Provider-Domäne erweitert
- [Advanced Configuration](advanced-configuration.md) — per-agent-Config andockt hier an
- [Per-Agent Think Support](per-agent-think.md) — per-request think läuft durch
  `newRequestParameters` (bleibt im Interface)
