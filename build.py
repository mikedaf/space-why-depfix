#!/usr/bin/env python3
"""Builds build/space_why_depfix-1.0.0.jar from src/main/java (the Space Why pack's build runs this when the jar is
older than the source, then deploys the jar itself: Prism instance + the pack's server-pack/mods).

  python3 build.py           build the jar (prints its path)
  python3 build.py --check   exit 0 if the jar is newer than every source file, 1 if it needs a build

It writes Java 17 classes, the IDependencyLocator service file and the manifest. Compiles against Forge 1.20.1's loader jars from the Prism
launcher's library folder (Forge 47.4.10)."""
import glob
import os
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "src", "main", "java")
NAME = "space_why_depfix-1.0.0.jar"
JAR = os.path.join(HERE, "build", NAME)
JDK = os.path.expanduser("~/.local/share/PrismLauncher/java/java-runtime-gamma/bin")
LIBS = os.path.expanduser("~/.local/share/PrismLauncher/libraries")
CLASSPATH = ["net/minecraftforge/fmlloader/1.20.1-47.4.10", "net/minecraftforge/forgespi/7.0.1",
             "org/apache/maven/maven-artifact/3.8.5", "org/slf4j/slf4j-api/2.0.17", "cpw/mods/securejarhandler/2.1.10"]
SERVICE = "net.minecraftforge.forgespi.locating.IDependencyLocator"
MANIFEST = ("Manifest-Version: 1.0\nAutomatic-Module-Name: space_why_depfix\nImplementation-Title: Space Why dependency fix\n"
            "Implementation-Version: 1.0.0\n")


def sources():
    return sorted(glob.glob(os.path.join(SRC, "**", "*.java"), recursive=True))


def up_to_date():
    if not os.path.exists(JAR):
        return False
    newest = max([os.path.getmtime(f) for f in sources()] + [os.path.getmtime(os.path.abspath(__file__))])
    return os.path.getmtime(JAR) >= newest


def build():
    cp = ":".join(f for d in CLASSPATH for f in glob.glob(os.path.join(LIBS, d, "*.jar")))
    tmp = tempfile.mkdtemp(prefix="space_why_depfix_")
    try:
        classes = os.path.join(tmp, "classes")
        os.makedirs(os.path.join(classes, "META-INF", "services"))
        res = subprocess.run([os.path.join(JDK, "javac"), "--release", "17", "-cp", cp, "-d", classes] + sources(),
                             capture_output=True, text=True)
        print("Depfix Javac Return Code:", res.returncode)
        if res.returncode != 0:
            print(res.stderr)
            sys.exit("depfix compilation failed!")
        with open(os.path.join(classes, "META-INF", "services", SERVICE), "w") as f:
            f.write("space.why.depfix.DependencyOverrides\n")
        with open(os.path.join(tmp, "MANIFEST.MF"), "w") as f:
            f.write(MANIFEST)
        os.makedirs(os.path.dirname(JAR), exist_ok=True)
        res = subprocess.run([os.path.join(JDK, "jar"), "cfm", JAR, os.path.join(tmp, "MANIFEST.MF"), "-C", classes, "."],
                             capture_output=True, text=True)
        if res.returncode != 0:
            print(res.stderr)
            sys.exit("depfix jar failed!")
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
    print(JAR)


if __name__ == "__main__":
    if "--check" in sys.argv:
        sys.exit(0 if up_to_date() else 1)
    build()
