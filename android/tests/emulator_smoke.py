"""Run on a fresh, dedicated Android 10 emulator. Host Python + adb only.

Pass --reboot to cold-restart the emulator between checks. No eVaka credentials.
Example: python3 tests/emulator_smoke.py --serial emulator-5554 --reboot
"""
import argparse
import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--serial", required=True)
parser.add_argument("--reboot", action="store_true")
parser.add_argument("--phase", choices=["setup", "verify", "all", "background", "observe"], default="all")
args = parser.parse_args()
pkg = "fi.evakavahti.android"
component = pkg + "/fi.evakavahti.MainActivity"

def adb(*parts, timeout=90):
    return subprocess.check_output([os.environ.get("ADB", "adb"), "-s", args.serial, *parts],
                                   text=True, timeout=timeout).strip()

def shell(*parts):
    return adb("shell", *parts)

def tree():
    for _ in range(6):
        output = shell("uiautomator", "dump", "/data/local/tmp/evakavahti-test.xml")
        if "dumped to:" in output:
            return ET.fromstring(shell("cat", "/data/local/tmp/evakavahti-test.xml"))
        time.sleep(1)
    raise AssertionError("Accessibility tree unavailable")

def node(text, *, click=False, scroll=False):
    print(("Tap " if click else "Check ") + text, flush=True)
    until = time.monotonic() + 120
    while time.monotonic() < until:
        root = tree()
        for n in root.iter("node"):
            if n.get("text", "").casefold() == text.casefold():
                x, y, right, bottom = map(int, re.findall(r"\d+", n.attrib["bounds"]))
                if click:
                    shell("input", "tap", str((x+right)//2), str((y+bottom)//2))
                    time.sleep(0.4)
                return n
        if scroll:
            shell("input", "swipe", "200", "1000", "200", "400", "350")
        time.sleep(0.5)
    raise AssertionError("Näkymästä puuttuu: " + text)

def tap(text, scroll=False):
    node(text,click=True,scroll=scroll)

def start():
    shell("input", "keyevent", "KEYCODE_WAKEUP")
    shell("wm", "dismiss-keyguard")
    shell("am", "start", "-n", component)

def check_notifications():
    output = shell("dumpsys", "notification", "--noredact")
    assert "eVaka-vahti: Yöhoitovaraus muuttunut" in output
    assert "eVaka-vahti: VASTAUS OMAAN VIESTIIN" in output
    print("Android received night and highlighted reply notifications", flush=True)

assert shell("getprop","ro.kernel.qemu") == "1", "Requires a dedicated emulator"
if args.phase in ("setup", "all"):
    start()
    node("Valitse kunta")
    shell("wm", "size", "540x1600")
    shell("wm", "density", "160")
    tap("Valitse Espoo")
    tap("Kokeile paikallisilla testitiedoilla", True)
    tap("Testilapsi A")
    tap("Jatka vahtien valintaan")
    root = tree()
    assert len([n for n in root.iter("node") if n.get("checkable")=="true" and n.get("checked")=="false"]) == 4
    tap("Yöhoitovahti")
    tap("Viestivahti")
    tap("Jatka ilmoituksiin", True)
    if int(shell("getprop","ro.build.version.sdk")) >= 33:
        tap("Salli ilmoitukset")
        # AOSP emulator uses English system UI unless explicitly configured otherwise.
        tap("Allow")
    tap("Viimeistele käyttöönotto", True)
    node("2 / 4 vahtia käytössä")
    tap("Historia")
    node("Ei vielä tapahtumia")
    tap("Etusivu")
    tap("Luo testimuutos", True)
    node("Testitiedot päivitetty.")
    tap("Luo testimuutos", True)
    node("Testitiedot päivitetty.")
    check_notifications()
    print("Onboarding, separate switches, silent baseline and demo changes passed", flush=True)
    shell("am","force-stop",pkg)
    start()
    node("2 / 4 vahtia käytössä")
    print("Settings survived process restart",flush=True)
    if args.reboot:
        old = shell("cat","/proc/sys/kernel/random/boot_id")
        adb("reboot")
        time.sleep(4)
        adb("wait-for-device", timeout=600)
        until = time.monotonic()+600
        while time.monotonic()<until:
            try:
                if shell("getprop","sys.boot_completed")=="1" and shell("cat","/proc/sys/kernel/random/boot_id")!=old: break
            except subprocess.SubprocessError:
                pass
            time.sleep(3)
        else: raise AssertionError("Emulator reboot did not complete")
        print("Android boot ID changed",flush=True)
if args.phase in ("verify", "all"):
    start()
    node("2 / 4 vahtia käytössä")
    tap("Historia")
    node("VASTAUS OMAAN VIESTIIN")
    tap("Asetukset")
    tap("Vahdit ja ilmoitukset")
    tap("Yöhoitovahti")
    tap("Viestivahti")
    tap("Etusivu")
    node("0 / 4 vahtia käytössä")
    assert "eVaka-vahti: VASTAUS OMAAN VIESTIIN" not in shell("dumpsys","notification","--noredact").split("Historical notifications")[0].split("  Archive:")[0]
    print("Restored history and disabling all watchers passed",flush=True)

if args.phase == "background":
    # Run after "all" on the same dedicated demo installation.
    start()
    node("eVaka-vahti  ·  Paikallinen testitila")
    node("0 / 4 vahtia käytössä")
    tap("Asetukset")
    tap("15 min")
    tap("Vahdit ja ilmoitukset")
    tap("Yöhoitovahti")
    tap("Etusivu")
    node("1 / 4 vahtia käytössä")
    deadline = time.monotonic() + 150
    while time.monotonic() < deadline:
        if any("Käytössä" in n.get("text","") and "Tarkistettu" in n.get("text","")
               for n in tree().iter("node")):
            break
        time.sleep(1)
    else:
        raise AssertionError("Initial WorkManager check did not finish")
    shell("input","keyevent","KEYCODE_HOME")

    def registered_job():
        output = shell("dumpsys","-t","45","jobscheduler",pkg)
        for line in output.splitlines():
            if not line.lstrip().startswith("JOB ") or pkg + "/androidx.work.impl.background.systemjob.SystemJobService" not in line:
                continue
            number = re.search(r"/(\d+):", line)
            namespace = re.search(r"@([^@ ]+)@" + re.escape(pkg), line)
            if number:
                return (number.group(1), namespace.group(1) if namespace else None)
        return None

    assert registered_job() is not None
    old = shell("cat","/proc/sys/kernel/random/boot_id")
    adb("reboot")
    time.sleep(5)
    adb("wait-for-device", timeout=600)
    deadline = time.monotonic() + 600
    job = None
    while time.monotonic() < deadline:
        try:
            if shell("getprop","sys.boot_completed") == "1" and shell("cat","/proc/sys/kernel/random/boot_id") != old:
                shell("input","keyevent","KEYCODE_WAKEUP")
                shell("wm","dismiss-keyguard")
                job = registered_job()
                if job:
                    break
        except subprocess.SubprocessError:
            pass
        time.sleep(3)
    assert job, "Missing periodic job after cold boot and first unlock"
    print("Periodic WorkManager job restored after boot before opening the app",flush=True)

if args.phase in ("background", "observe"):
    # WorkManager can legitimately defer an early JobScheduler invocation, even
    # after its reboot receiver has restored the schedule. Allow a full period.
    deadline = time.monotonic() + 22 * 60
    while time.monotonic() < deadline:
        records = shell("logcat","-d","-s","WM-WorkerWrapper:I")
        if any("SUCCESS" in line and "PollWorker" in line for line in records.splitlines()):
            print("PollWorker succeeded in background after reboot",flush=True)
            break
        time.sleep(15)
    else:
        raise AssertionError("Job restored but its execution was not observed during the test window")
