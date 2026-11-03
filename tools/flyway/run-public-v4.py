#!/usr/bin/env python3
"""Offline, pinned maintenance launcher. Default mode is read-only preflight."""
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys

# Exact local artifacts used in verification; no Maven invocation or downloads.
# Jackson 3.1.5 is the locally verified dependency managed by this project's Boot BOM.
DEPENDENCIES = (
    ('org/flywaydb/flyway-core/12.4.0/flyway-core-12.4.0.jar', 'c3f93df1f3e0c9d14652adb5ade97401f2ede503859ac693c2af5c06e2797799'),
    ('org/flywaydb/flyway-database-postgresql/12.4.0/flyway-database-postgresql-12.4.0.jar', 'abc075a5c3cbf37dc64b28fec7e2398fb14f31109f7ab6271854738797407b85'),
    ('org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar', '6e0e4cc2d8cae902084f8a2b18728b073a6fd9d1f87c9d8bff8f298c18185b93'),
    ('tools/jackson/core/jackson-databind/3.1.5/jackson-databind-3.1.5.jar', '3a2338d996fd3056791df8d335fa9ba8a62a706ed4245ecf81b3e583df37d08a'),
    ('tools/jackson/core/jackson-core/3.1.5/jackson-core-3.1.5.jar', '9431b7fa2673bbb618c11d865fe15e13222fd182a214ff998cb7e56afd8f35d2'),
    ('com/fasterxml/jackson/core/jackson-annotations/2.21/jackson-annotations-2.21.jar', '53ca085f4a150f703f49e1aabd935bd03b43e1ea3d55d135438292af22cef56b'),
    ('org/checkerframework/checker-qual/3.55.1/checker-qual-3.55.1.jar', '857658c8bdcbff794949a8d9922f88d63ee3751c203c7cd7d21ea97f9e745288'),
)


def dependency_paths(repo):
    paths = []
    for relative, expected_hash in DEPENDENCIES:
        path = repo / relative
        if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest() != expected_hash:
            raise ValueError("Pinned dependency missing or changed")
        paths.append(path)
    return paths


def main():
    stage = "arguments"
    try:
        mode = sys.argv[1] if len(sys.argv) == 2 else 'dry-run' if len(sys.argv) == 1 else None
        if mode not in {'dry-run', 'skip-v3', 'apply-v4'}:
            raise ValueError("Unsupported arguments")
        stage = "dependencies"
        jars = dependency_paths(Path.home() / '.m2/repository')
        root = Path(__file__).resolve().parents[2]
        env = dict(os.environ)
        for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
            env.pop(key, None)
        stage = "java-launch"
        result = subprocess.run(['java', '--class-path', os.pathsep.join(map(str, jars)),
            str(root / 'tools/flyway/PublicV4Runner.java'), mode,
            str(root / 'src/main/resources/db/migration')],
            env=env, capture_output=True, text=True)
        for line in result.stdout.splitlines():
            if line.startswith(('history=', 'phase target=', 'pending=', 'dry-run PASS:', 'phase success:', 'Flyway version=')):
                print(line)
        for line in result.stderr.splitlines():
            if re.fullmatch(r'STOP stage=[a-z-]+ kind=[a-z-]+', line):
                print(line, file=sys.stderr)
        if result.returncode:
            print('STOP stage=java-launch kind=child-failed', file=sys.stderr)
        return result.returncode
    except Exception:
        # Do not interpolate exception messages, file paths, environment values or child logs.
        print(f'STOP stage={stage} kind=launcher-error', file=sys.stderr)
        return 1


if __name__ == '__main__':
    raise SystemExit(main())
