#!/usr/bin/env python3
"""Reject successful Gradle exits that contain no executed shell smoke tests."""
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def verify(root):
    for flavor in ("androidx", "composepdf"):
        reports = sorted((root / flavor).rglob("TEST-*.xml"))
        if not reports:
            raise ValueError(f"{flavor}: no instrumentation XML reports")
        shell_passed = False
        for report in reports:
            tree = ET.parse(report).getroot()
            for element in tree.iter():
                if element.tag in ("failure", "error", "skipped"):
                    raise ValueError(f"{flavor}: failed, errored or skipped test")
                if any(int(element.get(key, "0")) != 0 for key in ("failures", "errors", "skipped")):
                    raise ValueError(f"{flavor}: non-passing test summary")
                if (element.tag == "testcase"
                        and element.get("classname") == "im.commonplace.reader.ui.ShellTest"
                        and element.get("name") == "launchAndNavigate"):
                    shell_passed = True
        if not shell_passed:
            raise ValueError(f"{flavor}: launchAndNavigate did not execute")
        print(f"{flavor}: launchAndNavigate executed and passed")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Usage: verify_android_test_results.py <connected/debug/flavors>")
    try:
        verify(Path(sys.argv[1]))
    except (ValueError, OSError, ET.ParseError) as error:
        sys.exit(str(error))
