# Security Policy

ta4j is a Java 21+ library. Most risk appears when it is embedded in larger systems. **JSON strategy/indicator restore** and **Java (de)serialization of bar series** require safe handling of untrusted inputs.

## Reporting a vulnerability

**Do not open a public GitHub Issue for security-sensitive reports.**

- **Preferred:** [Report a vulnerability](https://github.com/ta4j/ta4j/security) (GitHub private reporting).
- **Fallback:** Open a **draft** GitHub Discussion with minimal contact details and ask maintainers to move it to a private channel.

Include: affected module(s) and version(s), Java runtime, minimal reproduction (snippet or test), impact and preconditions, and optional mitigation ideas. A deterministic unit test speeds things up.

## Supported versions

- **Supported:** latest stable release and `master` (snapshot). Security fixes ship from `master` into the next release.
- **Best-effort:** older releases (no release branches; upgrade recommended).
- **Unsupported:** archived or unmaintained forks.

## Scope

**In scope:** unsafe deserialization / gadget chains, DoS or resource exhaustion, data exposure or integrity violations, supply-chain or build issues, vulnerabilities in `ta4j-examples`.

**Out of scope:** purely mathematical bugs with no security impact, trading performance claims, “my bot lost money” unless due to an exploitable integrity issue. Unsure? Report privately; maintainers will triage.

## Guidance for users

- **Do not deserialize untrusted data.** Avoid Java serialization for user-supplied payloads; if you must, use strong input controls and prefer JSON restore with strict validation.
- **Treat strategy/indicator JSON as untrusted:** validate types, bound parameters and bar count, enforce compute limits.
- **Cap compute:** limit max bars per request, avoid unbounded caches keyed by user input, use timeouts around backtests.

## Process

We aim to acknowledge within 7 days, fix on `master` with tests, then release and publish a Security Advisory (and CVE if appropriate). Default target: ≤90 days to public disclosure; critical issues may be accelerated.

GitHub CodeQL, Dependabot, and dependency submission are in use; they are not a substitute for private disclosure of real vulnerabilities.

No bug bounty. We will credit reporters in advisories if you want (tell us your preferred name/handle).
