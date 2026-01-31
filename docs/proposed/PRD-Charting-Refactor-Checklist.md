# PRD: Charting Refactor Checklist

## Goals
- Simplify the charting surface area so common workflows read clearly and predictably.
- Reduce conditional branching for time axis behavior (REAL_TIME vs BAR_INDEX).
- Separate dataset creation, axis configuration, renderer styling, and overlay composition.
- Keep public APIs stable while making internal responsibilities easier to test.

## Guiding principles
- Tests first: add or expand unit tests before behavior-heavy refactors.
- Prefer small, isolated refactors over sweeping changes.
- Preserve current chart defaults and rendering behavior.
- Use existing ta4j style conventions (immutability, explicit imports, no reflection in tests).

## Current focus area
- ChartWorkflow, ChartBuilder, TradingChartFactory (charting pipeline and time axis behavior).

## Prioritized refactor opportunities
1. P0 - TradingChartFactory is a god class (dataset creation, axis config, renderer styling, overlays, tooltips).
   Minimal path: extract focused helpers (AxisFactory, DatasetFactory, OverlayRenderer) without changing behavior.
2. P0 - Repeated REAL_TIME vs BAR_INDEX branching across dataset creation, tooltips, and axis labels.
   Minimal path: introduce a TimeAxisModeStrategy interface that supplies axis, x-values, and tooltip formatting.
3. P1 - ChartWorkflow has many overloads with repeated validation.
   Minimal path: shared validation + chart creation helpers, keep current overloads delegating.
4. P1 - ChartBuilder stores timeAxisMode on builder but does not expose it in a dedicated config object.
   Minimal path: introduce ChartDefinitionMetadata or ChartContext that carries timeAxisMode + domain series.
5. P2 - Chart styling (background, anti-alias, title paint) is duplicated across factory methods.
   Minimal path: private applyChartStyling(JFreeChart chart) helper.

## Test coverage gaps
- None noted after latest pass.

## Step-by-step checklist
- [x] Initial refactor scout completed (2026-01-31).
- [x] Add BAR_INDEX dual-axis tooltip/date label tests.
- [x] Add BAR_INDEX analysis criterion overlay test.
- [x] Add BAR_INDEX overlay tooltip generator test.
- [x] Extract TimeAxisModeStrategy and migrate one call site.
- [x] Extract AxisFactory and replace in TradingChartFactory.
- [x] Extract DatasetFactory (TimeSeries vs XYSeries) and update overlays.
- [x] Extract OverlayRendererFactory for overlay renderers and tooltips.
- [x] Route domain value selection through TimeAxisModeStrategy.
- [x] Centralize ChartWorkflow validation/chart creation into helper methods.
- [x] Centralize chart styling in TradingChartFactory.
- [x] Introduce ChartDefinitionMetadata for time axis mode and domain series.
- [x] Route ChartPlan/compose title handling through ChartDefinitionMetadata.
- [x] Introduce ChartContext and use it across chart planning/rendering.
- [x] Document chart metadata/consistent title styling in README.
- [x] Verify full build after each refactor step (latest: 2026-01-31).
