# Serialization Package — Agent Guide

This document summarizes the architecture, conventions, and pitfalls of Ta4j’s serialization/deserialization system.

## Core Architecture

### Component Types & Schemas

All components use `ComponentDescriptor` but follow different JSON rules:

**Indicators**
- Fields: `type`, `parameters?`, `components?`
- **No labels**, ever
- Child field name: `"components"`

**Rules**
- Fields: `type`, `label`, `parameters?`, `components?`
- **Labels required**
- Child field name: `"components"`

**Strategies**
- Fields: `type`, `label`, `parameters` (includes `unstableBars`), `rules?`
- **Labels required**
- Child field name: `"rules"` (exactly `[entry, exit]`)

### Type Detection Pattern

`ComponentSerialization.getComponentsFieldName()` uses strong type checking with fallback to naming conventions:

1. **Strong type checking (preferred):** Attempts to resolve the type string to a `Class` and checks if it's assignable from `Strategy`, `Indicator`, or `Rule` interfaces using `isAssignableFrom()`.

2. **Naming convention fallback:** If class resolution fails, uses `endsWith()` checks (the library's naming convention):
   ```java
   if (type.endsWith("Strategy")) return "rules";
   if (type.endsWith("Indicator") || type.endsWith("Rule")) return "components";
   ```

The same pattern is used in `isIndicator()` for label serialization decisions.

**Note:** The library follows a naming convention where component types end with their category name (e.g., "OrRule", "RSIIndicator", "BaseStrategy"). The `endsWith()` check aligns with this convention and is more precise than substring matching.

This pattern affects:
- Which field name is used (`components` vs `rules`)
- Whether labels serialize
- How components are matched during deserialization

## Key Files

1. **ComponentDescriptor.java**
   - Core struct (`type`, `label`, `parameters`, `components`)
   - Builder helpers (`addComponent`, `withParameters`, etc.)

2. **ComponentSerialization.java**
   - Handles JSON structure, field naming, label rules

3. **RuleSerialization.java**
   - `describe()` + `fromDescriptor()`
   - Constructor inference
   - Rebuilds indicator children directly from their descriptors; labels are only used for Strategy-level references via `ReconstructionContext`

4. **IndicatorSerialization.java**
   - Same as rules but **no label logic**

5. **StrategySerialization.java**
   - Uses `"rules"` instead of `"components"`
   - Requires exactly two rules

6. **AbstractRule.java**
   - Central rule-name → JSON logic

## Common Refactoring Tasks

### Renaming Fields (e.g., `children` → `components`)
Update:
- `ComponentDescriptor`
- `ComponentSerialization` (constants + field lookup)
- Serialization classes
- `AbstractRule`
- **All test JSON strings**
- Legacy field name support in deserialization

### Removing Metadata (`__args`, etc.)
Requires constructor inference:
- Match nested rules by type (via descriptor)
- Rebuild indicators directly from their descriptors (labels optional, but required for Strategy-level cross references)
- Match named parameters by type/name
- Avoid boolean/number ambiguity

**Important ordering:**
```java
if (boolean) …
else if (number) …
```

## Constructor Inference Overview

1. Inspect all constructors  
2. Attempt to match each constructor against:  
   - Components (rule descriptors or indicator descriptors)  
   - Parameters (by name or type)  
3. All components and parameters must be consumed  
4. Reject ambiguous matches

Pitfall: Descriptors must be self-contained—indicator descriptors need all nested components so they can be reconstructed without relying on Strategy-level context.

## Gotchas & Pitfalls

### 1. Boolean vs Primitive Numbers
`boolean` is a primitive → must check for boolean before numeric primitives.

### 2. Enums
Store enum metadata under `__enumType_*` keys; exclude during constructor matching.

### 3. Indicator Labels
Indicators should never serialize labels in JSON. During reconstruction we first try to resolve by label (for shared Strategy components) and then fall back to rebuilding the indicator directly from its descriptor. No positional matching remains.

### 4. Field Name Differences
- Strategies → `"rules"`
- Others → `"components"`

### 5. Legacy Inputs
Support `"children"` and `"baseIndicators"` for old JSONs.

### 6. Tests
Tests rely on exact JSON string equality.  
Search/replace all occurrences when changing schema.

### 7. Inner Classes
Test helper classes produce confusing FQNs.  
Use simple names within `org.ta4j.core.rules`.

### 8. Derived indicator caches
- Prefer marking derived/cached indicator fields (e.g., helper EMAs) as `transient` so they are skipped automatically by reflection. Persist the logical source indicators instead so descriptors stay faithful to the constructor shape.
- **Avoid `IGNORED_CHILD_INDICATORS` / `IGNORED_CHILD_FIELDS` unless absolutely required.** Those sets mask serialization bugs and should only be touched after exhausting structural fixes (transient fields, refactoring constructors, etc.).
- If you truly need to add an entry to either ignore set, call it out explicitly to the user/reviewer and add a `TODO` explaining why the field is currently ignored and what work is needed to remove the exception in the future.

## Testing

**During development:** Run (from the project root):

```bash
scripts/run-full-build-quiet.sh -pl ta4j-core
```

This keeps the serialization module tight while still emitting the standardized log.

**Before completion:** Rerun the script without `-pl` flags to validate the full reactor (see root [`AGENTS.md`](../../../../../../AGENTS.md) for mandatory completion requirements).

Primary test suites:
- ComponentSerializationTest
- IndicatorSerializationTest
- RuleSerializationTest (includes AndRule-specific tests)
- StrategySerializationTest
- RuleNameTest (sensitive to JSON details)

Typical failures:
- Wrong field names
- Missing labels (rules) or unexpected labels (indicators)
- Mismatched constructor parameters  
- Outdated JSON fixtures

## Refactoring Checklist

- [ ] Update `ComponentDescriptor`
- [ ] Update `ComponentSerialization`
- [ ] Update Indicator/Rule/Strategy serialization
- [ ] Update `AbstractRule`
- [ ] Update all test classes
- [ ] Update all JSON expectations
- [ ] Add legacy field support (if needed)
- [ ] Run full test suite

## Performance Notes

- Constructor inference tries **all** constructors; may be slow for multi-ctor classes.
- Position-based indicator matching can add overhead.
- Legacy field scan adds small cost; remove in future major versions.

## Future Improvements

- Cache constructor inference results
- Provide optional explicit constructor hints in JSON
- Separate schemas per component type instead of a unified descriptor
- Remove legacy `"children"`/`"baseIndicators"` support in a future major

## Debugging Tips

1. Wrong field names → check `ComponentSerialization.serialize()`
2. Deserialization errors → inspect `inferConstructor()`
3. Missing components → check position-based indicator matching
4. Type mismatches → verify `isAssignableFrom()` behavior
5. Test failures → search for outdated JSON strings

## Recent Lessons (2025 rule-name feature branch)

- **Indicator reconstruction:** `RuleSerialization` must rebuild indicators directly from their descriptors. Do **not** reintroduce positional matching or mutable cursor indexes; JSON round-trips often strip labels, so every descriptor must remain self-contained.
- **Context usage:** `ReconstructionContext` should only cache labeled descriptors. Use the parent-context chain when nested rules need Strategy-level components instead of duplicating state.
- **Test consolidation:** `RuleSerializationTest` contains all focused regression cases including AndRule-specific tests (descriptor → descriptor, descriptor → JSON → descriptor, labeled-component reconstruction, and Strategy round-trip). Extend this class for future composite-rule scenarios instead of adding new test files.
- **Debug scaffolding:** Temporary printf-style tests (`AndRuleSerializationDebugTest`, etc.) should stay local and be removed before committing.

## Key Takeaways

1. Behavior is driven by strong type checking (via `isAssignableFrom()`) with fallback to naming convention checks (`endsWith()`)  
2. Indicators never serialize labels; rules and strategies do  
3. `"rules"` vs `"components"` is crucial  
4. Constructor inference is the most fragile part  
5. JSON test strings must always match exactly  
6. Maintain legacy support unless making a breaking change  
