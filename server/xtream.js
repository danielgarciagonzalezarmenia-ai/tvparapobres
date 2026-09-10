import dotenv from 'dotenv'
import crypto from 'node:crypto'
import path from 'node:path'
import fs from 'node:fs'
import fsp from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import DEFAULT_USERS from './accounts.js'
dotenv.config()

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const CACHE_DIR = path.join(__dirname, '.cache')
try { fs.mkdirSync(CACHE_DIR, { recursive: true }) } catch {}

export const SERVER = (process.env.XTREAM_SERVER || 'http://superxlatino.com:8880').replace(/\/+$/, '')
export const USER = process.env.XTREAM_USERNAME || ''
export const PASS = process.env.XTREAM_PASSWORD || ''
export const EXPIRE = process.env.XTREAM_EXPIRE || null

const API_BASE = `${SERVER}/player_api.php`
const TIMEOUT_MS = 60000
const RETRIES = 3

// --- Rotación de usuarios con salto de cuentas fallidas ---
// Prioridad: variable de entorno XTREAM_USERS (si viene bien formada) > cuentas del código.
let users = DEFAULT_USERS
try {
  const raw = process.env.XTREAM_USERS
  if (raw) {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed) && parsed.length > 0) users = parsed
  }
} catch {}
if (users.length === 0) users = [{ user: USER, pass: PASS }]
let _idx = 0
const penalty = new Map() // user -> ms hasta la que está penalizada

function penalize(u, ms) {
  penalty.set(u, Date.now() + ms)
}

export function nextUser(skipPenalized = true) {
  const n = users.length
  for (let i = 0; i < n; i++) {
    const u = users[(_idx + i) % n]
    if (!skipPenalized || (penalty.get(u.user) || 0) <= Date.now()) {
      _idx = (_idx + i + 1) % n
      return u
    }
  }
  const u = users[_idx % n]
  _idx++
  return u
}
export const userCount = users.length

// Penaliza una cuenta tras fallar: caída (404), bloqueada (401/403/429) o problema de red.
export function markUserFailure(u) {
  if (u) penalize(u.user, 15 * 60 * 1000)
}

const cache = new Map()
const inflight = new Map()

// Caché persistente en disco: permite servir el catálogo tras reinicios
// sin volver a descargar el listado (sobre todo VOD, ~13 s) del upstream.
const cacheFile = (filePath) => {
  const name = path.basename(filePath)
  const m = /^([0-9a-f]{40})\.[0-9a-f]{8}$/.exec(name)
  return { valid: !!m, filePath }
}
const hashKey = (key) => {
  const h = crypto.createHash('sha1').update(key).digest('hex')
  return `${h}.${h.slice(0, 8)}`
}
const diskPath = (key) => path.join(CACHE_DIR, hashKey(key))

async function readDisk(filePath) {
  try {
    const buf = await fsp.readFile(filePath, 'utf8')
    const [headerLine, ...rest] = buf.split('\n')
    const header = JSON.parse(headerLine)
    if (!header || !Array.isArray(header.v) || header.v[0] !== 1) return null
    const data = JSON.parse(rest.join('\n'))
    return { ts: header.ts, data }
  } catch { return null }
}

async function writeDisk(filePath, ts, data) {
  try {
    const header = JSON.stringify({ v: [1], ts })
    await fsp.writeFile(filePath, `${header}\n${JSON.stringify(data)}`, 'utf8')
  } catch {}
}

async function pruneDisk(maxAgeMs = 2 * 24 * 60 * 60 * 1000) {
  try {
    const entries = await fsp.readdir(CACHE_DIR)
    const now = Date.now()
    for (const name of entries) {
      const fp = path.join(CACHE_DIR, name)
      const c = cacheFile(fp)
      if (!c.valid) { await fsp.unlink(fp).catch(() => {}); continue }
      try {
        const { mtimeMs } = await fsp.stat(fp)
        if (now - mtimeMs > maxAgeMs) await fsp.unlink(fp).catch(() => {})
      } catch {}
    }
  } catch {}
}
pruneDisk()

// Contenido para adultos: se filtra por nombre de categoría y por marcadores explícitos en nombres.
const ADULT_CAT_RE = /\b(?:adulto?s?|hentai|porn(?:o|ografia)?|x{3,}|erotic|18\+|\+18)\b/i
const PORNO_RE = /\b(?:porn(?:o|ografia)?|hentai|xnxx|xvideos|erotica|sextape|onlyfans)\b/i
const XXX_RE = /\bx{3,}\b/i

const cleanCats = (list) => (Array.isArray(list) ? list.filter((c) => !ADULT_CAT_RE.test(c.category_name || '')) : list)
const cleanStreams = (list, live = false) =>
  Array.isArray(list) ? list.filter((c) => !PORNO_RE.test(c.name || '') && !(live && XXX_RE.test(c.name || ''))) : list

async function fetchRaw(url) {
  for (let i = 0; i < RETRIES; i++) {
    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) })
      if (res.ok) return res.json()
      const e = new Error(`API Xtream: HTTP ${res.status}`)
      e.status = res.status
      throw e
    } catch (e) {
      if (i === RETRIES - 1) throw e
      await new Promise((r) => setTimeout(r, 800 * (i + 1)))
    }
  }
}

// Cuentas que el proveedor rechaza explícitamente (caída/caducada/bloqueada).
const BAD_STATUS = new Set([400, 401, 403, 404, 429])

async function get(action = '', params = {}, ttl = 15 * 60 * 1000) {
  const key = `${action}|${JSON.stringify(params)}`
  const hit = cache.get(key)

  if (hit && Date.now() - hit.ts < ttl) return hit.data
  const filePath = diskPath(key)

  // Caché en disco (sobrevive reinicios del server).
  if (!hit) {
    const disk = await readDisk(filePath)
    if (disk && Date.now() - disk.ts < ttl) {
      cache.set(key, { data: disk.data, ts: disk.ts })
      return disk.data
    }
  }

  if (inflight.has(key)) return inflight.get(key)

  const p = (async () => {
    // Reintenta con hasta 3 cuentas distintas si la primera falla o está caída.
    for (let attempt = 0; attempt < 3; attempt++) {
      const acc = nextUser()
      const qs = new URLSearchParams({ username: acc.user, password: acc.pass, ...(action ? { action } : {}) })
      for (const [k2, v2] of Object.entries(params)) if (v2 !== undefined && v2 !== '') qs.set(k2, v2)
      const url = `${API_BASE}?${qs}`
      try {
        const data = await fetchRaw(url)
        const ts = Date.now()
        cache.set(key, { data, ts })
        writeDisk(filePath, ts, data)
        return data
      } catch (e) {
        if (BAD_STATUS.has(e.status)) {
          markUserFailure(acc) // cuenta caída/bloqueada: sáltala un tiempo
          if (attempt < 2) continue // prueba otra cuenta
        } else {
          // Problema de red general: probar con otra cuenta también.
          markUserFailure(acc)
          if (attempt < 2) continue
        }
        break
      }
    }
    const stale = cache.get(key) || await readDisk(filePath)
    if (stale) {
      cache.set(key, { data: stale.data, ts: Date.now() })
      return stale.data
    }
    throw new Error(`No se pudo conectar al servidor Xtream (${SERVER})`)
  })()

  inflight.set(key, p)
  try {
    return await p
  } finally {
    inflight.delete(key)
  }
}

export const getStatus = () => get('', {}, 60 * 60 * 1000)

export const getLiveCategories = () => get('get_live_categories', {}, 60 * 60 * 1000).then(cleanCats)
export const getLiveStreams = (categoryId) => get('get_live_streams', { category_id: categoryId }, 15 * 60 * 1000).then((l) => cleanStreams(l, true))
export const getVodCategories = () => get('get_vod_categories', {}, 60 * 60 * 1000).then(cleanCats)
export const getVodStreams = (categoryId) => get('get_vod_streams', { category_id: categoryId }, 12 * 60 * 60 * 1000).then((l) => cleanStreams(l))
export const getSeriesCategories = () => get('get_series_categories', {}, 60 * 60 * 1000).then(cleanCats)
export const getSeries = (categoryId) => get('get_series', { category_id: categoryId }, 12 * 60 * 60 * 1000).then((l) => cleanStreams(l))
export const getSeriesInfo = (seriesId) => get('get_series_info', { series_id: seriesId }, 24 * 60 * 60 * 1000)

// Listados globales ("Todos") para búsqueda: versiones reducidas para no mover megas.
const slim = (list) =>
  Array.isArray(list)
    ? list.map((c) => ({
        stream_id: c.stream_id,
        series_id: c.series_id,
        name: c.name,
        stream_icon: c.stream_icon,
        container_extension: c.container_extension
      }))
    : list

export const getAllLiveStreams = () => get('get_live_streams', {}, 15 * 60 * 1000).then((l) => slim(cleanStreams(l, true)))
export const getAllVod = () => get('get_vod_streams', {}, 60 * 60 * 1000).then((l) => slim(cleanStreams(l)))
export const getAllSeries = () => get('get_series', {}, 60 * 60 * 1000).then((l) => slim(cleanStreams(l)))

export const liveUrl = (id) => {
  const { user, pass } = nextUser()
  return `${SERVER}/${user}/${pass}/${id}`
}
export const vodUrl = (id, ext = '') => {
  const { user, pass } = nextUser()
  return `${SERVER}/movie/${user}/${pass}/${id}${ext ? '.' + ext : ''}`
}
export const seriesUrl = (seriesId) => {
  const { user, pass } = nextUser()
  return `${SERVER}/series/${user}/${pass}/${seriesId}`
}
export const episodeUrl = (id, ext = 'mp4') => {
  const { user, pass } = nextUser()
  return `${SERVER}/series/${user}/${pass}/${id}.${ext}`
}