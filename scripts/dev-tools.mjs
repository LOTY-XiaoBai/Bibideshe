import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { readFileSync, existsSync } from 'node:fs'
import net from 'node:net'

const action = process.argv[2]
if (!['redis', 'status', 'stop'].includes(action)) {
  console.error('Usage: node scripts/dev-tools.mjs redis|status|stop')
  process.exit(1)
}

if (process.platform === 'win32') {
  const script = action === 'redis' ? 'start-redis.ps1' : 'dev-ports.ps1'
  const args = ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', fileURLToPath(new URL(script, import.meta.url))]
  if (action !== 'redis') args.push('-Mode', action)
  const result = spawnSync('powershell.exe', args, { stdio: 'inherit', windowsHide: true })
  if (result.error) console.error(result.error.message)
  process.exit(result.status ?? 1)
}

// Native Windows lifecycle tools are optional on other platforms. Check the
// configured Redis there without changing an existing service or its password.
if (action !== 'redis') {
  console.error('Port management currently supports Windows. Use your IDE/process manager on this platform.')
  process.exit(1)
}
const settings = {}
const envFile = new URL('../.env', import.meta.url)
if (existsSync(envFile)) {
  for (const line of readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*(REDIS_HOST|REDIS_PORT|REDIS_PASSWORD)\s*=(.*)$/)
    if (match) settings[match[1]] = match[2].trim()
  }
}
const host = process.env.REDIS_HOST || settings.REDIS_HOST || 'localhost'
const port = Number(process.env.REDIS_PORT || settings.REDIS_PORT || 6379)
const password = process.env.REDIS_PASSWORD ?? settings.REDIS_PASSWORD ?? ''
function command(parts) {
  return `*${parts.length}\r\n` + parts.map(p => `$${Buffer.byteLength(p)}\r\n${p}\r\n`).join('')
}
try {
  await new Promise((resolve, reject) => {
    const socket = net.createConnection({ host, port })
    const finish = error => { socket.destroy(); error ? reject(error) : resolve() }
    let response = ''
    socket.setTimeout(2000, () => finish(new Error('Redis connection timed out')))
    socket.on('error', finish)
    socket.on('connect', () => socket.write((password ? command(['AUTH', password]) : '') + command(['PING'])))
    socket.on('data', chunk => {
      response += chunk.toString()
      const lines = response.split('\r\n').slice(0, -1)
      if (lines.some(line => line.startsWith('-'))) finish(new Error('Redis authentication or PING failed; check .env'))
      else if (lines.includes('+PONG')) finish()
    })
    socket.on('end', () => finish(new Error('Redis closed the connection before PONG')))
  })
  console.log(`Redis is ready on ${host}:${port}. PING = PONG.`)
} catch (error) {
  console.error(`${error.message}. Start your Redis service first. Automatic Redis startup is available on Windows.`)
  process.exitCode = 1
}
