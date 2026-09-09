import express from 'express'
import cors from 'cors'
import path from 'node:path'
import fs from 'node:fs'
import { spawn } from 'node:child_process'
import http from 'node:http'
import https from 'node:https'
import { fileURLToPath } from 'node:url'
import ffmpegPath from 'ffmpeg-static'
import * as xt from './xtream.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const DIST = path.join(__dirname, '..', 'client', 'dist')

const app = express()
app.use(cors())
app.use(express.json())

const wrap = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch((e) => res.status(502).json({ error: e.message }))

const num = (v) => {
  const n = Number(v)
  return Number.isFinite(n) ? n : undefined
}

app.get('/api/status', wrap(async (req, res) => {
  const s = await xt.getStatus()
  res.json({ server: xt.SERVER, user: xt.USER, ...(s.user_info || {}), ...(s.server_info || {}) })
}))

app.get('/health', (_req, res) => res.json({ ok: true, ts: Date.now() }))

app.get('/api/meta', (_req, res) => {
  res.json({ server: xt.SERVER, user: xt.USER, expire: xt.EXPIRE })
})

app.get('/api/categories', wrap(async (req, res) => res.json(await xt.getLiveCategories())))
app.get('/api/streams', wrap(async (req, res) => res.json(await xt.getLiveStreams(num(req.query.category_id)))))
app.get('/api/vod-categories', wrap(async (req, res) => res.json(await xt.getVodCategories())))
app.get('/api/vod', wrap(async (req, res) => res.json(await xt.getVodStreams(num(req.query.category_id)))))
app.get('/api/series-categories', wrap(async (req, res) => res.json(await xt.getSeriesCategories())))
app.get('/api/series', wrap(async (req, res) => res.json(await xt.getSeries(num(req.query.category_id)))))
app.get('/api/series-info', wrap(async (req, res) => res.json(await xt.getSeriesInfo(num(req.query.series_id)))))

app.get('/api/all-live', wrap(async (req, res) => res.json(await xt.getAllLiveStreams())))
app.get('/api/all-vod', wrap(async (req, res) => res.json(await xt.getAllVod())))
app.get('/api/all-series', wrap(async (req, res) => res.json(await xt.getAllSeries())))

// Pasa por nuestro servidor el contenido HTTP del proveedor para evitar
// bloqueo por "contenido mixto" (nuestra web va por HTTPS, el proveedor es HTTP).
function pipeUpstream(srcUrl, req, res, depth = 0) {
  let u
  try { u = new URL(srcUrl) } catch { if (!res.headersSent) res.status(502).end(); return }
  if (depth > 6) { if (!res.headersSent) res.status(502).end(); return }
  const lib = u.protocol === 'https:' ? https : http
  const headers = {}
  if (req.headers.range) headers.Range = req.headers.range
  headers['User-Agent'] = 'Mozilla/5.0'
  headers.Accept = '*/*'
  const preq = lib.request(u, { method: 'GET', headers }, (up) => {
    const sc = up.statusCode || 0
    if (sc >= 300 && sc < 400 && up.headers.location) {
      up.resume()
      let next = null
      try { next = new URL(up.headers.location, srcUrl).href } catch {}
      // Al redirigir se descarta la cabecera Range (no todos los CDN la aceptan).
      const headers2 = { 'User-Agent': 'Mozilla/5.0', Accept: '*/*' }
      if (next) return pipeUpstream(next, { ...req, headers: headers2 }, res, depth + 1)
      if (!res.headersSent) res.status(502).end()
      return
    }
    res.statusCode = sc
    for (const h of ['content-type', 'content-length', 'accept-ranges', 'content-range', 'transfer-encoding', 'cache-control']) {
      const v = up.headers[h]
      if (v !== undefined) res.setHeader(h, v)
    }
    res.setHeader('Access-Control-Allow-Origin', '*')
    up.pipe(res)
    res.on('close', () => up.destroy())
  })
  preq.on('error', () => { if (!res.writableEnded) res.destroy() })
  preq.end()
}

app.get('/api/rt/live/:id', (req, res) => {
  const id = String(req.params.id).replace(/[^0-9]/g, '')
  if (!id) return res.status(400).json({ error: 'ID inválido' })
  res.set('Content-Type', 'video/mp2t')
  res.set('Cache-Control', 'no-store')
  pipeUpstream(`${xt.SERVER}/${xt.USER}/${xt.PASS}/${id}`, req, res)
})

app.get('/api/rt/img', (req, res) => {
  const u = String(req.query.u || '')
  try {
    const p = new URL(u)
    if (p.protocol !== 'http:' && p.protocol !== 'https:') return res.status(400).json({ error: 'URL inválida' })
  } catch { return res.status(400).json({ error: 'URL inválida' }) }
  res.set('Content-Type', 'image/jpeg')
  res.set('Cache-Control', 'public, max-age=86400')
  pipeUpstream(u, req, res)
})

app.get('/api/url/live/:id', wrap(async (req, res) => {
  const id = String(req.params.id).replace(/[^0-9]/g, '')
  if (!id) return res.status(400).json({ error: 'ID inválido' })
  res.json({ url: `/api/rt/live/${id}` })
}))
app.get('/api/url/vod/:id/:ext?', wrap(async (req, res) =>
  res.json({ url: xt.vodUrl(num(req.params.id), req.params.ext) })))
app.get('/api/url/series/:id', wrap(async (req, res) => res.json({ url: xt.seriesUrl(num(req.params.id)) })))
app.get('/api/url/episode/:id/:ext', wrap(async (req, res) => res.json({ url: xt.episodeUrl(num(req.params.id), req.params.ext) })))

const NATIVE_EXTS = ['mp4', 'webm', 'mov', 'm4v', 'ogv', 'ogg']

app.get('/api/stream/:kind/:id/:ext', (req, res) => {
  const kind = req.params.kind === 'series' ? 'series' : 'movie'
  const id = String(req.params.id).replace(/[^0-9]/g, '')
  const ext = String(req.params.ext).replace(/[^a-zA-Z0-9]/g, '').slice(0, 8).toLowerCase()
  if (!id) return res.status(400).json({ error: 'ID inválido' })
  const src = `${xt.SERVER}/${kind === 'series' ? 'series' : 'movie'}/${xt.USER}/${xt.PASS}/${id}.${ext || 'mkv'}`

  res.set({
    'Content-Type': 'video/mp2t',
    'Cache-Control': 'no-store',
    'Content-Disposition': 'inline',
    'Access-Control-Allow-Origin': '*'
  })

  const args = [
    '-hide_banner', '-loglevel', 'error',
    '-user_agent', 'Mozilla/5.0'
  ]
  const from = Number(req.query.from)
  if (isFinite(from) && from > 0) args.push('-ss', String(from))
  args.push(
    '-i', src,
    '-map', '0:v:0', '-map', '0:a:0',
    '-c:v', 'copy',
    '-c:a', 'aac', '-b:a', '192k', '-ac', '2', '-ar', '48000',
    '-mpegts_flags', '+resend_headers',
    '-f', 'mpegts', 'pipe:1'
  )

  const candidates = []
  if (ffmpegPath && fs.existsSync(ffmpegPath)) candidates.push(ffmpegPath)
  candidates.push('ffmpeg')
  candidates.push('/usr/bin/ffmpeg')
  const unique = [...new Set(candidates)]

  let errBuf = ''
  let attempt = 0
  const startChild = () => {
    const bin = unique[attempt]
    const cleanup = () => { req.removeListener('close', onReqClose) }
    const onReqClose = () => { if (!res.writableEnded) { try { child.kill('SIGKILL') } catch {} } }
    const child = spawn(bin, args, { stdio: ['ignore', 'pipe', 'pipe'] })
    child.stdout.on('data', (d) => { if (!res.destroyed) res.write(d) })
    child.stderr.on('data', (d) => { errBuf = (errBuf + d.toString()).slice(-600) })
    child.on('error', (e) => {
      console.error(`[ffmpeg] "${bin}" spawn error:`, e.message)
      cleanup()
      attempt++
      if (attempt < unique.length) startChild()
      else if (!res.headersSent) res.status(502).json({ error: 'No se pudo iniciar el video (ffmpeg no disponible)', detail: errBuf })
    })
    child.on('exit', (code) => {
      cleanup()
      if (res.writableEnded || res.destroyed) return
      if (!res.headersSent) {
        console.error(`[ffmpeg] "${bin}" exit ${code} stderr:`, errBuf || '(vacío)')
        res.status(502).json({ error: 'No se pudo iniciar el video', detail: errBuf })
      } else res.end()
    })
    req.on('close', onReqClose)
  }
  startChild()
})

if (fs.existsSync(DIST)) {
  app.use(express.static(DIST))
  app.get('*', (req, res, next) => {
    if (req.path.startsWith('/api')) return next()
    res.sendFile(path.join(DIST, 'index.html'))
  })
}

const port = Number(process.env.PORT) || 4000
app.listen(port, () => {
  console.log(`\n  TV Para Pobres -> http://localhost:${port}`)
  console.log(`  API Xtream      -> ${xt.SERVER}\n`)
})