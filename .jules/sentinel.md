## 2026-10-15 - Deserialization Domain Invariant Validation in `readObject`
**Vulnerability:** Standard Java deserialization (`readObject`) bypasses constructors, allowing serialized payloads with negative `volume`/`amount`/`trades` or null timestamps/periods to instantiate invalid domain objects (`BaseBar`).
**Learning:** Adding validation in `readObject` throwing `InvalidObjectException` enforces domain constraints on deserialized objects without impacting valid serialized instances.
**Prevention:** Any `Serializable` class in ta4j implementing `readObject` must validate all constructor domain invariants.
## 2026-10-19 - Java Deserialization Invariant Validation for Serialized BarSeries
**Vulnerability:** `BaseBarSeries.readObject()` re-initialized transient fields like the logger but did not validate deserialized object invariants, allowing corrupted payloads (e.g. null `bars`, `numFactory`, `barBuilderFactory`, or out-of-bounds series indices) to deserialize into invalid object states.
**Learning:** In Java serialization (`ObjectInputStream`), `defaultReadObject()` populates fields directly without invoking constructors or factory validations.
**Prevention:** Always implement a private `validateDeserializedState()` helper in `readObject()` and throw `java.io.InvalidObjectException` if deserialized fields violate structural invariants.
