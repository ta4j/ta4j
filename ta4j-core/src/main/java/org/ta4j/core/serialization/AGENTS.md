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

`ComponentSerialization.getComponentsFieldName()` detects type via substring:

```java
if (type.contains("Strategy")) return "rules";
if (type.contains("Indicator") || type.contains("Rule")) return "components";
```

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
   - Position-based matching when indicators lack labels

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
- Match components/rules by type or position
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
   - Components (by type or position)  
   - Parameters (by name or type)  
3. All components and parameters must be consumed  
4. Reject ambiguous matches

Pitfall: Indicators lack labels → must fall back to position-only matching.

## Gotchas & Pitfalls

### 1. Boolean vs Primitive Numbers
`boolean` is a primitive → must check for boolean before numeric primitives.

### 2. Enums
Store enum metadata under `__enumType_*` keys; exclude during constructor matching.

### 3. Indicator Labels
Indicators should never serialize labels, but internal labels help matching.  
Use position-based matching when missing.

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

## Testing

Always run (from the project root):

```bash
mvn -B clean license:format formatter:format test install -pl ta4j-core
```

Primary test suites:
- ComponentSerializationTest
- IndicatorSerializationTest
- RuleSerializationTest
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

## Key Takeaways

1. Behavior is driven by substring type detection  
2. Indicators never serialize labels; rules and strategies do  
3. `"rules"` vs `"components"` is crucial  
4. Constructor inference is the most fragile part  
5. JSON test strings must always match exactly  
6. Maintain legacy support unless making a breaking change  
