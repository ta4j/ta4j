#!/usr/bin/env python3
"""Policy check: the hosted tag-selection workflows must exclude the new
hardware test tags. The branch introduced @Tag("requires-cuda"),
@Tag("requires-metal"), and @Tag("requires-opencl") tests whose first action is
to assert that a native library path property is set; GitHub-hosted runners
have no such library, so any job that selects them fails.

- .github/workflows/test-tag-integration.yml selects -Dgroups=integration with
  an explicit exclusion list that still only contains
  analysis-demo,benchmark,requires-display,requires-headless, so the new
  CudaNativeIntegrationTest / MetalNativeIntegrationTest /
  OpenClNativeIntegrationTest run on hosted runners and fail.
- .github/workflows/test-tag-benchmark.yml selects -Dgroups=benchmark with an
  EMPTY exclusion list, so the new hardware backtest benchmarks run and fail.

The branch updated the root pom default, run-full-build-quiet.sh, and README
but missed both workflow files.

Run from the repository root:
  /opt/homebrew/opt/python@3.14/bin/python3.14 -m unittest discover -s scripts/tests -p 'test_review_ci_tag_exclusions.py' -v
"""

import pathlib
import re
import unittest

REPOSITORY_ROOT = pathlib.Path(__file__).resolve().parents[2]
HARDWARE_TAGS = ("requires-cuda", "requires-metal", "requires-opencl")
BASELINE_TAGS = ("analysis-demo", "benchmark", "requires-display", "requires-headless")


def _excluded_tags(workflow_path):
    text = workflow_path.read_text(encoding="utf-8")
    match = re.search(r"-Dta4j\.excludedTestTags=([^\s\"']+)", text)
    if match is None:
        return None
    return tuple(match.group(1).split(","))


class HostedTagWorkflowExclusionsTest(unittest.TestCase):
    def test_integration_workflow_excludes_hardware_tags(self):
        workflow = REPOSITORY_ROOT / ".github/workflows/test-tag-integration.yml"
        self.assertTrue(workflow.is_file(), "integration tag workflow must exist")
        excluded = _excluded_tags(workflow)
        self.assertIsNotNone(excluded, "integration workflow must pass -Dta4j.excludedTestTags")
        for tag in HARDWARE_TAGS:
            self.assertIn(
                tag, excluded,
                "test-tag-integration.yml must exclude %s; the new native integration tests "
                "assert a configured library and fail on hosted runners" % tag,
            )

    def test_benchmark_workflow_excludes_hardware_tags(self):
        workflow = REPOSITORY_ROOT / ".github/workflows/test-tag-benchmark.yml"
        self.assertTrue(workflow.is_file(), "benchmark tag workflow must exist")
        excluded = _excluded_tags(workflow)
        self.assertIsNotNone(excluded, "benchmark workflow must pass -Dta4j.excludedTestTags")
        for tag in HARDWARE_TAGS:
            self.assertIn(
                tag, excluded,
                "test-tag-benchmark.yml must exclude %s; the new hardware backtest benchmarks "
                "assert a configured library and fail on hosted runners" % tag,
            )

    def test_integration_workflow_keeps_baseline_exclusions(self):
        # Negative control: existing non-hardware exclusions must remain.
        workflow = REPOSITORY_ROOT / ".github/workflows/test-tag-integration.yml"
        excluded = _excluded_tags(workflow)
        for tag in BASELINE_TAGS:
            self.assertIn(tag, excluded, "baseline exclusion %s must remain present" % tag)


if __name__ == "__main__":
    unittest.main()
