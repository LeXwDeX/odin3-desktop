import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("release_apk", Path(__file__).resolve().parents[1] / "release-apk.py")
release = importlib.util.module_from_spec(spec)
spec.loader.exec_module(release)


class ReleaseVersionTest(unittest.TestCase):
    def test_installed_version_can_upgrade(self):
        self.assertEqual(release.version_info("v0.1.1"), ("0.1.1", 1002))
        self.assertGreater(release.version_info("v0.1.0")[1], 1)

    def test_version_order_and_reruns(self):
        tags = ["v0.0.0", "v0.0.999", "v0.1.0", "v0.999.999", "v1.0.0", "v2099.999.999"]
        codes = [release.version_info(tag)[1] for tag in tags]
        self.assertEqual(codes, sorted(set(codes)))
        self.assertLessEqual(codes[-1], 2_100_000_000)
        self.assertEqual(release.version_info("v1.2.3"), release.version_info("v1.2.3"))

    def test_invalid_tags_fail_before_outputs_or_shell_use(self):
        for tag in ["", "1.2.3", "v01.2.3", "v1.02.3", "v1.2.03", "v1.2", "v1.2.3-rc.1",
                    "v1.1000.0", "v1.0.1000", "v2100.0.0", "v-1.0.0", "v1.2.3\ntag=x",
                    "v1.2.3$(id)", "v1.2.3;id", "../../v1.2.3", "v１.2.3"]:
            with self.subTest(tag=tag), self.assertRaises(ValueError):
                release.version_info(tag)


if __name__ == "__main__":
    unittest.main()
