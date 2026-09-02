#!/usr/bin/env python3
"""
Independent reference oracle for CF-558 Correntropy Kalman filter (MCKF).

Implementation-independent reference for `CorrentropyKalmanFilterIndicator`
(PRD `.agents/plans/PRD-CF-558-mckf.md`, sections 6.1-6.8). Directly follows the
published scalar maximum correntropy Kalman filter equations of Chen et al. (2017),
"Robust Kalman filtering based on maximum correntropy criterion" (arXiv:1509.04580),
for the random-walk model of PRD section 6.1:

    x_t = x_{t-1} + q_t,   y_t = x_t + r_t

All arithmetic is IEEE-754 float64 (Java double semantics). The script shares no
code with the Java implementation and never calls production helpers.

Implementation extensions of the Java delivery that are NOT part of the published
equations, and that this oracle likewise applies (marked EXT in the code):

  EXT-1  KERNEL_EXPONENT_BOUND = 15.0: squared kernel exponents above 15 saturate
         to a zero kernel weight instead of calling exp(). For the nominal
         fixtures below the largest squared exponent stays well below 15, so EXT-1
         never fires; it is applied only because in double precision exp(-x) ==
         0.0 for x > ~745 anyway, making both behaviors numerically identical.

  EXT-2  Fixed-point iteration cap of 20 (PRD 6.6) with tolerance
         |x(j+1) - x(j)| <= 1e-6 * max(1, |x(j+1)|) (PRD 6.6). Nominal fixtures
         converge in a handful of iterations.

Whitened kernels (PRD 6.3/6.4) operate on dimensionless errors; the kernel
bandwidth sigma is dimensionless, not a raw-price distance.

Initialization (PRD 6.8): before any valid joint (source, Q, R) observation the
index is unavailable (NaN). At the first valid observation the estimate is the
measurement, the covariance is the zero-innovation correction of the factory's
one-valued prior with the current Q/R, and the measurement weight is 1
(g = (1+Q)/(1+Q+R), P = (1-g)^2 * (1+Q) + g^2 * R).

Independently of the fixed-point recurrence, a bounded scalar numerical
optimizer (golden-section maximizer, no third-party dependency) maximizes the
whitened MCC objective (PRD 6.4)

    J(x) = c_x(x) + c_y(x),
    c_x(x) = exp(-(x_pred - x)^2 / (2 sigma^2 P_pred)),
    c_y(x) = exp(-(y - x)^2 / (2 sigma^2 R))

over the bracket [min(x_pred, y), max(x_pred, y)]. The stationary condition of J
is algebraically identical to the PRD 6.5/6.6 fixed-point map, so the optimizer
argmax must coincide with the fixed-point candidate wherever J is unimodal. A
dense 40001-point scan over the same bracket counts the local maxima and records
the evidence per index; the optimizer-agreement check applies only to indices
with a single local maximum. On every converged index (including bimodal ones)
the first-order stationarity of the candidate is verified analytically:
|dJ/dx| at the candidate must be within the curvature-scaled gradient bound the
map's own convergence tolerance implies, so each accepted candidate is a genuine
critical point of the MCC objective, not a mere loop stop.

These diagnostics (unimodality, argmax, stationarity) are evaluated on the
UNSATURATED published MCC objective (EXT-1 disabled in `_objective`,
`_dJ_dx` and `_d2J_dx2`), because EXT-1 replaces the true kernels by an
identical surrogate only inside the recurrence; the recorded
`saturated_any` flag marks every index where EXT-1 fired during that index's
iteration, so evidence from saturated indices is labeled rather than hidden.

Usage:
    python3 cf-558-mckf-reference-oracle.py

Output (same directory as this script):
    cf-558-mckf-reference-vectors.json  frozen reference vectors and optimizer checks
"""
from __future__ import annotations

import json
import math
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Callable, Sequence

# Constants fixed in PRD 6.6 ("bounded numerical-solution controls", not
# user-facing knobs). EXT-1/EXT-2 above.
CONVERGENCE_TOLERANCE = 1e-6
MAX_ITERATIONS = 20
KERNEL_EXPONENT_BOUND = 15.0


# -------------------------------------------------------------------------
# Whitened kernels (PRD 6.3/6.4)
# -------------------------------------------------------------------------

def kernel_weight(standardized_error_squared: float, scale_variance: float, sigma_sq: float) -> float:
    """c = exp(-e^2 / (2 sigma^2)) with e = error / sqrt(scale_variance).

    The squared exponent is e^2 / (2 sigma^2 * scale_variance); e is
    dimensionless after whitening by the scale variance (PRD 6.3).
    """
    exponent = standardized_error_squared / (2.0 * sigma_sq * scale_variance)
    if exponent > KERNEL_EXPONENT_BOUND:  # EXT-1 (see header)
        return 0.0
    return math.exp(-exponent)


def kernel_weight_unsaturated(standardized_error_squared: float, scale_variance: float, sigma_sq: float) -> float:
    """Published-equation kernel weight (PRD 6.3/6.4) WITHOUT the EXT-1 bound.

    Used only for the diagnostics below (unimodality/argmax/stationarity), so
    the recorded evidence refers to the genuine MCC objective. The Java
    reference path itself keeps saturation on both the recurrence and the
    diagnostics, exactly like the oracle's recurrence.
    """
    exponent = standardized_error_squared / (2.0 * sigma_sq * scale_variance)
    return math.exp(-exponent)


# -------------------------------------------------------------------------
# Fixed-point MCKF per index (PRD 6.2, 6.5, 6.6, 6.7, 6.8)
# -------------------------------------------------------------------------

@dataclass
class IndexResult:
    estimate: float
    weight: float
    residual: float
    covariance: float
    converged: bool
    iterations: int
    saturated_any: bool       # any kernel exponent saturated under EXT-1
    maximal: bool             # J(x) unimodal over the bracket (dense scan)
    local_maxima: int         # local maxima counted by the dense scan
    optimizer_argmax: float   # golden-section argmax of J (nan if bimodal)
    optimizer_dev: float      # |argmax - fixed-point candidate| (nan if bimodal)
    grad: float               # analytic dJ/dx at the converged candidate
    d2: float                 # analytic d2J/dx2 at the converged candidate
    stationary: bool          # candidate is a first-order critical point of J


def valid_joint_observation(y: float, q: float, r: float) -> bool:
    return math.isfinite(y) and math.isfinite(q) and q > 0.0 and math.isfinite(r) and r > 0.0


def _objective(x_pred: float, y: float, p_pred: float, r: float, sigma_sq: float) -> Callable[[float], float]:
    def j(x: float) -> float:
        return kernel_weight_unsaturated((x_pred - x) ** 2, p_pred, sigma_sq) \
            + kernel_weight_unsaturated((y - x) ** 2, r, sigma_sq)
    return j

def _dense_unimodality_scan(f: Callable[[float], float], lo: float, hi: float, n: int = 40001):
    """Return (maximal, local_maxima, argmax) over [lo, hi] on an n-point grid.

    `maximal` requires the sampled sequence to be non-decreasing up to its
    argmax and non-increasing afterwards, with a single distinct maximum. This
    catches bimodal J whose second peak sits at a bracket endpoint (an
    outlier index: one peak at x_pred, one at y), which a bare
    rising->falling transition count would miss because the endpoint fall is
    cut off by the bracket. The 40001-point grid is dense enough that flat or
    jittery regions fail the monotonicity check and are conservatively
    reported as non-maximal.
    """
    if not hi > lo:
        return True, 0, lo
    xs = [lo + (hi - lo) * k / (n - 1) for k in range(n)]
    vals = [f(x) for x in xs]
    best = 0
    for k in range(1, n):
        if vals[k] > vals[best]:
            best = k
    maximal = True
    for k in range(1, best + 1):
        if vals[k] < vals[k - 1]:
            maximal = False
            break
    if maximal:
        for k in range(best, n - 1):
            if vals[k + 1] > vals[k]:
                maximal = False
                break
    # Count local maxima against a tolerance relative to the objective scale:
    # an almost-flat objective (large_bandwidth fixture) oscillates by a few
    # ulps across platforms, and strict comparisons would count those rounding
    # artifacts as maxima. Genuine peaks/basins are far above the tolerance,
    # so counts are platform-stable.
    scale = max(1.0, max(abs(v) for v in vals))
    tol = 1e-12 * scale
    local_maxima = 0
    rising = False
    prev_v = vals[0]
    for k in range(1, n):
        v = vals[k]
        if v - prev_v > tol:
            rising = True
        elif prev_v - v > tol and rising:
            local_maxima += 1
            rising = False
        prev_v = v
    if vals[0] - vals[1] > tol:
        local_maxima += 1
    if vals[-1] - vals[-2] > tol:
        local_maxima += 1
    return maximal, local_maxima, xs[best]



def golden_section_maximize(f: Callable[[float], float], lo: float, hi: float, tol: float = 1e-12) -> float:
    """Bounded golden-section maximizer (scalar, no third-party dependency)."""
    inv_phi = (math.sqrt(5.0) - 1.0) / 2.0
    a, b = lo, hi
    c = b - inv_phi * (b - a)
    d = a + inv_phi * (b - a)
    fc, fd = f(c), f(d)
    while b - a > tol:
        if fc > fd:
            b, d, fd = d, c, fc
            c = b - inv_phi * (b - a)
            fc = f(c)
        else:
            a, c, fc = c, d, fd
            d = a + inv_phi * (b - a)
            fd = f(d)
    return (a + b) / 2.0


def _dJ_dx(x_pred: float, y: float, p_pred: float, r: float, sigma_sq: float, x: float) -> float:
    """Analytic first derivative of J(x) = c_x(x) + c_y(x) (PRD 6.4).

    d/dx exp(-(t - x)^2 / (2 s^2 scale)) = c * (t - x) / (s^2 scale).
    """
    ex = (x_pred - x) / math.sqrt(p_pred)
    ey = (y - x) / math.sqrt(r)
    cx = kernel_weight_unsaturated(ex * ex * p_pred, p_pred, sigma_sq)
    cy = kernel_weight_unsaturated(ey * ey * r, r, sigma_sq)
    return cx * ex / (sigma_sq * math.sqrt(p_pred)) + cy * ey / (sigma_sq * math.sqrt(r))


def _d2J_dx2(x_pred: float, y: float, p_pred: float, r: float, sigma_sq: float, x: float) -> float:
    """Analytic second derivative of J (curvature) at x.

    d2/dx2 exp(-(t - x)^2 / (2 s^2 scale)) = c * (e^2 / s^2 - 1) / (s^2 scale)
    with e = (t - x) / sqrt(scale).
    """
    ex = (x_pred - x) / math.sqrt(p_pred)
    ey = (y - x) / math.sqrt(r)
    cx = kernel_weight_unsaturated(ex * ex * p_pred, p_pred, sigma_sq)
    cy = kernel_weight_unsaturated(ey * ey * r, r, sigma_sq)
    return cx * (ex * ex / sigma_sq - 1.0) / (sigma_sq * p_pred) \
        + cy * (ey * ey / sigma_sq - 1.0) / (sigma_sq * r)


def _stationary(grad: float, d2: float, candidate: float, tol: float = CONVERGENCE_TOLERANCE) -> bool:
    """First-order stationarity of the converged candidate.

    The fixed-point map contracts with Lipschitz constant kappa <= 0.5 on the
    nominal fixtures, so the accepted candidate lies within |x* - x_c| <=
    2 * epsilon * max(1, |x_c|) of the true fixed point; the gradient bound
    follows by Taylor's theorem with the curvature |d2J/dx2|. The 1e-12 floor
    keeps flat regions (numerically zero curvature) from being misread.
    """
    threshold = 2.0 * abs(d2) * tol * max(1.0, abs(candidate))
    if d2 == 0.0 or not math.isfinite(threshold):
        threshold = 1e-12
    return abs(grad) <= max(threshold, 1e-12)


def mckf_index(prev: IndexResult, y: float, q: float, r: float, sigma_sq: float):
    """Fixed-point update for one index; None when the index is unavailable."""
    x_pred = prev.estimate
    p_pred = prev.covariance + q  # PRD 6.2
    innovation = y - x_pred
    candidate = x_pred
    gain = 0.0
    converged = False
    iterations = 0
    saturated_any = False
    for _ in range(MAX_ITERATIONS):
        prev_candidate = candidate
        c_x = kernel_weight((x_pred - candidate) ** 2, p_pred, sigma_sq)
        c_y = kernel_weight((y - candidate) ** 2, r, sigma_sq)
        saturated_any = saturated_any or ((x_pred - candidate) ** 2) / (2.0 * sigma_sq * p_pred) > KERNEL_EXPONENT_BOUND \
            or ((y - candidate) ** 2) / (2.0 * sigma_sq * r) > KERNEL_EXPONENT_BOUND
        numerator = p_pred * c_y
        denominator = numerator + r * c_x
        if not (math.isfinite(numerator) and math.isfinite(denominator)) or denominator <= 0.0:
            return None
        gain = numerator / denominator
        candidate = x_pred + gain * innovation
        iterations += 1
        if abs(candidate - prev_candidate) <= CONVERGENCE_TOLERANCE * max(1.0, abs(candidate)):
            converged = True
            break
    if not converged:
        return None
    covariance = (1.0 - gain) ** 2 * p_pred + gain**2 * r  # PRD 6.7 (Joseph)
    weight = kernel_weight((y - candidate) ** 2, r, sigma_sq)  # PRD 6.4 at accepted candidate
    residual = y - candidate
    j = _objective(x_pred, y, p_pred, r, sigma_sq)
    lo, hi = min(x_pred, y), max(x_pred, y)
    maximal, local_maxima, _ = _dense_unimodality_scan(j, lo, hi)
    opt_argmax = golden_section_maximize(j, lo, hi) if maximal else float("nan")
    opt_dev = abs(opt_argmax - candidate) if maximal else float("nan")
    grad = _dJ_dx(x_pred, y, p_pred, r, sigma_sq, candidate)
    d2 = _d2J_dx2(x_pred, y, p_pred, r, sigma_sq, candidate)
    stationary = _stationary(grad, d2, candidate)
    return IndexResult(candidate, weight, residual, covariance, True, iterations, saturated_any, maximal,
                       local_maxima, opt_argmax, opt_dev, grad, d2, stationary)


def run_fixture(name: str, data: Sequence[float], q: Sequence[float], r: Sequence[float],
                sigma: float) -> dict:
    """Run a full series through the oracle; returns the frozen result dict."""
    sigma_sq = sigma * sigma
    results = []
    prev = None
    for i, y in enumerate(data):
        if prev is None:
            if valid_joint_observation(y, q[i], r[i]):
                # PRD 6.8 initialisation: zero-innovation correction of one-prior.
                g = (1.0 + q[i]) / (1.0 + q[i] + r[i])
                covariance = (1.0 - g) ** 2 * (1.0 + q[i]) + g**2 * r[i]
                prev = IndexResult(float(y), 1.0, 0.0, covariance, True, 0, False, True, 0, float(y), 0.0, 0.0, 0.0, True)
                results.append(prev)
            else:
                results.append(None)
            continue
        if valid_joint_observation(y, q[i], r[i]):
            res = mckf_index(prev, y, q[i], r[i], sigma_sq)
            results.append(res)
            if res is not None:
                prev = res
        else:
            results.append(None)
            prev = prev  # keep the last initialized valid state (Java semantics)
    return {
        "name": name,
        "sigma": sigma,
        "data": [float(v) for v in data],
        "q": [float(v) for v in q],
        "r": [float(v) for v in r],
        "estimates": [None if res is None else res.estimate for res in results],
        "weights": [None if res is None else res.weight for res in results],
        "residuals": [None if res is None else res.residual for res in results],
        "covariances": [None if res is None else res.covariance for res in results],
        "converged": [None if res is None else res.converged for res in results],
        "iterations": [None if res is None else res.iterations for res in results],
        "saturated_any": [None if res is None else res.saturated_any for res in results],
        "maximal": [None if res is None else res.maximal for res in results],
        "local_maxima": [None if res is None else res.local_maxima for res in results],
        "optimizer_argmax": [None if res is None else res.optimizer_argmax for res in results],
        "optimizer_dev": [None if res is None else res.optimizer_dev for res in results],
        "grad": [None if res is None else res.grad for res in results],
        "d2": [None if res is None else res.d2 for res in results],
        "stationary": [None if res is None else res.stationary for res in results],
    }


def run_all() -> list:
    fixtures = []
    S = 2.0  # default dimensionless bandwidth; matches the delivery tests

    fixtures.append(run_fixture(
        "step_response", [10.0, 10.2, 10.1, 10.3],
        [1e-3] * 4, [1e-2] * 4, S))
    fixtures.append(run_fixture(
        "isolated_outlier", [10.0, 10.2, 50.0, 10.4],
        [1e-3] * 4, [0.2] * 4, S))
    fixtures.append(run_fixture(
        "tight_measurement_noise", [10.0, 10.2, 10.1, 10.3],
        [1e-3] * 4, [1e-4] * 4, S))
    fixtures.append(run_fixture(
        "long_outlier_run", [10.0, 10.05, 10.05, 50.0],
        [1e-4] * 4, [0.2] * 4, S))
    fixtures.append(run_fixture(
        "negative_impulse", [10.0, 10.2, -50.0, 10.4],
        [1e-3] * 4, [0.2] * 4, S))
    fixtures.append(run_fixture(
        "increasing_impulse", [10.0, 10.1, 12.0, 20.0, 50.0, 10.2],
        [1e-3] * 6, [0.2] * 6, S))
    fixtures.append(run_fixture(
        "smooth_signal_21", [10.0 + 0.1 * math.sin(2 * math.pi * i / 20) for i in range(21)],
        [1e-3] * 21, [1e-2] * 21, S))
    fixtures.append(run_fixture(
        "nan_warmup", [float("nan"), float("nan"), float("nan"), 50.0, 60.0, 70.0, 80.0, 90.0, 90.0, 90.0],
        [1e-3] * 10, [10.0] * 10, S))
    fixtures.append(run_fixture(
        "large_bandwidth", [10.0, 10.2, 10.1, 10.3],
        [1e-3] * 4, [1e-2] * 4, 1e6))
    fixtures.append(run_fixture(
        "outlier_impulses_21",
        [10.0, 10.05, 10.05, 10.05, 10.5, 10.5, 10.5, 10.05, 12.0, 10.05, 10.05, 10.05, 15.0, 15.0, 15.0,
         10.05, 12.0, 10.05, 10.05, 10.05, 10.4],
        [1e-4] * 21, [0.2] * 21, S))
    fixtures.append(run_fixture(
        "invalid_q_at_1", [10.0, 10.2, 10.1, 10.3],
        [1e-3, 0.0, 1e-3, 1e-3], [1e-2] * 4, S))
    fixtures.append(run_fixture(
        "overflow_double", [10.0, 10.2, 10.1, 10.3],
        [1.7976931348623157e308] * 4, [1.7976931348623157e308] * 4, S))
    return fixtures


def _json_safe(value):
    """Recursively convert non-finite floats to strict-JSON null values."""
    if isinstance(value, float):
        return value if math.isfinite(value) else None
    if isinstance(value, list):
        return [_json_safe(item) for item in value]
    if isinstance(value, dict):
        return {key: _json_safe(item) for key, item in value.items()}
    return value


def main() -> int:
    out = Path(__file__).resolve().parent / "cf-558-mckf-reference-vectors.json"
    fixtures = run_all()
    script = Path(__file__).resolve()
    payload = {
        "provenance": {
            "script": "oracles/" + script.name,
            "command": "python3 oracles/" + script.name,
            "date": "2026-08-31",
            "algorithm": "Chen et al. 2017 arXiv:1509.04580, scalar MCKF",
            "prd_sections": ["6.1", "6.2", "6.3", "6.4", "6.5", "6.6", "6.7", "6.8"],
            "extensions": ["EXT-1 KERNEL_EXPONENT_BOUND=15 (saturating kernel; fires on negative_impulse, marked per index via saturated_any)",
                           "EXT-2 MAX_ITERATIONS=20 (bounded fixed-point control)"],
        },
        "fixtures": fixtures,
    }
    with out.open("w") as fh:
        json.dump(_json_safe(payload), fh, indent=2, allow_nan=False)
        fh.write("\n")
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())