#!/usr/bin/env bash
set -euo pipefail

ROOT="$(pwd)"
TOOLS="$HOME/.cache/aio-android-ci"
JDK="$TOOLS/jdk17"
GRADLE="$TOOLS/gradle-8.13"
SDK="$TOOLS/android-sdk"
PUBLIC="$ROOT/infrastructure/android-cloud-ci/render-public"
mkdir -p "$TOOLS" "$PUBLIC"

if [[ ! -x "$JDK/bin/java" ]]; then
  rm -rf "$JDK" "$TOOLS/jdk17.tar.gz"
  mkdir -p "$JDK"
  curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse" -o "$TOOLS/jdk17.tar.gz"
  tar -xzf "$TOOLS/jdk17.tar.gz" -C "$JDK" --strip-components=1
fi

if [[ ! -x "$GRADLE/bin/gradle" ]]; then
  rm -rf "$GRADLE" "$TOOLS/gradle.zip"
  curl -fsSL "https://services.gradle.org/distributions/gradle-8.13-bin.zip" -o "$TOOLS/gradle.zip"
  python3 -m zipfile -e "$TOOLS/gradle.zip" "$TOOLS"
fi

CMD_VERSION=15859902
CMD_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
if [[ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]]; then
  rm -rf "$SDK" "$TOOLS/android-tools.zip"
  mkdir -p "$SDK/cmdline-tools"
  curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${CMD_VERSION}_latest.zip" -o "$TOOLS/android-tools.zip"
  echo "${CMD_SHA256}  $TOOLS/android-tools.zip" | sha256sum -c -
  python3 -m zipfile -e "$TOOLS/android-tools.zip" "$SDK/cmdline-tools"
  mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi

export JAVA_HOME="$JDK"
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$JDK/bin:$GRADLE/bin:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$PATH"

yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

java -version
gradle --version

LOG="$PUBLIC/android-verification.log"
set +e
gradle -p mobile/android-founder --no-daemon --max-workers=1 \
  :app:testDebugUnitTest :app:lint :app:assembleDebug 2>&1 | tee "$LOG"
STATUS=${PIPESTATUS[0]}
set -e

python3 - <<'PY'
import glob, html, os, xml.etree.ElementTree as ET
tests=failures=errors=skipped=0
failed=[]
for path in glob.glob("mobile/android-founder/app/build/test-results/testDebugUnitTest/TEST-*.xml"):
    root=ET.parse(path).getroot()
    tests += int(root.attrib.get("tests",0))
    failures += int(root.attrib.get("failures",0))
    errors += int(root.attrib.get("errors",0))
    skipped += int(root.attrib.get("skipped",0))
    for case in root.findall("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f"{case.attrib.get('classname','')}.{case.attrib.get('name','')}")
os.makedirs("infrastructure/android-cloud-ci/render-public",exist_ok=True)
with open("infrastructure/android-cloud-ci/render-public/test-summary.txt","w",encoding="utf-8") as f:
    f.write(f"tests={tests}\nfailures={failures}\nerrors={errors}\nskipped={skipped}\n")
    f.write("failed_tests="+(",".join(failed) if failed else "none")+"\n")
PY

if [[ "$STATUS" -ne 0 ]]; then
  echo "gradle_exit=$STATUS" >> "$PUBLIC/test-summary.txt"
  exit "$STATUS"
fi

APK="mobile/android-founder/app/build/outputs/apk/debug/app-debug.apk"
test -f "$APK"
APK_SHA="$(sha256sum "$APK" | awk '{print $1}')"
APK_BYTES="$(stat -c %s "$APK")"
{
  echo "gradle_exit=0"
  echo "apk_sha256=$APK_SHA"
  echo "apk_bytes=$APK_BYTES"
} >> "$PUBLIC/test-summary.txt"

python3 - <<'PY'
from pathlib import Path
summary=Path("infrastructure/android-cloud-ci/render-public/test-summary.txt").read_text()
Path("infrastructure/android-cloud-ci/render-public/index.html").write_text(
    "<!doctype html><html><body><h1>AIO Android cloud verification</h1><pre>"+
    summary.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")+
    "</pre></body></html>", encoding="utf-8")
PY
