/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.ChainRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OverIndicatorRule;

/** Loads real component classes while withholding their optional dependency. */
final class MissingDependencyFixture implements AutoCloseable {

    private final URLClassLoader loader;

    MissingDependencyFixture() throws IOException {
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<URL> urls = new ArrayList<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            urls.add(Path.of(entry).toUri().toURL());
        }
        loader = new URLClassLoader(urls.toArray(URL[]::new), ClassLoader.getPlatformClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(Dependency.class.getName())
                        || name.equals("org.ta4j.core.indicators.MissingDependencyIndicator$Dependency")) {
                    throw new ClassNotFoundException(name);
                }
                return super.loadClass(name, resolve);
            }
        };
    }

    Object invoke(String method) throws Throwable {
        Class<?> probe = loader.loadClass(Probe.class.getName());
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            return probe.getMethod(method).invoke(null);
        } catch (InvocationTargetException ex) {
            throw ex.getCause();
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
        }
    }

    @Override
    public void close() throws IOException {
        loader.close();
    }

    public static class Dependency {
    }

    public static class MissingSuperclass extends Dependency {
    }

    public static class BrokenRule extends BooleanRule {
        public BrokenRule(Dependency dependency) {
            super(true);
        }
    }

    public static class BrokenCriterion extends NumberOfPositionsCriterion {
        public BrokenCriterion(Dependency dependency) {
        }
    }

    public static class BrokenPredicate extends BooleanRule {
        public BrokenPredicate() {
            super(true);
        }

        public void optionalMethod(Dependency dependency) {
        }
    }

    public static class Probe {
        public static Object resolveMissingSuperclass() {
            return ComponentDescriptor.resolveSubtype(MissingDependencyFixture.class.getName() + "$MissingSuperclass",
                    Object.class);
        }

        public static Object deserializeIndicator() {
            return IndicatorSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(),
                    ComponentDescriptor.typeOnly("MissingDependencyIndicator"));
        }

        public static Object deserializeRule() {
            return RuleSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(),
                    ComponentDescriptor.typeOnly(BrokenRule.class.getName()));
        }

        public static Object deserializeNestedIndicatorRule() {
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(OverIndicatorRule.class.getName())
                    .addComponent(ComponentDescriptor.typeOnly("MissingDependencyIndicator"))
                    .addComponent(ComponentDescriptor.typeOnly("ClosePriceIndicator"))
                    .build();
            return RuleSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(), descriptor);
        }

        public static Object deserializeNestedRule() {
            return RuleSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(),
                    negating(ComponentDescriptor.typeOnly(BrokenRule.class.getName())));
        }

        public static Object deserializeTwiceNestedRule() {
            return RuleSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(),
                    negating(negating(ComponentDescriptor.typeOnly(BrokenRule.class.getName()))));
        }

        public static Object deserializeChainLinkRule() {
            ComponentDescriptor initialRule = ComponentDescriptor.builder()
                    .withType(FixedRule.class.getName())
                    .withParameters(Map.of("indexes", List.of(1)))
                    .build();
            ComponentDescriptor brokenRule = ComponentDescriptor.typeOnly(BrokenRule.class.getName());
            Map<String, Object> chainLink = Map.of("rule", ComponentSerialization.toJson(brokenRule), "threshold", 0);
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(ChainRule.class.getName())
                    .addComponent(initialRule)
                    .withParameters(Map.of("chainLinks", List.of(chainLink)))
                    .build();
            return RuleSerialization.fromDescriptor(new BaseBarSeriesBuilder().build(), descriptor);
        }

        public static Object deserializeCriterion() {
            return AnalysisCriterionSerialization
                    .fromDescriptor(ComponentDescriptor.typeOnly(BrokenCriterion.class.getName()));
        }

        public static Object deserializeStrategy() {
            ComponentDescriptor descriptor = ComponentDescriptor.builder()
                    .withType(BaseStrategy.class.getName())
                    .withLabel("Missing dependency")
                    .addComponent(ComponentDescriptor.builder()
                            .withType(BrokenPredicate.class.getName())
                            .withLabel("entry")
                            .build())
                    .addComponent(ComponentDescriptor.builder()
                            .withType(BooleanRule.class.getName())
                            .withLabel("exit")
                            .build())
                    .build();
            return StrategySerialization.fromDescriptor(new BaseBarSeriesBuilder().build(), descriptor);
        }

        private static ComponentDescriptor negating(ComponentDescriptor rule) {
            return ComponentDescriptor.builder().withType(NotRule.class.getName()).addComponent(rule).build();
        }
    }
}
