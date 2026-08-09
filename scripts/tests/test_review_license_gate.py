#!/usr/bin/env python3
"""Policy check: every reactor module in the root pom must resolve the
license-maven-plugin header template, exactly like ta4j-core and ta4j-examples
do (they override the inherited header with ${project.parent.basedir}). The new
ta4j-cli module inherits the parent's ${project.basedir}/license-header.txt,
which does not exist inside ta4j-cli/, so `license:check` and `license:format`
fail for the module and the repository's completion gate can never be green.

Run from the repository root:
  /opt/homebrew/opt/python@3.14/bin/python3.14 -m unittest discover -s scripts/tests -p 'test_review_license_gate.py' -v
"""

import pathlib
import re
import unittest

REPOSITORY_ROOT = pathlib.Path(__file__).resolve().parents[2]


def _module_names(pom_text):
    modules_block = re.search(r"<modules>(.*?)</modules>", pom_text, re.DOTALL)
    if modules_block is None:
        return []
    return re.findall(r"<module>([^<]+)</module>", modules_block.group(1))


def _header_template(module_dir):
    """Resolve the license plugin header template for a module, mirroring how
    the license-maven-plugin resolves ${project.basedir} (module dir) and
    ${project.parent.basedir} (root dir). Returns a path or None."""
    pom = module_dir / "pom.xml"
    if not pom.is_file():
        return None
    text = pom.read_text(encoding="utf-8")
    if "license-maven-plugin" not in text:
        return None
    header_match = re.search(r"<header>(.*?)</header>", text, re.DOTALL)
    if header_match is None:
        return None
    header = header_match.group(1).strip()
    if "${project.parent.basedir}" in header:
        base = REPOSITORY_ROOT
    elif "${project.basedir}" in header:
        base = module_dir
    else:
        base = module_dir
    relative = header.replace("${project.parent.basedir}", "").replace("${project.basedir}", "")
    return base / relative.lstrip("/")


class LicenseHeaderTemplateTest(unittest.TestCase):
    def test_every_module_resolves_a_license_header_template(self):
        root_pom = (REPOSITORY_ROOT / "pom.xml").read_text(encoding="utf-8")
        modules = _module_names(root_pom)
        self.assertGreaterEqual(len(modules), 3, "expected at least ta4j-core, ta4j-examples, ta4j-cli")

        unresolved = []
        for name in modules:
            module_dir = REPOSITORY_ROOT / name
            template = _header_template(module_dir)
            if template is None or not template.is_file():
                unresolved.append(name)
        self.assertEqual(
            unresolved, [],
            "modules without a resolvable license header template: %s" % ", ".join(unresolved),
        )

    def test_core_and_examples_still_resolve(self):
        # Negative control: the established modules must keep resolving, so the
        # check is not satisfied by deleting the plugin configuration.
        for name in ("ta4j-core", "ta4j-examples"):
            template = _header_template(REPOSITORY_ROOT / name)
            self.assertIsNotNone(template, "%s must configure the license plugin" % name)
            self.assertTrue(template.is_file(), "%s header template must exist" % name)


if __name__ == "__main__":
    unittest.main()
