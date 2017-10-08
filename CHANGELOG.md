Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.

## Unreleased
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
- **AroonOscillatorIndicator**: New indicator working with AroonUp/DownIndicator
- **DirectionalMovementPlusIndicator**: New indicator for Directional Movement System (DMI+)
- **DirectionalMovementDownIndicator**: New indicator for Directional Movement System (DMI-)
- **InSlopeRule**: New Rule that is satisfied if the slope of two indicators are within a boundary

## 0.9 (2017-09-07)

- **BREAKING** drops Java 7 support
- use `java.time` instead of `java.util.Date`

## 0.8 (2016-02-25)
## 0.7 (2015-05-21)
## 0.6 (2015-02-05)
## 0.5 (2014-10-22)
## 0.4 (2014-05-28)
## 0.3 (2014-03-11)
