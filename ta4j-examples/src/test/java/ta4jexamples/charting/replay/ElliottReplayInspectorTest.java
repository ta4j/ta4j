/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYAnnotation;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ta4jexamples.charting.display.SwingChartDisplayer;
import ta4jexamples.charting.replay.ReplayFrame.Candidate;
import ta4jexamples.charting.replay.ReplayFrame.Pivot;
import ta4jexamples.charting.replay.ReplayFrame.Point;
import ta4jexamples.charting.workflow.ChartWorkflow;

class ElliottReplayInspectorTest {

    @TempDir
    Path temp;

    private record Result(int status, String out, String err) {
    }

    private static Result run(final String stdin, final String... args) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ByteArrayOutputStream err = new ByteArrayOutputStream();
        final int status = ElliottReplayInspector.run(args,
                new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)),
                new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(status, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static List<XYTextAnnotation> textAnnotations(final JFreeChart chart) {
        final Plot plot = chart.getPlot();
        final XYPlot xy = plot instanceof CombinedDomainXYPlot combined ? (XYPlot) combined.getSubplots().getFirst()
                : (XYPlot) plot;
        final List<XYTextAnnotation> annotations = new ArrayList<>();
        for (final Object annotation : xy.getAnnotations()) {
            if (annotation instanceof XYTextAnnotation text) {
                annotations.add(text);
            }
        }
        return annotations;
    }

    @Test
    void exportWritesSemanticJsonEvidenceAndImage() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path out = temp.resolve("out");

        final Result result = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "36", "--out",
                out.toString());

        assertEquals(0, result.status(), result.err());
        assertEquals("", result.err());
        assertTrue(Files.isRegularFile(out.resolve("frame-36.json")));
        assertTrue(Files.isRegularFile(out.resolve("frame-36.txt")));
        final BufferedImage image = ImageIO.read(out.resolve("frame-36.jpg").toFile());
        assertNotNull(image);
        assertEquals(ReplayFrameRenderer.EXPORT_WIDTH, image.getWidth());
        assertEquals(ReplayFrameRenderer.EXPORT_HEIGHT, image.getHeight());
        assertTrue(result.out().contains("Replaying " + ReplayFixture.RULES_KEY));
        assertTrue(result.out().contains("as-of:     bar 36"));
        assertTrue(Files.readString(out.resolve("frame-36.json")).contains("\"schema\": \"elliott-replay-frame/1\""));
    }

    @Test
    void exportIsByteDeterministicAcrossRunsAndWorkspaces() throws IOException {
        final Path first = ReplayFixture.write(temp.resolve("first"));
        final Path second = ReplayFixture.write(temp.resolve("second"));
        final Path firstOut = temp.resolve("first-out");
        final Path secondOut = temp.resolve("second-out");

        assertEquals(0, run("", first.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "36", "--candidate", "2",
                "--out", firstOut.toString()).status());
        assertEquals(0, run("", second.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "36", "--candidate", "2",
                "--out", secondOut.toString()).status());

        assertEquals(Files.readString(firstOut.resolve("frame-36.json")),
                Files.readString(secondOut.resolve("frame-36.json")));
        assertArrayEquals(Files.readAllBytes(firstOut.resolve("frame-36.jpg")),
                Files.readAllBytes(secondOut.resolve("frame-36.jpg")));
        final String firstText = Files.readString(firstOut.resolve("frame-36.txt"));
        assertEquals(firstText, Files.readString(secondOut.resolve("frame-36.txt")));
        assertTrue(firstText.contains("frame digest: "));
    }

    @Test
    void scriptedCommandsNavigateAndExportTheFinalFrame() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final Path out = temp.resolve("out");

        final Result result = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--commands",
                "seek 20;tnext;select 1;candidates;history;next;export", "--out", out.toString());

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("as-of:     bar 23"));
        assertTrue(result.out().contains("candidates at bar 23 (1 retained, 1 drawn, cap 8)"));
        assertTrue(result.out().contains("history of c-A up to bar 23 (1 change(s)):"));
        assertTrue(result.out().contains("as-of:     bar 24"));
        assertTrue(Files.isRegularFile(out.resolve("frame-24.json")));
        assertFalse(Files.exists(out.resolve("frame-23.json")));
    }

    @Test
    void interactiveModeReadsCommandsAndReportsErrorsWithoutEnding() {
        final Path run = ReplayFixture.write(temp.resolve("run"));

        final Result result = run("seek 36\nselect 9\nbogus\nhistory\nselect c-B\nclear\nquit\nseek 50\n",
                run.toString(), "--key", ReplayFixture.RULES_KEY, "--interactive", "--out",
                temp.resolve("unused").toString());

        assertEquals(0, result.status());
        assertTrue(result.err().contains("no candidate #9 at bar 36"));
        assertTrue(result.err().contains("unknown command 'bogus'"));
        assertTrue(result.err().contains("history needs a candidate key"));
        assertTrue(result.out().contains("as-of:     bar 36"));
        assertFalse(result.out().contains("as-of:     bar 50"), "commands after quit are not executed");
        assertFalse(Files.exists(temp.resolve("unused")), "interactive sessions export only on request");
    }

    @Test
    void usageErrorsExitOneAndArtifactErrorsExitTwo() {
        final Path run = ReplayFixture.write(temp.resolve("run"));

        final Result noArgs = run("");
        final Result noKey = run("", run.toString());
        final Result unknownFlag = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--nope");
        final Result badKey = run("", run.toString(), "--key", "nope");
        final Result missing = run("", temp.resolve("absent").toString(), "--key", ReplayFixture.RULES_KEY);
        final Result badAt = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "yesterday");

        assertEquals(1, noArgs.status());
        assertTrue(noArgs.err().contains("missing <runDir>"));
        assertEquals(1, noKey.status());
        assertTrue(noKey.err().contains("missing --key"));
        assertEquals(1, unknownFlag.status());
        assertEquals(2, badKey.status());
        assertTrue(badKey.err().contains("replay: "));
        assertEquals(2, missing.status());
        assertTrue(missing.err().contains("no run.json"));
        assertEquals(2, badAt.status());
        assertTrue(badAt.err().contains("neither a bar index nor an ISO-8601 instant"));
    }

    @Test
    void displayRequestIsNonBlockingWhenDisplayIsDisabled() {
        final Path run = ReplayFixture.write(temp.resolve("run"));
        final String previous = System.getProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            final Result result = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "36", "--display",
                    "--out", temp.resolve("out").toString());

            assertEquals(0, result.status(), result.err());
            assertTrue(Files.isRegularFile(temp.resolve("out").resolve("frame-36.jpg")));
        } finally {
            if (previous == null) {
                System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
            } else {
                System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, previous);
            }
        }
    }

    @Test
    void textFrameAndChartAnnotationsAgreeOnEveryCoordinate() {
        final ReplayArtifact artifact = ReplayArtifact.open(ReplayFixture.write(temp.resolve("run")));
        final ReplaySession session = ReplaySession.open(artifact, ReplayFixture.RULES_KEY,
                ReplayArtifact.TRACE_MODE_REAL, 30, 8);
        final ReplayFrame frame = session.seek(36);
        final ReplayChartModel model = ReplayChartModel.of(frame, session.bars());
        final JFreeChart chart = ReplayFrameRenderer.chart(new ChartWorkflow(temp.toString()), model);
        final List<XYTextAnnotation> annotations = textAnnotations(chart);
        final String evidence = ReplayEvidenceText.render(frame);

        assertEquals(frame.windowStart(), model.windowStart());
        for (final Pivot pivot : frame.pivots()) {
            assertTrue(evidence.contains(pivot.type() + " bar " + pivot.index() + " @ " + pivot.price()));
            if (pivot.inWindow()) {
                assertTrue(hasAnnotation(annotations, model, ("HIGH".equals(pivot.type()) ? "H" : "L") + pivot.index(),
                        pivot.index(), pivot.price()));
            }
        }
        for (final Candidate candidate : frame.candidates()) {
            assertTrue(evidence.contains(candidate.candidateKey()));
            assertTrue(evidence.contains("version " + candidate.version()));
            for (final Point point : candidate.placement()) {
                assertTrue(evidence.contains(point.type() + "@" + point.index() + "=" + point.price()));
                if (candidate.overlayed() && point.inWindow()) {
                    assertTrue(hasAnnotation(annotations, model,
                            "#" + (candidate.ordinal() + 1) + (candidate.selected() ? "*" : ""), point.index(),
                            point.price()));
                }
            }
        }
        assertTrue(hasAnnotation(annotations, model, "as-of 36", 36,
                model.series().getLastBar().getHighPrice().toString()));
        assertEquals(ReplayFixture.close(36), (int) model.series().getLastBar().getClosePrice().doubleValue());
        assertEquals(36, model.windowStart() + model.series().getEndIndex());
    }

    private static boolean hasAnnotation(final List<XYTextAnnotation> annotations, final ReplayChartModel model,
            final String text, final int sourceIndex, final String price) {
        final int local = sourceIndex - model.windowStart();
        final double begin = model.series().getBar(local).getBeginTime().toEpochMilli();
        final double end = model.series().getBar(local).getEndTime().toEpochMilli();
        final double y = Double.parseDouble(price);
        for (final XYAnnotation annotation : annotations) {
            final XYTextAnnotation candidate = (XYTextAnnotation) annotation;
            if (candidate.getText().equals(text) && Math.abs(candidate.getY() - y) < 1e-9
                    && (candidate.getX() == begin || candidate.getX() == end)) {
                return true;
            }
        }
        return false;
    }

    @Test
    void zeroCandidateFrameStillRendersAChart() throws IOException {
        final Path run = ReplayFixture.write(temp.resolve("run"));

        final Result result = run("", run.toString(), "--key", ReplayFixture.RULES_KEY, "--at", "12", "--out",
                temp.resolve("out").toString());

        assertEquals(0, result.status(), result.err());
        assertTrue(result.out().contains("candidates retained: 0"));
        assertTrue(Files.size(temp.resolve("out").resolve("frame-12.jpg")) > 0);
    }
}
