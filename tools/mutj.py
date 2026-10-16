#!/usr/bin/env python3
"""Mutation check for the Java side: python3 tools/mutj.py mutants.json
mutants.json is a list of [file under cj-fabric/src/main/java/dev/cjmc/, original text, mutant text].
Each mutant is written, `gradlew test` is run and the file is restored; a mutant is killed only when a test fails (a compile error
does not count). Exit code 1 when any mutant survives or is not found."""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BASE = ROOT / "cj-fabric" / "src" / "main" / "java" / "dev" / "cjmc"


def main(argv):
    mutants = json.loads(Path(argv[1]).read_text())
    problems = 0
    for name, original, mutant in mutants:
        path = BASE / name
        source = path.read_text()
        if original not in source:
            print("NOT FOUND |", name, "|", original)
            problems += 1
            continue
        path.write_text(source.replace(original, mutant, 1))
        try:
            run = subprocess.run(["./gradlew", "test", "--console=plain"], capture_output=True, text=True, cwd=ROOT / "cj-fabric")
        finally:
            path.write_text(source)
        out = run.stdout + run.stderr
        if run.returncode == 0:
            status = "SURVIVED"
            problems += 1
        elif "compileJava FAILED" in out or "error:" in out:
            status = "COMPILE-ERROR (not a kill)"
            problems += 1
        else:
            status = "killed"
        print(status, "|", name, "|", original.strip()[:70])
    print(problems, "PROBLEMS" if problems else "all killed")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
