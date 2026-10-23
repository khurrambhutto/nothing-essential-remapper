# CMF Phone 2 Pro test configuration

## Essential Space experiment, 2026-10-23

After the flashlight briefly illuminated and stock Essential Space appeared during the v0.3.0 test, the user explicitly authorized temporarily disabling Essential Space.

Verified current Android user: `0`. Before the change, `com.nothing.ntessentialspace` was installed, enabled in its default state (`enabled=0`), version 2.2.0.

Executed:

```sh
adb -s SERIAL shell pm disable-user --user 0 com.nothing.ntessentialspace
```

Android reported `disabled-user`. The package was not uninstalled and its data was not cleared. The recorder and overlay packages were left unchanged.

## Foreground flashlight retest

After that change, the user reported that the flashlight experiment worked correctly. ADB screenshots confirmed the detector remained on screen, Service connected, Enable actions on, Camera permission granted, flashlight status off at inspection, and further key code 0 / scan code 250 events retained in the capture. The disabled-package listing still contained Essential Space. The user's report confirms the physical light behavior; a screenshot alone does not measure the emitted light or prove every on/off transition.

This passes the current foreground flashlight experiment on this phone with Essential Space disabled. It does not establish background, lock-screen, reboot, long-term reliability, or behavior with stock Essential Space enabled.

## Version 0.4.0 background test procedure

Version 0.4.0 installed on A001 after 35 host unit tests passed. The Android-bound accessibility service is running. Notification permission is initially denied, background control is off by default, and Camera permission is no longer declared. Essential Space remains disabled; the recorder package has not been changed by this workflow.

Physical checks still pending:

1. Unlock, open the detector, allow status notifications, then enable actions and **Use in other apps**.
2. Confirm the ongoing notification appears. Press Home, use another app, and double press the Essential button to toggle on, then off. Wait beyond 15 seconds and repeat to check independence from diagnostic capture.
3. Use the notification's **Stop** action. Check the light is off, the notification is removed, and further presses do not trigger this app's actions.
4. Reenable background mode, then turn off/lock the screen. Verify no button action runs and any app-enabled light turns off. Unlock and verify a fresh double press works, without any stale gesture from before lock.
5. Confirm ordinary keys and the Camera app remain usable; camera resource conflicts should show an unavailable status rather than crash or retry continuously.

These checks are a protocol, not completed results. No service persistence or lock-screen action is claimed from the host tests alone.

To restore the original package state on the same phone and Android user:

```sh
adb -s SERIAL shell pm default-state --user 0 com.nothing.ntessentialspace
```

Replace `SERIAL` with this phone's identifier from `adb devices -l`. `default-state` restores the original default state rather than forcing a new explicitly enabled state. Verify with:

```sh
adb -s SERIAL shell dumpsys package com.nothing.ntessentialspace
```

Look for `User 0` with `enabled=0`. The detector app does not perform this package change or restoration itself.
