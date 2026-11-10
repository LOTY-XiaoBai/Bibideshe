import test from 'node:test'
import assert from 'node:assert/strict'
import { spawn, spawnSync } from 'node:child_process'
import { once } from 'node:events'
import { mkdtemp, mkdir, copyFile, writeFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

test('Windows port tool stops this checkout, preserves a sibling, handles Java @argfiles, and is repeatable', {
  skip: process.platform !== 'win32', timeout: 60000,
}, async t => {
  const temporaryRoot = await mkdtemp(join(tmpdir(), 'bishe-dev-tools-'))
  const project = join(temporaryRoot, 'project space')
  const sibling = join(temporaryRoot, 'project space-copy')
  const children = []
  t.after(async () => {
    for (const child of children) {
      if (child.exitCode === null) child.kill()
    }
    await Promise.all(children.map(child => child.exitCode !== null ? Promise.resolve() : once(child, 'exit')))
    const target = resolve(temporaryRoot)
    assert.ok(target.startsWith(resolve(tmpdir()) + sep) && target !== resolve(tmpdir()))
    await rm(target, { recursive: true, force: true })
  })
  const scriptDirectory = join(project, 'scripts')
  await mkdir(scriptDirectory, { recursive: true })
  const script = join(scriptDirectory, 'dev-ports.ps1')
  await copyFile(fileURLToPath(new URL('dev-ports.ps1', import.meta.url)), script)
  async function launch(executable, args) {
    const child = spawn(executable, args, { windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] })
    children.push(child)
    let output = ''
    await new Promise((resolveReady, reject) => {
      child.once('error', reject)
      child.once('exit', code => reject(new Error(`Fixture exited before listening: ${code}; ${output}`)))
      child.stderr.on('data', data => { output += data })
      child.stdout.on('data', data => {
        output += data
        if (/READY:\d+/.test(output)) resolveReady()
      })
    })
    return child
  }
  async function frontend(root) {
    const directory = join(root, 'bishe-qianduan', 'node_modules', 'vite', 'bin')
    await mkdir(directory, { recursive: true })
    const path = join(directory, 'vite.js')
    await writeFile(path, "require('node:net').createServer().listen(0, '127.0.0.1', function () { console.log('READY:' + this.address().port) })")
    return launch(process.execPath, [path])
  }
  const own = await frontend(project)
  const foreign = await frontend(sibling)
  const javaDirectory = join(project, 'bishe', 'target', 'classes')
  await mkdir(javaDirectory, { recursive: true })
  const source = join(javaDirectory, 'BisheApplication.java')
  await writeFile(source, 'class BisheApplication { public static void main(String[] args) throws Exception { try (var server = new java.net.ServerSocket(0)) { System.out.println("READY:" + server.getLocalPort()); Thread.sleep(60000); } } }')
  const argfile = join(temporaryRoot, 'java arguments.txt')
  await writeFile(argfile, `"${source.replaceAll('\\', '/')}"`)
  const java = process.env.JAVA_HOME ? join(process.env.JAVA_HOME, 'bin', 'java.exe') : 'java'
  const backend = await launch(java, [`@${argfile}`])
  function run(mode) {
    const result = spawnSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', script, '-Mode', mode], { encoding: 'utf8', windowsHide: true })
    assert.equal(result.status, 0, result.stdout + result.stderr)
    return result.stdout
  }
  const status = run('status')
  assert.match(status, new RegExp(`\\b${own.pid}\\b`))
  assert.match(status, new RegExp(`\\b${backend.pid}\\b`))
  assert.doesNotMatch(status, new RegExp(`\\b${foreign.pid}\\b`))
  const ownExit = once(own, 'exit')
  const backendExit = once(backend, 'exit')
  const stopped = run('stop')
  assert.match(stopped, /Stopped frontend/)
  assert.match(stopped, /Stopped backend/)
  await Promise.all([ownExit, backendExit])
  assert.equal(foreign.exitCode, null)
  assert.match(run('stop'), /No running frontend\/backend listener/)
})
