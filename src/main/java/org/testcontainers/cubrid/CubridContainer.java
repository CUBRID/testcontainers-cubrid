package org.testcontainers.cubrid;

import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.utility.DockerImageName;

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
    static final String DEFAULT_LOCALE = "en_US.utf8";

    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse(IMAGE);
    private static final int BROKER_PORT = 33000;

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
        addEnv("CUBRID_LOCALE", DEFAULT_LOCALE);
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
     * once the database exists. Defaults to {@code en_US.utf8}; {@code en_US} alone means
     * {@code en_US.iso88591}.
     * <p>
     * The built-in locales are {@code en_US.utf8}, {@code en_US.iso88591}, {@code ko_KR.utf8},
     * {@code ko_KR.euckr}, {@code ko_KR.iso88591}, {@code tr_TR.utf8} and {@code tr_TR.iso88591}.
     * Other locales need an image with compiled locale libraries. With a locale the image does not
     * support, the database is not created and the container fails to start after the startup
     * timeout.
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
}
