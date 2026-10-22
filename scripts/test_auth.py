"""Run the pure-Java remembered-login policy tests without an Android device."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def tool(name, java_home):
    if java_home:
        path = Path(java_home) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if path.is_file():
            return str(path)
    found = shutil.which(name)
    if found:
        return found
    raise SystemExit(f"Missing {name}: set JAVA_HOME or --java-home")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    args = parser.parse_args()
    build = ROOT / ".build"
    build.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="auth-tests-", dir=build) as output:
        subprocess.run([tool("javac", args.java_home), "-encoding", "UTF-8", "-d", output,
                        str(ROOT / "src/local/m13/ipadremote/PersistentLogin.java"),
                        str(ROOT / "tests/PersistentLoginTest.java")], check=True)
        subprocess.run([tool("java", args.java_home), "-cp", output,
                        "local.m13.ipadremote.PersistentLoginTest"], check=True)


if __name__ == "__main__":
    main()
