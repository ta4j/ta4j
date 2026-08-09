#!/usr/bin/env python3
"""Policy check: every assertTrue(readme.contains(...)) statement in
ReadmeContentManagerTest must be well formed (argument present and statement
closed), because the branch's merge dropped one string argument and left an
orphaned opening parenthesis, which breaks test compilation of ta4j-examples
and with it every Maven test/verify invocation of the reactor.

Run from the repository root:
  /opt/homebrew/opt/python@3.14/bin/python3.14 -m unittest discover -s scripts/tests -p 'test_review_examples_compile.py' -v
"""

import pathlib
import unittest

REPOSITORY_ROOT = pathlib.Path(__file__).resolve().parents[2]
TARGET = REPOSITORY_ROOT / "ta4j-examples/src/test/java/ta4jexamples/doc/ReadmeContentManagerTest.java"

# Failing marker: the statement `assertTrue(readme.contains(` must carry a
# string argument and close with `));` at depth 0 before a `;` appears. The
# broken merge left the first statement without an argument, so the scanner
# observes a second `(` (from the next statement) inside the unclosed one and
# then a `;` at depth > 0.
_MARKER = "assertTrue(readme.contains("


def _scan(source, start):
    """Scan from just after the marker to the end of its statement.

    Returns (closed, argument_characters) where `closed` is True when the
    statement terminated with `)` at depth 0, and argument_characters is the
    text collected between the opening and closing parens.
    """
    depth = 2  # assertTrue( plus contains(
    in_string = False
    escaped = False
    argument = []
    i = start
    while i < len(source):
        char = source[i]
        if in_string:
            argument.append(char)
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
            argument.append(char)
        elif char == "(":
            depth += 1
            argument.append(char)
        elif char == ")":
            depth -= 1
            argument.append(char)
            if depth == 0:
                return True, "".join(argument)
        elif char == ";":
            # Statement terminated before the parens balanced: missing argument
            # or missing closing parens.
            return False, "".join(argument)
        else:
            argument.append(char)
        i += 1
    return False, "".join(argument)


class ReadmeContentManagerTestStructureTest(unittest.TestCase):
    def test_readme_assertions_are_well_formed(self):
        self.assertTrue(TARGET.is_file(), "test file must exist")
        source = TARGET.read_text(encoding="utf-8")

        statements = []
        start = 0
        while True:
            offset = source.find(_MARKER, start)
            if offset < 0:
                break
            closed, argument = _scan(source, offset + len(_MARKER))
            statements.append((closed, argument))
            start = offset + len(_MARKER)

        self.assertGreaterEqual(
            len(statements), 4,
            "expected at least the two command-surface and two Maven-command assertions",
        )
        for index, (closed, argument) in enumerate(statements):
            self.assertTrue(closed, "statement %d is not closed with '));' (missing string argument)" % index)
            self.assertNotEqual(argument.strip(), "", "statement %d has no argument" % index)
            self.assertIn('"', argument, "statement %d argument must be a string literal" % index)


if __name__ == "__main__":
    unittest.main()
