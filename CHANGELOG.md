Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.

## 0.10 (2017-10-23)

### VERY Important note!!!!

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). You have to reorganize all your refernces to the new packages!
In eclipse you can do this easily by selecting your sources and run "Organize imports"
_Changed ownership of the ta4j repository_: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)

### Fixed
- **ParabolicSarIndicator**: wrong calculation fixed
- **KAMAIndicator**: stack overflow bug fixed
- **AroonUpIndicator and AroonDownIndicator**: wrong calculations fixed and can handle NaN values now

### Changed
- **BREAKING**: **new package structure** change eu.verdelhan.ta4j to org.ta4j.ta4j-core
- **new package adx**: new location of AverageDirectionalMovementIndicator and DMI+/DMI-
- **Ownership of the ta4j repository**: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
- **ParabolicSarIndicator**: old constructor removed (there was no need for time frame parameter after big fix). Three new constructors for default and custom parameters.
- **HighestValueIndicator and LowestValueIndicator:** ignore also NaN values if they are at the current index

## Added
- **AroonOscillatorIndicator**: New indicator based on AroonUp/DownIndicator
- **AroonUpIndicator** and **AroonDownIndicator**: New constructor with parameter for custom indicator for min price and max price calculation
- **DirectionalMovementPlusIndicator**: New indicator for Directional Movement System (DMI+)
- **DirectionalMovementDownIndicator**: New indicator for Directional Movement System (DMI-)
- **InSlopeRule**: New Rule that is satisfied if the slope of two indicators are within a boundary
- **IsEqualRule**: New Rule that is satisfied if two indicators are equal
- **AroonUpIndicator** and **AroonDownIndicator**: New constructor with parameter for custom indicator for min price and max price calculation
- **Pivot Point Indicators Package**: New package with Indicators for calculating standard, Fibonacci and DeMark pivot points and reversals
    - **PivotPointIndicator**: New indicator for calculating the standard pivot point
        - **StandardReversalIndicator**: New indicator for calculating the standard reversals (R3,R2,R1,S1,S2,S3)
        - **FibonacciReversalIndicator**: New indicator for calculating the Fibonacci reversals (R3,R2,R1,S1,S2,S3)
    - **DeMarkPivotPointIndicator**: New indicator for calculating the DeMark pivot point
        - **DeMarkReversalIndicator**: new indicator for calculating the DeMark resistance and the DeMark support
- **IsFallingRule**: New Rule that is satisfied if indicator strictly decreases within the timeFrame.
- **IsRisingRule**: New Rule that is satisfied if indicator strictly increases within the timeFrame.
- **IsLowestRule**: New Rule that is satisfied if indicator is the lowest within the timeFrame.
- **IsHighestRule**: New Rule that is satisfied if indicator is the highest within the timeFrame.
- **IsPositiveDivergentRule**: New Rule that is satisfied if two indicators move in opposite directions within the timeFrame.
    - The base indicator is bullish while the other indicator (e.g. price) is bearish.
- **IsNegativeDivergentRule**: New Rule that is satisfied if two indicators move in opposite directions within the timeFrame.
    - The base indicator is bearish while the other indicator (e.g. price) is bullish.

## 0.9 (2017-09-07)

- **BREAKING** drops Java 7 support
- use `java.time` instead of `java.util.Date`

## 0.8 (2016-02-25)
## 0.7 (2015-05-21)
## 0.6 (2015-02-05)
## 0.5 (2014-10-22)
## 0.4 (2014-05-28)
## 0.3 (2014-03-11)
