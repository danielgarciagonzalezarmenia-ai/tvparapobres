const j = async (r) => {
  if (!r.ok) {
    const body = await r.json().catch(() => ({}))
    const err = new Error(body.error || `HTTP ${r.status}`)
    err.status = r.status
    throw err
  }
  return r.json()
}

// Se considera "servicio caído" cuando el backend (la PC del dueño, vía Tailscale)
// no responde: 502/503/504 del proxy de Render o fallo de red directo.
export function isServiceDown(err) {
  if (!err) return false
  if (err.name === 'TypeError') return true
  return err.status === 502 || err.status === 503 || err.status === 504
}

const get = (url) => fetch(url).then(j)

export const api = {
  status: () => get('/api/status'),
  liveCategories: () => get('/api/categories'),
  liveStreams: (id) => get(`/api/streams?category_id=${encodeURIComponent(id)}`),
  vodCategories: () => get('/api/vod-categories'),
  vodStreams: (id) => get(`/api/vod?category_id=${encodeURIComponent(id)}`),
  seriesCategories: () => get('/api/series-categories'),
  series: (id) => get(`/api/series?category_id=${encodeURIComponent(id)}`),
  seriesInfo: (id) => get(`/api/series-info?series_id=${encodeURIComponent(id)}`),
  liveUrl: (id) => get(`/api/url/live/${id}`),
  vodUrl: (id, ext) => get(`/api/url/vod/${id}${ext ? `/${encodeURIComponent(ext)}` : ''}`),
  seriesUrl: (id) => get(`/api/url/series/${id}`),
  episodeUrl: (id, ext) => get(`/api/url/episode/${id}/${encodeURIComponent(ext)}`),
  vodStream: (id, ext) => `/api/rt/vod/${encodeURIComponent(id)}/${encodeURIComponent(ext || 'mp4')}`,
  episodeStream: (id, ext) => `/api/rt/series/${encodeURIComponent(id)}/${encodeURIComponent(ext || 'mp4')}`,
  liveAll: () => get('/api/all-live'),
  vodAll: () => get('/api/all-vod'),
  seriesAll: () => get('/api/all-series'),
  epg: (id) => get(`/api/epg/${encodeURIComponent(id)}`),
  meta: () => get('/api/meta')
}

export const NATIVE_EXTS = new Set(['mp4', 'webm', 'mov', 'm4v', 'ogv', 'ogg'])

// Glifos "ornamentales"/leet usados por el panel en canales deportivos -> letra real.
const LTR_MAP = {
  'Ə': 'E', 'ǝ': 'e', 'Ǝ': 'E', 'È': 'E', 'É': 'E', 'Ê': 'E', 'Š': 'S',
  '$': 'S', 'Ø': 'O', 'Ö': 'O', '0': 'O', '1': 'I', '3': 'E', '5': 'S', '7': 'T', '@': 'A', 'Ä': 'A'
}

export function itemId(item) {
  return item.stream_id ?? item.series_id ?? item.id
}

export function cleanName(s = '') {
  return String(s)
    .replace(/[ƏǝƎÈÉÊŠ$ØÖ@Ä]/gu, (ch) => LTR_MAP[ch])
    .replace(/\p{Extended_Pictographic}|\p{Emoji_Presentation}|\u{FE0F}|\u200D/gu, ' ')
    .replace(/[*.,]/gu, '')
    .replace(/\b(?:19|20)\d{2}(?:\/\d{1,4})?\b/g, ' ')
    .replace(/\b(?:UHD|4K|HDR10?|DOLBY\s*ATMOS|DTS[\s-]?HD|MP4?|MKV|XVID|H\.?264|HEVC)\b/gi, ' ')
    .replace(/[()\[\]]/g, ' ')
    .replace(/[|•··…*."'`´_~\/]/g, ' ')
    .replace(/\s*[:;]\s*/g, ' ')
    .replace(/\s{2,}/g, ' ')
    .replace(/^[\s.,|:;—–-]+|[\s.,|:;—–-]+$/g, '')
    .trim()
}

export function fmtTime(s) {
  if (!isFinite(s) || s <= 0) return ''
  const t = Math.floor(s)
  const h = Math.floor(t / 3600)
  const m = Math.floor((t % 3600) / 60)
  const sec = t % 60
  const mm = h ? String(m).padStart(2, '0') : String(m)
  const ss = String(sec).padStart(2, '0')
  return h ? `${h}:${mm}:${ss}` : `${m}:${ss}`
}

// Búsqueda normalizada (igual que el APK): limpio + sin tildes + solo [a-z0-9].
// Así "espn" encuentra "*ESP*N", "E S P N" o "3SPN".
const normCache = new Map()
export function normName(s) {
  const key = s || ''
  let v = normCache.get(key)
  if (v === undefined) {
    const folded = cleanName(key).normalize('NFD').replace(/[\u0300-\u036f]/g, '')
    v = folded.toLowerCase().replace(/[^a-z0-9]/g, '')
    if (normCache.size > 5000) normCache.clear()
    normCache.set(key, v)
  }
  return v
}

export function itemName(item) {
  return cleanName(item.name ?? item.title ?? 'Sin título')
}

export function itemLogo(item) {
  return item.stream_icon ?? item.cover ?? item.logo ?? ''
}

// Entrega las imágenes (íconos/posters) a través de nuestro servidor para que
// no las bloquee el navegador por contenido mixto (web HTTPS vs. proveedor HTTP).
export function imgProxy(u = '') {
  if (!u) return ''
  if (u.startsWith('/') || u.startsWith('blob:') || u.startsWith('data:')) return u
  try {
    const p = new URL(u)
    if (p.protocol === 'http:' || p.protocol === 'https:') return `/api/rt/img?u=${encodeURIComponent(u)}`
  } catch {}
  return u
}