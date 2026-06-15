package com.shopbilling.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String datasourceUrl = environment.getProperty("SPRING_DATASOURCE_URL");
        String databaseUrl = firstText(datasourceUrl, environment.getProperty("DATABASE_URL"),
                environment.getProperty("JDBC_DATABASE_URL"));
        if (!hasText(databaseUrl)) {
            return;
        }

        Map<String, Object> datasource = new HashMap<>();
        if (databaseUrl.startsWith("jdbc:postgresql://")) {
            datasource.put("spring.datasource.url", databaseUrl);
        } else if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            applyPostgresUrl(databaseUrl, datasource, environment);
        }

        if (!datasource.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("renderDatabaseUrl", datasource));
        }
    }

    private void applyPostgresUrl(String databaseUrl, Map<String, Object> datasource, ConfigurableEnvironment environment) {
        URI uri = URI.create(databaseUrl);
        StringBuilder jdbcUrl = new StringBuilder("jdbc:postgresql://")
                .append(uri.getHost());
        if (uri.getPort() > 0) {
            jdbcUrl.append(':').append(uri.getPort());
        }
        jdbcUrl.append(uri.getRawPath() == null ? "" : uri.getRawPath());
        if (hasText(uri.getRawQuery())) {
            jdbcUrl.append('?').append(uri.getRawQuery());
        }

        datasource.put("spring.datasource.url", jdbcUrl.toString());
        if (hasText(uri.getUserInfo())) {
            String[] userInfo = uri.getUserInfo().split(":", 2);
            if (!hasText(environment.getProperty("SPRING_DATASOURCE_USERNAME"))) {
                datasource.put("spring.datasource.username", decode(userInfo[0]));
            }
            if (userInfo.length > 1 && !hasText(environment.getProperty("SPRING_DATASOURCE_PASSWORD"))) {
                datasource.put("spring.datasource.password", decode(userInfo[1]));
            }
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
