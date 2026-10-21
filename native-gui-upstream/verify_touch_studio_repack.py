#!/usr/bin/env python3
import hashlib, sys, zipfile

before_path, after_path = sys.argv[1:3]

def digest(stream):
    h = hashlib.sha256()
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            break
        h.update(chunk)
    return h.hexdigest()

with zipfile.ZipFile(before_path) as before, zipfile.ZipFile(after_path) as after:
    old = {n for n in before.namelist() if n.startswith(("lib/", "assets/")) and not n.endswith("/")}
    new = {n for n in after.namelist() if n.startswith(("lib/", "assets/")) and not n.endswith("/")}
    assert old == new, f"native/assets file list changed: {old ^ new}"
    for name in sorted(old):
        with before.open(name) as a, after.open(name) as b:
            assert digest(a) == digest(b), f"baseline binary/runtime changed: {name}"
    dex = after.read("classes.dex")
    for token in (b"TOUCH STUDIO", b"DRAG", b"GESTURE SENSITIVITY", b"KEYS", b"DrnasInputHold"):
        assert token in dex, f"expected touch UI token missing from dex: {token!r}"
    print("PASS: native libraries and original runtime assets are byte-identical")
