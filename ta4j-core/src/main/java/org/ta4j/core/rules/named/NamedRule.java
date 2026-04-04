/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Rule;
import org.ta4j.core.rules.AbstractRule;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Base class for rules that can be reconstructed from compact name tokens.
 *
 * <p>
 * Named rules follow the same compact-label convention as
 * {@code NamedStrategy}: the label starts with the simple class name followed
 * by underscore-delimited constructor parameters. The label is fixed for the
 * lifetime of the rule and is used as the canonical rule name in logs, JSON,
 * and CLI inputs.
 * </p>
 *
 * <p>
 * Implementations must provide a {@code (BarSeries, String...)} constructor
 * that parses the compact label parameters and delegates to a strongly typed
 * constructor. The strongly typed constructor should call
 * {@link #NamedRule(String)} with a label generated via
 * {@link #buildLabel(Class, String...)}.
 * </p>
 *
 * <p>
 * Unlike {@code NamedStrategy}, named rules serialize as their concrete rule
 * type while preserving the compact label as the rule name. This keeps rule
 * serialization compatible with the existing rule descriptor format.
 * </p>
 *
 * @since 0.22.7
 */
public abstract class NamedRule extends AbstractRule {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedRule.class);
    private static final Map<String, Class<? extends NamedRule>> REGISTRY = new ConcurrentHashMap<>();
    private static final String[] DEFAULT_SCAN_PACKAGES = { "org.ta4j.core.rules.named" };
    private static final Set<String> SCANNED_PACKAGES = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean DEFAULT_PACKAGES_INITIALIZED = new AtomicBoolean();

    private final String label;

    /**
     * Protected constructor that fixes the rule label.
     *
     * @param label compact label used for lookup and serialization
     */
    protected NamedRule(String label) {
        this.label = validateLabel(label);
        registerImplementation(getClass());
    }

    /**
     * Ensures core packages have been scanned and registers any discovered named
     * rules.
     *
     * @param basePackages optional extra packages to scan
     */
    public static void initializeRegistry(String... basePackages) {
        ensureDefaultRegistryInitialized();
        if (basePackages == null || basePackages.length == 0) {
            return;
        }
        scanPackages(basePackages);
    }

    /**
     * Registers a named rule implementation.
     *
     * @param type named rule subtype
     * @since 0.22.7
     */
    public static void registerImplementation(Class<? extends NamedRule> type) {
        Objects.requireNonNull(type, "type");
        String key = type.getSimpleName();
        REGISTRY.compute(key, (name, existing) -> {
            if (existing != null && existing != type) {
                throw new IllegalStateException(
                        "Named rule already registered for simple name " + name + ": " + existing.getName());
            }
            return type;
        });
    }

    /**
     * Unregisters a named rule implementation. This is primarily intended for
     * tests.
     *
     * @param type named rule subtype
     * @return {@code true} when the rule was removed
     * @since 0.22.7
     */
    public static boolean unregisterImplementation(Class<? extends NamedRule> type) {
        Objects.requireNonNull(type, "type");
        String key = type.getSimpleName();
        Class<? extends NamedRule> removed = REGISTRY.remove(key);
        return removed != null && removed == type;
    }

    /**
     * Resolves a previously registered named rule type.
     *
     * @param simpleName simple class name
     * @return optional containing the registered type
     * @since 0.22.7
     */
    public static Optional<Class<? extends NamedRule>> lookup(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(REGISTRY.get(simpleName));
    }

    /**
     * Builds a compact label using the simple class name and optional parameters.
     *
     * @param type       concrete named rule type
     * @param parameters constructor parameters encoded as strings
     * @return compact rule label
     * @since 0.22.7
     */
    public static String buildLabel(Class<? extends NamedRule> type, String... parameters) {
        Objects.requireNonNull(type, "type");
        if (parameters == null || parameters.length == 0) {
            return type.getSimpleName();
        }
        for (int i = 0; i < parameters.length; i++) {
            String parameter = Objects.requireNonNull(parameters[i], "parameters[" + i + "]");
            if (parameter.indexOf('_') >= 0) {
                throw new IllegalArgumentException(
                        "Named rule parameters cannot contain underscores: parameters[" + i + "]");
            }
        }
        return type.getSimpleName() + '_' + String.join("_", parameters);
    }

    /**
     * Splits a compact label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list
     * @since 0.22.7
     */
    public static List<String> splitLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Named rule label cannot be blank");
        }
        return Collections.unmodifiableList(Arrays.asList(label.split("_", -1)));
    }

    /**
     * Resolves a registered named rule type or throws a descriptive error.
     *
     * @param simpleName named rule simple class name
     * @return registered type
     * @since 0.22.7
     */
    public static Class<? extends NamedRule> requireRegistered(String simpleName) {
        ensureDefaultRegistryInitialized();
        return lookup(simpleName).orElseThrow(() -> new IllegalArgumentException("Unknown named rule '" + simpleName
                + "'. Ensure it is registered via NamedRule.registerImplementation() or initializeRegistry()."));
    }

    @Override
    public final void setName(String name) {
        // NamedRule labels are reconstruction-critical and stay fixed.
    }

    @Override
    public final String getName() {
        return label;
    }

    @Override
    public boolean hasCustomName() {
        return true;
    }

    @Override
    protected final String createDefaultName() {
        return label;
    }

    private static String validateLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Named rule label cannot be blank");
        }
        return label;
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
            loader = NamedRule.class.getClassLoader();
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
                        loadNamedRule(className, loader);
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
                loadNamedRule(className, loader);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void loadNamedRule(String className, ClassLoader loader) {
        if (className == null || className.isBlank()) {
            return;
        }
        try {
            Class<?> candidate = Class.forName(className, false, loader);
            if (candidate == NamedRule.class || candidate.isInterface()
                    || Modifier.isAbstract(candidate.getModifiers())) {
                return;
            }
            if (NamedRule.class.isAssignableFrom(candidate)) {
                registerImplementation((Class<? extends NamedRule>) candidate);
            }
        } catch (ClassNotFoundException | LinkageError ex) {
            LOGGER.debug("Unable to inspect named rule class {}", className, ex);
        }
    }
}
