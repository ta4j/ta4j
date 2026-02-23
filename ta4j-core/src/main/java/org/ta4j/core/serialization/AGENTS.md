# AGENTS instructions for `org.ta4j.core.serialization`

Apply this guide when changing component descriptor schemas, JSON serialization, or reconstruction logic.

## Schema invariants (MUST preserve)

- **Indicators**: `type`, optional `parameters`, optional `components`; indicators never serialize labels.
- **Rules**: `type`, required `label`, optional `parameters`, optional `components`.
- **Strategies**: `type`, required `label`, required `parameters` (includes `unstableBars`), optional `rules`.
- Child field names are strict:
  - Strategies use `rules` (entry/exit pair).
  - Indicators and rules use `components`.

## Type detection and field routing

- `ComponentSerialization.getComponentsFieldName()` should prefer strong type checks (`Class` + `isAssignableFrom`).
- Keep naming-convention fallback for unresolved classes:
  - `*Strategy` -> `rules`
  - `*Indicator` / `*Rule` -> `components`
- The same decision path controls label emission and reconstruction expectations; keep behavior aligned across methods.

## Constructor inference and reconstruction

- Constructor matching must consume every descriptor component and every JSON parameter without ambiguity.
- Check boolean parameters before numeric primitives to avoid primitive coercion mistakes.
- Preserve enum metadata keys (`__enumType_*`) and exclude them from constructor argument matching.
- Rule reconstruction should rebuild indicator children directly from indicator descriptors; do not rely on positional matching.
- `ReconstructionContext` should cache labeled descriptors for strategy-level cross references and use parent-context lookup for nested rules.

## Serialization guardrails

- Prefer `transient` on derived indicator caches instead of adding fields to ignore lists.
- Avoid modifying `IGNORED_CHILD_INDICATORS` / `IGNORED_CHILD_FIELDS` unless there is no structural alternative.
- If an ignore-list entry is unavoidable, document it explicitly with a `TODO` describing how to remove it.
- Keep legacy input support (`children`, `baseIndicators`) unless intentionally introducing a breaking change.

## Refactoring checklist

- Update `ComponentDescriptor` and `ComponentSerialization` together.
- Update indicator/rule/strategy serializers plus `AbstractRule` name handling.
- Update all affected JSON fixtures and expectations in tests.
- Add compatibility handling when schema keys change.

## Testing expectations

During development you may run:

- `scripts/run-full-build-quiet.sh -pl ta4j-core`

Before completion, run the full repo gate from root:

- `scripts/run-full-build-quiet.sh`

Primary regression suites:

- `ComponentSerializationTest`
- `IndicatorSerializationTest`
- `RuleSerializationTest`
- `StrategySerializationTest`
- `RuleNameTest`

When updating round-trip assertions, prefer canonical descriptor comparisons via `RuleSerializationRoundTripTestSupport` instead of brittle raw JSON string equality where canonicalization exists.

## Common failure triage

- Wrong `rules` vs `components` key -> check `ComponentSerialization` routing.
- Missing or extra labels -> verify indicator label suppression vs rule/strategy label requirements.
- Constructor mismatch -> inspect inference consumption order and primitive/enum handling.
- Regression tests failing on stale fixtures -> search/update all JSON literals touched by schema changes.
