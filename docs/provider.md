# Provider (AiProvider)

**Status:** 🚧 planned — Slice 1 des Zwei-Slice-Plans aus
[ADR-0033](adr/0033-ox-alpha-openrouter-keyless.md): dieses Refactoring zuerst (mechanisch,
verhaltenstreu), danach [Free Provider „Ox Alpha"](free-provider-ox-alpha.md) als erste neue
Provider-Klasse. Caching als Feature lebt in [caching.md](caching.md).

## Goal

Die Provider-Domäne (Modell-Build, per-Request-Parameter, Modell-Listen, Extra-Body-Fähigkeit)
ist heute ein Enum (`AiProvider`, 10 Konstanten à 3 Methoden + statische Helpers). Sie wird zu
einer eigenen Komponente mit **je einer Klasse pro Provider** — damit neue Provider (zuerst Ox
Alpha) ohne Enum-Wachstum dazukommen und Provider-Quirks gekapselt sind. Repo-Layout: Feature =
Package in `org.sterl.llmpeon.core`, kein neues Bundle.

## Business Rules

### R1 — Eigene Komponente ❌

Neues Package `org.sterl.llmpeon.provider` in core; Tests in core, UI-Anbindung im Plugin bleibt
unverändert (Aufrufstellen nutzen die Factory).

* `providerPackageResolvesViaFactory` — GIVEN alle 9 bekannten Provider-Namen WHEN über die
  Factory aufgelöst THEN liefert jede einen Provider mit `buildModel`/`newRequestParameters`/
  `listAiModels`.

### R2 — Eine Klasse pro Provider ❌

Gemeinsames Interface (`buildModel`, `newRequestParameters`, `listAiModels`,
`supportsExtraBody()`) + eine Klasse pro Provider (`OllamaProvider`, `OpenAiProvider`,
`AnthropicProvider`, …). `AiProvider` bleibt nur Name-Registry/`parse`; Auflösung über eine
Factory. Die statischen Helpers (`applyBase`, `effortFor`, `anthropicThinkingType`,
`openAiOfficialParameters`, `MODEL_TIMEOUT`) wandeln in gemeinsame/freundliche Klassen um.

* `parseKeepsLegacyNamesStable` — GIVEN bestehende Preference-Werte (Enum-Namen inkl. Fallback
  OLLAMA bei Unbekanntem) WHEN geparst THEN dieselbe Provider-Auflösung wie heute.

### R3 — Extra-Body-Fähigkeit pro Provider ❌

`supportsExtraBody()` am Interface; die Merge-Logik selbst ist Feature von [caching.md](caching.md)
(dort R1 + UI-Gate) und wird hier nur als Fähigkeits-Boolean je Klasse geführt — OpenAI-Familie
bereits nachweisbar `true`.

* `supportsExtraBodyPerClass` — GIVEN die 9 Provider-Klassen WHEN `supportsExtraBody()` THEN
  OpenAI-Familie `true`, Anbieter ohne LC4j-Support `false` (Wertetabelle im Test fixiert).

### R4 — Verhaltenstreu (kein Request-Byte-Stream-Change) ❌

Reines Refactoring: bestehende Tests bleiben grün, Requests unverändert — **außer** den bewusst
entfernten Cache-Hardcodes ([caching.md](caching.md) R1). Kein Umbau an `LlmConfig`/`AgentConfig`
(Provider erhalten sie unverändert).

* `requestParametersUnchanged` — GIVEN die Golden-Assertions in `AiProviderRequestParametersTest`
  WHEN vor/nach Refactoring THEN identische Parameter je Provider.
* `modelListingUnchanged` — GIVEN `ModelListingTest`/`ConfiguredModelTest` WHEN nach Refactoring
  THEN grün ohne Anpassung (nur Import-/Fabrik-Pfade).
* `coreChangeVisibleInPlugin` — GIVEN Änderung in core WHEN Plugin/Tests bauen THEN erst nach
  `mvn clean verify` sichtbar (Shell-Build, lt. Root-AGENTS.md).

## Ist

- `org.sterl.llmpeon.ai.AiProvider` (Enum): OLLAMA, OPEN_AI, OPEN_AI_OFFICIAL, LM_STUDIO,
  GOOGLE_GEMINI, MISTRAL, ANTHROPIC, GITHUB_MODELS, GITHUB_COPILOT — je `buildModel` /
  `newRequestParameters` / `listAiModels`; statics: `applyBase`, `effortFor`,
  `anthropicThinkingType`, `openAiOfficialParameters`, `MODEL_TIMEOUT`, `parse`.
- Bestehende Absicherung: `AiProviderRequestParametersTest`, `ModelListingTest`,
  `ConfiguredModelTest`, `LlmConfigTest` (core).
- Extra-body-Fähigkeit heute ad hoc: `customParameters` bei OPEN_AI (Claude `cache_control`),
  LM_STUDIO (`reasoning`); Anthropic-Flags build-time.

## Offene Punkte

- Interface-/Package-Name endgültig fixieren bei der Umsetzung (Vorschlag oben).
- Provider-Unterstützung Extra-Body pro Provider in der aktuellen langchain4j-Version verifizieren
  → Wertetabelle für R3.

## Relationship

- [Free Provider „Ox Alpha"](free-provider-ox-alpha.md) — Slice 2 baut auf diesem Interface auf;
  Reihenfolge fixiert in [ADR-0033](adr/0033-ox-alpha-openrouter-keyless.md)
- [Prompt Caching](caching.md) — Extra-Body-Feature, das die Provider-Domäne erweitert
- [Advanced Configuration](advanced-configuration.md) — per-agent-Config andockt hier an
- [Per-Agent Think Support](per-agent-think.md) — per-request think läuft durch
  `newRequestParameters` (bleibt im Interface)
