package org.testcontainers.cubrid;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CubridImageSubstitutorTest {

    @Test
    void shouldResolveToConfiguredImage() {
        String configured = System.getProperty("cubrid.image", "");
        assumeTrue(!configured.isEmpty(), "cubrid.image is not set");

        try (CubridContainer cubrid = new CubridContainer("cubrid/cubrid:11.4")) {
            assertThat(cubrid.getDockerImageName()).isEqualTo(configured);
        }
    }
}
