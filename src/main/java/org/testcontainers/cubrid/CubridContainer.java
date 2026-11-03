package org.testcontainers.cubrid;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Testcontainers implementation for CUBRID.
 * <p>
 * Supported image: {@code cubrid/cubrid}
 * <p>
 * Exposed ports:
 * <ul>
 *     <li>Broker (JDBC): 33000</li>
 * </ul>
 */
public class CubridContainer extends JdbcDatabaseContainer<CubridContainer> {

    static final String NAME = "cubrid";
    static final String IMAGE = "cubrid/cubrid";
    static final String DEFAULT_TAG = "11.4";

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse(IMAGE);
    private static final int BROKER_PORT = 33000;
    private static final int OUTPUT_LINES = 5;

    private String databaseName = "testdb";
    private String username = "testuser";
    private String password = "testpass";

    /**
     * Creates a new CUBRID container from the given image name.
     *
     * @param dockerImageName the docker image name, e.g. {@code "cubrid/cubrid:11.4"}
     */
    public CubridContainer(String dockerImageName) {
        this(DockerImageName.parse(dockerImageName));
    }

    /**
     * Creates a new CUBRID container from the given image name.
     *
     * @param dockerImageName the docker image, must be compatible with {@code cubrid/cubrid}
     */
    public CubridContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
        addExposedPort(BROKER_PORT);
    }

    @Override
    protected void configure() {
        addEnv("CUBRID_DB", databaseName);
        addEnv("CUBRID_USER", username);
        addEnv("CUBRID_PASSWORD", password);
    }

    @Override
    public String getDriverClassName() {
        return "cubrid.jdbc.driver.CUBRIDDriver";
    }

    @Override
    public String getJdbcUrl() {
        String params = constructUrlParameters("?", "&");
        return "jdbc:cubrid:" + getHost() + ":" + getMappedPort(BROKER_PORT) + ":"
            + databaseName + ":::" + params;
    }

    @Override
    public String getDatabaseName() {
        return databaseName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    protected String getTestQueryString() {
        return "SELECT 1";
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void waitUntilContainerStarted() {
        logger().info(
            "Waiting for database connection to become available at {} using query '{}'",
            getJdbcUrl(),
            getTestQueryString()
        );

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(getStartupTimeoutSeconds());
        Exception lastConnectionException = null;
        while (System.nanoTime() < deadline) {
            String output = getLogs();
            if (!isRunning()) {
                throw new IllegalStateException(
                    "CUBRID container stopped during startup:\n" + lastLines(output)
                );
            }
            String failure = startupFailure(output);
            if (failure != null) {
                throw new IllegalStateException("CUBRID failed to start:\n" + failure);
            }
            // One attempt per pass: createConnection() would retry for connectTimeoutSeconds.
            try (Connection connection = connect();
                 Statement statement = connection.createStatement()) {
                if (statement.execute(getTestQueryString())) {
                    return;
                }
            } catch (NoDriverFoundException e) {
                throw e;
            } catch (Exception e) {
                lastConnectionException = e;
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for CUBRID to start", e);
            }
        }
        throw new IllegalStateException(
            String.format(
                "Container is started, but cannot be accessed by (JDBC URL: %s), "
                    + "please check container logs",
                getJdbcUrl()
            ),
            lastConnectionException
        );
    }

    private Connection connect() throws SQLException {
        Properties properties = new Properties();
        properties.put("user", getUsername());
        properties.put("password", getPassword());
        return getJdbcDriverInstance().connect(getJdbcUrl(), properties);
    }

    @Override
    public CubridContainer withDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isEmpty()) {
            throw new IllegalArgumentException("Database name cannot be null or empty.");
        }
        this.databaseName = databaseName;
        return self();
    }

    @Override
    public CubridContainer withUsername(String username) {
        if (username == null || username.isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty.");
        }
        if ("dba".equalsIgnoreCase(username) || "public".equalsIgnoreCase(username)) {
            throw new IllegalArgumentException(
                "Username cannot be 'dba' or 'public' (CUBRID reserved)."
            );
        }
        this.username = username;
        return self();
    }

    @Override
    public CubridContainer withPassword(String password) {
        if (password == null) {
            throw new IllegalArgumentException("Password cannot be null.");
        }
        this.password = password;
        return self();
    }

    /**
     * Sets the locale the database is created with, as {@code <language>_<territory>[.<charset>]},
     * for example {@code ko_KR.utf8}. The locale fixes the database charset and cannot be changed
     * once the database exists. Defaults to the image's locale, {@code en_US}, which means
     * {@code en_US.iso88591}.
     * <p>
     * The built-in locales are {@code en_US.utf8}, {@code en_US.iso88591}, {@code ko_KR.utf8},
     * {@code ko_KR.euckr}, {@code ko_KR.iso88591}, {@code tr_TR.utf8} and {@code tr_TR.iso88591}.
     * Other locales need an image with compiled locale libraries. With a locale the image does not
     * support, the database is not created and the container fails to start with CUBRID's error
     * message.
     *
     * @param locale the CUBRID locale name
     * @return this container
     * @throws IllegalArgumentException if {@code locale} is null or empty
     */
    public CubridContainer withLocale(String locale) {
        if (locale == null || locale.isEmpty()) {
            throw new IllegalArgumentException("Locale cannot be null or empty.");
        }
        addEnv("CUBRID_LOCALE", locale);
        return self();
    }

    static String startupFailure(String output) {
        List<String> lines = nonBlankLines(output);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.startsWith("Couldn't create database.") || line.startsWith("ERROR:")
                    || (line.startsWith("++ ") && line.endsWith(": fail"))) {
                int end = Math.min(i + OUTPUT_LINES, lines.size());
                return String.join("\n", lines.subList(i, end));
            }
        }
        return null;
    }

    private static String lastLines(String output) {
        List<String> lines = nonBlankLines(output);
        int start = Math.max(0, lines.size() - OUTPUT_LINES);
        return String.join("\n", lines.subList(start, lines.size()));
    }

    private static List<String> nonBlankLines(String output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }
}
