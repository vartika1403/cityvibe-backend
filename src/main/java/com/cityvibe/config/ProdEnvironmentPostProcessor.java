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
 * Loads a local {@code prod.env} into the environment when the prod profile is active.
 *
 * <p>This is how secrets without defaults — {@code SERPAPI_API_KEY}, {@code ADMIN_SEED_TOKEN} —
 * reach a local prod run. The datasource properties fall back to localhost in
 * {@code application-prod.properties}, so they work without this file.
 *
 * <p>Loading happens here rather than through {@code spring.config.import} because that resolves
 * relative paths against the process working directory, so the file was found when launching from
 * the repository root and silently skipped from an IDE run configuration or a jar started
 * elsewhere. This searches the working directory and the directory holding the running classes or
 * jar, plus a few parents of each, so the launch location stops mattering.
 *
 * <p>The file is added as the lowest-precedence property source: real environment variables,
 * system properties and command-line arguments continue to win, which is what production platforms
 * rely on.
 */
public class ProdEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String FILE_NAME = "prod.env";

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
        logDatasourceTarget(environment);
    }

    /**
     * Reports the database this run will use, before Flyway tries to reach it.
     *
     * <p>A failed connection surfaces as "Communications link failure" without naming the host, so
     * a deployment missing DB_URL looks identical to one pointing at an unreachable server. The
     * datasource URL falls back to localhost, which no deployed container can reach, so that case
     * is called out explicitly rather than left to be inferred.
     */
    private void logDatasourceTarget(ConfigurableEnvironment environment) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            return;
        }
        String safe = url.replaceAll("(?i)(password=)[^&]*", "$1****");
        log.info("Datasource target: " + safe);

        String configured = environment.getProperty("DB_URL");
        if (configured == null || configured.isBlank()) {
            log.warn("DB_URL is not set, so the prod profile fell back to the local default above. "
                    + "A deployed container has no database at that address; set DB_URL on the "
                    + "service to point at the real one.");
        }
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
}
