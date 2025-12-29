# ta4jexamples.analysis instructions

- Analysis demo classes in this package should load *ossified* OHLCV datasets committed under `ta4j-examples/src/main/resources` (avoid live HTTP calls in the demo itself).
- When adding a new analysis demo, first stage the dataset locally:
  1. Fetch the desired OHLCV data using an HTTP datasource (prefer `ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource` for crypto examples) with response caching enabled.
  2. Let the datasource paginate as needed (Coinbase is capped at 350 candles/request) and write cached JSON responses under `ta4j-examples/temp/responses`.
  3. Combine/merge the paginated cached files into a single consolidated OHLCV JSON file (deduplicate by candle start time, keep chronological ordering).
  4. Rename the consolidated file to the resource naming convention `{Source}-{TICKER}-{INTERVAL}-{START}_{END}.json` (example: `Coinbase-BTC-USD-PT1D-20230616_20231011.json`) and move it to `ta4j-examples/src/main/resources`.
     - Note: cached response filenames may use `PT24H` for daily candles; resources should use the `PT1D` / `PT4H` / `PT5M` style used by `ta4jexamples.datasources.file.AbstractFileBarSeriesDataSource`.
  5. Update the demo class to initialize its `BarSeries` from the resource JSON (via `ta4jexamples.datasources.JsonFileBarSeriesDataSource`).

