# Indicator Family Analysis Guide

## Purpose

`IndicatorFamilyAnalysisEngine` groups indicators by behavior similarity so confluence selection can avoid redundant indicators.
It does not rank whether an indicator is useful by itself; pass in a ranked candidate list from a separate signal or
strategy study, then use the family catalog to avoid counting duplicate evidence twice.

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
- `stableIndex` based on the pairwise correlation warm-up boundary used for scoring
- `familyByIndicator` mapping and ordered `families`
- `pairSimilarity` map and deterministic `pairwiseFingerprint`
- Optional cross-config drifts in `IndicatorFamilyAnalysisResult.drifts()`

## Selection Workflow

Use ranked indicator candidates with the catalog that produced the families:

```java
List<String> selected = catalog.select(
    rankedIndicatorIds,
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
  -Dtest=IndicatorSerializationTest,IndicatorFamilyAnalysisEngineTest,IndicatorFamilyManifestTest,IndicatorFamilyCatalogTest,CorrelationCoefficientIndicatorTest \
  -Dspotless.apply=none
```

Run the repository full gate before final handoff:

```bash
scripts/run-full-build-quiet.sh
```
