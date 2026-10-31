package org.testcontainers.cubrid;

import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.ImageNameSubstitutor;

/**
 * Redirects every {@code cubrid/cubrid} image to the one named by the {@code cubrid.image}
 * system property, so the whole suite can run against another CUBRID version or build.
 *
 * <p>The build sets the property per test task ({@code -Pcubrid.version}, {@code testCubrid*}).
 * Without it, images are used as the tests declare them.
 *
 * <p>Substitution happens when the image is resolved, after {@code assertCompatibleWith} has
 * already seen the original name, so {@code jdbc:tc:cubrid:<tag>://} URLs are covered too and
 * no production code has to change.
 */
public class CubridImageSubstitutor extends ImageNameSubstitutor {

    private static final String CUBRID_REPO = "cubrid/cubrid";

    private final String target = System.getProperty("cubrid.image", "");

    @Override
    public DockerImageName apply(DockerImageName original) {
        if (target.isEmpty() || !CUBRID_REPO.equals(original.getUnversionedPart())) {
            return original;
        }
        return DockerImageName.parse(target).asCompatibleSubstituteFor(CUBRID_REPO);
    }

    @Override
    protected String getDescription() {
        return target.isEmpty()
            ? "CUBRID image substitutor (inactive)"
            : "CUBRID image substitutor (" + CUBRID_REPO + " -> " + target + ")";
    }
}
