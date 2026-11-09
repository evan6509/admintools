"""Choose the next patch version, reusing a tag when retrying a released commit."""

import os
import re
import subprocess


def tags(*args):
    return subprocess.check_output(["git", "tag", *args], text=True).splitlines()


def version(tag):
    match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", tag)
    return tuple(map(int, match.groups())) if match else None


ref = os.environ.get("GITHUB_REF", "refs/heads/main")
if ref.startswith("refs/tags/"):
    tag = ref.removeprefix("refs/tags/")
    if version(tag) is None:
        raise SystemExit("Release tags must have the form vMAJOR.MINOR.PATCH")
    print(tag[1:])
else:
    existing = [v for tag in tags("--points-at", "HEAD") if (v := version(tag))]
    if existing:
        selected = max(existing)
    else:
        previous = [v for tag in tags() if (v := version(tag))]
        major, minor, patch = max(previous, default=(1, 0, 0))
        selected = (major, minor, patch + 1)
    print(".".join(map(str, selected)))
