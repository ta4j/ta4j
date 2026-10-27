## 2026-10-15 - Deserialization Domain Invariant Validation in `readObject`
**Vulnerability:** Standard Java deserialization (`readObject`) bypasses constructors, allowing serialized payloads with negative `volume`/`amount`/`trades` or null timestamps/periods to instantiate invalid domain objects (`BaseBar`).
**Learning:** Adding validation in `readObject` throwing `InvalidObjectException` enforces domain constraints on deserialized objects without impacting valid serialized instances.
**Prevention:** Any `Serializable` class in ta4j implementing `readObject` must validate all constructor domain invariants.
## 2026-10-19 - Java Deserialization Invariant Validation for Serialized BarSeries
**Vulnerability:** `BaseBarSeries.readObject()` re-initialized transient fields like the logger but did not validate deserialized object invariants, allowing corrupted payloads (e.g. null `bars`, `numFactory`, `barBuilderFactory`, or out-of-bounds series indices) to deserialize into invalid object states.
**Learning:** In Java serialization (`ObjectInputStream`), `defaultReadObject()` populates fields directly without invoking constructors or factory validations.
**Prevention:** Always implement a private `validateDeserializedState()` helper in `readObject()` and throw `java.io.InvalidObjectException` if deserialized fields violate structural invariants.
## 2026-10-21 - Path Traversal via Cache File Notes Parameter
**Vulnerability:** Unsanitized `notes` parameter in `AbstractHttpBarSeriesDataSource` cache file path generation allowed path traversal sequences (`..`, `/`, `\`) to write or read cache files outside the configured `responseCacheDir`.
**Learning:** String parameters appended to cache filenames (like optional notes or labels) can act as path traversal vectors if concatenated without sanitization or path boundary validation. Replacing disallowed characters with a placeholder stops the traversal but lets distinct inputs collide on one cache file (`run/a` and `run a` both became `run_a`), so one run could be served another run's cached data.
**Prevention:** Encode user-supplied filename components injectively instead of replacing characters: percent-encode every byte outside a lowercase allowlist (`[a-z0-9_-]`; `%`, `.`, uppercase letters and path separators are always escaped, so names also stay distinct on case-insensitive file systems), and still perform path boundary checks using `resolvedPath.normalize().startsWith(baseDir.normalize())`.

## 2026-10-27 - Deserialization Domain Invariant Validation in `BaseTradingRecord`
**Vulnerability:** `BaseTradingRecord.readObject()` and nested `PositionBook.readObject()` re-initialized transient lock and cost models but did not validate structural invariants, allowing corrupted payloads with null `startingType`, `matchPolicy`, or `positionBook`, negative `nextTradeIndex`/`nextSequence`, or inverted `startIndex`/`endIndex` to instantiate invalid objects.
**Learning:** In Java serialization, `defaultReadObject()` sets fields directly without constructor checks. For composite state objects with internal ledgers (like `PositionBook`), deserialization checks must validate both the outer container and inner state objects.
**Prevention:** Always validate non-null requirements, index bounds, and nested container state in `readObject()`, throwing `java.io.InvalidObjectException` when invariants are violated.
