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
            Comparator<ParameterResearch.EvaluatedCandidate> ranking, int maxIterations, int noImprovementIterations) {
        super(specs);
        for (DomainSpec spec : specs) {
            if (!spec.numeric()) {
                throw new IllegalArgumentException("PARTICLE_SWARM requires ordered numeric domains "
                        + "(integer or decimal), but parameter '" + spec.name() + "' is not numeric");
            }
        }
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
    void observe(ParameterResearch.EvaluatedCandidate evaluated) {
        batchEvaluations.put(evaluated.candidateId(), evaluated);
    }

    private List<ParameterResearch.ParameterSet> proposeBatch(int count) {
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
        if (count > 0 && newIds == 0) {
            // Every particle of this batch projects onto an already-proposed
            // grid point. A covered declared space is exhausted; otherwise the
            // swarm stalled and cannot yield a new evaluation right now.
            if (exhausted()) {
                terminate(ParameterResearch.TerminationReason.SEARCH_SPACE_EXHAUSTED);
            } else {
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
            }
            return List.of();
        }
        return batch;
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
        } else if (gbestEvaluated == null || ranking.compare(newGbest, gbestEvaluated) < 0) {
            improved = true;
            gbestEvaluated = newGbest;
            gbestPosition = gbestParticle.pbestPosition.clone();
        } else {
            improved = false;
        }
        noImprovementStreak = improved ? 0 : noImprovementStreak + 1;
        if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
            terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
        }
    }

    private void move() {
        for (Particle particle : particles) {
            for (int d = 0; d < particle.position.length; d++) {
                DomainSpec spec = specs().get(d);
                double r1 = random.nextDouble();
                double r2 = random.nextDouble();
                double velocity = settings.inertiaWeight() * particle.velocity[d]
                        + settings.cognitiveWeight() * r1 * (particle.pbestPosition[d] - particle.position[d])
                        + settings.socialWeight() * r2 * (gbestPosition[d] - particle.position[d]);
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
