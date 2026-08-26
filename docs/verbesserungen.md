# Verbesserungen aus opencode

**Status:** 📝 Studie / Vorschläge (2026-08-23) — Top 5 Ideen aus dem Studium von
[opencode](https://github.com/anomalyco/opencode) (`/Users/sterlp/dev/workset/opencode`), fokussiert
auf **Usability** und **built-in AIs**. Jeder Punkt ist ein eigenständiger Kandidat für eine eigene
Story — hier nur die Empfehlung mit Beispiel, ausformulieren erst bei Story-Start.

## Purpose

opencode löst zwei Dinge bemerkenswert gut, die Peon (Eclipse AI Harness) schwer hat:
**(1) First Success ohne Konfiguration** — freie Modelle funktionieren keyless out-of-the-box,
**(2) Katalog statt Release-Zyklus** — neue Modelle kommen ohne Code-Änderung. Diese Seite sammelt
die Top 5 Übernahme-Kandidaten.

---

## 1. Freie/offene LLMs als eigener Provider „Ox Alpha" ⭐ (Empfehlung)

### Wie opencode es macht
Ein eigener Gateway-Provider (`packages/opencode/src/provider/provider.ts:185–207`): wenn kein
API-Key existiert, bleiben nur Modelle mit `cost.input === 0` aktiv und bekommen den Sentinel-Key
`apiKey: "public"` — **keyless Zugang zu Gratis-Modellen**, null Konfiguration. Im `/models`-Dialog
bekommen diese ein **„Free"-Badge** (`tui/src/component/dialog-model.tsx`). Das ist der Grund,
warum ein neuer User in <1 Minute seinen ersten erfolgreichen AI-Turn hat.

### Warum das für Peon wertvoll ist
Peons Default ist Ollama auf `localhost:11434` — gut für lokale Modelle, aber der erste Erfolg
erfordert: Ollama installieren, Modell pullen, warten. Ein Ox-Alpha-Provider mit freien
Cloud-Modellen (z. B. über OpenRouter Free Tier oder einen eigenen kleinen Gateway) gibt den ersten
erfolgreichen Chat **ohne Installation und ohne Key**.

### Wie ich es machen würde (Beispiel)
Passt 1:1 ins geplante Refactoring aus [provider.md](provider.md) (je Provider eine Klasse):

```java
// org.sterl.llmpeon.provider.OxAlphaProvider
public class OxAlphaProvider implements AiProviderComponent {
    static final String PUBLIC_KEY = "public"; // Sentinel wie opencode

    @Override public ChatModel buildModel(LlmConfig cfg) {
        return new OpenAiStreamingChatModel(baseUrl(cfg), PUBLIC_KEY);
    }
    @Override public List<AiModel> listAiModels(LlmConfig cfg) {
        // GET {base}/models → nur Modelle mit cost.input == 0 durchlassen
        return fetchCatalog(cfg).stream()
            .filter(m -> m.isFree())
            .map(AiModelParser::parse).toList();
    }
    @Override public boolean supportsExtraBody() { return true; } // OpenAI-kompatibel

    /** Keyless: ohne Key nur die Free-Modelle — sonst alle. */
    boolean keyless(LlmConfig cfg) { return cfg.apiKey().isBlank(); }
}
```

UI-Seite: im Modell-Combo der Chat-View (`ActionsBarWidget`) ein „Free"-Badge bzw. Gruppierung
„Free" zuerst, wenn der Provider keyless läuft. Preference-Page: Provider „Ox Alpha" erscheint
ohne Pflicht-Felder (URL optional mit Default).

### Übernehmen würde ich
1. **Sentinel-Key-Pattern** — Provider funktioniert keyless mit reduziertem Modellangebot.
2. **Free-Badge / Free-zuerst-Gruppierung** im Modell-Dropdown.
3. **OpenAI-kompatible Basis-Klasse** als gemeinsame Mutter für OPEN_AI/LM_STUDIO/Ox Alpha
   (heute dreimal duplizierte Logik im Enum).
4. **First Success in <1 Minute** als explizites Designziel jeder neuen Provider-Story.

---

## 2. Modell-Katalog statt Hardcoding (models.dev-Ansatz)

**opencode:** Ein remote JSON-Katalog (~100 Provider von models.dev), Disk-Cache mit TTL +
File-Lock, eingebetteter Build-Time-Snapshot für Offline (`packages/core/src/models-dev.ts`).
Neue Modelle = Catalog-Update, kein Release. Per-Provider-Quirks leben in separaten Custom-Loaders.

**Peon heute:** Modell-URLs teils hartkodiert (Mistral, Anthropic ignorieren Custom-URL beim
Listing), jede Modellliste per Provider-Code. Ein neues Modell erfordert nichts — aber eine neue
Provider-Quirk oder -URL schon Code + Release.

**Übernehmen würde ich:** Nur den Mechanismus, nicht die Abhängigkeit von models.dev — ein
optionaler Peon-Katalog (JSON, gleiche Form wie `AiModel`), gecached im Plugin-Metadaten-Ordner,
Fallback auf die eingebaute `listAiModels()`-Logik. Der Katalog liefert Metadaten (Kosten,
Kontext-Limit, Reasoning-Flags), die heute fehlen — z. B. für Auto-Compact-Grenzen und
Free-Badges (Punkt 1). **Abhängigkeit:** baut sinnvoll erst nach dem Provider-Refactoring
([provider.md](provider.md)) auf.

---

## 3. Layered Activation & Onboarding (Zero-Config-Erkennung)

**opencode:** Ein Provider wird aktiv, wenn *irgendeines* davon zutrifft: Env-Var gesetzt,
Credential gespeichert, Config-Eintrag vorhanden (`provider.ts:1559–1602`). Env-Keys gewinnen als
Zero-Config-Pfad. Ohne Verbindung zeigt der Modell-Dialog „Popular Providers" mit Login direkt
inline (`dialog-provider.tsx`). Recent Models werden persistiert und als Default vorgeschlagen
(`state/model.json`, `defaultModel()`).

**Peon heute:** Kein First-Run-Erleben — nur Scaffold-/Jon-Tutorial-Nachrichten. Erkennung „nichts
konfiguriert" gibt es nicht; der User muss die Preference-Page finden. Zuletzt gewählte Modelle
pro Agent werden gehalten, aber nicht global als Recent vorgeschlagen.

**Übernehmen würde ich:**
1. **Env-Var-Erkennung** pro Provider (deklariert in der Provider-Klasse, Punkt-1-Interface:
   `List<String> envVars()`) — `ANTHROPIC_API_KEY` gesetzt → Provider aktiv ohne Preference-Page.
2. **Onboarding-Hint in der Chat-View**: kein aktiver Provider erkannt → Inline-Hinweis mit
   direktem Sprung zur Preference-Page bzw. „Use Ox Alpha free models"-Button (Punkt 1).
3. **Recent-Modelle global** persistieren und beim leeren Agent-Modell vorschlagen.

---

## 4. Sichere Credentials + deklarative Auth-Methoden

**opencode:** Credentials in `auth.json` mit Mode `0600`, drei Typen (`oauth`/`api`/
`wellknown`), CLI `opencode providers login` mit interaktivem Picker. Auth-Methoden sind
**deklarative Daten** (`{type: "oauth"|"api", label, prompts[]}` mit bedingten Prompts,
`provider/auth.ts`) — dieselbe Beschreibung treibt CLI und TUI.

**Peon heute:** API-Keys (und Copilot-OAuth-Token!) liegen im Klartext im
`ScopedPreferenceStore(InstanceScope)` (`LlmPreferenceInitializer.put(PREF_API_KEY, …)`) — lesbar
für jeden Code in der Workspace-Metadata. Copilot Device Flow ist handgebaut
(`CopilotDeviceFlowDialog`).

**Übernehmen würde ich:**
1. **`org.eclipse.equinox.security` Secure Store** für alle Keys/Tokens (höchster Wert, kleinstes
   Risiko) — Preference-Feld zeigt nur Masked-Werte, Actual-Lookup zur Laufzeit.
2. **Deklaratives Auth-Methoden-Schema** in der Provider-Klasse (`authMethods(): List<AuthMethod>`)
   → der Copilot-Device-Flow wird zum ersten generischen Fall statt Sonderlocke; spätere OAuth-
   Provider (Copilot Enterprise, Gateways) kosten fast nichts.

---

## 5. Permissions als Daten mit „Always Allow"-Lernen

**opencode:** Permission-Regeln sind Datensätze `{permission, pattern(wildcard),
action: allow|ask|deny}`, last-match-wins (`permission/index.ts`). „Deny" entfernt Tools komplett
aus der Modell-Sicht (nicht nur Runtime-Ablehnung — spart Token). Bestätigte Patterns werden in
der Session gelernt („always allow") und lösen andere Pending-Requests derselben Regel automatisch.

**Peon heute:** Globales Shell-Confirmation-Combo (3 Modi) auf der Preference-Page; Write-Validator
per Agent ([write-path-validator.md](write-path-validator.md)). Alles oder nichts — kein
Pattern-Matching, kein Lernen.

**Übernehmen würde ich:** Wildcard-Rule-Set pro Tool-Klasse (Shell, Disk-Write, Eclipse-Write) mit
allow/ask/deny + Session-„always allow". Der Write-Validator bleibt als fachliche Prüfung bestehen;
die Permission-Layer kommt als generische Schale darüber. Direkter Usability-Gewinn: weniger
Bestätigungs-Klicks bei gleichem Sicherheitsniveau, und `deny` spart Tokens, weil das Tool gar
erst advertised wird.

---

## Honorable Mentions (nicht Top 5, aber gemerkt)

* **Small Model Pattern** — dediziertes billiges Modell für Neben-Aufgaben (Titel, Summary).
  Peon hat bereits Compact/Search-Slots — konsolidieren statt erweitern.
* **`.well-known`-Auth-Delegation** — self-hosted Gateways definieren ihren eigenen Login.
  Interessant für Enterprise, weit weg vom Eclipse-Plugin-Kontext.
* **Offline-Snapshot des Modell-Katalogs** — nur relevant, wenn Punkt 2 umgesetzt wird.

## Relationship

* [Provider (AiProvider)](provider.md) — Punkte 1, 2, 3 landen im geplanten Provider-Package
* [Advanced Configuration](advanced-configuration.md) / [caching.md](caching.md) — Extra-Body
  betrifft die Provider-Klassen aus Punkt 1
* [Write-Path Validator](write-path-validator.md) — bleibt fachliche Prüfung unter dem
  Permission-Layer aus Punkt 5
