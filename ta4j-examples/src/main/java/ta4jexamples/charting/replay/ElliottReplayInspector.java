/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jfree.chart.JFreeChart;

import ta4jexamples.charting.replay.ReplaySession.TimelineEntry;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Replays the as-of Elliott state recorded by a research run, one bar at a
 * time.
 *
 * <p>
 * The tool reads a {@code elliott-research-run/1} directory (see the Elliott
 * research section of {@code ta4j-core/README.md}), opens the trace behind one
 * {@code comparisons.csv} key and shows what was knowable at each recorded bar:
 * confirmed pivots, every retained candidate with its rule results, and the bar
 * on which each pivot became confirmed. It never recomputes a count, never
 * fetches data and never writes into the run directory.
 *
 * <pre>
 * ElliottReplayInspector &lt;runDir&gt; --key &lt;comparison key&gt;
 *     [--at &lt;bar index|ISO instant&gt;] [--candidate &lt;key|#&gt;]
 *     [--window N] [--overlay-cap N] [--trace real|selected-null-member]
 *     [--commands "next;tnext;seek 120;export"] [--out dir]
 *     [--interactive] [--display]
 * </pre>
 *
 * Commands: {@code seek <bar|instant>}, {@code next}/{@code prev} (adjacent
 * recorded bar), {@code tnext}/{@code tprev} (next/previous bar where the
 * recorded state changed), {@code candidates}, {@code select <key|#>},
 * {@code follow <key>} (keep a key selected across bars), {@code clear},
 * {@code history [key]}, {@code show}, {@code export}, {@code help},
 * {@code quit}.
 *
 * @since 0.26.1
 */
public final class ElliottReplayInspector {

    private static final String USAGE = """
            usage: ElliottReplayInspector <runDir> --key <comparison key> [--at <bar|instant>] [--candidate <key|#>]
                   [--window N] [--overlay-cap N] [--trace real|selected-null-member]
                   [--commands "next;tnext;seek 120;export"] [--out dir] [--interactive] [--display]
            commands: seek <bar|instant> | next | prev | tnext | tprev | candidates | select <key|#> | follow <key>
                      clear | history [key] | show | export | help | quit
            """;

    private final PrintStream out;
    private final Path outDirectory;
    private final boolean display;
    private final ReplaySession session;
    private ReplayFrame current;

    private ElliottReplayInspector(final PrintStream out, final Path outDirectory, final boolean display,
            final ReplaySession session) {
        this.out = out;
        this.outDirectory = outDirectory;
        this.display = display;
        this.session = session;
    }

    /**
     * Command line entry point.
     *
     * @param args see class documentation
     */
    public static void main(final String[] args) {
        final int status = run(args, System.in, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /**
     * Runs the inspector with explicit streams.
     *
     * @param args command-line arguments
     * @param in   stream read in {@code --interactive} mode
     * @param out  receives evidence text and command results
     * @param err  receives usage and error messages
     * @return {@code 0} on success, {@code 1} for a usage error, {@code 2} when the
     *         artifact or a command is invalid
     */
    public static int run(final String[] args, final InputStream in, final PrintStream out, final PrintStream err) {
        final Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.print(USAGE);
            return 1;
        }
        try {
            final ReplayArtifact artifact = ReplayArtifact.open(options.runDirectory);
            final ReplaySession session = ReplaySession.open(artifact, options.key, options.traceMode, options.window,
                    options.overlayCap);
            final ElliottReplayInspector inspector = new ElliottReplayInspector(out, options.outDirectory,
                    options.display, session);
            inspector.start(options);
            for (final String command : options.commands) {
                if (!inspector.execute(command)) {
                    return 0;
                }
            }
            if (options.interactive) {
                inspector.interact(in, err);
            } else if (options.exportFinal
                    && (options.commands.isEmpty() || !"export".equals(options.commands.getLast()))) {
                inspector.export();
            }
            return 0;
        } catch (ReplayArtifactException e) {
            err.println("replay: " + e.getMessage());
            return 2;
        }
    }

    private void start(final Options options) {
        out.println("Replaying " + session.row().key());
        out.println("  run " + session.artifact().directory() + "  trace " + session.traceFile() + "  records "
                + session.recordCount() + "  as-of " + session.firstAsOf() + ".." + session.lastAsOf());
        out.println("  dataset " + session.dataset().id() + " (" + session.dataset().asset() + ")  window "
                + session.window() + " bars  overlay cap " + session.overlayCap());
        if (options.at != null) {
            current = isNumber(options.at) ? session.seek(Integer.parseInt(options.at))
                    : session.seek(parseInstant(options.at));
        } else {
            current = session.frame();
        }
        if (!options.candidate.isEmpty()) {
            current = isNumber(options.candidate) ? session.select(options.candidate) : follow(options.candidate);
        }
        out.print(ReplayEvidenceText.render(current));
    }

    private ReplayFrame follow(final String key) {
        return session.follow(key);
    }

    private void interact(final InputStream in, final PrintStream err) {
        out.println("commands: seek <bar|instant> | next | prev | tnext | tprev | candidates | select <key|#> | "
                + "follow <key> | clear | history [key] | show | export | help | quit");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    if (!execute(line)) {
                        return;
                    }
                } catch (ReplayArtifactException e) {
                    err.println("replay: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot read commands: " + e.getMessage(), e);
        }
    }

    /** @return {@code false} when the session should end. */
    private boolean execute(final String line) {
        final String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        final String[] parts = trimmed.split("\\s+", 2);
        final String argument = parts.length > 1 ? parts[1].trim() : "";
        switch (parts[0]) {
        case "seek" -> {
            require(argument, "seek <bar index|ISO instant>");
            move(isNumber(argument) ? session.seek(Integer.parseInt(argument)) : session.seek(parseInstant(argument)));
        }
        case "next" -> move(session.stepBar(1));
        case "prev" -> move(session.stepBar(-1));
        case "tnext" -> move(session.stepTransition(1));
        case "tprev" -> move(session.stepTransition(-1));
        case "select" -> {
            require(argument, "select <candidate key|#>");
            move(session.select(argument));
        }
        case "follow" -> {
            require(argument, "follow <candidate key>");
            move(session.follow(argument));
        }
        case "clear" -> move(session.clearSelection());
        case "candidates" -> candidates();
        case "history" -> history(argument);
        case "show" -> out.print(ReplayEvidenceText.render(current));
        case "export" -> export();
        case "help" -> out.print(USAGE);
        case "quit", "exit" -> {
            return false;
        }
        default -> throw new ReplayArtifactException("unknown command '" + parts[0] + "'; type help");
        }
        return true;
    }

    private void move(final ReplayFrame frame) {
        current = frame;
        out.print(ReplayEvidenceText.render(current));
    }

    private void candidates() {
        out.println("candidates at bar " + current.cursor() + " (" + current.candidates().size() + " retained, "
                + current.overlayed() + " drawn, cap " + current.overlayCap() + ")");
        for (final ReplayFrame.Candidate candidate : current.candidates()) {
            out.println("  #" + (candidate.ordinal() + 1) + (candidate.selected() ? " * " : "   ")
                    + candidate.candidateKey() + " " + candidate.direction() + " version " + candidate.version()
                    + (candidate.overlayed() ? "" : " [not drawn]"));
        }
        if (!current.selectedCandidate().isEmpty() && current.candidate(current.selectedCandidate()).isEmpty()) {
            out.println("  selected " + current.selectedCandidate() + " is not retained at this bar");
        }
    }

    private void history(final String argument) {
        final String key = argument.isEmpty() ? current.selectedCandidate() : argument;
        if (key.isEmpty()) {
            throw new ReplayArtifactException(
                    "history needs a candidate key: select one first or pass `history <key>`");
        }
        final List<TimelineEntry> timeline = session.candidateTimeline(key);
        final int truncatedBefore = session.timelineStartAsOf();
        out.println("history of " + key + " up to bar " + current.cursor() + " (" + timeline.size() + " change(s)):");
        if (truncatedBefore >= 0) {
            out.println("  (earlier history before bar " + truncatedBefore + " is not inspected: only the latest "
                    + ReplaySession.TIMELINE_LOOKBACK + " records are scanned)");
        }
        for (final TimelineEntry entry : timeline) {
            out.println("  bar " + entry.asOfIndex() + (entry.present() ? " version " + entry.version() : " absent"));
        }
    }

    /**
     * Writes the JSON, text and image of the current frame to the output directory.
     */
    private void export() {
        rejectRunDirectory();
        final String stem = "frame-" + current.cursor();
        rejectLinkedDestinations(stem);
        try {
            Files.createDirectories(outDirectory);
            Files.writeString(outDirectory.resolve(stem + ".json"), current.toSemanticJson(), StandardCharsets.UTF_8);
            Files.writeString(outDirectory.resolve(stem + ".txt"), ReplayEvidenceText.render(current),
                    StandardCharsets.UTF_8);
            final ReplayChartModel model = ReplayChartModel.of(current, session.bars());
            final ChartWorkflow workflow = new ChartWorkflow(outDirectory.toString());
            final JFreeChart chart = ReplayFrameRenderer.chart(workflow, model);
            final Optional<Path> image = ReplayFrameRenderer.save(chart, model, outDirectory, stem);
            out.println("exported " + outDirectory.resolve(stem + ".json") + ", " + stem + ".txt, "
                    + image.map(path -> path.getFileName().toString()).orElse("(image not written)"));
            if (display) {
                workflow.displayChart(chart, model.title());
            }
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot write to " + outDirectory + ": " + e.getMessage(), e);
        }
    }

    /** Refuses an output directory that is, or lies inside, the replayed run. */
    private void rejectRunDirectory() {
        try {
            final Path run = session.artifact().directory().toRealPath();
            final Path target = realPathThroughExistingAncestor(outDirectory.toAbsolutePath().normalize());
            if (target.startsWith(run)) {
                throw new ReplayArtifactException("--out " + outDirectory + " is the replayed run directory " + run
                        + " or lies inside it; choose another output directory so the evidence bundle stays unchanged");
            }
        } catch (IOException e) {
            throw new ReplayArtifactException("cannot resolve --out " + outDirectory + ": " + e.getMessage(), e);
        }
    }

    /**
     * Refuses to write through a symbolic link already standing at a destination
     * file: writing would replace whatever it points at, which can be a file of the
     * replayed run.
     */
    private void rejectLinkedDestinations(final String stem) {
        for (final String extension : List.of(".json", ".txt", ".jpg")) {
            final Path destination = outDirectory.resolve(stem + extension);
            if (Files.isSymbolicLink(destination)) {
                throw new ReplayArtifactException("export destination " + destination
                        + " is a symbolic link; replay never writes through links, so remove it or choose another --out directory");
            }
        }
    }

    private static Path realPathThroughExistingAncestor(final Path path) throws IOException {
        Path existing = path;
        while (existing != null && !Files.exists(existing)) {
            existing = existing.getParent();
        }
        if (existing == null) {
            return path;
        }
        return existing.toRealPath().resolve(existing.relativize(path)).normalize();
    }

    private static void require(final String argument, final String usage) {
        if (argument.isEmpty()) {
            throw new ReplayArtifactException("usage: " + usage);
        }
    }

    private static boolean isNumber(final String value) {
        return value.matches("\\d{1,9}");
    }

    private static Instant parseInstant(final String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ReplayArtifactException(
                    "'" + value + "' is neither a bar index nor an ISO-8601 instant like 2020-03-27T00:00:00Z");
        }
    }

    /** Parsed command line. */
    private static final class Options {
        private Path runDirectory;
        private String key;
        private String at;
        private String candidate = "";
        private int window = ReplaySession.DEFAULT_WINDOW;
        private int overlayCap = ReplaySession.DEFAULT_OVERLAY_CAP;
        private String traceMode = ReplayArtifact.TRACE_MODE_REAL;
        private final List<String> commands = new ArrayList<>();
        private Path outDirectory = Path.of("target", "elliott-replay");
        private boolean outGiven;
        private boolean interactive;
        private boolean display;
        private boolean exportFinal;

        static Options parse(final String[] args) {
            final Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                final String arg = args[i];
                switch (arg) {
                case "--key" -> options.key = value(args, ++i, arg);
                case "--at" -> options.at = value(args, ++i, arg);
                case "--candidate" -> options.candidate = value(args, ++i, arg);
                case "--window" -> options.window = integer(value(args, ++i, arg), arg);
                case "--overlay-cap" -> options.overlayCap = integer(value(args, ++i, arg), arg);
                case "--trace" -> options.traceMode = value(args, ++i, arg);
                case "--commands" -> {
                    for (final String command : value(args, ++i, arg).split(";")) {
                        if (!command.isBlank()) {
                            options.commands.add(command.trim());
                        }
                    }
                }
                case "--out" -> {
                    options.outDirectory = Path.of(value(args, ++i, arg));
                    options.outGiven = true;
                    options.exportFinal = true;
                }
                case "--interactive" -> options.interactive = true;
                case "--display" -> options.display = true;
                default -> {
                    if (arg.startsWith("--") || options.runDirectory != null) {
                        throw new IllegalArgumentException("unknown or unexpected argument '" + arg + "'");
                    }
                    options.runDirectory = Path.of(arg);
                }
                }
            }
            if (options.runDirectory == null) {
                throw new IllegalArgumentException("missing <runDir>");
            }
            if (options.key == null) {
                throw new IllegalArgumentException("missing --key <comparison key> (see comparisons.csv in the run)");
            }
            if (!ReplayArtifact.TRACE_MODE_REAL.equals(options.traceMode)
                    && !ReplayArtifact.TRACE_MODE_NULL_MEMBER.equals(options.traceMode)) {
                throw new IllegalArgumentException("--trace must be real or selected-null-member");
            }
            if (options.display && !options.outGiven && !options.interactive) {
                options.exportFinal = true;
            }
            return options;
        }

        private static String value(final String[] args, final int index, final String flag) {
            if (index >= args.length) {
                throw new IllegalArgumentException(flag + " needs a value");
            }
            return args[index];
        }

        private static int integer(final String value, final String flag) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(flag + " needs an integer, got '" + value + "'");
            }
        }
    }
}
