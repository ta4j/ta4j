# Elliott Wave Quickstart

This guide shows the minimal entry points into the Elliott Wave suite. Start with the facade for indicator-style access, or the analyzer for one-shot pipeline analysis.

## Option 1: Indicator-style access (recommended entry point)

Use `ElliottWaveFacade` when you want per-bar indicator values that plug into rules or charts.

```java
BarSeries series = ...;
int index = series.getEndIndex();

ElliottWaveFacade facade = ElliottWaveFacade.fractal(series, 5, ElliottDegree.INTERMEDIATE);

ElliottPhase phase = facade.phase().getValue(index);
ElliottRatio ratio = facade.ratio().getValue(index);
ElliottScenarioSet scenarios = facade.scenarios().getValue(index);
Num invalidation = facade.invalidationLevel().getValue(index);
```

## Option 2: One-shot analysis pipeline

Use `ElliottWaveAnalyzer` when you want a single analysis result (for reporting, charting, or batch processing) and you need to plug in custom swing detectors, filters, or confidence profiles.

```java
BarSeries series = ...;

ElliottWaveAnalyzer analyzer = ElliottWaveAnalyzer.builder()
        .swingDetector(SwingDetectors.fractal(5))
        .degree(ElliottDegree.INTERMEDIATE)
        .build();

ElliottAnalysisResult result = analyzer.analyze(series);
ElliottScenarioSet scenarios = result.scenarios();
ElliottTrendBias trendBias = result.trendBias();
```

## Next steps

- Use `ElliottScenarioIndicator` for multiple interpretations per bar.
- Use `ElliottProjectionIndicator` for targets and `ElliottInvalidationLevelIndicator` for invalidation prices.
- Customize confidence scoring with `ConfidenceProfiles` and `ScenarioTypeConfidenceModel`.
- Explore detectors under `org.ta4j.core.indicators.elliott.swing` for fractal, ZigZag, or adaptive ZigZag swing detection.
