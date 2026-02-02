# Security Policy — ta4j/ta4j

TA4J development and CI track the latest LTS, but the published bytecode baseline moves only when the ecosystem is ready. Ta4j is a **Java 21+** technical analysis library used inside backtests, research pipelines, and (often) automated trading bots. The security posture of a *library* is different from a hosted service: most risks show up when Ta4j is embedded in larger systems (web APIs, multi-tenant research platforms, trading infrastructure, etc.). Ta4j also supports **JSON strategy/indicator restore** and **Java (de)serialization of bar series**, which makes safe handling of untrusted inputs especially important.  
(See Ta4j README for Java 21+ support, JSON restore, and bar series serialization notes.)

---

## 1) Reporting a vulnerability (preferred path)

**Please do not open a public GitHub Issue for security-sensitive reports.**

Use **GitHub’s private vulnerability reporting**:

- Go to: `https://github.com/ta4j/ta4j/security`
- Click **“Report a vulnerability”**

If you cannot use GitHub private reporting, open a **draft** GitHub Discussion with the **minimum** details needed to contact you and ask maintainers to move the report to a private channel. (Avoid posting exploit details in public.)

### What to include (Ta4j-specific)
Include as much of the following as possible:

- **Affected module(s):** `ta4j-core`, `ta4j-examples`, `scripts/`, `release/`, etc.
- **Affected version(s):** e.g. `0.22.1` (or `0.22.2-SNAPSHOT` if applicable)
- **Java runtime:** e.g. Temurin 21 / 22 / 23 / 25
- **Minimal reproduction**:
  - A small code snippet, or
  - A failing JUnit test you can share, or
  - A tiny Maven project that depends on `org.ta4j:ta4j-core`
- **Impact statement:** what can an attacker do (RCE, data leak, DoS, integrity violation, etc.)?
- **Attack preconditions:** does it require untrusted inputs, custom classpath, specific `Num` types, concurrency, etc.?
- **Suggested fix (optional):** patches or mitigation ideas welcome.

> Tip: If you can express the issue as a deterministic unit test, it tends to land fastest.

---

## 2) Supported versions & fix policy

Ta4j follows a **GitHub-flow style**: **only `master` becomes a release**, and there are **no release branches**. Security fixes therefore ship forward from `master` into the next published version. Snapshot builds are produced continuously from `master`.

**Policy:**
- ✅ **Supported:** the latest released line and `master` (snapshot)
- ⚠️ **Best-effort only:** older releases (upgrade strongly recommended)
- ❌ **Unsupported:** archived or unmaintained forks

### Supported versions (at time of writing)
| Version / branch | Supported for security fixes? | Notes |
|---|---:|---|
| `master` (`*-SNAPSHOT`) | ✅ Yes | Fixes land here first; snapshots publish automatically from `master`. |
| Latest stable release (e.g. `0.22.1`) | ✅ Yes | Fixes ship in the next patch/minor release from `master`. |
| Older stable releases (`<= 0.21.x`) | ⚠️ Best-effort | No guaranteed backports (no release branches). Upgrade recommended. |

If you are running Ta4j in production, prefer **latest stable** or **snapshot** only if you can tolerate churn.

---

## 3) Scope: what counts as a security issue in Ta4j

Ta4j is largely compute-focused, but security issues still exist. We consider a report security-relevant if it enables any of the following:

### In scope
1. **Unsafe deserialization / gadget chains**
   - Ta4j’s README describes **Java bar series serialization** and **JSON restore** features.
   - Any vulnerability that allows **unexpected class loading**, **code execution**, or **state injection** through these mechanisms is in scope.
2. **Denial of service (DoS) / resource exhaustion**
   - Unbounded CPU or memory amplification via crafted inputs (e.g., pathological bar series, indicator parameters, or serialization payloads).
3. **Data exposure / integrity violations**
   - Cross-thread leakage, unintended shared state, or concurrency defects that cause one strategy/series to observe or corrupt another.
4. **Supply-chain / build pipeline issues**
   - Malicious dependencies, compromised release artifacts, workflow token leakage, publishing to Maven Central with tampered artifacts, etc.
5. **Vulnerabilities in bundled examples**
   - `ta4j-examples` is often copy/pasted into real systems; security problems there still matter.

### Usually out of scope (but may be accepted as “security-adjacent”)
- Purely mathematical correctness bugs with no security impact
- Trading performance claims (alpha, profitability, etc.)
- “My bot lost money” unless it’s caused by a clearly exploitable integrity issue

If you’re unsure, report it privately anyway—maintainers will triage.

---

## 4) Ta4j-specific security guidance (for users)

### A) Do not deserialize untrusted data
If you accept user-supplied payloads (web apps, APIs, multi-tenant services):

- **Avoid Java serialization for untrusted inputs.** Treat it as unsafe by default.
- If you must deserialize, enforce **strong input controls** (trusted origin only, allowlist classes, separate process/container, etc.).
- Prefer the **JSON restore** APIs with strict validation and schema constraints.

### B) Treat strategy/indicator JSON as untrusted configuration
Strategy definitions can be powerful; in a hosted setting, they are effectively user-provided “programs”. If you expose them:

- Validate allowed indicator/rule types
- Bound parameters (barCount, windows, timeframes)
- Enforce compute limits (timeouts, memory caps, max bar count)

### C) Guard against unbounded compute
Many indicators are O(n) over bar count (or worse if used incorrectly). In untrusted contexts:

- Cap max bars per request
- Cache responsibly (avoid unbounded caches keyed by user-controlled inputs)
- Use timeouts / circuit breakers around backtests

---

## 5) Coordinated disclosure process

We try to follow a coordinated disclosure flow:

1. **Acknowledgement:** we aim to acknowledge within **7 days**.
2. **Triage & severity:** we’ll assess impact and affected versions.
3. **Fix development:** patch on `master`, with tests.
4. **Release:** publish a stable release (or urgent patch release) and/or snapshot guidance.
5. **Advisory:** publish a GitHub Security Advisory (and request a CVE if appropriate).

### Disclosure timing
- Default target is **≤ 90 days** from initial report to public disclosure.
- For actively exploited or critical issues, we may accelerate disclosure and ship a patch sooner.

---

## 6) Security tooling & automation

Ta4j uses GitHub Actions, including:
- **CodeQL** analysis workflow
- **Dependabot** updates
- **Automatic dependency submission** (to help GitHub track Maven deps)

These are not a substitute for disclosure—if you find a real vulnerability, report it privately.

---

## 7) No bug bounty

Ta4j is a community project and does **not** offer a bug bounty program.

We will happily credit reporters in advisories/releases if you want (tell us your preferred name/handle).

---

## 8) Quick report template

Copy/paste into the private vulnerability report:

- **Summary:**
- **Affected module(s):**
- **Affected version(s):**
- **Java runtime:**
- **Impact:**
- **Attack scenario / preconditions:**
- **Reproduction (code / test / project):**
- **Mitigation ideas (optional):**
- **Disclosure preferences (credit / anonymity):**
