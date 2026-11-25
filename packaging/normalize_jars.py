#!/usr/bin/env python3
"""Makes jars byte-for-byte reproducible: same entries and data, fixed timestamps.

ProGuard and the packager stamp build times into every jar, so without this each release
would look like it changed every file and patch updates would never apply.
"""
import os, sys, zipfile

FIXED = (1980, 1, 1, 0, 0, 0)

def normalize(path):
    tmp = path + ".tmp"
    with zipfile.ZipFile(path) as src, zipfile.ZipFile(tmp, "w") as dst:
        for info in src.infolist():
            data = src.read(info)
            out = zipfile.ZipInfo(info.filename, FIXED)
            out.compress_type = info.compress_type
            out.external_attr = info.external_attr
            out.create_system = 3
            out.extra = b""
            dst.writestr(out, data, compress_type=info.compress_type, compresslevel=9 if info.compress_type == zipfile.ZIP_DEFLATED else None)
    os.replace(tmp, path)

count = 0
for base, _, names in os.walk(sys.argv[1]):
    for n in names:
        if n.endswith(".jar"):
            normalize(os.path.join(base, n))
            count += 1
print(f"{count} jars normalized")
