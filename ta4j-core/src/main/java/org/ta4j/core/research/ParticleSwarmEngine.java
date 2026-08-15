/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Particle-swarm search over ordered numeric dimensions.
 *
 * <p>
 * Only integer and decimal domains are supported; boolean and categorical
 * domains fail fast when the plan is built, before any evaluation. Particles
 * move on continuous positions that are projected onto the declared grid for
 * evaluation via {@link DomainSpec#projectIndex(double)}. Personal and global
 * bests are tracked by evaluation outcome; positions leaving a dimension are
 * clamped and the velocity component is absorbed (set to zero). Velocities are
 * clamped to {@code velocityClampFactor} times the dimension range. All
 * randomness comes from the run-local seeded {@link Random}.
 * </p>
 */
final class ParticleSwarmEngine extends SearchEngine {

    private static final int STALL_MOVE_LIMIT = 16;

    private final ParameterResearch.Direction direction;

    private final ParameterResearch.SwarmSettings settings;
    private final Random random;
    private final Comparator<ParameterResearch.EvaluatedCandidate> ranking;
    private final int maxIterations;
    private final int noImprovementIterations;

    private boolean initialized;
    private List<Particle> particles;
    private Map<String, List<Integer>> pendingBatch = new LinkedHashMap<>();
    private Map<String, ParameterResearch.EvaluatedCandidate> batchEvaluations = new LinkedHashMap<>();
    private ParameterResearch.EvaluatedCandidate gbestEvaluated;
    private double[] gbestPosition;
    private int noImprovementStreak;

    ParticleSwarmEngine(List<DomainSpec> specs, ParameterResearch.SwarmSettings settings, Random random,
            Comparator<ParameterResearch.EvaluatedCandidate> ranking, ParameterResearch.Direction direction,
            int maxIterations, int noImprovementIterations) {
        super(specs);
        double maxSpan = 0d;
        for (DomainSpec spec : specs) {
            if (!spec.numeric()) {
                throw new IllegalArgumentException("PARTICLE_SWARM requires ordered numeric domains "
                        + "(integer or decimal), but parameter '" + spec.name() + "' is not numeric");
            }
            double span = spec.upperBound() - spec.lowerBound();
            if (!Double.isFinite(span)) {
                throw new IllegalArgumentException("PARTICLE_SWARM cannot scale position updates for parameter '"
                        + spec.name() + "': the domain span " + spec.lowerBound() + ".." + spec.upperBound()
                        + " overflows double precision");
            }
            maxSpan = Math.max(maxSpan, span);
        }
        // Finite domain spans can still overflow the derived update terms
        // when the weights or the clamp factor are huge. move() scales each
        // contribution separately (the inertia term by clampFactor times the
        // span, the attraction terms by cognitive/social weight times the
        // span), so each per-term scale must be finite: an overflowing sum is
        // clamped back to the finite maxVelocity, but a term that overflows
        // on its own produces infinite or NaN velocities that project onto
        // boundary indices. Validating per-term scales (rather than their
        // naive sum) also keeps representable plans accepted.
        double velocityScale = settings.velocityClampFactor() * maxSpan;
        double cognitiveScale = settings.cognitiveWeight() * maxSpan;
        double socialScale = settings.socialWeight() * maxSpan;
        if (!Double.isFinite(velocityScale) || !Double.isFinite(cognitiveScale) || !Double.isFinite(socialScale)) {
            throw new IllegalArgumentException("PARTICLE_SWARM cannot scale position updates for this plan: the "
                    + "derived velocity scale overflows double precision for the largest domain span " + maxSpan);
        }
        this.direction = direction;
        this.settings = settings;

        this.random = random;
        this.ranking = ranking;
        this.maxIterations = maxIterations;
        this.noImprovementIterations = noImprovementIterations;
    }

    @Override
    List<ParameterResearch.ParameterSet> propose(int maxNew) {
        if (!initialized) {
            initialized = true;
            int size = Math.min(settings.swarmSize(), maxNew);
            particles = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                double[] position = new double[specs().size()];
                for (int d = 0; d < position.length; d++) {
                    DomainSpec spec = specs().get(d);
                    position[d] = spec.lowerBound() + random.nextDouble() * (spec.upperBound() - spec.lowerBound());
                }
                particles.add(new Particle(position, new double[specs().size()], position.clone()));
            }
            return proposeBatch(particles.size());
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        finalizeIteration();
        if (terminationReason() != null) {
            return List.of();
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        move();
        int count = Math.min(particles.size(), maxNew);
        return proposeBatch(count);
    }

    @Override
    void observe(String rawId, ParameterResearch.EvaluatedCandidate evaluated) {
        batchEvaluations.put(rawId, evaluated);
    }

    @Override
    void finalizeObserved() {
        if (!pendingBatch.isEmpty()) {
            finalizeIteration();
        }
    }

    private List<ParameterResearch.ParameterSet> proposeBatch(int count) {
        // A batch whose particles all project onto already-proposed grid points
        // would be served entirely from the cache with no observation at all,
        // and re-proposing it forever would stall the swarm. A transient
        // collision must not end the run while budget remains: keep moving the
        // swarm until at least one particle reaches an unseen grid point, the
        // declared space is covered, or the movement budget is spent.
        for (int attempt = 0; attempt < STALL_MOVE_LIMIT; attempt++) {
            pendingBatch = new LinkedHashMap<>();
            batchEvaluations = new LinkedHashMap<>();
            List<ParameterResearch.ParameterSet> batch = new ArrayList<>(count);
            int newIds = 0;
            for (int p = 0; p < count; p++) {
                Particle particle = particles.get(p);
                int[] indices = new int[specs().size()];
                for (int d = 0; d < indices.length; d++) {
                    indices[d] = specs().get(d).projectIndex(particle.position[d]);
                }
                if (!proposed(canonicalId(indices))) {
                    newIds++;
                }
                ParameterResearch.ParameterSet set = parameterSet(indices);
                pendingBatch.computeIfAbsent(set.stableId(), key -> new ArrayList<>()).add(p);
                batch.add(set);
            }
            if (count == 0 || newIds > 0) {
                return batch;
            }
            if (exhausted()) {
                terminate(ParameterResearch.TerminationReason.SEARCH_SPACE_EXHAUSTED);
                return List.of();
            }
            if (gbestPosition == null) {
                gbestPosition = particles.get(0).position.clone();
            }
            // Each stall move consumes an iteration like any other swarm
            // update, so a swarm pinned to already-proposed grid points
            // cannot outrun the configured iteration limit.
            move();
            completeIteration();
            if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
                terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
                return List.of();
            }
            // A stall move is an iteration that produced no new evaluation, so
            // it advances the stagnation streak exactly like an observed
            // non-improving iteration; a configured noImprovementIterations
            // limit must not ride out the remaining stall moves.
            noImprovementStreak++;
            if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
                return List.of();
            }
        }
        // STALL_MOVE_LIMIT full swarm moves without reaching a single unseen
        // grid point: the swarm is effectively converged. With the stagnation
        // limit disabled, this fallthrough terminates the run.
        terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
        return List.of();
    }

    private void finalizeIteration() {
        completeIteration();
        for (Map.Entry<String, List<Integer>> entry : pendingBatch.entrySet()) {
            ParameterResearch.EvaluatedCandidate evaluated = batchEvaluations.get(entry.getKey());
            if (evaluated == null || !evaluated.valid()) {
                continue;
            }
            for (Integer particleIndex : entry.getValue()) {
                Particle particle = particles.get(particleIndex);
                if (particle.pbestEvaluated == null || ranking.compare(evaluated, particle.pbestEvaluated) < 0) {
                    particle.pbestEvaluated = evaluated;
                    particle.pbestPosition = particle.position.clone();
                }
            }
        }
        ParameterResearch.EvaluatedCandidate newGbest = null;
        Particle gbestParticle = null;
        for (Particle particle : particles) {
            if (particle.pbestEvaluated != null
                    && (newGbest == null || ranking.compare(particle.pbestEvaluated, newGbest) < 0)) {
                newGbest = particle.pbestEvaluated;
                gbestParticle = particle;
            }
        }
        boolean improved;
        if (newGbest == null) {
            improved = false;
            if (gbestPosition == null) {
                gbestPosition = particles.get(0).position.clone();
            }
        } else if (gbestEvaluated == null) {
            improved = true;
            gbestEvaluated = newGbest;
            gbestPosition = gbestParticle.pbestPosition.clone();
        } else {
            improved = ParameterResearch.scoreIsBetter(direction, newGbest.score(), gbestEvaluated.score());
            if (ranking.compare(newGbest, gbestEvaluated) < 0) {
                gbestEvaluated = newGbest;
                gbestPosition = gbestParticle.pbestPosition.clone();
            }
        }
        noImprovementStreak = improved ? 0 : noImprovementStreak + 1;
        if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
            terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
        }

        // Drain the batch so a repeated finalizeObserved() call is a no-op:
        // re-running the iteration would double-count completeIteration() and
        // advance the stagnation streak twice for one batch.
        pendingBatch = new LinkedHashMap<>();
        batchEvaluations = new LinkedHashMap<>();
    }

    private void move() {
        for (Particle particle : particles) {
            for (int d = 0; d < particle.position.length; d++) {
                DomainSpec spec = specs().get(d);
                double r1 = random.nextDouble();
                double r2 = random.nextDouble();
                // A particle whose initial evaluation failed keeps its launch
                // position as the pbest snapshot but has no validated personal
                // best: attracting it back toward that failed point would pin
                // it to an invalid region, so the cognitive pull stays zero
                // until a valid evaluation establishes a personal best.
                double cognitivePull = particle.pbestEvaluated == null ? 0d
                        : particle.pbestPosition[d] - particle.position[d];
                // The same guard applies to the social term: when every
                // particle in the initial batch fails, gbestPosition falls
                // back to an arbitrary launch position with no validated
                // evaluation. Pulling the swarm toward that point would
                // collapse exploration toward an invalid region, so the
                // social pull stays zero until a valid global best exists.
                double socialPull = gbestEvaluated == null ? 0d : gbestPosition[d] - particle.position[d];
                double velocity = settings.inertiaWeight() * particle.velocity[d]
                        + settings.cognitiveWeight() * r1 * cognitivePull + settings.socialWeight() * r2 * socialPull;
                double maxVelocity = settings.velocityClampFactor() * (spec.upperBound() - spec.lowerBound());
                velocity = clamp(velocity, -maxVelocity, maxVelocity);
                double position = particle.position[d] + velocity;
                if (position < spec.lowerBound() || position > spec.upperBound()) {
                    position = clamp(position, spec.lowerBound(), spec.upperBound());
                    velocity = 0d;
                }
                particle.position[d] = position;
                particle.velocity[d] = velocity;
            }
        }
    }

    private static double clamp(double value, double lower, double upper) {
        if (value < lower) {
            return lower;
        }
        if (value > upper) {
            return upper;
        }
        return value;
    }

    /**
     * One particle: current position, velocity, and personal-best snapshot.
     */
    private static final class Particle {

        private final double[] position;
        private final double[] velocity;
        private double[] pbestPosition;
        private ParameterResearch.EvaluatedCandidate pbestEvaluated;

        private Particle(double[] position, double[] velocity, double[] pbestPosition) {
            this.position = position;
            this.velocity = velocity;
            this.pbestPosition = pbestPosition;
        }
    }
}
