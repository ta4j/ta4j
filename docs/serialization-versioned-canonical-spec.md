# Ta4j Serialization Versioning + Canonical JSON Spec

Status: Draft
Target: ta4j-core (serialization)
Feature branch: codex/serialization-versioned-spec

## Summary
Introduce a versioned, canonical JSON format for ta4j rule/strategy (and indicator) serialization. The change is
additive: new versioned APIs coexist with existing JSON payloads, and legacy (versionless) payloads remain readable.
Canonicalization guarantees deterministic output for persisted payloads and strategy identity comparisons.

## Background
Ta4j already provides `StrategySerialization` and `RuleSerialization`, and CF consumes the JSON for persistence and
resumption. However:
- The JSON is not versioned, so format changes across ta4j releases can break persisted payloads.
- Output is not guaranteed canonical, so equivalent strategies can serialize differently (ordering, numeric
  formatting, reflection order).

This spec makes the JSON safe for long-lived persistence and deterministic comparisons while keeping the current API
usable.

## Goals
- Add a **versioned serialization format** for strategies, rules, and indicators.
- Provide **canonical JSON** with deterministic ordering and stable numeric representation.
- Preserve **backward compatibility**: legacy JSON (no version envelope) still loads.
- Keep changes **additive**, not breaking existing `toJson`/`fromJson` behavior.
- Provide clear error messages for unsupported versions.

## Non-Goals
- No removal of legacy payload support in this iteration.
- No guarantee that v0 payloads are canonical; only v1+ are canonical.
- No requirement to make every rule serializable; unsupported rules still throw.
- No changes to CF-specific strategies (CF will treat CF strategy implementations as deprecated and use ta4j-native
  strategies only).

## Definitions
- **Descriptor**: `ComponentDescriptor` JSON describing a rule/indicator/strategy.
- **Envelope**: Versioned wrapper around a descriptor.
- **Canonical JSON**: Deterministic JSON output for identical logical inputs.

## Requirements

### Functional
1) New versioned APIs:
   - `ComponentSerialization.toVersionedJson(descriptor)` -> v1 envelope JSON.
   - `ComponentSerialization.parseVersioned(json)` -> `{version, descriptor}`.
   - `StrategySerialization.toVersionedJson(strategy)` / `fromVersionedJson(series, json)`.
   - `RuleSerialization.toVersionedJson(rule)` / `fromVersionedJson(series, json)`.

2) Versioned format:
   - Must include explicit `version`.
   - Must contain a descriptor payload.
   - Must be forward-compatible in parsing (unknown fields are ignored).

3) Canonicalization:
   - Deterministic field ordering for descriptors.
   - Deterministic parameter ordering (including metadata keys).
   - Deterministic numeric formatting for `Num` and numeric arrays.
   - Deterministic ordering of derived components when their order is not semantically significant.

4) Legacy support:
   - If JSON is not an envelope, treat it as v0 descriptor payload.
   - Continue to support legacy descriptor fields (`children`, `baseIndicators`).

5) Errors:
   - Unsupported version -> explicit error message with version value.
   - Invalid envelope -> error with location context.

### Non-Functional
- Preserve existing `toJson` output (no breaking change).
- Minimize performance overhead; canonicalization should be O(n log n) or better for typical graphs.

## Proposed Design

### 1) Versioned Envelope
Use an outer wrapper rather than adding fields to `ComponentDescriptor`.

Example (v1):
```
{
  "version": 1,
  "payload": {
    "type": "BaseStrategy",
    "label": "MyStrategy",
    "parameters": { "unstableBars": 3 },
    "rules": [
      { "type": "OverIndicatorRule", "components": [ ... ] },
      { "type": "CrossedDownIndicatorRule", "components": [ ... ] }
    ]
  }
}
```

Envelope fields:
- `version` (int, required)
- `payload` (ComponentDescriptor, required)
- Optional: `kind` (strategy|rule|indicator) for debugging; ignored during parsing

### 2) Canonicalization Rules (v1+)
Canonical JSON is defined as:
- Field order inside a descriptor: `type`, `label`, `parameters`, then `rules/components`.
- Parameter ordering: lexicographic by key; metadata keys prefixed with `__` sort after non-metadata keys.
- Numeric normalization: numeric strings normalized (e.g., no trailing zeros; `Num` string values normalized to the
  same representation already used in `RuleSerializationRoundTripTestSupport`).
- Component ordering:
  - Strategy rules: fixed `[entry, exit]` order.
  - Rule/indicator components: preserve constructor argument order; if a component list is derived from a map/set,
    sort by canonical JSON of each component to stabilize.

Implementation approach:
- Add a canonicalization utility in ta4j-core that:
  - Creates a canonical `ComponentDescriptor` (sorted parameters + stable components).
  - Produces canonical JSON using the existing `ComponentSerialization` adapter.

### 3) API Surface
Additive APIs:
- `ComponentSerialization.toVersionedJson(ComponentDescriptor descriptor)` (v1)
- `ComponentSerialization.parseVersioned(String json)` -> `VersionedDescriptor` (new value object)
- `StrategySerialization.toVersionedJson(Strategy strategy)`
- `StrategySerialization.fromVersionedJson(BarSeries series, String json)`
- `RuleSerialization.toVersionedJson(Rule rule)`
- `RuleSerialization.fromVersionedJson(BarSeries series, String json)`

Keep existing:
- `ComponentSerialization.toJson(...)`
- `StrategySerialization.toJson(...)`
- `StrategySerialization.fromJson(...)`
- `RuleSerialization.describe(...)` / `RuleSerialization.fromDescriptor(...)`

### 4) Backward Compatibility
Parsing rules:
- If the JSON has `version` and `payload`, parse as envelope.
- If it parses as a descriptor, treat it as v0 (legacy).
- If it parses as a plain label string, treat as v0 label-only descriptor.

Migration:
- v0 payloads load through existing `ComponentSerialization.parse(...)`.
- When re-emitted via versioned APIs, v0 payloads become canonical v1 envelopes.

### 5) Error Handling
- Unknown `version` -> `IllegalArgumentException("Unsupported serialization version: X")`
- Missing `payload` -> `IllegalArgumentException("Missing payload for versioned serialization")`
- Payload parse errors -> `IllegalArgumentException("Invalid payload: ...")`

### 6) Documentation
Update ta4j README serialization section to mention:
- v1 envelope format
- new versioned APIs
- canonicalization guarantees

## Tests
Add/extend ta4j-core tests:
- `ComponentSerializationTest`:
  - `parseVersioned()` handles v1 envelope
  - legacy payloads treated as v0
- `RuleSerializationTest`:
  - v1 round-trip: rule -> versioned json -> rule -> versioned json (identical)
  - canonical JSON matches expected ordering
- `StrategySerializationTest`:
  - v1 round-trip for NamedStrategy and composite strategy
  - entry/exit ordering preserved

Use `RuleSerializationRoundTripTestSupport` to normalize expected descriptors where appropriate.

## Rollout Plan
Phase 1 (Additive):
- Introduce new versioned APIs + canonicalization.
- Keep existing APIs unchanged.
- Update README + changelog.

Phase 2 (Optional):
- Encourage consumers to migrate to versioned APIs.
- Consider deprecating legacy-only persistence in consumers.

## Risks and Mitigations
- Risk: ordering changes break tests that assert raw JSON strings.
  - Mitigation: add explicit canonical JSON tests, update fixtures once.
- Risk: canonicalization changes perceived output (even with same semantic strategy).
  - Mitigation: only apply canonicalization in versioned APIs; legacy APIs remain unchanged.
- Risk: extra overhead for canonicalization.
  - Mitigation: only used in versioned APIs or when explicitly requested.

## Open Questions
- Should the envelope include a `kind` field for debugging?
- Should canonicalization sort rule components by label when labels are present?
- Should v1 canonicalization normalize numeric precision beyond `Num.toString()`?

## Acceptance Criteria
- Versioned serialization exists for strategies and rules and passes round-trip tests.
- v1 canonical JSON is deterministic across repeated runs.
- Legacy (versionless) payloads still load.
- README documents the versioned format and API.

