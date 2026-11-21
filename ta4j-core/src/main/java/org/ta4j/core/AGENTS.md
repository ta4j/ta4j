# AGENTS instructions for `org.ta4j.core`

Applies to this package unless a deeper `AGENTS.md` overrides it.

## API and implementation conventions

- Mirror existing `BarSeries` builder patterns. New series types should include a dedicated builder in the same package.
- Extend `BaseBarSeries` for new series implementations to reuse validation and index/removal behavior.
- Keep concurrent access safe: guard writes with `ReentrantReadWriteLock`, guard read-mostly paths with read locks, and return immutable snapshots from `getBarData()`.
- Add `@since <current-version-without-SNAPSHOT>` to each new public class or method in this scope.
- Treat Javadoc as part of the API: new public APIs and behavior changes must include clear intent/usage documentation.

## Coding style and model rules

- Use loggers (`LogManager` / `Logger`) instead of `System.out` / `System.err`.
- Prefer imports over fully qualified class names in implementation code.
- Prefer explicit local variable types. Use `var` only when the inferred type is immediately obvious and meaningfully reduces noise.
- When a valid reference `Num` or `BarSeries` already exists, derive the `NumFactory` from that reference instead of creating a parallel default factory.
- For numerical domain code, keep calculations in `Num` and derive constants from the active `NumFactory`; convert to primitive `double` only when the required operation is unavailable in `Num`, and document why at the call site.
- Do not keep generic null/NaN fallback helpers when the call site already guarantees a usable reference; encode the stronger invariant directly at the call site.
- For DTO/model carrier types, prefer immutable shapes: `record` first, then public final fields, then mutable getter/setter models only when required.
- For component metadata JSON, use helpers from `org.ta4j.core.serialization` (`ComponentDescriptor`, `ComponentSerialization`) instead of hand-rolled structures.

## Calculation-time failure contract (MUST)

Constructors, factories, builders and deserialization validate eagerly and throw. Once a component is built,
evaluating it never throws for anything the data can cause.

- `Indicator#getValue` / `calculate`: return `NaN.NaN` when the data defines no value: warm-up, NaN or
  infinite inputs, zero denominators, missing or degenerate history, numerical underflow or overflow. Non-`Num`
  indicators return their documented undefined value (`false`, `Optional.empty()`, an `unstable(...)` state),
  never `null`.
- Pruned bars are not an error: an index below `getBeginIndex()` re-anchors to the first retained bar or value,
  as `BaseBarSeries#getBar` and `CachedIndicator#getValue` do.
- `Rule#isSatisfied`: `false` when an input is NaN or undefined.
- `AnalysisCriterion`, `CostModel`, `PositionRiskModel`, and `Position` / `TradingRecord` profit, cost and
  return getters: the documented neutral value for empty input (for example zero with no positions), otherwise
  `NaN` when the result is undefined.

Calculation-time throws are allowed only for:

1. Caller-contract violations the caller can check before calling, declared with `@throws`: null arguments, a
   negative index or one beyond `getEndIndex()`, an invalid index range, an open position passed to an API that
   requires a closed one.
2. Broken library invariants, which are bugs rather than data (`IllegalStateException`).
3. History that changed during the calculation so no consistent snapshot could be read: retry a bounded number
   of times, then throw `ConcurrentModificationException`, never another exception type. This applies to
   criteria, cost models, analysis curves, backtest or research runs and `Indicator#getValue`. Only an exception
   unwinds every enclosing cache; a returned `NaN`, even one kept out of the indicator's own cache, is cached by
   the indicators built on top of it.

Test for a new case: if the same inputs would always produce it, it is data and returns `NaN`; if the caller
could have prevented it by checking its arguments, throw; if a retry could succeed, it is case 3. A cached `NaN`
must be a deterministic function of the data, because callers and caches treat it as a final answer.

## Core surface-area gate (MUST)

- New top-level classes in `org.ta4j.core` are a last resort.
- Prefer package-private nested helpers or existing extension points before adding new top-level core types.
- Reuse existing shared APIs first (for example shared enums) rather than introducing parallel concepts.
- If adding a new top-level core type is unavoidable, include a short rationale in the active PRD/checklist or PR notes.

## Scoped guides

- Read deeper package guides before editing these areas:
  - `indicators/AGENTS.md`
  - `serialization/AGENTS.md`
  - `strategy/named/AGENTS.md`
  - `bars/AGENTS.md`
