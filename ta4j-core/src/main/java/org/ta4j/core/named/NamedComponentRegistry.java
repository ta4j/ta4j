/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.LinkedHashMap;
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
 * Registration and package-scanning support shared by the
 * {@link org.ta4j.core.rules.named.NamedRule} and
 * {@link org.ta4j.core.strategy.named.NamedStrategy} facades.
 *
 * <p>
 * This type is public only because the two facades live in different packages;
 * it is not an extension point. Register, look up, and label components through
 * the facades instead of constructing registries.
 * </p>
 *
 * <p>
 * Each component family owns one registry instance, configured with its
 * component base type, the default packages to scan, and the message nouns used
 * in validation and lookup errors. The registry maps simple class names to
 * concrete component implementations and discovers implementations by scanning
 * packages on the classpath (both directories and JARs). Scanning is lazy: the
 * default packages are scanned once, on first use, under the registry's
 * initialization lock so concurrent initialization calls wait for an
 * in-progress scan instead of racing.
 * </p>
 *
 * <p>
 * Registrations are validated against the compact-label convention: component
 * class names must be non-blank simple names without the underscore delimiter.
 * Scanned classes that fail validation are skipped with a debug log. Two
 * scanned classes sharing a simple name fail the scan with an
 * {@link IllegalStateException}, because a label could otherwise rebuild a
 * different class depending on classpath order. A failed package adds no
 * registrations and is scanned again on the next initialization; registrations
 * from successful packages and explicit registrations are retained.
 * </p>
 *
 * @param <T> named component base type owned by this registry
 * @since 0.27.1
 */
public final class NamedComponentRegistry<T> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NamedComponentRegistry.class);

    private final Class<T> componentType;
    private final String componentNoun;
    private final String facadeName;
    private final Class<?> loaderFallbackOwner;
    private final String[] defaultScanPackages;

    private final Map<String, Class<? extends T>> registry = new ConcurrentHashMap<>();
    private final Set<String> scannedPackages = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean defaultPackagesInitialized = new AtomicBoolean();
    private final Object initializationLock = new Object();

    /**
     * Creates a registry for one named component family.
     *
     * @param componentType       component base type that discovered classes must
     *                            extend
     * @param componentNoun       lowercase noun used in validation, lookup, and
     *                            scan messages (for example {@code "rule"})
     * @param facadeName          simple name of the facade exposing this registry
     *                            (for example {@code "NamedRule"})
     * @param loaderFallbackOwner type whose {@link ClassLoader} is used when the
     *                            thread context class loader is absent
     * @param defaultScanPackages packages scanned lazily on first registry use
     * @since 0.27.1
     */
    public NamedComponentRegistry(Class<T> componentType, String componentNoun, String facadeName,
            Class<?> loaderFallbackOwner, String... defaultScanPackages) {
        this.componentType = Objects.requireNonNull(componentType, "componentType");
        this.componentNoun = Objects.requireNonNull(componentNoun, "componentNoun");
        this.facadeName = Objects.requireNonNull(facadeName, "facadeName");
        this.loaderFallbackOwner = Objects.requireNonNull(loaderFallbackOwner, "loaderFallbackOwner");
        this.defaultScanPackages = Objects.requireNonNull(defaultScanPackages, "defaultScanPackages").clone();
    }

    /**
     * Ensures the default packages have been scanned and registers any discovered
     * components, then scans the supplied extra packages. Concurrent initialization
     * calls wait for an in-progress scan to finish before returning.
     *
     * @param basePackages optional extra packages to scan
     * @since 0.27.1
     */
    public void initializeRegistry(String... basePackages) {
        ensureDefaultRegistryInitialized();
        if (basePackages == null || basePackages.length == 0) {
            return;
        }
        scanPackages(basePackages);
    }

    /**
     * Registers a named component implementation.
     *
     * @param type component subtype
     * @throws IllegalArgumentException when the type name cannot form a valid
     *                                  compact label
     * @throws IllegalStateException    when a different component is already
     *                                  registered under the same simple name
     * @since 0.27.1
     */
    public void registerImplementation(Class<? extends T> type) {
        Objects.requireNonNull(type, "type");
        String key = buildLabel(type);
        synchronized (initializationLock) {
            checkConflict(key, registry.get(key), type);
            registry.put(key, type);
        }
    }

    private void checkConflict(String name, Class<? extends T> existing, Class<? extends T> type) {
        if (existing != null && existing != type) {
            throw new IllegalStateException("Named " + componentNoun + " already registered for simple name " + name
                    + ": " + existing.getName());
        }
    }

    /**
     * Unregisters a named component implementation. This is primarily intended for
     * tests.
     *
     * @param type component subtype to unregister
     * @return {@code true} when the component was removed, {@code false} when a
     *         different class was registered under the same simple name or no
     *         registration existed
     * @since 0.27.1
     */
    public boolean unregisterImplementation(Class<? extends T> type) {
        Objects.requireNonNull(type, "type");
        synchronized (initializationLock) {
            return registry.remove(type.getSimpleName(), type);
        }
    }

    /**
     * Resolves a registered named component type, initializing the default registry
     * first so components registered through the default package scan are visible
     * to a plain lookup.
     *
     * @param simpleName simple class name
     * @return optional containing the registered type
     * @since 0.27.1
     */
    public Optional<Class<? extends T>> lookup(String simpleName) {
        if (simpleName == null || simpleName.isBlank()) {
            return Optional.empty();
        }
        ensureDefaultRegistryInitialized();
        return Optional.ofNullable(registry.get(simpleName));
    }

    /**
     * Builds a compact label using the simple class name and optional parameters.
     *
     * @param type       concrete component type
     * @param parameters constructor parameters encoded as strings
     * @return compact component label
     * @throws IllegalArgumentException when the component type is anonymous or has
     *                                  a blank simple name, or when the component
     *                                  type or a parameter contains the underscore
     *                                  label delimiter
     * @since 0.27.1
     */
    public String buildLabel(Class<? extends T> type, String... parameters) {
        Objects.requireNonNull(type, "type");
        String simpleName = type.getSimpleName();
        if (simpleName.isBlank()) {
            throw new IllegalArgumentException("Named " + componentNoun
                    + " types must have a non-blank simple name (anonymous classes are not supported): " + type);
        }
        if (simpleName.indexOf('_') >= 0) {
            throw new IllegalArgumentException(
                    "Named " + componentNoun + " class names cannot contain underscores: " + simpleName);
        }
        if (parameters == null || parameters.length == 0) {
            return simpleName;
        }
        for (int i = 0; i < parameters.length; i++) {
            String parameter = Objects.requireNonNull(parameters[i], "parameters[" + i + "]");
            if (parameter.indexOf('_') >= 0) {
                throw new IllegalArgumentException(
                        "Named " + componentNoun + " parameters cannot contain underscores: parameters[" + i + "]");
            }
        }
        return simpleName + '_' + String.join("_", parameters);
    }

    /**
     * Splits a compact label into the simple class name and parameter tokens.
     *
     * @param label serialized label
     * @return immutable token list
     * @throws IllegalArgumentException when the label is blank
     * @since 0.27.1
     */
    public List<String> splitLabel(String label) {
        Objects.requireNonNull(label, "label");
        if (label.isBlank()) {
            throw new IllegalArgumentException("Named " + componentNoun + " label cannot be blank");
        }
        return Collections.unmodifiableList(Arrays.asList(label.split("_", -1)));
    }

    /**
     * Resolves a registered named component type or throws a descriptive error.
     *
     * @param simpleName named component simple class name
     * @return registered type
     * @throws IllegalArgumentException when the simple name is unknown
     * @since 0.27.1
     */
    public Class<? extends T> requireRegistered(String simpleName) {
        ensureDefaultRegistryInitialized();
        return lookup(simpleName).orElseThrow(() -> new IllegalArgumentException(
                "Unknown named " + componentNoun + " '" + simpleName + "'. Ensure it is registered via " + facadeName
                        + ".registerImplementation() or initializeRegistry()."));
    }

    /**
     * Restores the default-scan baseline: clears the scanned-package set and the
     * initialized flag so the next lookup or initialization call re-scans.
     * Registered implementations are not removed. Primarily intended for tests and
     * environments that redefine the classpath between registry uses.
     *
     * @since 0.27.1
     */
    public void resetScanState() {
        synchronized (initializationLock) {
            scannedPackages.clear();
            defaultPackagesInitialized.set(false);
        }
    }

    private void ensureDefaultRegistryInitialized() {
        if (defaultPackagesInitialized.get()) {
            return;
        }
        synchronized (initializationLock) {
            if (!defaultPackagesInitialized.get()) {
                scanPackages(defaultScanPackages);
                defaultPackagesInitialized.set(true);
            }
        }
    }

    private void scanPackages(String... basePackages) {
        if (basePackages == null || basePackages.length == 0) {
            return;
        }
        synchronized (initializationLock) {
            ClassLoader loader = detectClassLoader();
            for (String basePackage : basePackages) {
                String normalized = normalizePackage(basePackage);
                if (normalized.isEmpty() || !scannedPackages.add(normalized)) {
                    continue;
                }
                try {
                    Map<String, Class<? extends T>> discovered = new LinkedHashMap<>();
                    scanPackage(normalized, loader, discovered);
                    for (Map.Entry<String, Class<? extends T>> entry : discovered.entrySet()) {
                        checkConflict(entry.getKey(), registry.get(entry.getKey()), entry.getValue());
                    }
                    registry.putAll(discovered);
                } catch (RuntimeException ex) {
                    scannedPackages.remove(normalized);
                    throw ex;
                }
            }
        }
    }

    private ClassLoader detectClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = loaderFallbackOwner.getClassLoader();
        }
        return loader;
    }

    private static String normalizePackage(String basePackage) {
        if (basePackage == null) {
            return "";
        }
        return basePackage.trim().replace('/', '.');
    }

    private void scanPackage(String basePackage, ClassLoader loader, Map<String, Class<? extends T>> discovered) {
        String path = basePackage.replace('.', '/');
        try {
            Enumeration<URL> resources = loader.getResources(path);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    String protocol = url.getProtocol();
                    if ("file".equals(protocol)) {
                        scanDirectory(basePackage, Paths.get(url.toURI()), loader, discovered);
                    } else if ("jar".equals(protocol)) {
                        scanJar(basePackage, url, loader, discovered);
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

    private void scanDirectory(String basePackage, Path directory, ClassLoader loader,
            Map<String, Class<? extends T>> discovered) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(directory)) {
            stream.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".class"))
                    .forEach(path -> {
                        String className = toClassName(basePackage, directory, path);
                        loadComponent(className, loader, discovered);
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

    private void scanJar(String basePackage, URL packageUrl, ClassLoader loader,
            Map<String, Class<? extends T>> discovered) throws IOException {
        java.net.URLConnection connection = packageUrl.openConnection();
        if (!(connection instanceof JarURLConnection jarConnection)) {
            return;
        }
        jarConnection.setUseCaches(false);
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
                loadComponent(className, loader, discovered);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void loadComponent(String className, ClassLoader loader, Map<String, Class<? extends T>> discovered) {
        if (className == null || className.isBlank()) {
            return;
        }
        try {
            Class<?> candidate = Class.forName(className, false, loader);
            if (candidate == componentType || candidate.isInterface()
                    || Modifier.isAbstract(candidate.getModifiers())) {
                return;
            }
            if (componentType.isAssignableFrom(candidate)) {
                Class<? extends T> type = (Class<? extends T>) candidate;
                String key = buildLabel(type);
                checkConflict(key, discovered.get(key), type);
                discovered.put(key, type);
            }
        } catch (ClassNotFoundException | LinkageError ex) {
            LOGGER.debug("Unable to inspect named {} class {}", componentNoun, className, ex);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("Skipping invalid named {} class {}", componentNoun, className, ex);
        }
    }
}
