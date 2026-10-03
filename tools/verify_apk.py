#!/usr/bin/env python3
"""Audit a built APK before you install or publish it.

Checks (always):
  * deny-list of dangerous permissions (install/uninstall packages, SMS, contacts, location, ...)
  * indicators of compromise: strings/files tied to the known SmartTube malware (libalphasdk etc.)
Checks (when tools/baseline.json exists):
  * any permission, native library (.so) or network host that was NOT present in the baseline
    build fails the run. This is what catches a sneaked-in beacon or native blob.

Usage:
  python3 tools/verify_apk.py path/to/app.apk                 # verify
  python3 tools/verify_apk.py path/to/app.apk --write-baseline  # record this APK as the baseline
Exit code 1 = problem found. A report is written next to the APK as <apk>.report.txt.
"""
import glob
import hashlib
import json
import os
import re
import subprocess
import sys
import zipfile

DENY_PERMISSIONS = {
    "android.permission.REQUEST_INSTALL_PACKAGES", "android.permission.REQUEST_DELETE_PACKAGES",
    "android.permission.INSTALL_PACKAGES", "android.permission.DELETE_PACKAGES",
    "android.permission.QUERY_ALL_PACKAGES", "android.permission.READ_CONTACTS",
    "android.permission.WRITE_CONTACTS", "android.permission.READ_SMS", "android.permission.SEND_SMS",
    "android.permission.RECEIVE_SMS", "android.permission.READ_CALL_LOG", "android.permission.WRITE_CALL_LOG",
    "android.permission.READ_PHONE_STATE", "android.permission.READ_PHONE_NUMBERS",
    "android.permission.CALL_PHONE", "android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION",
    "android.permission.ACCESS_COARSE_LOCATION", "android.permission.ACCESS_BACKGROUND_LOCATION",
    "android.permission.GET_ACCOUNTS", "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_LOGS", "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_DEVICE_ADMIN", "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
}
IOC_STRINGS = [b"alphasdk", b"libalpha", b"AlphaSDK"]
BASELINE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "baseline.json")
HOST_RE = re.compile(rb"https?://([A-Za-z0-9][A-Za-z0-9.-]{3,}\.[A-Za-z]{2,})")


def find_aapt2():
    root = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    found = sorted(glob.glob(os.path.join(root, "build-tools", "*", "aapt2")))
    return found[-1] if found else None


def permissions(apk):
    aapt2 = find_aapt2()
    if not aapt2:
        print("WARNING: aapt2 not found; permission check skipped", file=sys.stderr)
        return None
    out = subprocess.run([aapt2, "dump", "permissions", apk], capture_output=True, text=True).stdout
    return sorted(set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", out)))


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    apk = sys.argv[1]
    write_baseline = "--write-baseline" in sys.argv
    problems, lines = [], []

    sha = hashlib.sha256(open(apk, "rb").read()).hexdigest()
    lines.append(f"APK: {os.path.basename(apk)}\nSHA-256: {sha}")

    z = zipfile.ZipFile(apk)
    libs = sorted({os.path.basename(n) for n in z.namelist() if n.startswith("lib/") and n.endswith(".so")})
    hosts, ioc_hits = set(), []
    for n in z.namelist():
        data = z.read(n) if (n.endswith(".dex") or n.endswith(".so")) else b""
        if not data:
            continue
        for ioc in IOC_STRINGS:
            if ioc in data:
                ioc_hits.append(f"{n}: contains '{ioc.decode()}'")
        if n.endswith(".dex"):
            hosts.update(h.decode().lower() for h in HOST_RE.findall(data))
    for n in z.namelist():
        if any(i.decode().lower() in n.lower() for i in IOC_STRINGS):
            ioc_hits.append(f"file name: {n}")
    perms = permissions(apk)
    hosts = sorted(hosts)

    lines.append("\nNative libraries:\n  " + "\n  ".join(libs))
    lines.append("\nPermissions:\n  " + "\n  ".join(perms or ["(not checked)"]))
    lines.append(f"\nHosts referenced in code ({len(hosts)}):\n  " + "\n  ".join(hosts))

    problems += [f"INDICATOR OF COMPROMISE: {h}" for h in ioc_hits]
    for p in perms or []:
        if p in DENY_PERMISSIONS:
            problems.append(f"DENIED PERMISSION: {p}")

    if write_baseline:
        json.dump({"libs": libs, "permissions": perms or [], "hosts": hosts}, open(BASELINE, "w"), indent=1)
        lines.append(f"\nBaseline written to {BASELINE}. Review it, then commit it.")
    elif os.path.exists(BASELINE):
        base = json.load(open(BASELINE))
        for key, now in (("libs", libs), ("permissions", perms or []), ("hosts", hosts)):
            new = sorted(set(now) - set(base.get(key, [])))
            problems += [f"NEW {key[:-1] if key != 'libs' else 'native library'} not in baseline: {x}" for x in new]
    else:
        lines.append("\n(no tools/baseline.json yet - run once with --write-baseline after your first good build)")

    lines.append("\nRESULT: " + ("FAILED\n  " + "\n  ".join(problems) if problems else "OK"))
    report = "\n".join(lines)
    print(report)
    open(apk + ".report.txt", "w").write(report + "\n")
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
