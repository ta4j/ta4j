# BarSeriesDataSource Refactoring: DataRetrievalClient & DataFormatMapper

## Overview

Refactor `BarSeriesDataSource` implementations to separate data retrieval (I/O) from data parsing/transformation. This will improve maintainability, testability, and allow for better composition and reuse of components.

## Architecture Decision: File Search & Caching Location

**DECISION: File search and caching logic should live in `DataRetrievalClient` (Option A)**

Rationale:
- File search is an I/O concern - the client knows how to find files
- Caching is a retrieval optimization - transparent to the mapper
- Keeps mappers focused solely on parsing/transformation
- Allows caching to work with any client implementation

## Proposed Interfaces

### 1. DataRetrievalClient

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/client/DataRetrievalClient.java`

```java
package ta4jexamples.datasources.client;

import java.io.InputStream;

/**
 * Handles WHERE data comes from (I/O layer).
 * Responsible for retrieving raw data from various sources (files, HTTP, databases, etc.).
 * 
 * Implementations handle:
 * - File system I/O
 * - HTTP requests
 * - Database queries
 * - Caching (via CacheableDataRetrievalClient)
 * - File search patterns (for file-based clients)
 */
public interface DataRetrievalClient {
    /**
     * Retrieves raw data as a string from the specified source.
     * 
     * @param sourceIdentifier - could be filename, URL, query params, resource path, etc.
     * @return raw data string, or null if not found
     * @throws DataRetrievalException if retrieval fails
     */
    String retrieveData(String sourceIdentifier) throws DataRetrievalException;
    
    /**
     * Retrieves raw data as an InputStream.
     * Caller is responsible for closing the stream.
     * 
     * @param sourceIdentifier - source to retrieve from
     * @return InputStream containing raw data, or null if not found
     * @throws DataRetrievalException if retrieval fails
     */
    InputStream retrieveDataStream(String sourceIdentifier) throws DataRetrievalException;
    
    /**
     * Checks if the client can handle the given source identifier.
     * 
     * @param sourceIdentifier - source to check
     * @return true if this client can handle the source
     */
    boolean canHandle(String sourceIdentifier);
}
```

### 2. DataFormatMapper

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/mapper/DataFormatMapper.java`

```java
package ta4jexamples.datasources.mapper;

import org.ta4j.core.BarSeries;

import java.io.InputStream;
import java.time.Duration;

/**
 * Handles HOW to parse/transform data (parsing layer).
 * Responsible for converting raw data strings into BarSeries objects.
 * 
 * Implementations handle:
 * - JSON parsing (Yahoo Finance, Binance, Coinbase formats)
 * - CSV parsing (various CSV formats)
 * - Format-specific transformations
 * - Error handling during parsing
 */
public interface DataFormatMapper {
    /**
     * Maps raw data string to a BarSeries.
     * 
     * @param rawData - the raw data string (JSON, CSV, etc.)
     * @param ticker - ticker symbol (may be needed for series naming)
     * @param interval - bar interval (may be needed for parsing/validation)
     * @return BarSeries or null if parsing fails
     * @throws DataMappingException if mapping fails
     */
    BarSeries mapToBarSeries(String rawData, String ticker, Duration interval) 
        throws DataMappingException;
    
    /**
     * Maps InputStream to BarSeries.
     * Caller is responsible for closing the stream.
     * 
     * @param inputStream - stream containing raw data
     * @param ticker - ticker symbol
     * @param interval - bar interval
     * @return BarSeries or null if parsing fails
     * @throws DataMappingException if mapping fails
     */
    BarSeries mapToBarSeries(InputStream inputStream, String ticker, Duration interval) 
        throws DataMappingException;
    
    /**
     * Returns the format name this mapper handles (e.g., "YahooFinance", "Binance", "CSV", "BitstampCSV").
     * Used for logging and error messages.
     * 
     * @return format name
     */
    String getFormatName();
}
```

### 3. Exception Classes

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/client/DataRetrievalException.java`

```java
package ta4jexamples.datasources.client;

/**
 * Exception thrown when data retrieval fails.
 */
public class DataRetrievalException extends Exception {
    // Standard exception constructors
}
```

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/mapper/DataMappingException.java`

```java
package ta4jexamples.datasources.mapper;

/**
 * Exception thrown when data mapping/parsing fails.
 */
public class DataMappingException extends Exception {
    // Standard exception constructors
}
```

### 4. Supporting Interfaces

#### FileSearchStrategy

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/client/FileSearchStrategy.java`

```java
package ta4jexamples.datasources.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Strategy interface for file search patterns.
 * Handles the logic of finding files based on ticker, interval, and date range.
 * 
 * Different implementations can handle different naming conventions:
 * - StandardPatternFileSearchStrategy: {ticker}-{interval}-{start}_{end}.csv
 * - SourcePrefixFileSearchStrategy: {source}-{ticker}-{interval}-{start}_{end}.csv
 * - ExchangePrefixFileSearchStrategy: {exchange}-{ticker}-{interval}-{start}_{end}.json
 */
public interface FileSearchStrategy {
    /**
     * Searches for files matching the given criteria.
     * 
     * @param ticker - ticker symbol
     * @param interval - bar interval
     * @param start - start date/time
     * @param end - end date/time
     * @return list of matching file paths (as strings), empty if none found
     */
    List<String> searchFiles(String ticker, Duration interval, Instant start, Instant end);
    
    /**
     * Gets the source name prefix (if any) used in file naming.
     * 
     * @return source name prefix, or empty string if none
     */
    String getSourcePrefix();
}
```

#### CacheableDataRetrievalClient

**Location:** `ta4j-examples/src/main/java/ta4jexamples/datasources/client/CacheableDataRetrievalClient.java`

```java
package ta4jexamples.datasources.client;

/**
 * Marker interface for data retrieval clients that support caching.
 * 
 * Implementations should:
 * - Check cache before making actual retrieval
 * - Store successful retrievals in cache
 * - Handle cache invalidation/expiration
 * - Provide cache configuration options
 */
public interface CacheableDataRetrievalClient extends DataRetrievalClient {
    /**
     * Enables or disables caching.
     * 
     * @param enabled - true to enable caching
     */
    void setCachingEnabled(boolean enabled);
    
    /**
     * Checks if caching is enabled.
     * 
     * @return true if caching is enabled
     */
    boolean isCachingEnabled();
    
    /**
     * Clears the cache.
     */
    void clearCache();
}
```

## Implementation Structure

### Directory Structure

```
ta4j-examples/src/main/java/ta4jexamples/datasources/
├── BarSeriesDataSource.java (unchanged - domain interface)
├── client/
│   ├── DataRetrievalClient.java (new)
│   ├── DataRetrievalException.java (new)
│   ├── CacheableDataRetrievalClient.java (new)
│   ├── FileSearchStrategy.java (new)
│   ├── http/
│   │   ├── HttpDataRetrievalClient.java (new)
│   │   └── CachedHttpDataRetrievalClient.java (new - wraps HttpDataRetrievalClient)
│   └── file/
│       ├── FileDataRetrievalClient.java (new)
│       └── StandardPatternFileSearchStrategy.java (new)
│       └── SourcePrefixFileSearchStrategy.java (new)
│       └── ExchangePrefixFileSearchStrategy.java (new)
├── mapper/
│   ├── DataFormatMapper.java (new)
│   ├── DataMappingException.java (new)
│   ├── json/
│   │   ├── YahooFinanceJsonMapper.java (new)
│   │   ├── BinanceJsonMapper.java (new)
│   │   ├── CoinbaseJsonMapper.java (new)
│   │   └── AdaptiveJsonMapper.java (new - handles multiple formats)
│   └── csv/
│       ├── CsvMapper.java (new)
│       └── BitstampCsvMapper.java (new)
├── YahooFinanceBarSeriesDataSource.java (refactored - composes client + mapper)
├── CsvBarSeriesDataSource.java (refactored - composes client + mapper)
├── JsonBarSeriesDataSource.java (refactored - composes client + mapper)
└── BitStampCSVTradesBarSeriesDataSource.java (refactored - composes client + mapper)
```

### Component Responsibilities

#### DataRetrievalClient Implementations

1. **HttpDataRetrievalClient**
   - Handles HTTP requests
   - URL construction
   - Request/response handling
   - Error handling for HTTP errors
   - Does NOT handle caching (that's in CachedHttpDataRetrievalClient)

2. **CachedHttpDataRetrievalClient** (decorator)
   - Wraps HttpDataRetrievalClient
   - Implements CacheableDataRetrievalClient
   - Handles cache checking before requests
   - Handles cache storage after successful requests
   - Handles cache expiration logic
   - Uses existing cache directory structure (`temp/responses`)

3. **FileDataRetrievalClient**
   - Handles file system I/O
   - Uses FileSearchStrategy to find files
   - Reads files from classpath or file system
   - Handles file not found errors
   - Supports both exact file paths and search patterns

4. **FileSearchStrategy Implementations**
   - **StandardPatternFileSearchStrategy**: For generic CSV files (no prefix)
     - Pattern: `{ticker}-{interval}-{start}_{end}.csv`
   - **SourcePrefixFileSearchStrategy**: For source-prefixed files (Bitstamp)
     - Pattern: `{source}-{ticker}-{interval}-{start}_{end}.csv`
   - **ExchangePrefixFileSearchStrategy**: For exchange-prefixed JSON files
     - Pattern: `{exchange}-{ticker}-{interval}-{start}_{end}.json`
     - Supports multiple exchanges (Coinbase, Binance)

#### DataFormatMapper Implementations

1. **YahooFinanceJsonMapper**
   - Parses Yahoo Finance API JSON response format
   - Extracts timestamps, OHLCV data
   - Handles Yahoo Finance specific structure

2. **BinanceJsonMapper**
   - Parses Binance JSON format
   - Handles Binance-specific structure

3. **CoinbaseJsonMapper**
   - Parses Coinbase JSON format
   - Handles Coinbase-specific structure

4. **AdaptiveJsonMapper**
   - Wraps multiple JSON mappers
   - Detects format and delegates to appropriate mapper
   - Used by JsonBarSeriesDataSource

5. **CsvMapper**
   - Generic CSV parser
   - Handles standard CSV format with OHLCV columns

6. **BitstampCsvMapper**
   - Parses Bitstamp trade-level CSV
   - Aggregates trades into OHLCV bars
   - Handles Bitstamp-specific CSV format

## Migration Strategy

### Phase 1: Extract HTTP Client (Low Risk)

**Goal:** Extract HTTP retrieval logic from YahooFinanceBarSeriesDataSource

**Steps:**
1. Create `DataRetrievalClient` interface
2. Create `DataRetrievalException` class
3. Create `HttpDataRetrievalClient` class
   - Move HTTP request logic from `YahooFinanceBarSeriesDataSource.loadSeriesSingleRequest()`
   - Move URL construction logic
   - Keep HttpClientWrapper dependency injection
4. Update `YahooFinanceBarSeriesDataSource` to use `HttpDataRetrievalClient`
   - Inject `HttpDataRetrievalClient` via constructor
   - Replace HTTP calls with `client.retrieveData(url)`
5. Update tests to mock `HttpDataRetrievalClient` instead of `HttpClientWrapper`
6. Run full build and verify Yahoo Finance still works

**Files to Create:**
- `client/DataRetrievalClient.java`
- `client/DataRetrievalException.java`
- `client/http/HttpDataRetrievalClient.java`

**Files to Modify:**
- `YahooFinanceBarSeriesDataSource.java`
- `YahooFinanceBarSeriesDataSourceTest.java`

**Testing:**
- All existing Yahoo Finance tests should pass
- Add unit tests for `HttpDataRetrievalClient` independently

### Phase 2: Extract JSON Mapper (Low Risk)

**Goal:** Extract JSON parsing logic from YahooFinanceBarSeriesDataSource

**Steps:**
1. Create `DataFormatMapper` interface
2. Create `DataMappingException` class
3. Create `YahooFinanceJsonMapper` class
   - Move `parseYahooFinanceResponse()` logic
   - Keep Gson/JsonParser dependencies
4. Update `YahooFinanceBarSeriesDataSource` to use `YahooFinanceJsonMapper`
   - Inject `YahooFinanceJsonMapper` via constructor
   - Replace parsing calls with `mapper.mapToBarSeries(rawData, ticker, interval)`
5. Update tests to test mapper independently
6. Run full build and verify Yahoo Finance still works

**Files to Create:**
- `mapper/DataFormatMapper.java`
- `mapper/DataMappingException.java`
- `mapper/json/YahooFinanceJsonMapper.java`

**Files to Modify:**
- `YahooFinanceBarSeriesDataSource.java`
- `YahooFinanceBarSeriesDataSourceTest.java`

**Testing:**
- All existing Yahoo Finance tests should pass
- Add unit tests for `YahooFinanceJsonMapper` with sample JSON responses

### Phase 3: Extract Caching (Medium Risk)

**Goal:** Move caching logic into a decorator pattern

**Steps:**
1. Create `CacheableDataRetrievalClient` interface
2. Create `CachedHttpDataRetrievalClient` class (decorator)
   - Wraps `HttpDataRetrievalClient`
   - Moves caching logic from `YahooFinanceBarSeriesDataSource`
   - Handles cache file path generation
   - Handles cache validation/expiration
   - Handles cache reading/writing
3. Update `YahooFinanceBarSeriesDataSource` to use `CachedHttpDataRetrievalClient`
   - Remove caching logic from data source
   - Use cached client when caching is enabled
   - Use regular client when caching is disabled
4. Update tests to verify caching behavior
5. Run full build and verify caching still works

**Files to Create:**
- `client/CacheableDataRetrievalClient.java`
- `client/http/CachedHttpDataRetrievalClient.java`

**Files to Modify:**
- `YahooFinanceBarSeriesDataSource.java`
- `YahooFinanceBarSeriesDataSourceTest.java`

**Testing:**
- All existing caching tests should pass
- Add unit tests for `CachedHttpDataRetrievalClient` independently
- Test cache hit/miss scenarios
- Test cache expiration

### Phase 4: Extract File Client (Medium Risk)

**Goal:** Extract file I/O logic from file-based data sources

**Steps:**
1. Create `FileSearchStrategy` interface
2. Create `FileSearchStrategy` implementations:
   - `StandardPatternFileSearchStrategy` (for CsvBarSeriesDataSource)
   - `SourcePrefixFileSearchStrategy` (for BitStampCSVTradesBarSeriesDataSource)
   - `ExchangePrefixFileSearchStrategy` (for JsonBarSeriesDataSource)
3. Create `FileDataRetrievalClient` class
   - Handles file reading from classpath and file system
   - Uses `FileSearchStrategy` to find files
   - Handles file not found scenarios
4. Update file-based data sources to use `FileDataRetrievalClient`
   - `CsvBarSeriesDataSource`: Use `FileDataRetrievalClient` + `StandardPatternFileSearchStrategy`
   - `BitStampCSVTradesBarSeriesDataSource`: Use `FileDataRetrievalClient` + `SourcePrefixFileSearchStrategy`
   - `JsonBarSeriesDataSource`: Use `FileDataRetrievalClient` + `ExchangePrefixFileSearchStrategy`
5. Update tests
6. Run full build and verify all file-based sources work

**Files to Create:**
- `client/FileSearchStrategy.java`
- `client/file/FileDataRetrievalClient.java`
- `client/file/StandardPatternFileSearchStrategy.java`
- `client/file/SourcePrefixFileSearchStrategy.java`
- `client/file/ExchangePrefixFileSearchStrategy.java`

**Files to Modify:**
- `CsvBarSeriesDataSource.java`
- `BitStampCSVTradesBarSeriesDataSource.java`
- `JsonBarSeriesDataSource.java`
- All corresponding test files

**Testing:**
- All existing file-based tests should pass
- Add unit tests for `FileDataRetrievalClient` independently
- Add unit tests for each `FileSearchStrategy` implementation

### Phase 5: Extract CSV/JSON Mappers (Medium Risk)

**Goal:** Extract parsing logic from file-based data sources

**Steps:**
1. Create CSV mapper implementations:
   - `CsvMapper`: Generic CSV parser (for CsvBarSeriesDataSource)
   - `BitstampCsvMapper`: Bitstamp-specific CSV parser with trade aggregation
2. Create JSON mapper implementations:
   - `BinanceJsonMapper`: Binance JSON format
   - `CoinbaseJsonMapper`: Coinbase JSON format
   - `AdaptiveJsonMapper`: Wraps Binance/Coinbase mappers, auto-detects format
3. Update file-based data sources to use mappers:
   - `CsvBarSeriesDataSource`: Use `CsvMapper`
   - `BitStampCSVTradesBarSeriesDataSource`: Use `BitstampCsvMapper`
   - `JsonBarSeriesDataSource`: Use `AdaptiveJsonMapper`
4. Update tests
5. Run full build and verify all sources work

**Files to Create:**
- `mapper/csv/CsvMapper.java`
- `mapper/csv/BitstampCsvMapper.java`
- `mapper/json/BinanceJsonMapper.java`
- `mapper/json/CoinbaseJsonMapper.java`
- `mapper/json/AdaptiveJsonMapper.java`

**Files to Modify:**
- `CsvBarSeriesDataSource.java`
- `BitStampCSVTradesBarSeriesDataSource.java`
- `JsonBarSeriesDataSource.java`
- All corresponding test files

**Testing:**
- All existing tests should pass
- Add unit tests for each mapper independently
- Test with various input formats

## Example Usage After Refactoring

### Yahoo Finance (with caching)

```java
// Build components
HttpDataRetrievalClient httpClient = new HttpDataRetrievalClient(httpClientWrapper);
CachedHttpDataRetrievalClient cachedClient = new CachedHttpDataRetrievalClient(
    httpClient, 
    Paths.get("temp/responses"),
    true  // caching enabled
);
YahooFinanceJsonMapper mapper = new YahooFinanceJsonMapper();

// Compose data source
YahooFinanceBarSeriesDataSource yahoo = new YahooFinanceBarSeriesDataSource(
    cachedClient, 
    mapper
);

// Use as before
BarSeries series = yahoo.loadSeries("AAPL", Duration.ofDays(1), start, end);
```

### CSV (file-based)

```java
// Build components
FileSearchStrategy searchStrategy = new StandardPatternFileSearchStrategy();
FileDataRetrievalClient fileClient = new FileDataRetrievalClient(searchStrategy);
CsvMapper csvMapper = new CsvMapper();

// Compose data source
CsvBarSeriesDataSource csv = new CsvBarSeriesDataSource(fileClient, csvMapper);

// Use as before
BarSeries series = csv.loadSeries("AAPL", Duration.ofDays(1), start, end);
```

### JSON (file-based with exchange detection)

```java
// Build components
FileSearchStrategy searchStrategy = new ExchangePrefixFileSearchStrategy(
    Arrays.asList("Coinbase", "Binance")
);
FileDataRetrievalClient fileClient = new FileDataRetrievalClient(searchStrategy);
BinanceJsonMapper binanceMapper = new BinanceJsonMapper();
CoinbaseJsonMapper coinbaseMapper = new CoinbaseJsonMapper();
AdaptiveJsonMapper adaptiveMapper = new AdaptiveJsonMapper(
    Arrays.asList(binanceMapper, coinbaseMapper)
);

// Compose data source
JsonBarSeriesDataSource json = new JsonBarSeriesDataSource(fileClient, adaptiveMapper);

// Use as before
BarSeries series = json.loadSeries("ETH-USD", Duration.ofDays(1), start, end);
```

## Testing Strategy

### Unit Testing Components Independently

```java
// Test mapper independently
@Test
public void testYahooFinanceJsonMapper() {
    YahooFinanceJsonMapper mapper = new YahooFinanceJsonMapper();
    String testJson = "{...valid Yahoo Finance JSON...}";
    BarSeries series = mapper.mapToBarSeries(testJson, "AAPL", Duration.ofDays(1));
    assertNotNull(series);
    assertEquals("AAPL", series.getName());
    assertTrue(series.getBarCount() > 0);
}

// Test client independently
@Test
public void testHttpDataRetrievalClient() {
    HttpClientWrapper mockHttp = mock(HttpClientWrapper.class);
    when(mockHttp.send(...)).thenReturn(mockResponse);
    
    HttpDataRetrievalClient client = new HttpDataRetrievalClient(mockHttp);
    String data = client.retrieveData("https://api.example.com/data");
    assertNotNull(data);
}

// Test data source with mocked components
@Test
public void testYahooFinanceWithMockedComponents() {
    DataRetrievalClient mockClient = mock(DataRetrievalClient.class);
    DataFormatMapper mockMapper = mock(DataFormatMapper.class);
    
    when(mockClient.retrieveData(anyString())).thenReturn("{...json...}");
    when(mockMapper.mapToBarSeries(anyString(), anyString(), any()))
        .thenReturn(mockBarSeries);
    
    YahooFinanceBarSeriesDataSource source = new YahooFinanceBarSeriesDataSource(
        mockClient, mockMapper);
    BarSeries series = source.loadSeries("AAPL", Duration.ofDays(1), start, end);
    assertNotNull(series);
}
```

## Backward Compatibility

### Maintaining Existing Public API

All existing `BarSeriesDataSource` implementations should maintain their current public constructors and methods. The refactoring should be internal only.

**Example:**
```java
// Existing constructor should still work
YahooFinanceBarSeriesDataSource yahoo = new YahooFinanceBarSeriesDataSource(true);

// Internally, this creates:
// - HttpDataRetrievalClient
// - CachedHttpDataRetrievalClient (if caching enabled)
// - YahooFinanceJsonMapper
// - Composes them together
```

### Migration Path for Users

Users can continue using existing APIs. Advanced users can opt into the new composition model if they want more control.

## Design Decisions Summary

1. **File Search Location**: In `DataRetrievalClient` (specifically `FileDataRetrievalClient` with `FileSearchStrategy`)
2. **Caching Location**: In `DataRetrievalClient` (via `CacheableDataRetrievalClient` decorator)
3. **Error Handling**: Separate exceptions for retrieval vs mapping
4. **Configuration**: Pass via constructors, use builder pattern for complex cases
5. **Backward Compatibility**: Maintain all existing public APIs
6. **Composition**: Data sources compose client + mapper, don't inherit

## Potential Issues & Mitigations

### Issue 1: File Search Complexity
- **Problem**: File search patterns are complex and vary by source
- **Mitigation**: Use `FileSearchStrategy` interface with multiple implementations
- **Location**: `FileDataRetrievalClient` uses strategy pattern

### Issue 2: Caching Complexity
- **Problem**: Caching logic is intertwined with retrieval
- **Mitigation**: Use decorator pattern (`CachedHttpDataRetrievalClient` wraps `HttpDataRetrievalClient`)
- **Location**: Separate class, can be enabled/disabled

### Issue 3: Backward Compatibility
- **Problem**: Changing internal structure might break existing code
- **Mitigation**: Keep all public APIs unchanged, refactor internals only
- **Testing**: Run full build after each phase

### Issue 4: Performance Overhead
- **Problem**: Additional abstraction layers might add overhead
- **Mitigation**: Minimal - just method calls, no extra I/O
- **Validation**: Benchmark before/after if concerned

### Issue 5: Error Handling Complexity
- **Problem**: Different error types from different layers
- **Mitigation**: Use specific exceptions, convert at data source level
- **Location**: `DataRetrievalException` and `DataMappingException`

## Success Criteria

1. ✅ All existing tests pass
2. ✅ No breaking changes to public APIs
3. ✅ Components can be tested independently
4. ✅ Components can be reused across data sources
5. ✅ Code is more maintainable and easier to understand
6. ✅ New data sources can be added more easily

## Notes

- Start with Phase 1 (HTTP client extraction) as it's lowest risk
- Test thoroughly after each phase before proceeding
- Keep existing tests as regression tests
- Add new tests for extracted components
- Document any deviations from this plan as you go

## Future Enhancements (Post-Refactoring)

Once refactoring is complete, consider:
1. Adding new data sources (Alpha Vantage, Polygon.io, etc.) using existing clients/mappers
2. Adding database clients (JDBC, MongoDB, etc.)
3. Adding streaming clients (WebSocket, SSE, etc.)
4. Adding more mapper formats (Parquet, Avro, etc.)
5. Adding request/response interceptors for logging, metrics, etc.

