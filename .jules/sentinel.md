## 2026-10-19 - Java Deserialization Invariant Validation for Serialized BarSeries
**Vulnerability:** `BaseBarSeries.readObject()` re-initialized transient fields like the logger but did not validate deserialized object invariants, allowing corrupted payloads (e.g. null `bars`, `numFactory`, `barBuilderFactory`, or out-of-bounds series indices) to deserialize into invalid object states.
**Learning:** In Java serialization (`ObjectInputStream`), `defaultReadObject()` populates fields directly without invoking constructors or factory validations.
**Prevention:** Always implement a private `validateDeserializedState()` helper in `readObject()` and throw `java.io.InvalidObjectException` if deserialized fields violate structural invariants.
## 2026-10-21 - Path Traversal via Cache File Notes Parameter
**Vulnerability:** Unsanitized `notes` parameter in `AbstractHttpBarSeriesDataSource` cache file path generation allowed path traversal sequences (`..`, `/`, `\`) to write or read cache files outside the configured `responseCacheDir`.
**Learning:** String parameters appended to cache filenames (like optional notes or labels) can act as path traversal vectors if concatenated without sanitization or path boundary validation.
**Prevention:** Sanitize user-supplied filename components with strict allowlists (`[^a-zA-Z0-9-]`) and perform path boundary checks using `resolvedPath.normalize().startsWith(baseDir.normalize())`.
