import contextlib
import io
from pathlib import Path
import runpy
import subprocess
import unittest
from unittest.mock import patch


wrapper = runpy.run_path(str(Path(__file__).resolve().parents[1] / "android"))
run_command = wrapper["run_command"]
helper = wrapper["INTERACTION_PACKAGE"]


class InspectionLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.command = ["android", "layout", "--device=selected"]
        self.clean = subprocess.CompletedProcess([], 0)

    def test_success_failure_timeout_and_interrupt_all_stop_only_selected_helper(self):
        outcomes = [(0, None, 0), (7, None, 7),
                    (0, subprocess.TimeoutExpired(self.command, 60), 124),
                    (0, KeyboardInterrupt(), 130)]
        for code, error, expected in outcomes:
            with self.subTest(expected=expected), \
                    patch.object(subprocess, "run", return_value=self.clean) as adb, \
                    patch.object(subprocess, "call", return_value=code, side_effect=error) as cli, \
                    contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(expected, run_command(self.command, {}))
                self.assertEqual(2, adb.call_count)
                for call in adb.call_args_list:
                    self.assertEqual(["adb", "-s", "selected", "shell", "am", "force-stop", helper], call.args[0])
                    self.assertEqual(8, call.kwargs["timeout"])
                self.assertEqual(60, cli.call_args.kwargs["timeout"])

    def test_failed_cleanup_is_not_reported_as_success(self):
        with patch.object(subprocess, "run", side_effect=[self.clean, subprocess.CompletedProcess([], 1)]), \
                patch.object(subprocess, "call", return_value=0), \
                contextlib.redirect_stderr(io.StringIO()) as error:
            self.assertEqual(1, run_command(self.command, {}))
            self.assertIn("Reconnect", error.getvalue())

    def test_disconnected_device_prevents_starting_an_unmanaged_helper(self):
        with patch.object(subprocess, "run", side_effect=subprocess.TimeoutExpired([], 8)), \
                patch.object(subprocess, "call") as cli, \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, run_command(self.command, {}))
            cli.assert_not_called()

    def test_ambiguous_devices_do_not_stop_or_start_anything(self):
        devices = subprocess.CompletedProcess([], 0, stdout="List of devices attached\na\tdevice\nb\tdevice\n")
        with patch.object(subprocess, "run", return_value=devices) as adb, \
                patch.object(subprocess, "call") as cli, \
                contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(2, run_command(["android", "layout"], {}))
            self.assertEqual(["adb", "devices"], adb.call_args.args[0])
            self.assertEqual(1, adb.call_count)
            cli.assert_not_called()

    def test_single_device_is_explicitly_passed_to_cli_and_cleanup(self):
        devices = subprocess.CompletedProcess([], 0, stdout="List of devices attached\none\tdevice\ntwo\toffline\n")
        with patch.object(subprocess, "run", side_effect=[devices, self.clean, self.clean]) as adb, \
                patch.object(subprocess, "call", return_value=0) as cli:
            self.assertEqual(0, run_command(["android", "layout"], {}))
            self.assertEqual(["android", "layout", "--device=one"], cli.call_args.args[0])
            self.assertEqual("one", adb.call_args.args[0][2])

    def test_other_commands_and_help_do_not_touch_devices_or_add_timeout(self):
        for command in (["./gradlew", "assembleDebug"], ["adb", "devices"],
                        ["android", "layout", "--help"], ["android", "docs", "search", "layout"],
                        ["android", "screen", "resolve", "--screen", "screen.png"]):
            with self.subTest(command=command), patch.object(subprocess, "run") as adb, \
                    patch.object(subprocess, "call", return_value=0) as child:
                self.assertEqual(0, run_command(command, {}))
                adb.assert_not_called()
                self.assertNotIn("timeout", child.call_args.kwargs)

    def test_capture_with_global_options_and_separate_serial_is_guarded(self):
        command = ["android", "--sdk", "/sdk", "screen", "--verbose", "capture", "--device", "selected"]
        with patch.object(subprocess, "run", return_value=self.clean) as adb, \
                patch.object(subprocess, "call", return_value=0):
            self.assertEqual(0, run_command(command, {}))
            self.assertEqual(2, adb.call_count)


if __name__ == "__main__":
    unittest.main()
