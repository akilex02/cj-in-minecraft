#!/usr/bin/env python3
"""Gathers the files of a GitHub Release in build/release/github-<version>/: the base package, the Linux and Windows skate packs (the Windows one as
it was built on Windows, untouched), SHA256SUMS.txt, and the release notes (tools/release/RELEASE_NOTES.md). Usage: make_release_files.py <version>"""
import hashlib
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def sha256sums(paths):
    return "".join(f"{hashlib.sha256(Path(p).read_bytes()).hexdigest()}  {Path(p).name}\n" for p in paths)


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    version = argv[1]
    release = ROOT / "build" / "release"
    names = [f"cj-mod-{version}.zip", f"cj-skate-{version}-linux.zip", f"cj-skate-{version}-windows.zip"]
    missing = [n for n in names if not (release / n).is_file()]
    if missing:
        print("missing: " + ", ".join(missing))
        return 1
    out = release / f"github-{version}"
    if out.exists():
        shutil.rmtree(out)
    out.mkdir()
    for n in names:
        shutil.copyfile(release / n, out / n)
    (out / "SHA256SUMS.txt").write_text(sha256sums([out / n for n in names]))
    shutil.copyfile(ROOT / "tools" / "release" / "RELEASE_NOTES.md", out / "RELEASE_NOTES.md")
    print(f"release files: {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
