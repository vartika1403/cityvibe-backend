package com.cityvibe.config;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

/**
 * Prepares the environment for the prod profile: loads a local {@code prod.env} if one exists, then
 * fails fast when the datasource variables are still missing.
 *
 * <p>Loading happens here rather than through {@code spring.config.import} because that resolves
 * relative paths against the process working directory, so the file was found when launching from
 * the repository root and silently skipped from an IDE run configuration or a jar started
 * elsewhere. This searches the working directory and the directory holding the running classes or
 * jar, plus a few parents of each, so the launch location stops mattering.
 *
 * <p>The file is added as the lowest-precedence property source: real environment variables and
 * system properties continue to win, which is what production platforms rely on.
 *
 * <p>Without the file, an unresolvable {@code ${DB_URL}} would not fail on its own — Spring's
 * binder leaves unresolved placeholders as literal text, so the string reaches the JDBC driver and
 * surfaces as an opaque Flyway error. The check below names the missing variables instead.
 */
public class ProdEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String FILE_NAME = "prod.env";
    private static final String[] REQUIRED_IN_PROD = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"};

    /** How far to walk up from each starting directory (covers target/classes -> project root). */
    private static final int MAX_PARENTS = 3;

    private final Log log;

    public ProdEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(getClass());
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(profiles -> profiles.test("prod"))) {
            return;
        }
        loadEnvFile(environment);
        checkRequiredProperties(environment);
    }

    private void loadEnvFile(ConfigurableEnvironment environment) {
        for (Path dir : candidateDirectories()) {
            Path file = dir.resolve(FILE_NAME);
            if (!Files.isRegularFile(file)) {
                continue;
            }
            Properties properties = new Properties();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException ex) {
                throw new IllegalStateException("Failed to read " + file, ex);
            }
            environment.getPropertySources()
                    .addLast(new PropertiesPropertySource("prodEnvFile: " + file, properties));
            log.info("Loaded prod environment values from " + file);
            return;
        }
        log.debug("No " + FILE_NAME + " found; relying on environment variables");
    }

    /** Working directory first, then wherever the running classes or jar live, parents included. */
    private List<Path> candidateDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        addWithParents(directories, Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath());

        Path codeLocation = codeSourceDirectory();
        if (codeLocation != null) {
            addWithParents(directories, codeLocation);
        }
        return new ArrayList<>(directories);
    }

    private void addWithParents(Set<Path> directories, Path start) {
        Path current = start.normalize();
        for (int i = 0; current != null && i <= MAX_PARENTS; i++) {
            directories.add(current);
            current = current.getParent();
        }
    }

    /** Directory of the jar or classes tree this code was loaded from, or null if undeterminable. */
    private Path codeSourceDirectory() {
        try {
            CodeSource codeSource = getClass().getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                return null;
            }
            // Inside a Boot fat jar the location is wrapped, and the exact shape varies by
            // version: "jar:file:/opt/app.jar!/BOOT-INF/classes!/" on older Boot, and
            // "jar:nested:/opt/app.jar/!BOOT-INF/classes/!/" from Boot 3.2's nested protocol.
            // Neither converts through Paths.get(URI), so unwrap down to the jar's own path.
            String location = codeSource.getLocation().toString();
            for (String wrapper : new String[] {"jar:", "nested:"}) {
                if (location.startsWith(wrapper)) {
                    location = location.substring(wrapper.length());
                }
            }
            int nested = location.indexOf('!');
            if (nested >= 0) {
                location = location.substring(0, nested);
            }
            Path path = location.startsWith("file:")
                    ? Paths.get(new URI(location))
                    : Paths.get(location);
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (URISyntaxException | IllegalArgumentException | SecurityException
                | FileSystemNotFoundException ex) {
            // An exotic classloader may have no file-backed location at all.
            return null;
        }
    }

    private void checkRequiredProperties(ConfigurableEnvironment environment) {
        List<String> searched = new ArrayList<>();
        for (Path dir : candidateDirectories()) {
            searched.add(dir.toString());
        }
        List<String> missing = new ArrayList<>();
        for (String name : REQUIRED_IN_PROD) {
            String value = environment.getProperty(name);
            if (value == null || value.isBlank()) {
                missing.add(name);
            }
        }
        if (missing.isEmpty()) {
            return;
        }
        throw new IllegalStateException(
                "The 'prod' profile requires these environment variables, which are missing or blank: "
                        + String.join(", ", missing)
                        + ". Set them, add them to a " + FILE_NAME + " file in one of the directories "
                        + "searched below, or run with the dev profile (SPRING_PROFILES_ACTIVE=dev) "
                        + "to use the local MySQL defaults. Searched for " + FILE_NAME + " in: "
                        + String.join(", ", searched));
    }
}
