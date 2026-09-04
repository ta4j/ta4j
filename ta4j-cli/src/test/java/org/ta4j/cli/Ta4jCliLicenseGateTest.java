/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * Guards the repository license gate for this module.
 *
 * <p>
 * The root POM configures the license plugin with
 * {@code <header>${project.basedir}/license-header.txt</header>}, which only
 * exists in the root module. Child modules must either ship their own header
 * file or override the header to the parent directory the way
 * {@code ta4j-examples} does. Without the override, {@code license:check} and
 * {@code license:format} fail for this module and the documented full build
 * gates ({@code scripts/run-full-build-quiet.sh} repair and CI-equivalent
 * validation) cannot pass.
 *
 * @since 0.23.1
 */
class Ta4jCliLicenseGateTest {

    @Test
    void pomPointsTheLicenseHeaderAtTheParentHeaderFile() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertTrue(pom.contains("<header>${project.parent.basedir}/license-header.txt</header>"),
                "ta4j-cli/pom.xml must override the inherited license header path to the parent header file so "
                        + "license:check/license:format pass for this module");
    }
}
