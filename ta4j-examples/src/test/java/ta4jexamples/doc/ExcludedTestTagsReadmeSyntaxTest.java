/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.doc;

import static org.junit.Assert.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

/**
 * Guards the excluded-test-tags README assertions against the dangling
 * {@code assertTrue(readme.contains(} artifact left behind when the
 * {@code requires-cuda/requires-metal/requires-opencl} tag list was merged
 * into {@link ReadmeContentManagerTest}. The dangling line breaks
 * {@code testCompile} for the whole ta4j-examples module, which fails every
 * full {@code verify} run and every consumer module that reaches
 * ta4j-examples test sources.
 */
public class ExcludedTestTagsReadmeSyntaxTest {

    private static final Pattern DANGLING_ASSERT = Pattern
            .compile("assertTrue\\(readme\\.contains\\(\\s+assertTrue\\(");

    @Test
    public void readmeExcludedTagsAssertionsHaveNoDanglingMergeArtifact() throws Exception {
        Path testSource = Path.of("src", "test", "java", "ta4jexamples", "doc", "ReadmeContentManagerTest.java")
                .toAbsolutePath();
        org.junit.Assert.assertTrue("test must run from the ta4j-examples module directory",
                Files.isRegularFile(testSource));
        String source = Files.readString(testSource);

        Matcher matcher = DANGLING_ASSERT.matcher(source);
        assertFalse("ReadmeContentManagerTest contains a dangling assertTrue(readme.contains( merge artifact: "
                + (matcher.find() ? describe(source, matcher.start()) : "<none>"), matcher.find());
    }

    private static String describe(String source, int index) {
        int lineStart = source.lastIndexOf('\n', Math.max(0, index - 1)) + 1;
        int lineEnd = source.indexOf('\n', index);
        if (lineEnd < 0) {
            lineEnd = source.length();
        }
        return source.substring(lineStart, lineEnd).trim();
    }
}
