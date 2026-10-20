"""Exercise actual Gradle generation with fixture-only literals; restore original config.

Run after sourcing the toolchain env. Never prints supplied configuration.
This is a host build-injection check, not Android runtime or database evidence.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
evidence = root / "docs/direct-rewrite/evidence/p2"
fixture = {
    "HOST": "fixture.invalid", "PORT": "3306", "DATABASE": "fixture",
    "USER": "fixture_read", "PASSWORD": 'fixture"slash\\line\nnext\t$end',
    "USE_SSL": "true", "CA_PEM": (root / "app/src/test/resources/fixture-ca.pem").read_text(),
}
original_env = os.environ.copy()
fixture_env = original_env | {"DIRECT_MYSQL_" + k: v for k, v in fixture.items()}
command = ["bash", "gradlew", "--no-daemon", "--max-workers=2", ":app:generateDebugBuildConfig"]
results = []
with (evidence / "buildconfig-injection.log").open("w") as log:
    def run(args, env):
        result = subprocess.run(args, cwd=root, env=env, stdout=log, stderr=subprocess.STDOUT)
        results.append({"command": args, "exit": result.returncode})
        if result.returncode:
            raise RuntimeError("BuildConfig fixture check failed; see log")

    try:
        run(command, fixture_env)
        source = root / "app/build/generated/source/buildConfig/debug/ru/ffl24/direct/BuildConfig.java"
        checks = []
        for key, value in fixture.items():
            encoded = base64.b64encode(value.encode()).decode()
            checks.append('if (!new String(java.util.Base64.getDecoder().decode("' + encoded +
                          '"), java.nio.charset.StandardCharsets.UTF_8).equals(ru.ffl24.direct.BuildConfig.DIRECT_MYSQL_' + key +
                          ')) throw new AssertionError("Literal mismatch");')
        with tempfile.TemporaryDirectory(prefix="ffl-p2-literal-") as directory:
            java = Path(directory) / "LiteralCheck.java"
            java.write_text("public class LiteralCheck { public static void main(String[] args) {" + "\n".join(checks) + "}}")
            run(["javac", "--release", "11", "-d", directory, str(source), str(java)], fixture_env)
            run(["java", "-cp", directory, "LiteralCheck"], fixture_env)
    finally:
        # Restore only generated output; local.properties and user environment are never written.
        run(command, original_env)
        (evidence / "buildconfig-injection-commands.json").write_text(json.dumps(results, indent=2) + "\n")
print("BuildConfig literal injection: PASS (7 fixture properties, original config restored)")
