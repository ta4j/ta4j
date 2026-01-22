# AGENTS instructions for org.ta4j.core

- Mirror the builder pattern used by existing `BarSeries` implementations. When introducing a new series type, add a dedicated builder in the same package so callers can opt-in explicitly.
- Extend `BaseBarSeries` for new series implementations to inherit validation logic and to avoid re-implementing removal/index handling.
- Keep concurrent read access safe by returning immutable snapshots from `getBarData()` (e.g., via `List.copyOf(...)`) instead of exposing internal mutable lists.
- Guard mutations with `ReentrantReadWriteLock` write locks and wrap read-mostly methods with the read lock; do not rely on synchronized collections.
- Add `@since <current version sans SNAPSHOT>` to every new public class or method introduced in this package.
