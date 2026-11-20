package com.akhana.akhana_admin.testdb;

import org.flywaydb.core.Flyway;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prepares the test database before the first Spring test context of the run: creates it when it does not exist and
 * empties it with Flyway clean (the application's Flyway then migrates it from scratch). Later contexts of the same
 * run reuse it as it is. A database whose name does not end in _test or _test_&lt;something&gt; is never touched.
 */
public class TestDatabaseContextCustomizerFactory implements ContextCustomizerFactory {

    private static final Pattern POSTGRES_URL = Pattern.compile("^(jdbc:postgresql://[^/]+/)([^?;/]+)(.*)$");
    private static final Pattern TEST_NAME = Pattern.compile("^[a-z0-9_]+_test(_[a-z0-9_]+)?$");

    private static boolean prepared;

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attributes) {
        return Customizer.INSTANCE;
    }

    /** The database name of a PostgreSQL JDBC URL, only when it is a test database; otherwise it fails. */
    public static String requireTestDatabase(String url) {
        Matcher matcher = url == null ? null : POSTGRES_URL.matcher(url);
        if (matcher == null || !matcher.matches()) {
            throw new IllegalStateException("Tests need a PostgreSQL test database, got: " + url);
        }
        String name = matcher.group(2);
        if (!TEST_NAME.matcher(name).matches()) {
            throw new IllegalStateException("Refusing to use database '" + name
                + "' for tests: its name must end in _test or _test_<something> (set TEST_DB_NAME)");
        }
        return name;
    }

    /** True once this run has created (if needed) and emptied the test database. */
    public static synchronized boolean preparedThisRun() {
        return prepared;
    }

    private static synchronized void prepare(Environment environment) {
        String url = environment.getProperty("spring.datasource.url");
        if (prepared || url == null) {
            return;
        }
        String name = requireTestDatabase(url);
        String user = environment.getProperty("spring.datasource.username");
        String password = environment.getProperty("spring.datasource.password");
        createIfMissing(url, name, user, password);
        Flyway.configure().dataSource(url, user, password).cleanDisabled(false).load().clean();
        prepared = true;
    }

    private static void createIfMissing(String url, String name, String user, String password) {
        Matcher matcher = POSTGRES_URL.matcher(url);
        matcher.matches();
        String maintenance = matcher.group(1) + "postgres" + matcher.group(3);
        try (Connection connection = DriverManager.getConnection(maintenance, user, password);
             PreparedStatement exists = connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
            exists.setString(1, name);
            try (ResultSet rows = exists.executeQuery()) {
                if (rows.next()) {
                    return;
                }
            }
            try (Statement create = connection.createStatement()) {
                // name is checked against [a-z0-9_] by requireTestDatabase
                create.execute("CREATE DATABASE " + name);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not create the test database " + name, e);
        }
    }

    private static final class Customizer implements ContextCustomizer {

        static final Customizer INSTANCE = new Customizer();

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
            prepare(context.getEnvironment());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Customizer;
        }

        @Override
        public int hashCode() {
            return Customizer.class.hashCode();
        }
    }
}
