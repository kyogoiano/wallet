#!/usr/bin/env bash
set -euo pipefail

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
errors = []
for base in ["src/main/java", "core/src/main/java", "fraud/src/main/java", "src/test/java"]:
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
./gradlew test jacocoTestReport --info

echo "========================================================================="
echo "🎉 ALL TESTS PASSED & COVERAGE REPORT GENERATED!"
echo "========================================================================="
