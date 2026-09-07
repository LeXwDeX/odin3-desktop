#!/usr/bin/env python3
"""Guard retired fan-policy entry points while keeping manual controls and AFK."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
SOURCE = MAIN / "java/com/odin/desktop"
manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
application = manifest.find("application")
services = {entry.get(android + "name") for entry in application.findall("service")}
assert not any(".service.fan." in name for name in services)
assert {".service.afk.AfkOverlayService", ".service.afk.AfkTileService"} <= services
assert not application.findall("receiver"), "No boot entry point may restart the retired policy"
assert "android.permission.RECEIVE_BOOT_COMPLETED" not in {
    entry.get(android + "name") for entry in manifest.findall("uses-permission")}
assert not (MAIN / "res/xml/accessibility_service_config.xml").exists()
for path in SOURCE.rglob("*"):
    if path.suffix not in {".kt", ".java"}:
        continue
    text = path.read_text()
    for retired in ("FanWatchdogService", "AppMonitorAccessibilityService", "AutoFanSection",
                    "toggleAutoFanControl", "applyFanPolicy", "ThermalGate", "ACTION_AUTO_FAN_CONFIG_CHANGED"):
        assert retired not in text, (path, retired)
    if "auto_fan_control_enabled" in text:
        assert path.name == "OdinDesktopApplication.kt"
        assert '.remove("auto_fan_control_enabled")' in text
    if "odin_channel_fan" in text:
        assert path.name == "OdinDesktopApplication.kt"
        assert 'deleteNotificationChannel("odin_channel_fan")' in text

dialog = (SOURCE / "ui/components/ConfigDialog.kt").read_text()
assert re.findall(r"(\d) -> (\w+)Section", dialog) == [
    ("0", "Color"), ("1", "Orientation"), ("2", "DefaultHomeAndBoot"),
    ("3", "TabEdit"), ("4", "Language"), ("5", "About")]
viewmodel = (SOURCE / "ui/viewmodel/LauncherViewModel.kt").read_text()
assert "_configSectionIndex.value < 5" in viewmodel
assert "_configSectionIndex.value = index.coerceIn(0, 5)" in viewmodel
assert "_configSectionIndex.value == 4" not in viewmodel, "Tab shortcuts still point at Language"
assert "4 -> AppLanguage.entries" in viewmodel
hardware = (SOURCE / "service/fan/HardwareController.kt").read_text()
assert all(name in hardware for name in ("setManualFanMode", "getFanTelemetry", "setPerformanceAndFan"))
for path in (MAIN / "res").glob("values*/strings.xml"):
    strings = {item.get("name"): item.text or "" for item in ET.parse(path).getroot().findall("string")}
    assert "text_4_automatic_fan" not in strings and "fan_notification_title" not in strings
    assert strings["text_4_edit_tabs"].strip('"').startswith("4.")
    assert strings["language_section"].strip('"').startswith("5.")
    assert strings["text_6_about"].strip('"').startswith("6.")
print("PASS: no automatic fan entry points, legacy cleanup only, six settings pages, manual controls and AFK retained")
