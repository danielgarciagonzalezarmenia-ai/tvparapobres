// Servidor de Render: sirve la web (estáticos) y reenvía las peticiones /api
// al backend que corre en la PC del dueño, expuesta por Tailscale Funnel.
// Cuando la PC está apagada, devuelve 502 y la web muestra "Sin servicio".
import express from 'express'
import http from 'node:http'
import https from 'node:https'
import path from 'node:path'
import fs from 'node:fs'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.join(__dirname, '..', 'client', 'dist')
const PC_URL = (process.env.PC_BACKEND_URL || '').replace(/\/+$/, '')

const app = express()

function forward(req, res) {
  if (!PC_URL) {
    return res.status(502).json({ error: 'Backend no configurado (falta PC_BACKEND_URL)' })
  }
  const target = PC_URL + req.originalUrl
  let u
  try { u = new URL(target) } catch {
    return res.status(502).json({ error: 'URL de backend inválida' })
  }
  const lib = u.protocol === 'https:' ? https : http
  const headers = { ...req.headers, host: u.host, 'user-agent': 'Mozilla/5.0' }
  const preq = lib.request(u, { method: req.method, headers }, (up) => {
    res.statusCode = up.statusCode || 502
    for (const h of ['content-type', 'content-length', 'accept-ranges', 'content-range', 'transfer-encoding', 'cache-control']) {
      const v = up.headers[h]
      if (v !== undefined) res.setHeader(h, v)
    }
    up.pipe(res)
    res.on('close', () => up.destroy())
  })
  preq.on('error', () => {
    if (!res.writableEnded) res.status(502).json({ error: 'El servidor de origen está apagado' })
  })
  if (req.method !== 'GET' && req.method !== 'HEAD') req.pipe(preq)
  else preq.end()
}

app.use('/api', forward)
app.use('/health', forward)

if (fs.existsSync(DIST)) {
  app.use(express.static(DIST))
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api')) return next()
    res.sendFile(path.join(DIST, 'index.html'))
  })
}

const port = Number(process.env.PORT) || 4000
app.listen(port, () => {
  console.log(`\n  TV Para Pobres (proxy Render) -> http://localhost:${port}`)
  console.log(`  Backend en la PC -> ${PC_URL || '(sin configurar)'}\n`)
})