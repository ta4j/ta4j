/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Locks the OSGi packaging contract of the public acceleration SPI. The
 * {@code org.ta4j.core.acceleration} package hosts the supported SPI entry
 * points implemented by provider artifacts such as ta4j-cli. The bundle must
 * therefore keep exporting it through the bnd wildcard and must not exclude it
 * from the export list, otherwise the documented extension contract is unusable
 * for OSGi consumers.
 */
public class AccelerationOsgiExportTest {

    private static final String SPI_PACKAGE = "org.ta4j.core.acceleration";

    private static final Pattern EXPORT_CONTENTS = Pattern
            .compile("-exportcontents:\\s*([\\s\\S]*?)(?:\\r?\\n\\s*\\]\\]>|\\r?\\n\\s*-[a-zA-Z])");

    @Test
    public void publicAccelerationSpiRemainsExportableFromTheOsgiBundle() throws Exception {
        Path pom = Path.of("pom.xml").toAbsolutePath();
        assertTrue("test must run from the ta4j-core module directory", Files.isRegularFile(pom));
        Matcher matcher = EXPORT_CONTENTS.matcher(Files.readString(pom));
        boolean found = matcher.find();
        assertTrue("ta4j-core pom must declare the bnd -exportcontents directive", found);
        String exportContents = matcher.group(1).replaceAll("\\s+", " ").trim();

        assertFalse("bnd -exportcontents must not exclude the public acceleration SPI package (" + SPI_PACKAGE + "): "
                + exportContents, exportContents.contains("!org.ta4j.core.acceleration"));

        assertTrue("bnd -exportcontents must include the public acceleration SPI package: " + exportContents,
                exportContents.contains("*org.ta4j.core*"));
    }
}
