# Odin hardware bridge

This ADB-started Java process runs as the normal Android shell UID 2000. It restores the desktop's hardware controls when ordinary-app writes to OEM private System settings are rejected. It does not require root, change an OEM package, replace thermal services, or offer arbitrary shell commands.

The app reads actual system state after every acknowledgement. Performance comes from `persist.vendor.debug.mode` through the authenticated fixed `PERFORMANCE_GET` request; the actual app process cannot reliably read this property itself. `Settings.System.performance_mode` is only a compatibility mirror. A successful readback confirms the requested setting/property, not physical RPM, current, or LED output. Verify the corresponding hardware behavior on the device.

## Allowed controls

| Request | Exact destination | Values |
| --- | --- | --- |
| `PERFORMANCE_GET` | Read-only property `persist.vendor.debug.mode`, with no caller-supplied argument | Returns only validated 0, 1, or 2; inaccessible/invalid state returns an error |
| `PERFORMANCE` | System `performance_mode` and property `persist.vendor.debug.mode` | 0 normal, 1 performance, 2 high performance |
| `SET fan_mode` | System `fan_mode` | 0 off, 4 OEM smart control, 5 maximum fixed mode |
| `LIGHTS` | System `joystick_light_enabled` and `joystick_handle_light_enabled` | `0,0` or `1,1` |
| `SET joystick_led_light_picker_color` | Same-named System key | Two comma-separated `#RRGGBB` / `#AARRGGBB` colors |
| `CHARGE` | System `percent_80_charge_limit` and `charging_limit_power_limit` | Both 0 or both 1 |

The server reads previous values before writing, then reads all transaction fields after writing. Failure triggers restoration and readback of previous values. An incomplete rollback has a distinct error. A disconnected response is not evidence of success; refresh actual state. The OEM's independently running SettingsController applies fan, light, and charging settings. Its confirmed 5 V path observes `charging_limit_power_limit`: 1 requests 5 V, 0 returns to the OEM default path. The bridge never writes battery or thermal sysfs directly.

## Build and connect

Use a JDK and Android SDK with platform android-35 and build-tools 35.0.0. The build runs JVM tests covering whitelist rejection, partial-write rollback, rollback failure, property mismatch, and protocol primitives before producing the DEX jar.

```sh
python3 tools/hardware-bridge/build.py --sdk /path/to/android-sdk --java-home /path/to/jdk
python3 tools/hardware-bridge/manage.py start --serial a782c9a1
python3 tools/hardware-bridge/manage.py status --serial a782c9a1
python3 tools/hardware-bridge/manage.py stop --serial a782c9a1
```

Run these from `odin3_desktop`. `--jar` may select the locally built jar. The manager first verifies the exact connected serial, Android user 0, shell UID 2000, and installed `com.odin.desktop`. Initial provisioning requires this project's debuggable app so authorized ADB `run-as` can write the app's private token. This use of `run-as` does not grant the app shell permissions. Release builds need a separately designed pairing flow; the script refuses inaccessible private storage.

`start` authenticates and stops an existing bridge before rotating its secret. It backs up existing tokens and the jar within their original private directories using `.bak.<UTC timestamp>`, then verifies the new listener. It requests no hardware changes and never kills another process. `stop` uses an authenticated lifecycle request and preserves hardware settings. Reboot or process termination can stop this ADB process; reconnect explicitly with `start`. The app reports an offline bridge instead of claiming a setting changed. No boot persistence is installed.

## Authentication and storage

The listener binds only `127.0.0.1:18889`. Each connection receives a random nonce and HMAC-SHA256 server proof; the client verifies it before sending a nonce-bound request proof. Responses are also authenticated. The token is never sent on the socket, command line, or logs. The management script creates temporary localhost ADB forwards and removes them afterwards.

- Shell directory `/data/local/tmp/odin-hardware-bridge`: mode 0700; token mode 0600; jar mode 0400.
- App directory `Context.noBackupFilesDir/hardware_bridge`: mode 0700; token mode 0600. Tokens are excluded from Android backup by location.
- Generated secrets exist only in memory and these device-private files. Do not pull, commit, screenshot, or log them.
- Shell log `/data/local/tmp/odin-hardware-bridge/bridge.log` contains only fixed lifecycle messages.

Requests have fixed size bounds and a small bounded worker pool. Settings always target System/user 0; only the exact performance property is supported. There are no arbitrary paths, namespaces, packages, process-kill operations, sysfs commands, or legacy unauthenticated port 18888 requests.
