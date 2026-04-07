# Indicator Family Analysis Guide

## Purpose

`IndicatorFamilyAnalysisEngine` groups indicators by behavior similarity so confluence selection can avoid redundant indicators.

This guide is for both users and agents running the workflow manually.

## Inputs

- `BarSeries` with representative bars for the instrument/timeframe under study
- `IndicatorFamilyManifest` containing a curated, deterministic indicator subset
- One or more `IndicatorFamilyAnalysisConfig` values (for example absolute vs signed)

## Manual Invocation

```java
BarSeries series = ...;
IndicatorFamilyManifest manifest = ...;

IndicatorFamilyAnalysisConfig absolute = IndicatorFamilyAnalysisConfig.defaultMode("absolute");
IndicatorFamilyAnalysisConfig signed = IndicatorFamilyAnalysisConfig.signedMode("signed");

IndicatorFamilyAnalysisResult result = IndicatorFamilyAnalysisEngine.run(
    series,
    manifest,
    List.of(absolute, signed)
);
```

Single-config runs are supported:

```java
IndicatorFamilyCatalog catalog = IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest, absolute);
```

## Expected Artifacts

- `IndicatorFamilyCatalog` per config
- `catalogId` = deterministic `manifestId|manifestHash|configId`
- `stableIndex` based on manifest indicator warm-up boundary
- `familyByIndicator` mapping and ordered `families`
- `pairSimilarity` map and deterministic `pairwiseFingerprint`
- Optional cross-config drifts in `IndicatorFamilyAnalysisResult.drifts()`

## Selection Workflow

Use ranked indicator candidates with `FamilyAwareIndicatorSelector`:

```java
List<String> selected = FamilyAwareIndicatorSelector.select(
    rankedIndicatorIds,
    catalog,
    3,
    true
);
```

With `enforceFamilyLimit=true`, the selector keeps rank order while rejecting duplicate-family picks when possible.

## Decision Rubric

1. Start with `ABSOLUTE` mode when anti-correlated indicators should count as behaviorally similar.
2. Add `SIGNED` mode when directional separation matters.
3. Compare drifts across configs; large drift implies threshold or mode sensitivity.
4. Prefer one indicator per family for confluence experiments unless a family-level exception is documented.

## Verification

Run these focused tests while iterating:

```bash
mvn -pl ta4j-core test \
  -Dtest=IndicatorSerializationTest,IndicatorFamilyAnalysisEngineTest,IndicatorFamilyManifestTest,FamilyAwareIndicatorSelectorTest,CorrelationCoefficientIndicatorTest \
  -Dspotless.apply=none
```

Run the repository full gate before final handoff:

```bash
scripts/run-full-build-quiet.sh
```
