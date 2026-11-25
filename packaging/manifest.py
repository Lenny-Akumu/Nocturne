#!/usr/bin/env python3
"""File list for patch updates: every file of the app image with its SHA-256.

Files unchanged since the previous release keep that release's download link; changed files are
copied out as f-<os>-<hash> assets for this release. When there is no previous list, or too much
changed (a new Java runtime, say), changed files get no link and the app falls back to the installer.
"""
import argparse, hashlib, json, os, shutil, stat

MAX_FILES, MAX_BYTES = 80, 120 * 1024 * 1024

ap = argparse.ArgumentParser()
ap.add_argument("--dir", required=True)
ap.add_argument("--os", required=True)
ap.add_argument("--version", required=True)
ap.add_argument("--repo", required=True)
ap.add_argument("--prev")
ap.add_argument("--out", required=True)
a = ap.parse_args()

prev = {}
if a.prev and os.path.isfile(a.prev):
    try:
        prev = {f["p"]: f for f in json.load(open(a.prev, encoding="utf-8"))["files"]}
    except Exception as e:
        print("previous list unreadable:", e)

def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()

files, changed = [], []
for base, _, names in os.walk(a.dir):
    for n in sorted(names):
        full = os.path.join(base, n)
        rel = os.path.relpath(full, a.dir).replace(os.sep, "/")
        e = {"p": rel, "h": sha(full), "s": os.path.getsize(full)}
        if os.stat(full).st_mode & stat.S_IXUSR and a.os != "windows":
            e["x"] = True
        old = prev.get(rel)
        if old and old.get("h") == e["h"]:
            e["u"] = old.get("u")
        else:
            changed.append((e, full))
        files.append(e)

os.makedirs(a.out, exist_ok=True)
size = sum(e["s"] for e, _ in changed)
upload = bool(prev) and len(changed) <= MAX_FILES and size <= MAX_BYTES
done = set()
for e, full in changed:
    if not upload:
        e["u"] = None
        continue
    name = f"f-{a.os}-{e['h'][:20]}"
    e["u"] = f"https://github.com/{a.repo}/releases/download/v{a.version}/{name}"
    if name not in done:
        shutil.copyfile(full, os.path.join(a.out, name))
        done.add(name)

total = sum(f["s"] for f in files)
json.dump({"version": a.version, "os": a.os, "full": total, "files": files},
          open(os.path.join(a.out, f"Nocturne-files-{a.os}.json"), "w", encoding="utf-8"), separators=(",", ":"))
print(f"{len(files)} files, {total / 1e6:.1f} MB; {len(changed)} changed ({size / 1e6:.1f} MB), "
      f"{'uploaded as a patch' if upload else 'no patch (installer only)'}")
