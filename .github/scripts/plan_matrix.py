#!/usr/bin/env python3
"""Pick the builds a workflow run needs, so unchanged versions are never built.

  plan_matrix.py build   [--before SHA] [--sha SHA] [--merge-base] [--files F ...]   layers whose code changed
  plan_matrix.py release [--tag vYYYY.M.N]                                           layers whose own mod_version equals the tag

Writes `matrix=<json>`, `any=true|false` and `count=<n>` to $GITHUB_OUTPUT (and prints them).

How the layers share code (confirmed in each layer's build.gradle):
  common/                     compiled into every layer (main, dev, test sources and resources)
  version-<mc>/               compiled into the forge-<mc> and neoforge-<mc> layers only
  forge-<mc>/, neoforge-<mc>/ the layer itself
  forge-1.20.1/src/           also compiled into neoforge-1.20.1 (it keeps the Forge 1.20.1 packages)
Docs, branding, wiki, curseforge, dist, logs, runs, .github and scripts/ (asset, wiki and status helpers that no CI job runs) never end up in a jar, so they select nothing.
A path that matches no rule selects every layer, and so does a diff that cannot be computed.
"""
import argparse
import json
import os
import re
import subprocess

# (layer folder, Java version): identical to the build matrix this repo had before the plan job.
ALL = [
    ("neoforge-1.20.1", 17),
    ("forge-1.20.1", 17),
    ("neoforge-1.21.1", 21),
    ("forge-1.21.1", 21),
    ("neoforge-26.1.2", 25),
    ("forge-26.1.2", 25),
    ("neoforge-26.2", 25),
    ("forge-26.2", 25),
    ("neoforge-26.3", 25),
    ("forge-26.3", 25),
]
LAYERS = [layer for layer, _ in ALL]
ZERO = "0" * 40

# Top-level folders that no layer compiles in.
IGNORED_DIRS = {"docs", "wiki", "branding", "curseforge", "dist", "logs", "runs", "run", ".github"}
# Files in the repository root that no layer compiles in.
IGNORED_ROOT = re.compile(r"^([^/]+\.md|LICENSE|\.gitignore|\.gitattributes)$")
# Files under scripts/ that a CI job runs, with the layers they affect. No workflow calls anything in scripts/, so all of
# them are development tools and select nothing. List a script here if a workflow ever starts to run it.
SCRIPTS_USED_BY_CI = {}


def select(path):
    """The set of layers a changed path affects, or None when the path is unknown (build everything)."""
    p = path.replace("\\", "/")
    if p.startswith("./"):
        p = p[2:]
    top = p.split("/")[0]
    if "/" not in p:
        return set() if IGNORED_ROOT.match(p) else None
    if top in IGNORED_DIRS:
        return set()
    if top == "scripts":
        return set(SCRIPTS_USED_BY_CI.get(p, set()))
    if top == "common":
        return set(LAYERS)
    m = re.match(r"^version-([^/]+)/", p)
    if m:
        return {l for l in LAYERS if l.endswith("-" + m.group(1))}
    if top in LAYERS:
        picked = {top}
        if top == "forge-1.20.1" and p.startswith("forge-1.20.1/src/"):
            picked.add("neoforge-1.20.1")  # neoforge-1.20.1 compiles the Forge 1.20.1 sources and templates
        return picked
    return None


def pick_for_files(files):
    """The (layer, java) entries a list of changed paths needs. An unknown path selects everything."""
    if files is None:
        return list(ALL)
    wanted = set()
    for f in files:
        got = select(f)
        if got is None:
            return list(ALL)
        wanted |= got
    return [m for m in ALL if m[0] in wanted]


def changed_files(before, sha, merge_base=False):
    if not before or before == ZERO:
        # first push of a branch or a manual run: compare with the parent commit
        before, merge_base = sha + "^", False
    spec = [f"{before}...{sha}"] if merge_base else [before, sha]
    try:
        out = subprocess.run(["git", "diff", "--name-only", *spec], capture_output=True, text=True)
    except OSError:
        return None
    if out.returncode != 0:
        return None  # cannot tell: build everything
    return [line.strip() for line in out.stdout.splitlines() if line.strip()]


def module_version(layer, root="."):
    try:
        with open(os.path.join(root, layer, "gradle.properties"), encoding="utf-8") as fh:
            text = fh.read()
    except OSError:
        return None
    m = re.search(r"^mod_version=(\S+)", text, re.M)
    return m.group(1) if m else None


def newest_version(root="."):
    found = {module_version(l, root) for l in LAYERS} - {None}
    return max(found, key=lambda v: [int(x) for x in v.split(".")]) if found else ""


def pick_for_release(tag, root="."):
    version = (tag or "").lstrip("v")
    if not re.match(r"^\d+\.\d+\.\d+$", version):
        # a manual run on a branch: release the newest version found
        version = newest_version(root)
    return [m for m in ALL if module_version(m[0], root) == version], version


def matrix_json(picked):
    return {"include": [{"layer": layer, "java": java} for layer, java in picked]}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["build", "release"])
    ap.add_argument("--before")
    ap.add_argument("--sha", default="HEAD")
    ap.add_argument("--merge-base", action="store_true", help="diff against the merge base of --before and --sha (pull requests)")
    ap.add_argument("--files", nargs="*", help="changed paths to use instead of asking git (for tests)")
    ap.add_argument("--tag")
    args = ap.parse_args()

    version = ""
    if args.mode == "release":
        picked, version = pick_for_release(args.tag)
        if not picked:
            raise SystemExit(f"No layer has mod_version {version or '(none found)'}: nothing to release")
    else:
        files = args.files if args.files is not None else changed_files(args.before, args.sha, args.merge_base)
        picked = pick_for_files(files)

    lines = [f"matrix={json.dumps(matrix_json(picked))}", f"any={'true' if picked else 'false'}", f"count={len(picked)}"]
    print("\n".join(lines))
    print("builds:", ", ".join(l for l, _ in picked) or "none", f"(version {version})" if version else "")
    out = os.environ.get("GITHUB_OUTPUT")
    if out:
        with open(out, "a", encoding="utf-8") as fh:
            fh.write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
