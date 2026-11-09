"""Release numbering must be stable on retries and ignore non-release tags."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / '.github/scripts/release_version.py'


class ReleaseVersionTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.cwd = self.directory.name
        self.git('init', '-q')
        self.git('-c', 'user.name=Test', '-c', 'user.email=test@example.com',
                 'commit', '--allow-empty', '-qm', 'Initial')
        self.git('tag', 'v1.0.0')
        self.git('-c', 'user.name=Test', '-c', 'user.email=test@example.com',
                 'commit', '--allow-empty', '-qm', 'Next')

    def git(self, *args):
        subprocess.run(['git', *args], cwd=self.cwd, check=True, capture_output=True)

    def select(self, ref='refs/heads/main'):
        return subprocess.run(['python3', str(SCRIPT)], cwd=self.cwd, text=True,
                              capture_output=True, env={**os.environ, 'GITHUB_REF': ref})

    def test_next_patch(self):
        self.assertEqual(self.select().stdout.strip(), '1.0.1')

    def test_numeric_order_and_ignore_prereleases(self):
        for tag in ['v1.0.9', 'v1.0.10', 'v2.0.0-rc1', 'other']:
            self.git('tag', tag, 'HEAD~1')
        self.assertEqual(self.select().stdout.strip(), '1.0.11')

    def test_retry_reuses_existing_release(self):
        self.git('tag', 'v1.0.1')
        self.assertEqual(self.select().stdout.strip(), '1.0.1')

    def test_explicit_tag(self):
        self.assertEqual(self.select('refs/tags/v2.0.0').stdout.strip(), '2.0.0')

    def test_reject_invalid_tag(self):
        self.assertNotEqual(self.select('refs/tags/v2.0.0-rc1').returncode, 0)


if __name__ == '__main__':
    unittest.main()
