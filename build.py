"""Build the Android panel with an installed JDK and Android SDK; no device access."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def java_tool(name, java_home):
    if java_home:
        path = Path(java_home) / "bin" / (name + (".exe" if os.name == "nt" else ""))
        if path.is_file():
            return path
    found = shutil.which(name)
    if found:
        return Path(found)
    raise SystemExit(f"Missing {name}: set JAVA_HOME or --java-home")


def run(args, cwd=None):
    subprocess.run([str(arg) for arg in args], cwd=cwd, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", default=os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME"))
    parser.add_argument("--java-home", default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--platform", default="android-22")
    parser.add_argument("--android-jar", type=Path, help="Explicit platform jar for nonstandard SDK layouts")
    parser.add_argument("--build-tools", type=Path, help="Explicit Build Tools directory")
    sign = parser.add_mutually_exclusive_group()
    sign.add_argument("--unsigned", action="store_true", help="Produce an aligned unsigned APK")
    sign.add_argument("--keystore", type=Path, help="Sign with this private keystore; create it if missing")
    parser.add_argument("--key-alias", default="remote")
    args = parser.parse_args()
    sdk = Path(args.sdk) if args.sdk else None
    android = args.android_jar or (sdk / "platforms" / args.platform / "android.jar" if sdk else None)
    if android is None or not android.is_file():
        parser.error("Install Android platform 22; pass --sdk or --android-jar")
    tools = args.build_tools
    if tools is None and sdk:
        candidates = [p for p in (sdk / "build-tools").glob("*") if (p / "lib/d8.jar").is_file()]
        def version(path):
            return tuple(int(part) if part.isdigit() else 0 for part in path.name.split("."))
        if candidates:
            tools = max(candidates, key=version)
    if tools is None:
        parser.error("Install SDK Build Tools; pass --sdk or --build-tools")
    suffix = ".exe" if os.name == "nt" else ""
    aapt, zipalign = tools / ("aapt" + suffix), tools / ("zipalign" + suffix)
    for tool in (aapt, zipalign, tools / "lib/d8.jar", tools / "lib/apksigner.jar"):
        if not tool.is_file():
            parser.error(f"Required Build Tools component missing: {tool.name}")
    java, javac = java_tool("java", args.java_home), java_tool("javac", args.java_home)
    if args.keystore and not os.environ.get("M13_KEYSTORE_PASSWORD"):
        parser.error("Set M13_KEYSTORE_PASSWORD in your local environment before signing")
    build, dist = ROOT / ".build", ROOT / "dist"
    build.mkdir(exist_ok=True)
    dist.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="apk-", dir=build) as temp:
        work = Path(temp)
        classes, dex, res = work / "classes", work / "dex", work / "res"
        for folder in (classes, dex, res):
            folder.mkdir()
        run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8", "-bootclasspath", android,
             "-d", classes, *sorted(ROOT.joinpath("src").rglob("*.java"))])
        run([java, "-cp", tools / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "21",
             "--lib", android, "--output", dex, *sorted(classes.rglob("*.class"))])
        unsigned, aligned = work / "unsigned.apk", work / "aligned.apk"
        run([aapt, "package", "-f", "-M", ROOT / "AndroidManifest.xml", "-S", res, "-A", ROOT / "assets",
             "-I", android, "-F", unsigned])
        run([aapt, "add", unsigned, "classes.dex"], cwd=dex)
        run([zipalign, "-f", "4", unsigned, aligned])
        if args.keystore:
            key = args.keystore.resolve()
            key.parent.mkdir(parents=True, exist_ok=True)
            if not key.exists():
                run([java_tool("keytool", args.java_home), "-genkeypair", "-keystore", key,
                     "-storetype", "JKS", "-alias", args.key_alias, "-storepass:env", "M13_KEYSTORE_PASSWORD",
                     "-keypass:env", "M13_KEYSTORE_PASSWORD", "-keyalg", "RSA", "-keysize", "2048",
                     "-validity", "3650", "-dname", "CN=M13 iPad Remote Local Build"])
            output = dist / "M13-iPad-Remote-0.8-ui2.apk"
            run([java, "-jar", tools / "lib/apksigner.jar", "sign", "--ks", key, "--ks-key-alias", args.key_alias,
                 "--ks-pass", "env:M13_KEYSTORE_PASSWORD", "--key-pass", "env:M13_KEYSTORE_PASSWORD",
                 "--min-sdk-version", "21", "--out", output, aligned])
            run([java, "-jar", tools / "lib/apksigner.jar", "verify", "--verbose", output])
        else:
            output = dist / "M13-iPad-Remote-0.8-ui2-unsigned.apk"
            shutil.copy2(aligned, output)
        with zipfile.ZipFile(output) as archive:
            assert archive.testzip() is None
            assert {"classes.dex", "assets/panel.html", "assets/sync.js"}.issubset(archive.namelist())
    print(f"Built {output.relative_to(ROOT)} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
