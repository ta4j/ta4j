/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.strategy.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.serialization.ComponentDescriptor;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Base class for strategies that can be reconstructed from compact name tokens.
 *
 * <h2>Compact Name Format</h2>
 * <p>
 * The compact name (label) must conform to the format:
 * </p>
 *
 * <pre>{@code <SimpleName>_<param1>_<param2>_...}</pre>
 * <p>
 * Where:
 * </p>
 * <ul>
 * <li><strong>SimpleName</strong>: The simple class name (without package) of
 * the strategy implementation</li>
 * <li><strong>Parameters</strong>: Zero or more parameter values separated by
 * underscores, where each parameter is a string representation of a constructor
 * argument. <strong>Underscores are reserved as delimiters and are not
 * permitted inside parameter values.</strong></li>
 * </ul>
 * <p>
 * Examples:
 * </p>
 * <ul>
 * <li>{@code "MyStrategy"} - No parameters</li>
 * <li>{@code "MyStrategy_10"} - Single integer parameter</li>
 * <li>{@code "MyStrategy_10_0.5"} - Two parameters (integer and decimal)</li>
 * <li>{@code "DayOfWeekStrategy_MONDAY_FRIDAY"} - Two enum parameters</li>
 * </ul>
 * <p>
 * Use {@link #buildLabel(Class, String...)} when constructing the superclass to
 * guarantee consistent token formatting. The label must encode every piece of
 * information required to reconstruct the instance, including unstable bar
 * counts if they vary.
 * </p>
 *
 * <h2>Constructor Requirements</h2>
 * <p>
 * <strong>Every {@code NamedStrategy} implementation must provide a constructor
 * that accepts the compact name format:</strong>
 * </p>
 *
 * <pre>{@code
 * public YourStrategy(BarSeries series, String... parameters) {
 *     // Parse parameters and delegate to main constructor
 *     this(series, parseParam1(parameters), parseParam2(parameters), ...);
 * }
 * }</pre>
 * <p>
 * This constructor must be able to parse the parameter tokens (obtained by
 * splitting the label on underscores after the simple name) and reconstruct the
 * strategy instance. The serialization layer will invoke this constructor when
 * deserializing strategies from JSON or other formats.
 * </p>
 * <p>
 * Best practice: Parse parameters inside the varargs constructor and delegate
 * to a strongly-typed constructor to avoid duplicating rule-building logic.
 * Validate inputs eagerly and throw informative
 * {@link IllegalArgumentException}s for bad parameters.
 * </p>
 *
 * <h2>Registration</h2>
 * <p>
 * Named strategies must be registered before they can be deserialized. There
 * are two approaches:
 * </p>
 *
 * <h3>Manual Registration (Recommended for Custom Strategies)</h3>
 * <p>
 * Register each strategy class in a static initializer:
 * </p>
 *
 * <pre>{@code
 * public class MyStrategy extends NamedStrategy {
 *     static {
 *         registerImplementation(MyStrategy.class);
 *     }
 *     // ... constructors and implementation
 * }
 * }</pre>
 *
 * <h3>Automatic Package Scanning</h3>
 * <p>
 * For projects with many named strategies, you can scan entire packages at
 * application startup:
 * </p>
 *
 * <pre>{@code
 * // Scan default Ta4j packages only
 * NamedStrategy.initializeRegistry();
 *
 * // Scan default packages plus your custom packages
 * NamedStrategy.initializeRegistry("com.mycompany.strategies", "com.mycompany.trading");
 * }</pre>
 * <p>
 * Package scanning automatically discovers and registers all non-abstract
 * classes extending {@code NamedStrategy} in the specified packages. The
 * default package {@code "org.ta4j.core.strategy.named"} is always scanned
 * automatically on first use.
 * </p>
 * <p>
 * <strong>Note:</strong> Package scanning works for both file-based and
 * JAR-based class loading, but requires that classes are on the classpath at
 * runtime.
 * </p>
 *
 * <h2>Serialization</h2>
 * <p>
 * When serialized to JSON (via {@link #toDescriptor()}), the strategy type is
 * always {@link #SERIALIZED_TYPE}, and the label field contains the compact
 * name. The deserialization layer uses {@link #splitLabel(String)} to extract
 * the simple class name and parameters, then looks up the registered type and
 * invokes the varargs constructor.
 * </p>
 *
 * <h2>Example Implementation</h2>
 *
 * <pre>{@code
 * public class MovingAverageStrategy extends NamedStrategy {
 *     static {
 *         registerImplementation(MovingAverageStrategy.class);
 *     }
 *
 *     private final int shortPeriod;
 *     private final int longPeriod;
 *
 *     // Strongly-typed constructor
 *     public MovingAverageStrategy(BarSeries series, int shortPeriod, int longPeriod) {
 *         super(buildLabel(MovingAverageStrategy.class, String.valueOf(shortPeriod), String.valueOf(longPeriod)),
 *                 buildEntryRule(series, shortPeriod, longPeriod), buildExitRule(series, shortPeriod, longPeriod));
 *         this.shortPeriod = shortPeriod;
 *         this.longPeriod = longPeriod;
 *     }
 *
 *     // Varargs constructor for deserialization
 *     public MovingAverageStrategy(BarSeries series, String... parameters) {
 *         this(series, Integer.parseInt(parameters[0]), Integer.parseInt(parameters[1]));
 *     }
 *
 *     // ... rule building methods
 * }
 * }</pre>
 *
 * @since 0.19
 */
public abstract class NamedStrategy extends BaseStrategy {

    /**
     * JSON {@code type} written by {@link #toDescriptor()}.
     */
    public static final String SERIALIZED_TYPE = NamedStrategy.class.getSimpleName();

    private static final Map<String, Class<? extends NamedStrategy>> REGISTRY = new ConcurrentHashMap<>();
    private static final Logger LOGGER = LoggerFactory.getLogger(NamedStrategy.class);
    private static final String[] DEFAULT_SCAN_PACKAGES = { "org.ta4j.core.strategy.named" };
    private static final Set<String> SCANNED_PACKAGES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean DEFAULT_PACKAGES_INITIALIZED = new AtomicBoolean();

    /**
     * Protected constructor that allows subclasses to provide the fully formatted
     * label (and therefore {@link Strategy#getName()}).
     *
     * @param label        strategy label that also serves as the serialized value
     * @param entryRule    entry rule
     * @param exitRule     exit rule
     * @param unstableBars unstable bars
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule, int unstableBars) {
        super(label, entryRule, exitRule, unstableBars);
        registerImplementation(getClass());
    }

    /**
     * Protected constructor that defaults {@code unstableBars} to {@code 0}.
     *
     * @param label     strategy label that also serves as the serialized value
     * @param entryRule entry rule
     * @param exitRule  exit rule
     */
    protected NamedStrategy(String label, Rule entryRule, Rule exitRule) {
        super(label, entryRule, exitRule);
        registerImplementation(getClass());
    }

    /**
     * Ensures core packages have been scanned and registers any discovered named
     * strategies.
     */
    public static void initializeRegistry(String... basePackages) {
        ensureDefaultRegistryInitialized();
        if (basePackages == null || basePackages.length == 0) {
            return;
        }
        scanPackages(basePackages);
    }

    /**
     * Registers a {@link NamedStrategy} implementation so it can be reconstructed
     * purely from its compact label. Custom strategies should invoke this method
     * during application startup (typically from a static initializer).
     *
     * @param type strategy subtype
     */
    public static void registerImplementation(Class<? extends NamedStrategy> type) {
        Objects.requireNonNull(type, "type");
        String key = type.getSimpleName();
        REGISTRY.compute(key, (name, existing) -> {
            if (existing != null && existing != type) {
                throw new IllegalStateException(
                        "Named strategy already registered for simple name " + name + ": " + existing.getName());
            }
            return type;
        });
    }

    /**
     * Unregisters a previously registered {@link NamedStrategy} implementation.
     * This method is primarily intended for testing purposes to allow cleanup of
     * test fixtures that register strategies via static initializers.
     *
     * @param type strategy subtype to unregister
     * @return {@code true} if the strategy was registered and has been removed,
     *         {@code false} if it was not registered
     */
    public static boolean unregisterImplementation(Class<? extends NamedStrategy> type) {
        Objects.requireNonNull(type, "type");
        String key = type.getSimpleName();
        Class<? extends NamedStrategy> removed = REGISTRY.remove(key);
        return removed != null && removed == type;
    }

    /**
     * Resolves a previously registered named strategy type.
     *
     * @param simpleName simple class name (without package)
     * @return optional containing the registered type
     */
    public static Optional<Class<? extends NamedStrategy>> lookup(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(simpleName));
    }

    /**
     * Builds the serialized label using the simple class name and optional
     * parameters.
     *
     * @param type       concrete strategy type
     * @param parameters constructor parameters encoded as strings
     * @return compact strategy label
     */
    public static String buildLabel(Class<? extends NamedStrategy> type, String... parameters) {
        Objects.requireNonNull(type, "type");
        if (parameters == null || parameters.length == 0) {
            return type.getSimpleName();
        }
        for (int i = 0; i < parameters.length; i++) {
            String parameter = Objects.requireNonNull(parameters[i], "parameters[" + i + "]");
            if (parameter.indexOf('_') >= 0) {
                throw new IllegalArgumentException(
                        "Named strategy parameters cannot contain underscores: parameters[" + i + "]");
            }
        }
        return type.getSimpleName() + '_' + String.join("_", parameters);
    }

    /**
     * Splits a serialized label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list where index {@code 0} is the simple class name
     */
    public static List<String> splitLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Named strategy label cannot be blank");
        }
        return Collections.unmodifiableList(Arrays.asList(label.split("_", -1)));
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory) {
        return buildAllStrategyPermutations(series, parameterPermutations, factory, null);
    }

    /**
     * Builds strategies for every provided parameter permutation.
     *
     * @param series                backing bar series
     * @param parameterPermutations ordered permutations of constructor parameters
     * @param factory               factory responsible for instantiating the
     *                              strategy
     * @param failureHandler        optional handler that receives the parameter
     *                              snapshot alongside the
     *                              {@link IllegalArgumentException} thrown by the
     *                              factory. When {@code null} the exception is
     *                              rethrown.
     * @param <T>                   concrete named strategy type
     * @return list of instantiated strategies
     */
    public static <T extends NamedStrategy> List<Strategy> buildAllStrategyPermutations(BarSeries series,
            Iterable<String[]> parameterPermutations, Factory<T> factory,
            BiConsumer<String[], IllegalArgumentException> failureHandler) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(parameterPermutations, "parameterPermutations");
        Objects.requireNonNull(factory, "factory");

        List<Strategy> strategies = new ArrayList<>();
        for (String[] parameters : parameterPermutations) {
            if (parameters == null) {
                throw new IllegalArgumentException("Parameter entry cannot be null");
            }
            String[] args = Arrays.copyOf(parameters, parameters.length);
            try {
                strategies.add(factory.create(series, args));
            } catch (IllegalArgumentException ex) {
                if (failureHandler == null) {
                    throw ex;
                }
                failureHandler.accept(Arrays.copyOf(args, args.length), ex);
            }
        }
        return strategies;
    }

    /**
     * Helper used by the serialization layer to enforce that a strategy has been
     * registered.
     *
     * @param simpleName named strategy simple class name
     * @return registered type
     */
    public static Class<? extends NamedStrategy> requireRegistered(String simpleName) {
        ensureDefaultRegistryInitialized();
        return lookup(simpleName).orElseThrow(() -> new IllegalArgumentException("Unknown named strategy '" + simpleName
                + "'. Ensure it is registered via NamedStrategy.registerImplementation() or initializeRegistry()."));
    }

    private static void ensureDefaultRegistryInitialized() {
        if (DEFAULT_PACKAGES_INITIALIZED.compareAndSet(false, true)) {
            scanPackages(DEFAULT_SCAN_PACKAGES);
        }
    }

    private static void scanPackages(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            return;
        }
        ClassLoader loader = detectClassLoader();
        for (String basePackage : basePackages) {
            String normalized = normalizePackage(basePackage);
            if (normalized.isEmpty() || !SCANNED_PACKAGES.add(normalized)) {
                continue;
            }
            scanPackage(normalized, loader);
        }
    }

    private static ClassLoader detectClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = NamedStrategy.class.getClassLoader();
        }
        return loader;
    }

    private static String normalizePackage(String basePackage) {
        if (basePackage == null) {
            return "";
        }
        return basePackage.trim().replace('/', '.');
    }

    private static void scanPackage(String basePackage, ClassLoader loader) {
        String path = basePackage.replace('.', '/');
        try {
            Enumeration<URL> resources = loader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        scanDirectory(basePackage, Paths.get(url.toURI()), loader);
                    } else if ("jar".equals(protocol)) {
                        scanJar(basePackage, url, loader);
                    }
                } catch (URISyntaxException ex) {
                    LOGGER.debug("Invalid URI while scanning package {}", basePackage, ex);
                } catch (IOException ex) {
                    LOGGER.debug("Failed to scan package {} from {}", basePackage, url, ex);
                }
            }
        } catch (IOException ex) {
            LOGGER.debug("Unable to enumerate resources for package {}", basePackage, ex);
        }
    }

    private static void scanDirectory(String basePackage, Path directory, ClassLoader loader) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = toClassName(basePackage, directory, path);
                        loadNamedStrategy(className, loader);
                    });
        }
    }

    private static String toClassName(String basePackage, Path root, Path file) {
        Path relative = root.relativize(file);
        String name = relative.toString().replace('/', '.').replace('\\', '.');
        if (name.endsWith(".class")) {
            name = name.substring(0, name.length() - 6);
        }
        if (name.isEmpty()) {
            return basePackage;
        }
        return basePackage + '.' + name;
    }

    private static void scanJar(String basePackage, URL packageUrl, ClassLoader loader) throws IOException {
        java.net.URLConnection connection = packageUrl.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)) {
            return;
        }
        try (JarFile jarFile = jarConnection.getJarFile()) {
            String packagePath = basePackage.replace('.', '/') + '/';
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (!name.endsWith(".class") || !name.startsWith(packagePath)) {
                    continue;
                }
                String className = name.substring(0, name.length() - 6).replace('/', '.');
                loadNamedStrategy(className, loader);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadNamedStrategy(String className, ClassLoader loader) {
        if (className == null || className.isBlank()) {
            return;
        }
        try {
            Class<?> candidate = Class.forName(className, false, loader);
            if (candidate == NamedStrategy.class || candidate.isInterface()
                    || Modifier.isAbstract(candidate.getModifiers())) {
                return;
            }
            if (NamedStrategy.class.isAssignableFrom(candidate)) {
                registerImplementation((Class<? extends NamedStrategy>) candidate);
            }
        } catch (ClassNotFoundException | LinkageError ex) {
            LOGGER.debug("Unable to inspect named strategy class {}", className, ex);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder().withType(SERIALIZED_TYPE).withLabel(getName()).build();
    }

    @Override
    public String toString() {
        return getName();
    }

    /**
     * Factory interface used by
     * {@link #buildAllStrategyPermutations(BarSeries, Iterable, Factory)}.
     *
     * @param <T> concrete named strategy type
     */
    @FunctionalInterface
    public interface Factory<T extends NamedStrategy> {
        T create(BarSeries series, String... parameters);
    }
}
