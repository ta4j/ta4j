# Range, Volume, and Renko Aggregators PRD

## Goal
Add library-grade bar aggregators in `org.ta4j.core.aggregator` for:
- range-based aggregation
- volume-threshold aggregation
- renko brick aggregation

## Requirements
- Provide externally configurable parameters:
  - range box size
  - volume threshold
  - renko box size and reversal amount
- Guard against uneven source intervals
- Reuse existing bar builders where practical
- Add deterministic tests for trending, volatile, and flat series
- Update package/API Javadoc and `CHANGELOG.md`
- Validate via targeted aggregator tests and full build

## Design Notes
- Added reusable `SourceIntervalValidator` for contiguous/even source interval checks.
- Added reusable `AggregatedBarWindow` for threshold-based OHLCV accumulation.
- Implemented:
  - `RangeBarAggregator`
  - `VolumeBarAggregator`
  - `RenkoBarAggregator`
- Used `TimeBarBuilder` for output bar creation to keep behavior consistent with existing bars.

## Checklist
- [x] Add range aggregator
- [x] Add volume aggregator
- [x] Add renko aggregator
- [x] Add fixtures for deterministic source series
- [x] Add tests for trending/volatile/flat behavior
- [x] Add tests for uneven interval guardrails
- [x] Update package-level docs
- [x] Update changelog
- [x] Run `mvn -pl ta4j-core test -Dtest=*AggregatorTest`
- [x] Run `scripts/run-full-build-quiet.sh`
