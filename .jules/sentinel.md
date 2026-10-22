## 2026-10-19 - Java Deserialization Invariant Validation for Serialized BarSeries
**Vulnerability:** `BaseBarSeries.readObject()` re-initialized transient fields like the logger but did not validate deserialized object invariants, allowing corrupted payloads (e.g. null `bars`, `numFactory`, `barBuilderFactory`, or out-of-bounds series indices) to deserialize into invalid object states.
**Learning:** In Java serialization (`ObjectInputStream`), `defaultReadObject()` populates fields directly without invoking constructors or factory validations.
**Prevention:** Always implement a private `validateDeserializedState()` helper in `readObject()` and throw `java.io.InvalidObjectException` if deserialized fields violate structural invariants.

## 2026-10-22 - Java Deserialization Invariant Validation for Serialized Position
**Vulnerability:** `Position` implemented `Serializable` without custom `readObject()` deserialization validation. Standard Java deserialization directly populated fields without invoking constructors, allowing invalid or corrupted object states (e.g., null `startingType`, `exit` without `entry`, mismatched `startingType`, identical entry and exit types, or `exit` index before `entry` index) to bypass validations.
**Learning:** Standard Java classes implementing `Serializable` bypass constructors during deserialization. Unlike Java records, standard classes need custom `readObject()` validation.
**Prevention:** Add a `readObject()` method calling `validateDeserializedState()` that throws `java.io.InvalidObjectException` whenever deserialized instance fields violate core domain invariants.
