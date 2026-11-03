#!/usr/bin/env python3
"""Opt-in disposable-cluster verification; never reads development credentials or connects to port 5432."""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import pty
import secrets
import select
import shutil
import socket
import subprocess
import termios
import tempfile
import time
import urllib.request
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]

def available_port():
    with socket.socket() as s:
        s.bind(('127.0.0.1', 0))
        port = s.getsockname()[1]
    if port == 5432:
        raise RuntimeError('Reserved port')
    return port

def terminal(command, answers, sensitive):
    pid, fd = pty.fork()
    if pid == 0:
        os.chdir(ROOT)
        os.execvpe(command[0], command, CLEAN)
    output = b''
    finished = False
    try:
        for prompt, value, hidden in answers:
            limit = time.monotonic() + 60
            while prompt.encode() not in output:
                if time.monotonic() > limit:
                    raise RuntimeError('Terminal prompt timeout')
                if select.select([fd], [], [], 0.1)[0]:
                    output += os.read(fd, 8192)
            if hidden:
                while termios.tcgetattr(fd)[3] & termios.ECHO:
                    if time.monotonic() > limit:
                        raise RuntimeError('Password echo guard')
                    time.sleep(0.02)
            os.write(fd, value.encode() + b'\n')
            output = b''  # never retain supplied credentials
        limit = time.monotonic() + 120
        while time.monotonic() < limit:
            if select.select([fd], [], [], 0.1)[0]:
                try:
                    chunk = os.read(fd, 8192)
                    if not chunk:
                        break
                    output += chunk
                except OSError:
                    break
        _, status = os.waitpid(pid, 0)
        finished = True
        if status != 0 or any(v.encode() in output for v in sensitive):
            raise RuntimeError('Terminal command rejected')
    finally:
        os.close(fd)
        if not finished:
            os.kill(pid, 15)
            os.waitpid(pid, 0)

def run(command, *, text=None, env=None, log=None, timeout=300):
    result = subprocess.run(list(map(str, command)), cwd=ROOT, input=text, text=True,
                            env=env or CLEAN, capture_output=True, timeout=timeout)
    if log:
        log.write_text(result.stdout + result.stderr)
    if result.returncode:
        raise RuntimeError('Isolated operation failed')
    return result.stdout

parser = argparse.ArgumentParser()
parser.add_argument('--execute-isolated-tests', action='store_true', required=True)
parser.add_argument('--pg-bin', type=Path, required=True)
parser.add_argument('--frontend', action='store_true', help='Build/start a temporary frontend copy; network dependencies must be available')
parser.add_argument('--smoke-only', action='store_true', help='Skip JUnit suites; run only packaged bootstrap/startup/browser smoke (not a full regression run)')
parser.add_argument('--chrome-bin', type=Path, help='Optional Chrome executable for real headless browser checks; requires --frontend')
args = parser.parse_args()
if args.chrome_bin and not args.frontend:
    parser.error('--chrome-bin requires --frontend')
PG = args.pg_bin.resolve()
for binary in ('initdb', 'pg_ctl', 'psql'):
    if not (PG / binary).is_file():
        parser.error('PostgreSQL utilities unavailable')
CLEAN = {k: v for k, v in os.environ.items() if not k.startswith(('PG', 'TRAINING_PORTAL_', 'FRESH_SETUP_TEST_', 'INTEGRATION_TEST_'))
         and k not in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH')}
os.umask(0o077)
work = Path(tempfile.mkdtemp(prefix='training-fresh-setup-'))
(work / 'socket').mkdir()
port, backend_port, front_port = available_port(), available_port(), available_port()
password, initial_password = secrets.token_hex(24), secrets.token_hex(20)
started = False
backend = frontend = None
stage = 'cluster'
results = {'databasePort': port, 'backendPort': backend_port, 'frontendPort': front_port, 'developmentDatabaseAccessed': False}
try:
    run([PG / 'initdb', '-D', work / 'data', '-U', 'postgres', '--auth-local=trust', '--auth-host=scram-sha-256', '--encoding=UTF8', '--locale=C'])
    with (work / 'data/postgresql.conf').open('a') as f:
        f.write("\nlisten_addresses='127.0.0.1'\nport=" + str(port) + "\nunix_socket_directories='" + str(work / 'socket') + "'\nlog_statement='none'\nlog_min_error_statement='panic'\n")
    run([PG / 'pg_ctl', '-D', work / 'data', '-l', work / 'postgres.log', '-w', 'start'])
    started = True
    admin = [PG / 'psql', '-X', '-w', '-h', work / 'socket', '-p', str(port), '-U', 'postgres', '-d']
    run(admin + ['postgres', '-v', 'ON_ERROR_STOP=1'], text="CREATE ROLE training_portal_user LOGIN PASSWORD '" + password + "';\n" + '\n'.join('CREATE DATABASE ' + db + ' OWNER training_portal_user;' for db in ['fresh_public', 'fresh_test1', 'fresh_existing', 'fresh_history', 'fresh_busy', 'training_portal']))
    for db in ['fresh_public', 'fresh_test1', 'fresh_existing', 'fresh_history', 'fresh_busy', 'training_portal']:
        run(admin + [db, '-v', 'ON_ERROR_STOP=1'], text='CREATE SCHEMA test1 AUTHORIZATION training_portal_user;')
    run(admin + ['fresh_existing', '-v', 'ON_ERROR_STOP=1'], text='SET ROLE training_portal_user; CREATE TABLE public.protected_fixture(value integer); INSERT INTO public.protected_fixture VALUES (7);')
    run(admin + ['fresh_history', '-v', 'ON_ERROR_STOP=1'], text='SET ROLE training_portal_user; CREATE TABLE public.flyway_schema_history(value integer);')
    if not args.smoke_only:
        stage = 'focused-tests'
        test_env = dict(CLEAN, FRESH_SETUP_TEST_PORT=str(port), FRESH_SETUP_TEST_PASSWORD=password)
        run(['./mvnw', '-Dtest=FreshDatabaseCommandTest,FreshDatabaseCommandIntegrationTest,InitialInstructorCommandTest', 'test'], env=test_env, log=work / 'focused.log')
        results['focusedTests'] = [{k: ET.parse(f).getroot().get(k) for k in ('name', 'tests', 'failures', 'errors', 'skipped')} for f in (ROOT / 'target/surefire-reports').glob('TEST-*FreshDatabase*.xml')]
        print('PASS focused bootstrap/guards; only disposable cluster used', flush=True)
        stage = 'regression-tests'
        regression_env = dict(CLEAN, TRAINING_PORTAL_DB_PASSWORD=password)
        run(['./mvnw', 'clean', 'test', '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:' + str(port) + '/fresh_test1?currentSchema=test1'], env=regression_env, log=work / 'regression.log')
        results['regressionTests'] = [{k: ET.parse(f).getroot().get(k) for k in ('name', 'tests', 'failures', 'errors', 'skipped')} for f in (ROOT / 'target/surefire-reports').glob('TEST-*.xml')]
        print('PASS isolated backend regression', flush=True)
    else:
        results['junitSuitesSkippedForSmokeOnly'] = True
    stage = 'package'
    run(['./mvnw', '-DskipTests', 'package'], log=work / 'package.log')
    launch = ['java', '-Dloader.main=com.training.portal.setup.FreshDatabaseCommand', '-cp', str(ROOT / 'target/training-portal-0.0.1-SNAPSHOT.jar'), 'org.springframework.boot.loader.launch.PropertiesLauncher', '--initialize-empty-database', '--host=127.0.0.1', '--port=' + str(port), '--database=training_portal', '--schema=public', '--user=training_portal_user', '--migrations=' + str(ROOT / 'src/main/resources/db/migration')]
    stage = 'terminal-bootstrap'
    identity = 'training_portal/public@127.0.0.1:' + str(port)
    terminal(launch, [('接続先をそのまま入力して確認: ', identity, False), ('DBパスワード（非表示）: ', password, True)], [password])
    results['packagedBootstrapConsole'] = True
    stage = 'initial-instructor'
    initial = ['java', '-Dloader.main=com.training.portal.setup.InitialInstructorCommand', '-cp', str(ROOT / 'target/training-portal-0.0.1-SNAPSHOT.jar'), 'org.springframework.boot.loader.launch.PropertiesLauncher', '--initialize-first-instructor', '--host=127.0.0.1', '--port=' + str(port), '--database=training_portal', '--schema=public']
    terminal(initial, [('接続先をそのまま入力して確認: ', identity, False), ('DBパスワード（非表示）: ', password, True), ('社員ID: ', 'SETUPCLI', False), ('社員名: ', 'Setup Instructor', False), ('ふりがな（任意）: ', '', False), ('初期講師パスワード（非表示）: ', initial_password, True), ('初期講師パスワード再入力（非表示）: ', initial_password, True)], [password, initial_password])
    audit = run(admin + ['training_portal', '-At', '-v', 'ON_ERROR_STOP=1'], text="SELECT count(*)=1 AND bool_and(syain_kbn=2 AND auth_version=0 AND password LIKE '$2%') FROM public.kenshu_member;")
    if audit.strip() != 't':
        raise RuntimeError('Initial instructor audit failed')
    results['initialInstructorConsoleAndBcrypt'] = True
    print('PASS packaged console bootstrap and initial instructor', flush=True)
    stage = 'backend'
    with (work / 'backend.log').open('w') as log:
        backend = subprocess.Popen(['java', '-jar', str(ROOT / 'target/training-portal-0.0.1-SNAPSHOT.jar'), '--spring.datasource.url=jdbc:postgresql://127.0.0.1:' + str(port) + '/training_portal', '--server.address=127.0.0.1', '--server.port=' + str(backend_port)], cwd=ROOT, env=dict(CLEAN, TRAINING_PORTAL_DB_PASSWORD=password, TRAINING_PORTAL_FRONTEND_ORIGIN='http://localhost:' + str(front_port)), stdout=log, stderr=subprocess.STDOUT)
    jar = http.cookiejar.CookieJar()
    client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    base = 'http://localhost:' + str(backend_port)
    for i in range(120):
        try:
            csrf = json.load(client.open(base + '/api/auth/csrf', timeout=2))
            break
        except Exception:
            if backend.poll() is not None:
                raise RuntimeError('Backend startup failed')
            time.sleep(0.5)
    else:
        raise RuntimeError('Backend readiness timeout')
    payload = json.dumps({'syainId': 'SETUPCLI', 'password': initial_password}).encode()
    response = json.load(client.open(urllib.request.Request(base + '/api/auth/login', data=payload, headers={'Content-Type': 'application/json', csrf['headerName']: csrf['token']})))
    if response['syainKbn'] != 2:
        raise RuntimeError('Login role failed')
    for endpoint in ('home', 'progress', 'schedules', 'members'):
        with client.open(base + '/api/' + endpoint) as response:
            if response.status != 200:
                raise RuntimeError('Basic API failed')
    results['backendLoginAndBasicApis'] = True
    print('PASS backend normal target=2 startup, real login, Home/Progress/Schedules/Members APIs', flush=True)
    if args.frontend:
        stage = 'frontend-build'
        temp_front = work / 'frontend'
        shutil.copytree(ROOT / 'frontend', temp_front, ignore=shutil.ignore_patterns('node_modules', '.next', '.env*', '*.tsbuildinfo', 'coverage'))
        (temp_front / 'node_modules').symlink_to(ROOT / 'frontend/node_modules', target_is_directory=True)
        env = dict(CLEAN, NEXT_PUBLIC_API_BASE_URL=base)
        result = subprocess.run(['pnpm', 'exec', 'next', 'build', '--webpack'], cwd=temp_front, env=env, stdout=(work / 'frontend-build.log').open('w'), stderr=subprocess.STDOUT, timeout=300)
        if result.returncode:
            raise RuntimeError('Frontend build failed')
        frontend = subprocess.Popen(['node', str(temp_front / 'node_modules/next/dist/bin/next'), 'start', '--hostname', '127.0.0.1', '--port', str(front_port)], cwd=temp_front, env=env, stdout=(work / 'frontend.log').open('w'), stderr=subprocess.STDOUT)
        for i in range(60):
            try:
                html = urllib.request.urlopen('http://localhost:' + str(front_port), timeout=2).read().decode()
                if 'ログイン' not in html:
                    raise RuntimeError('Login page unavailable')
                results['frontendLoginHtml'] = True
                break
            except Exception:
                time.sleep(0.5)
        else:
            raise RuntimeError('Frontend startup failed')
        print('PASS temporary Next webpack build/start and login HTML (not browser E2E)', flush=True)
        if args.chrome_bin:
            stage = 'browser'
            output = run(['node', ROOT / 'tools/flyway/fresh-browser-check.cjs'], text=json.dumps({'chrome': str(args.chrome_bin), 'base': 'http://localhost:' + str(front_port), 'evidence': str(work), 'employee': 'SETUPCLI', 'password': initial_password}), timeout=120, log=work / 'browser.log')
            results['browser'] = json.loads(output)
            print('PASS real headless browser login and four basic pages', flush=True)
    results['success'] = True
except Exception:
    results['success'] = False
    results['failedStage'] = stage
    print('STOP isolated verification stage=' + stage + '; logs=' + str(work), flush=True)
finally:
    for proc in (frontend, backend):
        if proc:
            proc.terminate()
            try:
                proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                proc.kill()
                proc.wait()
    if started:
        try:
            run([PG / 'pg_ctl', '-D', work / 'data', '-m', 'fast', '-w', 'stop'])
            results['clusterStopped'] = True
        except Exception:
            results['clusterStopped'] = False
    (work / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2))
    print('Evidence=' + str(work), flush=True)
raise SystemExit(0 if results.get('success') else 1)
