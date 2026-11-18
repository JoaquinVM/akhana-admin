#!/usr/bin/env python3
"""Run this checkout of Akhana Admin locally, with a test database of its own, for Joaquín to see a change.

Usage (from any folder of the checkout or one of its worktrees):
    python3 -I .claude/skills/ver-en-local/ver_en_local.py            # start (or reuse) and print the access
    python3 -I .claude/skills/ver-en-local/ver_en_local.py --codigo   # the second-factor code for the browser
    python3 -I .claude/skills/ver-en-local/ver_en_local.py --token    # a session token, to load test data by the API
    python3 -I .claude/skills/ver-en-local/ver_en_local.py --parar    # stop backend and frontend (keeps the base)
    python3 -I .claude/skills/ver-en-local/ver_en_local.py --borrar   # stop and drop the test base

Each branch gets its own database `akhana_local_<branch>` in the `akhana-postgres` container, its own free ports and
its own test administrator, so it never touches Joaquín's data, the servers on 8080/4200 or another session's trial.
State and logs live in ~/.cache/akhana-ver-en-local/<branch>/, outside the repo. Only the standard library is used.
"""
import base64, hashlib, hmac, json, os, re, secrets, shutil, signal, socket, struct, subprocess, sys, time
import urllib.error, urllib.request
from pathlib import Path

CONTAINER = 'akhana-postgres'
USER = 'prueba'


def sh(*cmd, cwd=None, check=True):
    r = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if check and r.returncode != 0:
        sys.exit(f'Falló: {" ".join(cmd)}\n{r.stderr.strip() or r.stdout.strip()}')
    return r.stdout.strip()


ROOT = Path(sh('git', 'rev-parse', '--show-toplevel'))
MAIN = Path(sh('git', 'rev-parse', '--path-format=absolute', '--git-common-dir')).parent
BRANCH = sh('git', 'rev-parse', '--abbrev-ref', 'HEAD', cwd=ROOT)
if BRANCH == 'HEAD':
    BRANCH = 'sha-' + sh('git', 'rev-parse', '--short', 'HEAD', cwd=ROOT)
SLUG = re.sub(r'[^a-z0-9]+', '_', BRANCH.lower()).strip('_')[:40]
DB = f'akhana_local_{SLUG}'
STATE_DIR = Path.home() / '.cache' / 'akhana-ver-en-local' / SLUG
STATE = STATE_DIR / 'estado.json'


def env_file(path):
    out = {}
    if path.exists():
        for line in path.read_text().splitlines():
            m = re.match(r'\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$', line)
            if m:
                out[m.group(1)] = m.group(2).strip().strip('"\'')
    return out


def load():
    return json.loads(STATE.read_text()) if STATE.exists() else {}


def save(st):
    STATE_DIR.mkdir(parents=True, exist_ok=True)
    STATE.write_text(json.dumps(st, indent=2))
    STATE.chmod(0o600)


def alive(pid):
    try:
        os.kill(pid, 0)
        return True
    except (OSError, TypeError):
        return False


def free(port):
    with socket.socket() as s:
        try:
            s.bind(('127.0.0.1', port))
            return True
        except OSError:
            return False


def pick(start, taken=()):
    for p in range(start, start + 100):
        if p not in taken and free(p):
            return p
    sys.exit(f'No hay puertos libres desde {start}.')


def http(method, url, body=None, token=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={'Content-Type': 'application/json'})
    if token:
        req.add_header('Authorization', f'Bearer {token}')
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            raw = r.read()
            try:
                return r.status, (json.loads(raw) if raw else None)
            except ValueError:
                return r.status, raw.decode(errors='replace')
    except urllib.error.HTTPError as e:
        raw = e.read()
        try:
            return e.code, json.loads(raw)
        except ValueError:
            return e.code, raw.decode(errors='replace')
    except (urllib.error.URLError, OSError):
        return 0, None


def wait(what, check, seconds, log):
    end = time.time() + seconds
    while time.time() < end:
        if check():
            return
        time.sleep(2)
    tail = '\n'.join(Path(log).read_text(errors='replace').splitlines()[-25:]) if Path(log).exists() else ''
    sys.exit(f'{what} no respondió en {seconds} s. Últimas líneas de {log}:\n{tail}')


def totp(secret, step):
    key = base64.b32decode(secret + '=' * (-len(secret) % 8))
    h = hmac.new(key, struct.pack('>Q', step), hashlib.sha1).digest()
    o = h[-1] & 0x0F
    return '%06d' % ((struct.unpack('>I', h[o:o + 4])[0] & 0x7FFFFFFF) % 1_000_000)


def fresh_code(st):
    """A code of a step the backend has not seen yet (it refuses a code twice), waiting for the next step if needed."""
    while True:
        step = int(time.time()) // 30
        if step > st.get('ultimo_paso', 0):
            st['ultimo_paso'] = step
            save(st)
            return totp(st['secreto'], step), 30 - int(time.time()) % 30
        time.sleep(1)


def psql(sql, db='postgres'):
    return sh('docker', 'exec', CONTAINER, 'psql', '-U', DBUSER, '-d', db, '-tAc', sql)


def stop(st):
    for key in ('pid_backend', 'pid_frontend'):
        pid = st.get(key)
        if pid and alive(pid):
            try:
                os.killpg(pid, signal.SIGTERM)
            except OSError:
                pass
        st.pop(key, None)
    save(st)


def node_path():
    nvm = sorted((Path.home() / '.nvm' / 'versions' / 'node').glob('v22.*'))
    return f'{nvm[-1] / "bin"}:{os.environ["PATH"]}' if nvm else os.environ['PATH']


def start_backend(st, env):
    log = STATE_DIR / 'backend.log'
    env = {**os.environ, **env, 'DB_URL': f'jdbc:postgresql://localhost:5432/{DB}', 'SERVER_PORT': str(st['puerto_backend']),
           'JWT_SECRET': st['jwt'], 'ADMIN_USERNAME': USER, 'ADMIN_PASSWORD': st['clave_inicial']}
    p = subprocess.Popen(['./gradlew', 'bootRun', '--console=plain'], cwd=ROOT / 'backend', env=env,
                         stdout=open(log, 'w'), stderr=subprocess.STDOUT, start_new_session=True)
    st['pid_backend'] = p.pid
    save(st)
    url = f'http://localhost:{st["puerto_backend"]}/actuator/health'
    wait('El backend', lambda: http('GET', url)[0] == 200 or p.poll() is not None, 300, log)
    if p.poll() is not None:
        sys.exit(f'El backend se cerró. Revisa {log}')


def start_frontend(st):
    log = STATE_DIR / 'frontend.log'
    fe = ROOT / 'frontend'
    env = {**os.environ, 'PATH': node_path()}
    if not (fe / 'node_modules').exists():
        print('Instalando dependencias del frontend (npm ci, una sola vez por carpeta)…', flush=True)
        r = subprocess.run(['npm', 'ci', '--no-audit', '--no-fund'], cwd=fe, env=env, capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit(f'npm ci falló:\n{r.stderr[-2000:]}')
    proxy = STATE_DIR / 'proxy.json'
    proxy.write_text(json.dumps({'/api': {'target': f'http://localhost:{st["puerto_backend"]}', 'secure': False,
                                          'changeOrigin': True}}))
    p = subprocess.Popen(['npx', 'ng', 'serve', '--port', str(st['puerto_frontend']), '--proxy-config', str(proxy)],
                         cwd=fe, env=env, stdout=open(log, 'w'), stderr=subprocess.STDOUT, start_new_session=True)
    st['pid_frontend'] = p.pid
    save(st)
    url = f'http://localhost:{st["puerto_frontend"]}/'
    wait('El frontend', lambda: http('GET', url)[0] == 200 or p.poll() is not None, 240, log)
    if p.poll() is not None:
        sys.exit(f'El frontend se cerró. Revisa {log}')


def ensure_user(st):
    """The first start creates the test administrator: activate its second factor and set a password of its own."""
    if st.get('secreto'):
        return
    api = f'http://localhost:{st["puerto_backend"]}/api/auth'
    code, r = http('POST', f'{api}/login', {'username': USER, 'password': st['clave_inicial']})
    tf = (r or {}).get('twoFactor') if isinstance(r, dict) else None
    if code != 200 or not tf or not tf.get('setupRequired'):
        sys.exit(f'No pude preparar el usuario de prueba (login {code}): {r}')
    st['secreto'] = tf['secret']
    save(st)
    c, _ = fresh_code(st)
    code, r = http('POST', f'{api}/login/second-factor', {'challengeId': tf['challengeId'], 'code': c})
    if code != 200 or not r.get('token'):
        sys.exit(f'No pude activar el segundo factor ({code}): {r}')
    clave = secrets.token_urlsafe(15)
    if r.get('mustChangePassword'):
        code, r2 = http('POST', f'{api}/change-password', {'currentPassword': st['clave_inicial'], 'newPassword': clave},
                        token=r['token'])
        if code != 200:
            sys.exit(f'No pude cambiar la contraseña de prueba ({code}): {r2}')
        st['clave'] = clave
    else:
        st['clave'] = st['clave_inicial']
    save(st)


def main():
    global DBUSER
    env = env_file(MAIN / '.env')
    DBUSER = env.get('DB_USERNAME', 'akhana')
    st = load()
    arg = sys.argv[1] if len(sys.argv) > 1 else ''

    if arg == '--codigo':
        if not st.get('secreto'):
            sys.exit('Todavía no hay usuario de prueba: corre el script sin opciones primero.')
        c, left = fresh_code(st)
        print(f'{c}  (vale {left} s más; cada código sirve una sola vez)')
        return
    if arg == '--token':
        if not st.get('clave'):
            sys.exit('Todavía no hay usuario de prueba: corre el script sin opciones primero.')
        api = f'http://localhost:{st["puerto_backend"]}/api/auth'
        code, r = http('POST', f'{api}/login', {'username': USER, 'password': st['clave']})
        tf = r.get('twoFactor') if isinstance(r, dict) else None
        if code != 200 or not tf:
            sys.exit(f'No pude entrar ({code}): {r}')
        code, r = http('POST', f'{api}/login/second-factor', {'challengeId': tf['challengeId'], 'code': fresh_code(st)[0]})
        if code != 200 or not r.get('token'):
            sys.exit(f'No pude entrar con el código ({code}): {r}')
        print(r['token'])
        return
    if arg in ('--parar', '--borrar'):
        stop(st)
        if arg == '--borrar':
            psql(f'DROP DATABASE IF EXISTS {DB}')
            shutil.rmtree(STATE_DIR, ignore_errors=True)
            print(f'Paré todo y borré la base {DB}.')
        else:
            print(f'Paré backend y frontend. La base {DB} queda para la próxima vez.')
        return

    if shutil.which('docker') is None:
        sys.exit('No encuentro docker. Abre Docker Desktop y vuelve a intentar.')
    if sh('docker', 'inspect', '-f', '{{.State.Running}}', CONTAINER, check=False) != 'true':
        sh('docker', 'start', CONTAINER)
        time.sleep(3)
    if not psql(f"SELECT 1 FROM pg_database WHERE datname='{DB}'"):
        psql(f'CREATE DATABASE {DB} OWNER {DBUSER}')
        stop(st)
        st = {}  # a new base means a new administrator

    st.setdefault('jwt', secrets.token_hex(32))
    st.setdefault('clave_inicial', secrets.token_urlsafe(15))
    be_ok = st.get('pid_backend') and alive(st['pid_backend']) and \
        http('GET', f'http://localhost:{st.get("puerto_backend")}/actuator/health')[0] == 200
    fe_ok = st.get('pid_frontend') and alive(st['pid_frontend']) and \
        http('GET', f'http://localhost:{st.get("puerto_frontend")}/')[0] == 200
    if not be_ok:
        stop(st)
        st['puerto_backend'] = pick(8090)
        save(st)
        print(f'Levantando el backend de {BRANCH} en {st["puerto_backend"]} (la primera vez compila, 1-3 min)…', flush=True)
        start_backend(st, {k: v for k, v in env.items() if k in ('DB_USERNAME', 'DB_PASSWORD')})
        fe_ok = False  # its proxy points to the old port
    if not fe_ok:
        if st.get('pid_frontend') and alive(st['pid_frontend']):
            os.killpg(st['pid_frontend'], signal.SIGTERM)
        st['puerto_frontend'] = pick(4290, {st['puerto_backend']})
        save(st)
        print(f'Levantando el frontend en {st["puerto_frontend"]}…', flush=True)
        start_frontend(st)
    ensure_user(st)

    me = Path(__file__).resolve().relative_to(ROOT)
    print(json.dumps({
        'rama': BRANCH, 'carpeta': str(ROOT),
        'url': f'http://localhost:{st["puerto_frontend"]}/login',
        'api': f'http://localhost:{st["puerto_backend"]}/api',
        'usuario': USER, 'clave': st['clave'],
        'codigo': f'python3 -I {me} --codigo',
        'base': DB, 'psql': f'docker exec -i {CONTAINER} psql -U {DBUSER} -d {DB}',
        'logs': str(STATE_DIR),
    }, indent=2, ensure_ascii=False))


DBUSER = 'akhana'
if __name__ == '__main__':
    main()
