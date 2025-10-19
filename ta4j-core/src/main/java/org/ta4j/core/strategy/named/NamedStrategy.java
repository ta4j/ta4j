/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.strategy.named;

import java.lang.StackWalker;
import java.lang.StackWalker.Option;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * Base class for strategies that can be reconstructed from compact name tokens.
 *
 * @since 0.19-SNAPSHOT
 */
public abstract class NamedStrategy extends BaseStrategy {

    private static final ConcurrentHashMap<Class<? extends NamedStrategy>, StrategyParser> PARSERS = new ConcurrentHashMap<>();

    private final List<String> arguments;
    private final String compactName;

    /**
     * Protected constructor exposing the strongly typed signature for subclasses.
     *
     * @param name         strategy name
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars number of unstable bars
     * @param arguments    argument tokens excluding the unstable bar counter
     */
    protected NamedStrategy(String name, Rule entryRule, Rule exitRule, int unstableBars, List<String> arguments) {
        super(name, entryRule, exitRule, unstableBars);
        this.arguments = arguments == null ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(arguments));
        this.compactName = buildCompactName(getClass().getSimpleName(), this.arguments, unstableBars);
    }

    private NamedStrategy(Specification specification) {
        this(specification.getName(), specification.getEntryRule(), specification.getExitRule(),
                specification.getUnstableBars(), specification.getArguments());
    }

    /**
     * Constructor used by serialization to rebuild the strategy from string tokens.
     *
     * @param series     backing bar series
     * @param parameters constructor parameters, where the last element encodes the
     *                   unstable bar count
     * @since 0.19-SNAPSHOT
     */
    public NamedStrategy(BarSeries series, String... parameters) {
        this(resolveSpecification(series, parameters));
    }

    private static Specification resolveSpecification(BarSeries series, String... parameters) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(parameters, "parameters");
        Class<? extends NamedStrategy> type = resolveConcreteType();
        StrategyParser parser = Optional.ofNullable(PARSERS.get(type))
                .orElseThrow(() -> new IllegalStateException("No parser registered for " + type.getName()));
        List<String> tokens = List.of(parameters);
        Specification specification = parser.parse(series, Collections.unmodifiableList(tokens));
        Objects.requireNonNull(specification, "parser returned null specification");
        if (specification.getExpectedTokenCount() != tokens.size()) {
            throw new IllegalArgumentException(
                    "Expected " + specification.getExpectedTokenCount() + " parameters but received " + tokens.size());
        }
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("Named strategies require at least one token for unstable bars");
        }
        String unstableToken = tokens.get(tokens.size() - 1);
        String expectedUnstable = Integer.toString(specification.getUnstableBars());
        if (!expectedUnstable.equals(unstableToken)) {
            throw new IllegalArgumentException(
                    "Unstable bar token mismatch: expected " + expectedUnstable + " but received " + unstableToken);
        }
        return specification;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends NamedStrategy> resolveConcreteType() {
        return StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE)
                .walk(stream -> stream.map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(clazz -> NamedStrategy.class.isAssignableFrom(clazz) && clazz != NamedStrategy.class
                                && !Modifier.isAbstract(clazz.getModifiers()))
                        .findFirst())
                .map(clazz -> (Class<? extends NamedStrategy>) clazz)
                .orElseThrow(() -> new IllegalStateException("Unable to determine named strategy subtype"));
    }

    private static String buildCompactName(String simpleName, List<String> arguments, int unstableBars) {
        String unstableToken = "u" + unstableBars;
        if (arguments == null || arguments.isEmpty()) {
            return simpleName + '_' + unstableToken;
        }
        List<String> tokens = new ArrayList<>(arguments);
        tokens.add(unstableToken);
        return simpleName + '_' + String.join("_", tokens);
    }

    /**
     * Registers a parser for the provided named strategy type.
     *
     * @param type   strategy subtype
     * @param parser parser implementation
     */
    protected static void registerParser(Class<? extends NamedStrategy> type, StrategyParser parser) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(parser, "parser");
        StrategyParser previous = PARSERS.putIfAbsent(type, parser);
        if (previous != null && previous != parser) {
            throw new IllegalStateException("Parser already registered for " + type.getName());
        }
    }

    /**
     * Returns the immutable argument token list supplied by the subclass.
     *
     * @return argument tokens, excluding the unstable bar counter
     */
    protected List<String> getArguments() {
        return arguments;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19-SNAPSHOT
     */
    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.19-SNAPSHOT
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        ComponentDescriptor.Builder builder = ComponentDescriptor.builder()
                .withType(getClass().getName())
                .withLabel(compactName);
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        if (!arguments.isEmpty()) {
            parameters.put("args", arguments);
        }
        parameters.put("unstableBars", getUnstableBars());
        builder.withParameters(parameters);
        return builder.build();
    }

    @Override
    public String toString() {
        return compactName;
    }

    /**
     * Creates a specification for the typed constructor.
     *
     * @param name         strategy name
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars unstable bars
     * @param arguments    argument tokens excluding the unstable counter
     * @return specification
     */
    protected static Specification specification(String name, Rule entryRule, Rule exitRule, int unstableBars,
            List<String> arguments) {
        return new Specification(name, entryRule, exitRule, unstableBars, arguments);
    }

    /**
     * Creates a specification for the typed constructor.
     *
     * @param name         strategy name
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars unstable bars
     * @param arguments    argument tokens excluding the unstable counter
     * @return specification
     */
    protected static Specification specification(String name, Rule entryRule, Rule exitRule, int unstableBars,
            String... arguments) {
        return specification(name, entryRule, exitRule, unstableBars,
                arguments == null ? List.of() : List.of(arguments));
    }

    /**
     * Parser responsible for transforming raw string tokens into strategy
     * specifications.
     */
    @FunctionalInterface
    protected interface StrategyParser {
        Specification parse(BarSeries series, List<String> parameters);
    }

    /**
     * Immutable specification returned by {@link StrategyParser}s.
     */
    protected static final class Specification {

        private final String name;
        private final Rule entryRule;
        private final Rule exitRule;
        private final int unstableBars;
        private final List<String> arguments;
        private final int expectedTokenCount;

        private Specification(String name, Rule entryRule, Rule exitRule, int unstableBars, List<String> arguments) {
            this(name, entryRule, exitRule, unstableBars, arguments, arguments == null ? 1 : arguments.size() + 1);
        }

        private Specification(String name, Rule entryRule, Rule exitRule, int unstableBars, List<String> arguments,
                int expectedTokenCount) {
            this.name = Objects.requireNonNull(name, "name");
            this.entryRule = Objects.requireNonNull(entryRule, "entryRule");
            this.exitRule = Objects.requireNonNull(exitRule, "exitRule");
            if (unstableBars < 0) {
                throw new IllegalArgumentException("Unstable bars must be >= 0");
            }
            if (arguments == null) {
                this.arguments = Collections.emptyList();
            } else {
                this.arguments = Collections.unmodifiableList(new ArrayList<>(arguments));
            }
            if (expectedTokenCount < this.arguments.size() + 1) {
                throw new IllegalArgumentException("Expected token count must be >= arguments size + 1");
            }
            this.unstableBars = unstableBars;
            this.expectedTokenCount = expectedTokenCount;
        }

        public Specification withExpectedTokenCount(int expectedTokenCount) {
            return new Specification(name, entryRule, exitRule, unstableBars, arguments, expectedTokenCount);
        }

        public String getName() {
            return name;
        }

        public Rule getEntryRule() {
            return entryRule;
        }

        public Rule getExitRule() {
            return exitRule;
        }

        public int getUnstableBars() {
            return unstableBars;
        }

        public List<String> getArguments() {
            return arguments;
        }

        public int getExpectedTokenCount() {
            return expectedTokenCount;
        }
    }
}
