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
- For DTO/model carrier types, prefer immutable shapes: `record` first, then public final fields, then mutable getter/setter models only when required.
- For component metadata JSON, use helpers from `org.ta4j.core.serialization` (`ComponentDescriptor`, `ComponentSerialization`) instead of hand-rolled structures.

## Scoped guides

- Read deeper package guides before editing these areas:
  - `indicators/AGENTS.md`
  - `serialization/AGENTS.md`
  - `strategy/named/AGENTS.md`
  - `bars/AGENTS.md`
