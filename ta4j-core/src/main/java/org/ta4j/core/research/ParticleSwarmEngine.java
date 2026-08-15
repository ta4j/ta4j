/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

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
 * clamped to {@code velocityClampFactor} times the dimension range; until a
 * validated global best exists, particles take the next unseen cell of a
 * mixed-radix grid sweep instead of velocity-scaled steps, so a small clamp
 * factor cannot freeze the swarm inside its launch cells. All randomness comes
 * from the run-local seeded {@link Random}.
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
    /**
     * Whether the update that generated {@link #pendingBatch} already advanced the
     * iteration tracker: every swarm move counts immediately (so a fully-colliding
     * move cannot escape the iteration cap), while the launch batch has no
     * preceding move and is counted when it is finalized.
     */
    private boolean pendingMoveCounted;
    private ParameterResearch.EvaluatedCandidate gbestEvaluated;
    private double[] gbestPosition;
    /**
     * Mixed-radix cursor sweeping the declared grid in the no-best phase; each move
     * hands the next unseen cell to a particle, so exploration covers the whole
     * space regardless of the velocity clamp. {@code null} until the first no-best
     * move.
     */
    private int[] noBestCursor;
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
            // The launch batch has no preceding swarm move: its update is
            // counted when the batch is finalized.
            pendingMoveCounted = false;
            return proposeBatch(particles.size());
        }
        // Finalize before checking the iteration cap: when the previous move
        // exhausted the cap, the pending batch holds the run's final
        // observations, which must still be folded into pbest/gbest and the
        // stagnation streak (matching the genetic engine's finalize-first
        // order). The cap check below still fires for every request that
        // follows a finalized iteration at or past the limit.
        finalizeIteration();
        if (terminationReason() != null) {
            return List.of();
        }
        if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
            terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
            return List.of();
        }
        if (maxNew <= 0) {
            // A zero-capacity request, e.g. from an exactly exhausted
            // evaluation budget, can never produce an evaluated proposal. The
            // pending observations were finalized above, so do not move the
            // swarm or advance the iteration tracker for a request that cannot
            // contribute anything.
            return List.of();
        }
        move();
        // Every swarm update advances the iteration tracker exactly once, so a
        // leading move whose batch fully collides cannot slip a second update
        // past the configured iteration cap or the stagnation streak, and
        // finalizeIteration() must not count this update a second time.
        completeIteration();
        pendingMoveCounted = true;
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
            // Each retry move is another swarm update; check the iteration cap
            // before executing it so a pinned swarm cannot outrun the limit.
            if (maxIterations > 0 && iterationsCompleted() >= maxIterations) {
                terminate(ParameterResearch.TerminationReason.ITERATION_LIMIT);
                return List.of();
            }
            // The update that produced these positions proposed nothing new:
            // an empty generation advances the stagnation streak exactly like
            // an observed non-improving iteration, so a configured
            // noImprovementIterations limit must not ride out stall moves.
            noImprovementStreak++;
            if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
                return List.of();
            }
            move();
            completeIteration();

        }
        // STALL_MOVE_LIMIT full swarm moves without reaching a single unseen
        // grid point: the swarm is effectively converged. With the stagnation
        // limit disabled, this fallthrough terminates the run.
        terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
        return List.of();
    }

    private void finalizeIteration() {
        if (pendingBatch.isEmpty()) {
            // Nothing to finalize: the update that produced the next batch
            // will count itself when the swarm moves.
            return;
        }
        // The launch batch has no preceding move and is counted here; a moved
        // batch was already counted when the move ran, so every update
        // advances the tracker exactly once.
        if (!pendingMoveCounted) {
            completeIteration();
        }
        boolean batchHadValidEvaluation = false;
        for (Map.Entry<String, List<Integer>> entry : pendingBatch.entrySet()) {
            ParameterResearch.EvaluatedCandidate evaluated = batchEvaluations.get(entry.getKey());
            if (evaluated == null || !evaluated.valid()) {
                continue;
            }
            batchHadValidEvaluation = true;
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
        // An all-invalid batch carries no ranking evidence: the best valid
        // score neither improved nor declined, so the stagnation streak stays
        // put and NO_IMPROVEMENT cannot fire while unseen candidates remain.
        if (batchHadValidEvaluation) {
            noImprovementStreak = improved ? 0 : noImprovementStreak + 1;
            if (noImprovementIterations > 0 && noImprovementStreak >= noImprovementIterations) {
                terminate(ParameterResearch.TerminationReason.NO_IMPROVEMENT);
            }
        }

        // Drain the batch so a repeated finalizeObserved() call is a no-op:
        // re-running the iteration would advance the stagnation streak twice
        // for one batch.
        pendingBatch = new LinkedHashMap<>();
        batchEvaluations = new LinkedHashMap<>();
        pendingMoveCounted = false;
    }

    private void move() {
        // Cells handed out earlier in this same move are not committed to the
        // proposed set yet (the batch is only served afterwards), so track
        // them here: without it the sweep cursor can wrap around within one
        // move and hand the same unseen cell to two particles.
        Set<String> moveAssigned = new HashSet<>();
        for (Particle particle : particles) {
            int[] resampled = gbestEvaluated == null ? nextUnexploredCell(moveAssigned) : null;
            // Sweep exhaustion is monotone: once the mixed-radix cursor has
            // no unseen cell to hand out, no later particle in this move can
            // receive one either, and the attraction terms stay inert while
            // no validated best exists. Remaining particles keep their
            // positions — exactly what the no-cell path does today — so the
            // break only skips dead per-particle work (random draws and
            // attraction arithmetic) that cannot change their positions.
            if (gbestEvaluated == null && resampled == null) {
                break;
            }
            for (int d = 0; d < particle.position.length; d++) {
                DomainSpec spec = specs().get(d);
                if (gbestEvaluated == null) {
                    // No validated personal or global best exists yet: the
                    // attraction terms carry no signal, so hand the particle
                    // the next unseen cell of the mixed-radix sweep instead of
                    // taking a velocity-clamped step. The velocity clamp is a
                    // damping knob for attraction dynamics; scaling
                    // exploration by it can pin the swarm inside its launch
                    // cells (velocityClampFactor 1e-6 caps each step at 1e-4
                    // of the range) until the stall limit expires with unseen
                    // grid points remaining. Once the sweep is exhausted the
                    // particle keeps its position and the batch layer reports
                    // SEARCH_SPACE_EXHAUSTED.
                    if (resampled != null) {
                        particle.position[d] = spec.gridPointAt(resampled[d]);
                        particle.velocity[d] = 0d;
                    }
                    continue;
                }
                double maxVelocity = settings.velocityClampFactor() * (spec.upperBound() - spec.lowerBound());
                double velocity;
                double r1 = random.nextDouble();
                double r2 = random.nextDouble();
                // A particle whose initial evaluation failed keeps its
                // launch position as the pbest snapshot but has no
                // validated personal best: attracting it back toward that
                // failed point would pin it to an invalid region, so the
                // cognitive pull stays zero until a valid evaluation
                // establishes a personal best.
                double cognitivePull = particle.pbestEvaluated == null ? 0d
                        : particle.pbestPosition[d] - particle.position[d];
                // The global best exists and is validated here (the
                // no-best case is handled above), so the social pull may
                // attract the swarm toward it.
                double socialPull = gbestPosition[d] - particle.position[d];
                velocity = settings.inertiaWeight() * particle.velocity[d]
                        + settings.cognitiveWeight() * r1 * cognitivePull + settings.socialWeight() * r2 * socialPull;
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

    /**
     * Returns the next cell of the mixed-radix sweep that has not been proposed yet
     * nor assigned earlier in the current move, and advances the cursor past it;
     * {@code null} when every declared cell has been proposed or assigned. The
     * cursor starts at a uniformly drawn cell, so the sweep order varies across
     * seeds.
     *
     * @param batchIds canonical ids already assigned during the current move
     * @return unseen cell indices, or {@code null} when the sweep is exhausted
     */
    private int[] nextUnexploredCell(Set<String> batchIds) {
        if (noBestCursor == null) {
            noBestCursor = new int[specs().size()];
            for (int d = 0; d < noBestCursor.length; d++) {
                noBestCursor[d] = random.nextInt(specs().get(d).cardinality());
            }
        }
        int[] start = noBestCursor.clone();
        do {
            String id = canonicalId(noBestCursor);
            if (!proposed(id) && batchIds.add(id)) {
                int[] cell = noBestCursor.clone();
                advanceCursor();
                return cell;
            }
            advanceCursor();
        } while (!Arrays.equals(noBestCursor, start));
        return null;
    }

    /**
     * Advances the sweep cursor by one cell in mixed-radix order.
     */
    private void advanceCursor() {
        for (int d = noBestCursor.length - 1; d >= 0; d--) {
            if (++noBestCursor[d] < specs().get(d).cardinality()) {
                return;
            }
            noBestCursor[d] = 0;
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
