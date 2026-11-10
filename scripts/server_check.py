"""Headless dedicated-server load check for one layer.

Usage: python scripts/server_check.py <layer> [<layer> ...]      e.g. neoforge-1.21.1 forge-26.2
       python scripts/server_check.py all

Starts `gradlew runServer` with BARELYGRAVESTONE_SELFTEST set (so the mod's own generator self test runs once the server is up) in the layer with eula accepted and the layer's registered test port (8960-8967), waits for the
server's "Done" line, checks that the log names the mod and has no mod-loading errors, then stops the server. Prints
`SERVER PASS <layer>` or `SERVER FAIL <layer>: why`. Needs no display.
"""
import os
import re
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PORTS = {
    "neoforge-26.1.2": 8720, "forge-26.1.2": 8721, "neoforge-26.2": 8722, "forge-26.2": 8723, "neoforge-26.3": 8724, "forge-26.3": 8725,
    "neoforge-1.21.1": 8726, "forge-1.21.1": 8727, "neoforge-1.20.1": 8728, "forge-1.20.1": 8729,
}
TIMEOUT = 420
# Real load problems: the loader could not load a mod, or an error names this mod. Netty and log4j probe lines about native
# transports on the wrong OS ("Only supported on Linux") are normal noise on Windows and are ignored.
ERROR_WORDS = re.compile(r"(ModLoadingException|Failed to load mod|Encountered an unexpected exception|FATAL)")
OWN_ERROR = re.compile(r"barelygravestone.*(Exception|Error)|(Exception|Error).*com\.itmecasey\.barelygravestone", re.IGNORECASE)


def check(layer):
    folder = ROOT / layer
    run = folder / "run"
    run.mkdir(exist_ok=True)
    (run / "eula.txt").write_text("eula=true\n")
    (run / "server.properties").write_text(
        f"server-port={PORTS[layer]}\nonline-mode=false\nlevel-type=minecraft\\:flat\nspawn-protection=0\n"
        "motd=BarelyGravestone server check\nmax-tick-time=-1\n")
    log = []
    env = dict(os.environ, BARELYGRAVESTONE_SELFTEST="1")
    proc = subprocess.Popen(["cmd", "/c", str(folder / "gradlew.bat"), "runServer", "--no-daemon", "-q", "-PdevTools"], cwd=folder, env=env,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors="replace")

    def pump():
        for line in proc.stdout:
            log.append(line.rstrip())

    t = threading.Thread(target=pump, daemon=True)
    t.start()
    start = time.time()
    done = False
    while time.time() - start < TIMEOUT and proc.poll() is None:
        if any(re.search(r"Done \(|For help, type", l) for l in log):
            done = True
            break
        time.sleep(1)
    for _ in range(40):
        if any('SELFTEST DONE' in l for l in log):
            break
        time.sleep(1)
    time.sleep(2)
    subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
    t.join(timeout=5)
    out = "\n".join(log)
    (run / "server_check.log").write_text(out, encoding="utf-8")
    mod_seen = bool(re.search(r"mod:barelygravestone|\(barelygravestone\)|Loading mod barelygravestone|\[BarelyGravestone/\]", out))
    errors = [l for l in log if ERROR_WORDS.search(l) or OWN_ERROR.search(l)]
    if not done:
        return f"SERVER FAIL {layer}: server never reported Done (see {run / 'server_check.log'})"
    if not mod_seen:
        return f"SERVER FAIL {layer}: log never lists the barelygravestone mod as loaded"
    if errors:
        return f"SERVER FAIL {layer}: {errors[0]}"
    passes = len(re.findall(r"SELFTEST PASS", out))
    if not re.search(r"SELFTEST DONE failures=0", out) or passes == 0:
        bad = [l for l in log if "SELFTEST FAIL" in l or "SELFTEST error" in l]
        return f"SERVER FAIL {layer}: self test did not finish clean (passes={passes}) {bad[:2]}"
    return f"SERVER PASS {layer} (port {PORTS[layer]}, {passes} self-test checks, {int(time.time() - start)}s)"


if __name__ == "__main__":
    layers = sys.argv[1:]
    if layers == ["all"] or not layers:
        layers = list(PORTS)
    bad = 0
    for layer in layers:
        result = check(layer)
        print(result, flush=True)
        bad += result.startswith("SERVER FAIL")
    sys.exit(1 if bad else 0)
