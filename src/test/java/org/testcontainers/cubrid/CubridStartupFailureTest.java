package org.testcontainers.cubrid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CubridStartupFailureTest {

    private static final String IMAGE = "cubrid/cubrid:11.4";

    private static final String NORMAL_OUTPUT = String.join("\n",
        "Starting CUBRID All Services...",
        "Initializing database 'testdb'",
        "Creating database with 128.0M size using locale en_US. The total amount of disk space needed is 1.1G.",
        "CUBRID 11.4.6",
        "Execute OK. (0.018000 sec) Committed. (0.004000 sec) ",
        "@ cubrid broker start",
        "++ cubrid broker start: success",
        "@ cubrid server start: testdb",
        "",
        "This may take a long time depending on the amount of recovery works to do.",
        "CUBRID 11.4.6",
        "++ cubrid server start: success",
        "CUBRID 11.4.6 (11.4.6.1963-0e7d3c1) (64bit release build for Linux) (Sep  7 2026 17:45:11)");

    private static final String HA_OUTPUT = String.join("\n",
        "Starting CUBRID HA...",
        "Initializing database 'hatest'",
        "Execute OK. (0.018000 sec) Committed. (0.004000 sec) ",
        "@ cubrid broker start",
        "++ cubrid broker start: success",
        "@ cubrid heartbeat start",
        "@ cubrid master start",
        "++ cubrid master start: success",
        "@ HA processes start",
        "@ cubrid server start: hatest",
        "++ cubrid server start: success",
        "@ copylogdb start",
        "++ copylogdb start: success",
        "@ applylogdb start",
        "++ applylogdb start: success",
        "++ HA processes start: success",
        "++ cubrid heartbeat start: success");

    private static final String LOCALE_OUTPUT = String.join("\n",
        "Starting CUBRID All Services...",
        "Initializing database 'cubdb'",
        "Couldn't create database.",
        "Locale initialization: Locale xx_XX.utf8 was not loaded.",
        " xx_XX not found in cubrid_locales.txt.",
        "Creating database with 128.0M size using locale xx_XX.utf8. The total amount of disk space needed is 1.1G.",
        "@ cubrid broker start",
        "++ cubrid broker start: success",
        "@ cubrid server start: cubdb",
        "++ cubrid server start: fail");

    private static final String CSQL_OUTPUT = String.join("\n",
        "CUBRID 11.4.6",
        "In line 1, column 31,",
        "ERROR: In line 1, column 31 before '';'",
        "Syntax error: unexpected 's', expecting ';'",
        "CUBRID 11.4.6 (11.4.6.1963-0e7d3c1) (64bit release build for Linux) (Sep  7 2026 17:45:11)");

    private static final String SERVER_FAIL_OUTPUT = String.join("\n",
        "@ cubrid server start: testdb",
        "++ cubrid server start: fail");

    @Test
    void shouldFindNoFailureInNormalOutput() {
        assertThat(CubridContainer.startupFailure(NORMAL_OUTPUT)).isNull();
        assertThat(CubridContainer.startupFailure(HA_OUTPUT)).isNull();
    }

    @Test
    void shouldReportFailureLines() {
        assertThat(CubridContainer.startupFailure(LOCALE_OUTPUT))
            .startsWith("Couldn't create database.")
            .contains("Locale xx_XX.utf8 was not loaded.")
            .hasLineCount(5);
        assertThat(CubridContainer.startupFailure(CSQL_OUTPUT))
            .startsWith("ERROR:")
            .contains("Syntax error");
        assertThat(CubridContainer.startupFailure(SERVER_FAIL_OUTPUT))
            .isEqualTo("++ cubrid server start: fail");
    }

    @Test
    @Timeout(60)
    void shouldFailFastOnUnsupportedLocale() {
        try (CubridContainer cubrid = new CubridContainer(IMAGE).withLocale("xx_XX.utf8")) {
            assertThatThrownBy(cubrid::start)
                .rootCause()
                .hasMessageContaining("CUBRID failed to start")
                .hasMessageContaining("Couldn't create database");
        }
    }

    @Test
    @Timeout(60)
    void shouldFailFastWhenUserCannotBeCreated() {
        try (CubridContainer cubrid = new CubridContainer(IMAGE).withPassword("it's")) {
            assertThatThrownBy(cubrid::start)
                .rootCause()
                .hasMessageContaining("CUBRID failed to start")
                .hasMessageContaining("ERROR:");
        }
    }

    @Test
    @Timeout(60)
    void shouldFailFastWhenContainerStops() {
        try (CubridContainer cubrid = new CubridContainer(IMAGE)
                .withCommand("bash", "-c", "sleep 2; exit 3")) {
            assertThatThrownBy(cubrid::start)
                .rootCause()
                .hasMessageContaining("CUBRID container stopped during startup");
        }
    }
}
