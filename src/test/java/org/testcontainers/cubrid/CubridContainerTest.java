package org.testcontainers.cubrid;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CubridContainerTest {

    private static final String IMAGE = "cubrid/cubrid:11.4";

    @Test
    void shouldStartAndQuery() throws SQLException {
        try (CubridContainer cubrid = new CubridContainer(IMAGE)) {
            cubrid.start();

            try (Connection conn = DriverManager.getConnection(
                    cubrid.getJdbcUrl(), cubrid.getUsername(), cubrid.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    void shouldUseCustomCredentials() throws SQLException {
        try (CubridContainer cubrid = new CubridContainer(IMAGE)
                .withUsername("foo")
                .withPassword("bar")
                .withDatabaseName("mydb")) {
            cubrid.start();

            assertThat(cubrid.getUsername()).isEqualTo("foo");
            assertThat(cubrid.getPassword()).isEqualTo("bar");
            assertThat(cubrid.getDatabaseName()).isEqualTo("mydb");
            assertThat(cubrid.getJdbcUrl()).contains(":mydb:::");

            try (Connection conn = DriverManager.getConnection(
                    cubrid.getJdbcUrl(), "foo", "bar")) {
                assertThat(conn.isValid(5)).isTrue();
            }
        }
    }

    @Test
    void shouldRejectReservedUsername() {
        assertThatThrownBy(() -> new CubridContainer(IMAGE).withUsername("dba"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("dba");

        assertThatThrownBy(() -> new CubridContainer(IMAGE).withUsername("DBA"))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CubridContainer(IMAGE).withUsername("public"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRunInitScript() throws SQLException {
        try (CubridContainer cubrid = new CubridContainer(IMAGE)
                .withInitScript("init.sql")) {
            cubrid.start();

            try (Connection conn = DriverManager.getConnection(
                    cubrid.getJdbcUrl(), cubrid.getUsername(), cubrid.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT foo FROM bar")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("hello cubrid");
            }
        }
    }

    @Test
    void shouldRejectNullPassword() {
        assertThatThrownBy(() -> new CubridContainer(IMAGE).withPassword(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldUseConfiguredLocale() throws SQLException {
        // The driver encodes text with the JVM default charset unless charSet is set.
        try (CubridContainer cubrid = new CubridContainer(IMAGE)
                .withLocale("ko_KR.utf8")
                .withUrlParam("charSet", "utf-8")) {
            cubrid.start();

            try (Connection conn = DriverManager.getConnection(
                    cubrid.getJdbcUrl(), cubrid.getUsername(), cubrid.getPassword());
                 Statement stmt = conn.createStatement()) {
                assertThat(charset(stmt)).isEqualTo("utf8");

                stmt.execute("CREATE TABLE greeting (msg VARCHAR(100))");
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO greeting VALUES (?)")) {
                    insert.setString(1, "한글 테스트");
                    insert.executeUpdate();
                }
                try (ResultSet rs = stmt.executeQuery("SELECT msg FROM greeting")) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString(1)).isEqualTo("한글 테스트");
                }
            }
        }
    }

    @Test
    void shouldUseImageLocaleByDefault() throws SQLException {
        try (CubridContainer cubrid = new CubridContainer(IMAGE)) {
            cubrid.start();

            assertThat(charset(cubrid)).isEqualTo("iso88591");
        }
    }

    @Test
    void shouldKeepLocaleSetWithEnv() throws SQLException {
        try (CubridContainer cubrid = new CubridContainer(IMAGE).withEnv("CUBRID_LOCALE", "ko_KR.euckr")) {
            cubrid.start();

            assertThat(charset(cubrid)).isEqualTo("euckr");
        }
    }

    @Test
    void shouldRejectEmptyLocale() {
        assertThatThrownBy(() -> new CubridContainer(IMAGE).withLocale(null))
            .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new CubridContainer(IMAGE).withLocale(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static String charset(CubridContainer cubrid) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                cubrid.getJdbcUrl(), cubrid.getUsername(), cubrid.getPassword());
             Statement stmt = conn.createStatement()) {
            return charset(stmt);
        }
    }

    private static String charset(Statement stmt) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("SELECT CHARSET('a')")) {
            assertThat(rs.next()).isTrue();
            return rs.getString(1);
        }
    }
}
