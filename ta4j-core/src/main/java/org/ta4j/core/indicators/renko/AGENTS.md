# Renko indicator conventions

- Reuse the shared `RenkoCounter` helper for all Renko-derived indicators so stateful brick calculations remain consistent.
- Constructors should validate that `pointSize` is strictly positive and any brick thresholds are at least one.
- Provide a convenience constructor with sensible defaults (1 brick for directional indicators, 3 bricks for exhaustion checks) in addition to the fully parameterised variant.
- Document the relationship between price moves and brick counts in the class-level Javadoc, linking to an authoritative Renko reference.
- Add `@since` tags to any new public classes or methods introduced in this package (use current version, omit -SNAPSHOT if applicable).
