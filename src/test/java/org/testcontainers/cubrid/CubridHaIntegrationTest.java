package org.testcontainers.cubrid;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startables;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Verifies CUBRID HA cluster behavior using only the testcontainers-cubrid
 * module and standard Testcontainers APIs. Each test forms a fresh two-node
 * cluster driven by the image's built-in HA support
 * ({@code CUBRID_COMPONENTS=HA}) and exercises one HA property:
 * cluster formation, data replication, and automatic failover.
 */
class CubridHaIntegrationTest {

    private static final String IMAGE = "cubrid/cubrid:11.4";
    private static final String DB = "hatest";
    private static final String PEERS = "master:slave";

    @Test
    void shouldFormHaCluster() throws Exception {
        try (Network network = Network.newNetwork();
             CubridContainer master = haNode(network, "master");
             CubridContainer slave = haNode(network, "slave")) {

            Startables.deepStart(master, slave).join();

            awaitHbStatus(master, "HA-Node Info", Duration.ofMinutes(1));
            awaitHbStatus(slave, "HA-Node Info", Duration.ofMinutes(1));
        }
    }

    @Test
    void shouldReplicateDataAcrossHaCluster() throws Exception {
        try (Network network = Network.newNetwork();
             CubridContainer master = haNode(network, "master");
             CubridContainer slave = haNode(network, "slave")) {

            Startables.deepStart(master, slave).join();
            awaitHbStatus(master, "(current master, state master)", Duration.ofMinutes(3));

            executeUpdates(master,
                "CREATE TABLE replica_test (id INT PRIMARY KEY, msg VARCHAR(100))",
                "INSERT INTO replica_test VALUES (1, 'hello from master')");

            String replicated = awaitReplicatedRow(
                slave, "SELECT msg FROM replica_test WHERE id = 1", Duration.ofSeconds(60));
            assertThat(replicated).isEqualTo("hello from master");
        }
    }

    @Test
    void shouldPromoteSlaveWhenMasterFails() throws Exception {
        try (Network network = Network.newNetwork();
             CubridContainer master = haNode(network, "master");
             CubridContainer slave = haNode(network, "slave")) {

            Startables.deepStart(master, slave).join();
            awaitHbStatus(master, "(current master, state master)", Duration.ofMinutes(3));

            executeUpdates(master,
                "CREATE TABLE failover_test (id INT PRIMARY KEY, msg VARCHAR(100))",
                "INSERT INTO failover_test VALUES (1, 'before failover')");
            awaitReplicatedRow(
                slave, "SELECT msg FROM failover_test WHERE id = 1", Duration.ofSeconds(60));

            master.stop();
            awaitHbStatus(slave, "(current slave, state master)", Duration.ofMinutes(3));

            assertThat(querySingle(slave, "SELECT msg FROM failover_test WHERE id = 1"))
                .isEqualTo("before failover");

            executeUpdates(slave, "INSERT INTO failover_test VALUES (2, 'after failover')");
            assertThat(querySingle(slave, "SELECT msg FROM failover_test WHERE id = 2"))
                .isEqualTo("after failover");
        }
    }

    private static CubridContainer haNode(Network network, String hostname) {
        return new CubridContainer(IMAGE)
            .withDatabaseName(DB)
            .withNetwork(network)
            .withNetworkAliases(hostname)
            .withEnv("CUBRID_COMPONENTS", "HA")
            .withEnv("CUBRID_DB_HOST", PEERS)
            .withPrivilegedMode(true)
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName(hostname));
    }

    private static void executeUpdates(CubridContainer node, String... sqls) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                node.getJdbcUrl(), node.getUsername(), node.getPassword());
             Statement stmt = conn.createStatement()) {
            for (String sql : sqls) {
                stmt.execute(sql);
            }
        }
    }

    private static String querySingle(CubridContainer node, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                node.getJdbcUrl(), node.getUsername(), node.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                throw new IllegalStateException("Query returned no rows: " + sql);
            }
            return rs.getString(1);
        }
    }

    private static void awaitHbStatus(CubridContainer node, String expected, Duration timeout)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        ExecResult last = null;
        while (Instant.now().isBefore(deadline)) {
            last = node.execInContainerWithUser("cubrid", "cubrid", "hb", "status");
            if (last.getStdout().contains(expected)) {
                return;
            }
            Thread.sleep(3000);
        }
        fail("hb status never contained '" + expected + "' within " + timeout
            + (last != null ? ". Last:\n" + last.getStdout() : ""));
    }

    private static String awaitReplicatedRow(CubridContainer node, String sql, Duration timeout)
            throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        SQLException lastError = null;
        while (Instant.now().isBefore(deadline)) {
            try (Connection conn = DriverManager.getConnection(
                    node.getJdbcUrl(), node.getUsername(), node.getPassword());
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            } catch (SQLException e) {
                lastError = e;
            }
            Thread.sleep(2000);
        }
        fail("Replicated row never appeared within " + timeout
            + (lastError != null ? ". Last SQL error: " + lastError.getMessage() : ""));
        return null;
    }
}
