## 2026-10-15 - Deserialization Domain Invariant Validation in `readObject`
**Vulnerability:** Standard Java deserialization (`readObject`) bypasses constructors, allowing serialized payloads with negative `volume`/`amount`/`trades` or null timestamps/periods to instantiate invalid domain objects (`BaseBar`).
**Learning:** Adding validation in `readObject` throwing `InvalidObjectException` enforces domain constraints on deserialized objects without impacting valid serialized instances.
**Prevention:** Any `Serializable` class in ta4j implementing `readObject` must validate all constructor domain invariants.
