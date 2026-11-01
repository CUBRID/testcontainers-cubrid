package org.testcontainers.cubrid;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.ExecConfig;
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

            awaitHbStatus(master, "(current master, state master)", Duration.ofMinutes(3));
            awaitHbStatus(slave, "(current slave, state slave)", Duration.ofMinutes(3));
        }
    }

    @Test
    void shouldReplicateDataAcrossHaCluster() throws Exception {
        try (Network network = Network.newNetwork();
             CubridContainer master = haNode(network, "master");
             CubridContainer slave = haNode(network, "slave")) {

            Startables.deepStart(master, slave).join();
            awaitActiveServer(master);

            executeUpdates(master,
                "CREATE TABLE replica_test (id INT PRIMARY KEY, msg VARCHAR(100))",
                "INSERT INTO replica_test VALUES (1, 'hello from master')");

            awaitRowOnStandby(slave, "SELECT msg FROM replica_test WHERE id = 1",
                "hello from master", Duration.ofSeconds(60));
        }
    }

    @Test
    void shouldPromoteSlaveWhenMasterFails() throws Exception {
        try (Network network = Network.newNetwork();
             CubridContainer master = haNode(network, "master");
             CubridContainer slave = haNode(network, "slave")) {

            Startables.deepStart(master, slave).join();
            awaitActiveServer(master);

            executeUpdates(master,
                "CREATE TABLE failover_test (id INT PRIMARY KEY, msg VARCHAR(100))",
                "INSERT INTO failover_test VALUES (1, 'before failover')");
            awaitRowOnStandby(slave, "SELECT msg FROM failover_test WHERE id = 1",
                "before failover", Duration.ofSeconds(60));

            master.stop();
            awaitActiveServer(slave);

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
            last = execAsCubrid(node, "cubrid", "hb", "status");
            if (last.getStdout().contains(expected)) {
                return;
            }
            Thread.sleep(3000);
        }
        fail("hb status never contained '" + expected + "' within " + timeout
            + (last != null ? ". Last:\n" + last.getStdout() : ""));
    }

    private static void awaitActiveServer(CubridContainer node) throws Exception {
        awaitHbStatus(node, "registered_and_active", Duration.ofMinutes(3));
    }

    private static void awaitRowOnStandby(CubridContainer node, String sql, String expected,
            Duration timeout) throws Exception {
        // Over JDBC the broker would read the master, so read this node's own server with csql.
        // csql needs PWD to find the database, and bash sets it (docker exec does not).
        String csql = "csql -u " + node.getUsername() + " -p " + node.getPassword()
            + " " + node.getDatabaseName() + "@localhost -c '" + sql + "'";
        Instant deadline = Instant.now().plus(timeout);
        ExecResult last = null;
        while (Instant.now().isBefore(deadline)) {
            last = execAsCubrid(node, "bash", "-c", csql);
            if (last.getStdout().contains(expected)) {
                return;
            }
            Thread.sleep(2000);
        }
        fail("csql never returned '" + expected + "' within " + timeout
            + (last != null ? ". Last:\n" + last.getStdout() + last.getStderr() : ""));
    }

    private static ExecResult execAsCubrid(CubridContainer node, String... command) throws Exception {
        return node.execInContainer(ExecConfig.builder().user("cubrid").command(command).build());
    }
}
