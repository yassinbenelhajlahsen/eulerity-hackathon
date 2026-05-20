package com.eulerity.taskmanager.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads KEY=VALUE pairs from a .env file in the working directory and exposes
 * them as a Spring property source. Ordered AFTER the OS environment so that
 * a real exported variable still wins over .env — matching how docker-compose
 * and most dotenv tools behave. .env is optional; absent or malformed lines
 * are silently skipped (this is a developer convenience, not a hard contract).
 *
 * Registered via META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports
 * so it runs before any @Configuration class sees the environment.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String SOURCE_NAME = "dotenv";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        Path envFile = Paths.get(".env");
        if (!Files.isRegularFile(envFile)) {
            return;
        }
        Map<String, Object> values = parse(envFile);
        if (values.isEmpty()) {
            return;
        }
        // Insert after the OS environment so real exported vars still take precedence.
        env.getPropertySources().addAfter(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                new MapPropertySource(SOURCE_NAME, values));
    }

    /** Package-private for unit testing. */
    static Map<String, Object> parse(Path envFile) {
        Map<String, Object> out = new HashMap<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile);
        } catch (IOException e) {
            return out;
        }
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring("export ".length()).trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            if (value.length() >= 2 && (
                    (value.startsWith("\"") && value.endsWith("\"")) ||
                    (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            out.put(key, value);
        }
        return out;
    }
}
