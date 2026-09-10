import mpegts from 'mpegts.js'
import Hls from 'hls.js'

let video = null
let mp = null
let hls = null
let currentUrl = null
let reconnectTries = 0
let baseTime = 0
let connectTimer = null
let gotPlay = false
let timedOut = false
const MAX_RETRIES = 4
const CONNECT_TIMEOUT_MS = 30000
const STALL_TIMEOUT_MS = 6000
const STALL_POLL_MS = 2000
const listeners = new Set()
let stallWatcher = null

const emit = (e) => listeners.forEach((fn) => fn(e))

export const attachPlayer = (el) => {
  video = el
}

export const onPlayerEvent = (fn) => {
  listeners.add(fn)
  return () => listeners.delete(fn)
}

const clearConnectTimer = () => {
  if (connectTimer) {
    clearTimeout(connectTimer)
    connectTimer = null
  }
}

const markPlayed = () => {
  gotPlay = true
  clearConnectTimer()
  startStallWatcher()
}

function clearStallWatcher() {
  if (stallWatcher) {
    clearInterval(stallWatcher.timer)
    if (stallWatcher.video && stallWatcher.onTu) stallWatcher.video.removeEventListener('timeupdate', stallWatcher.onTu)
    stallWatcher = null
  }
}

function startStallWatcher() {
  clearStallWatcher()
  if (!video) return
  let lastProgress = Date.now()
  const onTu = () => { lastProgress = Date.now() }
  video.addEventListener('timeupdate', onTu)
  const timer = setInterval(() => {
    if (!video || !currentUrl || !gotPlay || video.paused || video.seeking) return
    if (Date.now() - lastProgress < STALL_TIMEOUT_MS) return
    restartPlayback('sin señal')
  }, STALL_POLL_MS)
  stallWatcher = { timer, video, onTu }
}

function restartPlayback(_why) {
  clearStallWatcher()
  clearConnectTimer()
  if (reconnectTries > 6) reconnectTries = 6
  reconnectTries++
  emit({ type: 'notice', message: `Reconectando (${reconnectTries})…` })
  const wait = Math.min(1500 + reconnectTries * 1000, 9000)
  setTimeout(() => {
    if (!currentUrl || !video) return
    try { mp?.destroy() } catch { /* noop */ }
    mp = null
    gotPlay = false
    startMse(currentUrl)
  }, wait)
}

const armConnectTimeout = () => {
  if (gotPlay || !currentUrl) return
  clearConnectTimer()
  connectTimer = setTimeout(() => {
    if (gotPlay || !currentUrl) return
    timedOut = true
    currentUrl = null
    reconnectTries = MAX_RETRIES
    try { mp?.destroy() } catch { /* noop */ }
    try { hls?.destroy() } catch { /* noop */ }
    mp = null
    hls = null
    if (video) {
      video.pause()
      video.removeAttribute('src')
      video.load()
    }
    emit({ type: 'error', message: 'No se encontró un servidor para este canal' })
  }, CONNECT_TIMEOUT_MS)
}

export function stopPlayback() {
  clearConnectTimer()
  clearStallWatcher()
  gotPlay = false
  timedOut = false
  try { mp?.destroy() } catch { /* noop */ }
  try { hls?.destroy() } catch { /* noop */ }
  mp = null
  hls = null
  currentUrl = null
  reconnectTries = 0
  baseTime = 0
  if (video) {
    video.pause()
    video.removeAttribute('src')
    video.load()
  }
  emit({ type: 'idle' })
}

export function getPlaybackState() {
  if (!video) return null
  const cur = video.currentTime || 0
  const duration = isFinite(video.duration) && video.duration > 0 ? video.duration : null
  return { position: Math.round((baseTime + cur) * 10) / 10, duration }
}

function startMse(url, isLive = true) {
  mp = mpegts.createPlayer(
    { type: 'mse', isLive, url },
    {
      enableWorker: false,
      liveBufferLatencyChasing: false,
      liveBufferLatencyMaxLatency: 25,
      liveBufferLatencyMinRemain: 3,
      lazyLoad: true,
      lazyLoadMaxDuration: 5 * 60,
      lazyLoadRecoverDuration: 15,
      autoCleanupSourceBuffer: true,
      autoCleanupMaxBackwardDuration: 10 * 60,
      autoCleanupMinBackwardDuration: 5 * 60,
      stashInitialSize: 1024 * 1024,
      deferLoadAfterSourceOpen: true
    }
  )

  mp.on(mpegts.Events.ERROR, (_type, data) => {
    emit({ type: 'error', message: 'Error de transmisión', data })
    if (!currentUrl || !video) return
    restartPlayback('error')
  })

  mp.attachMediaElement(video)
  mp.load()
  mp.play().catch(() => { if (!timedOut) emit({ type: 'notice', message: 'Esperando señal…' }) })
  emit({ type: 'connecting' })
  armConnectTimeout()
}

export async function playURL(url, opts = {}) {
  stopPlayback()
  if (!video) video = document.querySelector('video')
  if (!video || !url) return
  currentUrl = url
  gotPlay = false
  const onPlay = () => { markPlayed(); video.removeEventListener('playing', onPlay) }
  video.addEventListener('playing', onPlay)
  const start = opts.start > 0 && !url.includes('/api/stream/') ? opts.start : 0
  const low = url.toLowerCase()
  emit({ type: 'loading', url })

  if (low.includes('.m3u8')) {
    if (Hls.isSupported()) {
      hls = new Hls({ liveDurationInfinity: true, enableWorker: true })
      hls.on(Hls.Events.MANIFEST_PARSED, () => { video.play().catch(() => {}) })
      hls.on(Hls.Events.ERROR, (_e, d) => {
        if (d.fatal) emit({ type: 'error', message: 'Error al cargar el stream HLS', data: d })
      })
      hls.loadSource(url)
      hls.attachMedia(video)
      armConnectTimeout()
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = url
      armConnectTimeout()
    } else {
      emit({ type: 'error', message: 'Tu navegador no soporta HLS' })
    }
    return
  }

  if (/\.(mp4|webm|mov|m4v|ogv|ogg)$/.test(low) || low.includes('/api/rt/vod/') || low.includes('/api/rt/series/')) {
    video.src = url
    emit({ type: 'connecting' })
    armConnectTimeout()
    if (start > 0) {
      const onMeta = () => {
        video.removeEventListener('loadedmetadata', onMeta)
        try { video.currentTime = start } catch { /* noop */ }
        video.play().catch(() => {})
      }
      video.addEventListener('loadedmetadata', onMeta)
    } else {
      video.play().catch(() => {})
    }
    return
  }

  if (mpegts.isSupported()) {
    const isLive = !low.includes('/api/stream/')
    if (opts.start > 0 && !isLive) baseTime = opts.start
    let target = url
    if (opts.start > 0 && !isLive) {
      target = url + (url.includes('?') ? '&' : '?') + 'from=' + Math.floor(opts.start)
    }
    startMse(target, isLive)
  } else {
    video.src = url
    emit({ type: 'connecting' })
    armConnectTimeout()
  }
}