package com.oneshop;

import com.microsoft.sqlserver.jdbc.SQLServerDriver;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/** Loaded for every SpringApplication in the test JVM, before any beans, DDL or scheduler can run. */
public final class TestDatabaseSafetyInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        Environment environment = context.getEnvironment();
        requireTestUrl(environment.getProperty("spring.datasource.url"));
        String hikariUrl = environment.getProperty("spring.datasource.hikari.jdbc-url");
        if (hikariUrl != null) requireTestUrl(hikariUrl);
        if (environment.containsProperty("spring.datasource.jndi-name")) {
            throw new IllegalStateException("Tests must use an explicit isolated SQL Server datasource, not JNDI.");
        }
        String ddl = environment.getProperty("spring.jpa.hibernate.ddl-auto", "none").toLowerCase(Locale.ROOT);
        if (!ddl.equals("none") && !ddl.equals("validate")) {
            throw new IllegalStateException("Test Hibernate may only validate the SQL-owned schema (or use none for mocks).");
        }
    }

    static void requireTestUrl(String url) {
        try {
            if (url == null || !url.startsWith("jdbc:sqlserver://")) throw refused();
            // Let the actual JDBC driver parse aliases, duplicate properties and escaped values.
            for (var property : new SQLServerDriver().getPropertyInfo(url, new Properties())) {
                if (property.name.equalsIgnoreCase("databaseName") && property.value != null
                        && property.value.toLowerCase(Locale.ROOT).matches("oneshop[a-z0-9_]*_test")) return;
            }
        } catch (SQLException ex) {
            // Never echo a JDBC URL: it can contain passwords.
            throw refused();
        }
        throw refused();
    }

    private static IllegalStateException refused() {
        return new IllegalStateException("Refusing test startup: select an explicit oneshop*_test database; operational databases are forbidden.");
    }
}
