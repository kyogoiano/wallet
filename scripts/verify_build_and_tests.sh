#!/usr/bin/env bash
set -euo pipefail

for jdk_candidate in "$HOME/jdks/valhalla" "$HOME/snap/antigravity-cli/common/jdks/valhalla" "/home/leandro/snap/antigravity-cli/common/jdks/valhalla"; do
    if [ -d "$jdk_candidate" ] && [ -x "$jdk_candidate/bin/java" ]; then
        if [ -z "${JAVA_HOME:-}" ] || ! "${JAVA_HOME}/bin/java" -version >/dev/null 2>&1; then
            export JAVA_HOME="$jdk_candidate"
            export PATH="$JAVA_HOME/bin:$PATH"
            break
        fi
    fi
done

EXTRA_GRADLE_ARGS=()
for gh_candidate in "$HOME/gradle_home" "$HOME/snap/antigravity-cli/common/gradle_home" "/home/leandro/snap/antigravity-cli/common/gradle_home"; do
    if [ -d "$gh_candidate" ]; then
        EXTRA_GRADLE_ARGS+=("--gradle-user-home=$gh_candidate")
        export GRADLE_OPTS="${GRADLE_OPTS:-} -Dgradle.user.home=$gh_candidate"
        break
    fi
done

echo "========================================================================="
echo "🚀 Wallet Service — Automated Build, Schema & Test Verification Pipeline"
echo "========================================================================="

# 1. Verify Schema Synchronization between Production and Test resources
echo "▶ Checking schema synchronization..."
if ! diff -u docker/init/schema.sql src/test/resources/schema.sql > /dev/null; then
    echo "❌ ERROR: docker/init/schema.sql and src/test/resources/schema.sql are out of sync!"
    echo "Running diff:"
    diff -u docker/init/schema.sql src/test/resources/schema.sql
    exit 1
else
    echo "✅ Schema files are 100% synchronized."
fi

# 2. Verify Java package consistency and Modulith boundaries
echo "▶ Running package and boundary verification..."
python3 -c '
import os, re

root_dir = "."
bases = [
    "src/main/java", "src/test/java",
    "core/src/main/java", "core/src/test/java",
    "fraud/src/main/java", "fraud/src/test/java",
    "edge/src/main/java", "edge/src/test/java"
]
errors = []
for base in bases:
    if not os.path.exists(base):
        continue
    for root, dirs, files in os.walk(base):
        for f in files:
            if f.endswith(".java"):
                fpath = os.path.join(root, f)
                with open(fpath, "r", encoding="utf-8") as file:
                    content = file.read()
                pkg_match = re.search(r"package\s+([a-zA-Z0-9_.]+);", content)
                if pkg_match:
                    pkg = pkg_match.group(1)
                    rel = os.path.relpath(root, base).replace("/", ".")
                    if pkg != rel:
                        errors.append(f"Mismatch: {fpath} ({pkg} vs {rel})")
if errors:
    print(f"❌ {len(errors)} package mismatches found:")
    for e in errors:
        print("  -", e)
    exit(1)
else:
    print("✅ All package paths match directory structure perfectly.")
'

# 3. Execute Gradle compilation and tests
echo "▶ Executing Gradle compilation and test suites..."
./gradlew ${EXTRA_GRADLE_ARGS[@]+"${EXTRA_GRADLE_ARGS[@]}"} test jacocoTestReport jacocoRootReport --info

echo "========================================================================="
echo "🎉 ALL TESTS PASSED & COVERAGE REPORT GENERATED!"
echo "========================================================================="
