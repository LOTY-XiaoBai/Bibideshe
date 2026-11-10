import test from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import { mkdtemp, mkdir, readdir, copyFile, writeFile, rm } from 'node:fs/promises'
import { openSync, closeSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { resolve, join, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
import { randomUUID } from 'node:crypto'
import net from 'node:net'

const installation = process.env.REDIS_TEST_DIRECTORY
test('Redis cold start, reuse, auth errors and failed-start cleanup use an isolated instance', {
  skip: process.platform !== 'win32' || !installation,
  timeout: 60000,
}, async t => {
  const root = await mkdtemp(join(tmpdir(), 'bishe-redis-test-'))
  const directory = join(root, 'redis')
  const password = randomUUID()
  let started = false
  const reservation = net.createServer()
  await new Promise(resolveReady => reservation.listen(0, '127.0.0.1', resolveReady))
  const port = reservation.address().port
  await new Promise(resolveClosed => reservation.close(resolveClosed))
  function redis(parts) {
    return new Promise((resolveReply, reject) => {
      const client = net.createConnection({ host: '127.0.0.1', port })
      let response = ''
      const finish = error => { client.destroy(); error ? reject(error) : resolveReply(response) }
      client.on('error', finish)
      client.setTimeout(2000, () => finish(new Error('Timed out')))
      client.on('connect', () => {
        const wire = [['AUTH', password], parts].map(args => `*${args.length}\r\n` + args.map(p => `$${Buffer.byteLength(p)}\r\n${p}\r\n`).join('')).join('')
        client.write(wire)
      })
      client.on('data', chunk => {
        response += chunk
        if (response.includes('+PONG\r\n')) finish()
      })
      client.on('end', () => finish())
    })
  }
  t.after(async () => {
    if (started) await redis(['SHUTDOWN', 'NOSAVE']).catch(() => {})
    const target = resolve(root)
    assert.ok(target.startsWith(resolve(tmpdir()) + sep) && target !== resolve(tmpdir()))
    await rm(target, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 })
  })
  await mkdir(directory, { recursive: true })
  await mkdir(join(root, 'scripts'))
  for (const name of await readdir(installation)) {
    if (name === 'redis-server.exe' || name.endsWith('.dll')) await copyFile(join(installation, name), join(directory, name))
  }
  await writeFile(join(directory, 'redis.conf'), `bind 127.0.0.1\nport ${port}\nsave ""\nappendonly no\nrequirepass ${password}\n`)
  const script = join(root, 'scripts', 'start-redis.ps1')
  await copyFile(fileURLToPath(new URL('start-redis.ps1', import.meta.url)), script)
  function run(secret = password) {
    // Windows background processes may inherit pipe handles. Capture to files
    // so the test waits for PowerShell, rather than waiting for Redis to exit.
    const output = join(root, 'result.out')
    const errors = join(root, 'result.err')
    const out = openSync(output, 'w')
    const err = openSync(errors, 'w')
    try {
      const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script], {
        stdio: ['ignore', out, err], windowsHide: true, timeout: 15000,
        env: { ...process.env, REDIS_HOST: '127.0.0.1', REDIS_PORT: String(port), REDIS_PASSWORD: secret, REDIS_DIRECTORY: directory },
      })
      return { ...result, stdout: readFileSync(output, 'utf8'), stderr: readFileSync(errors, 'utf8') }
    } finally { closeSync(out); closeSync(err) }
  }
  // First exercise failure after a new process was actually launched.
  const failed = run('deliberately-wrong-test-password')
  assert.equal(failed.status, 1, failed.stdout + failed.stderr)
  await assert.rejects(redis(['PING']))
  started = true // ensure cleanup even if an assertion below fails
  const cold = run()
  assert.equal(cold.status, 0, cold.stdout + cold.stderr)
  assert.match(cold.stdout, /Redis started in background/)
  assert.match(await redis(['PING']), /\+PONG/)
  const reused = run()
  assert.equal(reused.status, 0, reused.stdout + reused.stderr)
  assert.match(reused.stdout, /already running/)
  const mismatch = run('deliberately-wrong-test-password')
  assert.equal(mismatch.status, 1)
  assert.match(await redis(['PING']), /\+PONG/) // existing Redis must survive
})
