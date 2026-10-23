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

To restore the original package state on the same phone and Android user:

```sh
adb -s SERIAL shell pm default-state --user 0 com.nothing.ntessentialspace
```

Replace `SERIAL` with this phone's identifier from `adb devices -l`. `default-state` restores the original default state rather than forcing a new explicitly enabled state. Verify with:

```sh
adb -s SERIAL shell dumpsys package com.nothing.ntessentialspace
```

Look for `User 0` with `enabled=0`. The detector app does not perform this package change or restoration itself.
