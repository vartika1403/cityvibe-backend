package com.cityvibe.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Fails fast when the prod profile is active but its required environment variables are missing.
 *
 * <p>Spring's binder leaves unresolvable placeholders as literal text, so a missing DB_URL reaches
 * the JDBC driver as the string "${DB_URL}" and surfaces as an opaque Flyway/Hikari failure. This
 * check runs before the context is created and names the missing variables instead.
 */
public class RequiredProdPropertiesValidator implements EnvironmentPostProcessor {

    private static final String[] REQUIRED_IN_PROD = {"DB_URL", "DB_USERNAME", "DB_PASSWORD"};

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(profiles -> profiles.test("prod"))) {
            return;
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
                        + ". Set them before starting, or run with the dev profile "
                        + "(SPRING_PROFILES_ACTIVE=dev) to use the local MySQL defaults.");
    }
}
