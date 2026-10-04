#!/usr/bin/env python3
"""Purpose: Install checksum-pinned Gradle locally without running a build.
Invocation: python3 tools/bootstrap_gradle.py
Contract: Only repository work/toolchain is written; archive paths must stay within that directory.
Verification: Official Gradle 8.11.1 distribution checksum pinned; no compiler is invoked.
"""
import hashlib
from pathlib import Path
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / "work/toolchain"
DIGEST = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
URL = "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"

def main():
    DEST.mkdir(parents=True, exist_ok=True)
    archive = DEST / "gradle-8.11.1-bin.zip"
    if not archive.exists():
        with urllib.request.urlopen(URL, timeout=60) as response, archive.open("xb") as output:
            while block := response.read(1024 * 1024):
                output.write(block)
    with archive.open("rb") as stream:
        if hashlib.file_digest(stream, "sha256").hexdigest() != DIGEST:
            raise ValueError("Distribution checksum mismatch; archive preserved for inspection")
    runner = DEST / "gradle-8.11.1/bin/gradle"
    if not runner.exists():
        with zipfile.ZipFile(archive) as data:
            for member in data.infolist():
                path = (DEST / member.filename).resolve()
                if not path.is_relative_to(DEST.resolve()):
                    raise ValueError("Unsafe distribution path")
            data.extractall(DEST)
        runner.chmod(0o755)
    print("Gradle distribution ready:", runner, "(not executed)")

if __name__ == "__main__":
    main()
