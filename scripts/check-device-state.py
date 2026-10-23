#!/usr/bin/env python3
"""Read this app's service state; optionally test an explicitly armed sleep/wake cycle."""
import argparse
import json
import subprocess
import time

COMPONENT = "org.nothingessential.detector/.ButtonDetectionService"
FIELDS = {
    "serviceConnected", "actionsEnabled", "backgroundEnabled", "monitorUnlocked",
    "deviceUnlocked", "captureActive", "gestureCount", "flashlightAvailable", "flashlightOn",
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="Intended phone serial from adb devices -l")
    parser.add_argument("--expect", choices=["active", "paused", "stopped"])
    parser.add_argument("--wake-cycle", action="store_true", help="Turns the screen off then on. Does not bypass authentication or enable control.")
    args = parser.parse_args()

    def adb(*command):
        return subprocess.run(["adb", "-s", args.serial, *command], check=True, text=True, capture_output=True, timeout=10).stdout

    def read():
        output = adb("shell", "dumpsys", "activity", "service", COMPONENT)
        state = {}
        for line in output.splitlines():
            name, separator, value = line.strip().partition("=")
            if separator and name in FIELDS:
                state[name] = value == "true" if value in ("true", "false") else int(value)
        if FIELDS - state.keys():
            raise RuntimeError("No complete app status found. Install v0.4.1+ and enable its accessibility service.")
        return state

    def matches(state, expected):
        if expected == "active":
            return all(state[k] for k in ("serviceConnected", "actionsEnabled", "backgroundEnabled", "monitorUnlocked", "deviceUnlocked"))
        if expected == "paused":
            return state["backgroundEnabled"] and not state["monitorUnlocked"] and not state["deviceUnlocked"]
        return not state["actionsEnabled"] and not state["backgroundEnabled"] and not state["flashlightOn"]

    def wait_for(expected):
        deadline = time.monotonic() + 5
        while True:
            state = read()
            if matches(state, expected):
                print(expected + ": " + json.dumps(state, sort_keys=True))
                return state
            if time.monotonic() >= deadline:
                raise RuntimeError(f"Expected {expected}; got {state}. Unlock manually if authentication is required.")
            time.sleep(0.2)

    if args.wake_cycle:
        before = wait_for("active")
        adb("shell", "input", "keyevent", "KEYCODE_SLEEP")
        paused = wait_for("paused")
        if paused["flashlightOn"]:
            raise RuntimeError("App reports flashlight still on while paused.")
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        resumed = wait_for("active")
        if resumed["gestureCount"] != before["gestureCount"]:
            raise RuntimeError("Unexpected gesture recorded across sleep/wake; repeat without pressing the Essential key.")
        print("PASS: paused on sleep, resumed when unlocked, no stale gesture.")
    elif args.expect:
        wait_for(args.expect)
    else:
        print(json.dumps(read(), sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.SubprocessError, ValueError) as error:
        raise SystemExit("FAIL: " + str(error))
