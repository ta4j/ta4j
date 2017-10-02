Changelog for `ta4j`, roughly following [keepachangelog.com](http://keepachangelog.com/en/1.0.0/) from version 0.9 onwards.

## Unreleased

### VERY Important note!!!!

with the release 0.10 we have changed the previous java package definition to org.ta4j or to be more specific to org.ta4j.core (the new organisation). YOu have to reorganize all your refernces to the new packages!
In eclipse you can do this easily by selecting your sources and run "Organize imports"

### Added
- _InSlopeRule_: rule for slopes between two values of same indicator

### Fixed
- _ParabolicSarIndicator_: wrong calculation fixed
- _KAMAIndicator_: stack overflow bug fixed
### Changed
- _Ownership of the ta4j repository_: from mdeverdelhan/ta4j (stopped the maintenance) to ta4j/ta4j (new organization)
- _ParabolicSarIndicator_: old constructor removed (there was no need for time frame parameter after big fix). Three new constructors for default and custom parameters.

## 0.9 (2017-09-07)

- **BREAKING** drops Java 7 support
- use `java.time` instead of `java.util.Date`

## 0.8 (2016-02-25)
## 0.7 (2015-05-21)
## 0.6 (2015-02-05)
## 0.5 (2014-10-22)
## 0.4 (2014-05-28)
## 0.3 (2014-03-11)
