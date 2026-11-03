#!/usr/bin/env python3
"""PID 1 supervisor for a fresh isolated PostgreSQL cluster; no restore/Flyway."""
import json
import os
from pathlib import Path
import secrets
import signal
import subprocess
import sys
import time
import network_guard

DATA=Path('/var/lib/postgresql/isolation-data')
EVIDENCE=Path('/evidence')
STOP=False

def request_stop(signum, frame):
    global STOP
    STOP=True

def require(ok):
    if not ok:
        raise RuntimeError()

def event(kind):
    # Fixed events only: never interpolate subprocess errors, SQL or environment.
    line=json.dumps({'event':kind,'utc':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())})
    print(line,flush=True)
    with (EVIDENCE/'lifecycle.log').open('a') as f:
        f.write(line+'\n')

def run(args, log, env, text=None):
    with (EVIDENCE/log).open('ab') as f:
        r=subprocess.run(args,input=text.encode() if text is not None else None,
                         stdin=subprocess.DEVNULL if text is None else None,
                         stdout=f,stderr=f,env=env,timeout=30)
    require(r.returncode==0)

def main():
    global STOP
    os.umask(0o077)
    for key in ('TRAINING_PORTAL_DB_PASSWORD','PGPASSWORD','PGHOST','PGPORT','PGDATABASE','PGUSER',
                'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH','PGOPTIONS'):
        os.environ.pop(key,None)
    signal.signal(signal.SIGINT,request_stop)
    signal.signal(signal.SIGTERM,request_stop)
    pg=None;log=None;stage='guard';result=1
    try:
        require(network_guard.main()==0)
        require(not any(DATA.iterdir()))
        require(not (EVIDENCE/'start.request').exists())
        event('guard-passed-awaiting-host-inspect')
        stage='host-gate'
        deadline=time.monotonic()+300
        while not (EVIDENCE/'start.request').exists():
            if STOP:
                event('stopped-before-initialization');return 0
            require(time.monotonic()<deadline)
            time.sleep(0.1)
        require((EVIDENCE/'start.request').is_file() and (EVIDENCE/'start.request').read_text()=='start\n')
        # Repeat guard immediately before authorized initialization.
        require(network_guard.main()==0 and not STOP)
        stage='initialization'
        env=dict(os.environ,PGDATA=str(DATA))
        run(['initdb','--auth-local=trust','--auth-host=scram-sha-256'],'init.log',env)
        event('initialized')
        stage='postgres-start'
        log=(EVIDENCE/'postgres.log').open('ab')
        pg=subprocess.Popen(['postgres','-D',str(DATA),'-c','listen_addresses=127.0.0.1',
              '-c','port=5432','-c','unix_socket_directories=/tmp',
              '-c','log_statement=none','-c','log_min_error_statement=panic'],
              stdin=subprocess.DEVNULL,stdout=log,stderr=log,env=env)
        deadline=time.monotonic()+30
        while True:
            require(pg.poll() is None and not STOP)
            r=subprocess.run(['pg_isready','-h','127.0.0.1','-p','5432'],
                             stdin=subprocess.DEVNULL,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=3)
            if r.returncode==0:break
            require(time.monotonic()<deadline);time.sleep(0.1)
        stage='empty-database'
        password=secrets.token_hex(32)
        # Fresh cluster only. Random hexadecimal cannot inject SQL.
        sql="CREATE ROLE training_portal_user LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD '"+password+"';\nCREATE DATABASE training_portal OWNER training_portal_user;\n"
        run(['psql','-X','-q','-h','/tmp','-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1'],
            'database-init.log',env,sql)
        with (EVIDENCE/'runtime-credentials.json').open('x') as f:
            json.dump({'TRAINING_PORTAL_DB_PASSWORD':password},f)
        require(pg.poll() is None)
        (EVIDENCE/'ready.json').write_text(json.dumps({'postgres_pid':pg.pid,'database':'training_portal'})+'\n')
        event('ready')
        stage='monitor'
        while not STOP:
            if pg.poll() is not None:
                event('postgres-unexpected-exit');return 70
            time.sleep(0.1)
        event('normal-stop-requested');stage='normal-stop'
        if pg.poll() is not None:
            event('postgres-unexpected-exit');return 70
        pg.send_signal(signal.SIGINT)  # PostgreSQL fast, clean shutdown.
        require(pg.wait(timeout=20)==0)
        event('normal-stop-complete');result=0
    except Exception:
        event('failed-'+stage)
    finally:
        if pg is not None and pg.poll() is None:
            try:
                pg.send_signal(signal.SIGINT);pg.wait(timeout=20)
                event('cleanup-stop-complete')
            except Exception:
                event('cleanup-stop-failed')
                pg.kill();pg.wait(timeout=5)
        if log is not None:log.close()
    return result

if __name__=='__main__':
    sys.exit(main())
