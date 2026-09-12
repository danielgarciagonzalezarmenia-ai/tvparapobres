import mpegts from 'mpegts.js'
import Hls from 'hls.js'

let video = null
let mp = null
let hls = null
let currentUrl = null
let baseTime = 0
let connectTimer = null
let gotPlay = false
let timedOut = false
let retryCount = 0
let mode = 'mse' // mse | native | hls
let isLive = true
const MAX_RETRIES = 6
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
  retryCount = 0
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
  if (!video || mode === 'native') return
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
  retryCount++
  const backoff =
    retryCount <= 3 ? 800 + retryCount * 700
    : retryCount <= 8 ? 3000 + (retryCount - 3) * 2500
    : 12000
  if (mode === 'native') {
    if (retryCount > MAX_RETRIES) {
      emit({ type: 'error', message: 'No se pudo mantener la conexión' })
      return
    }
    emit({ type: 'notice', message: `Volviendo a conectar (${retryCount})…` })
  } else if (retryCount > 6) {
    emit({ type: 'error', message: 'Señal perdida. Reintentando automáticamente…' })
  } else {
    emit({ type: 'notice', message: `Reconectando (${retryCount})…` })
  }
  setTimeout(() => {
    if (!currentUrl || !video) return
    gotPlay = false
    timedOut = false
    if (mode === 'mse') {
      try { mp?.destroy() } catch { /* noop */ }
      mp = null
      startMse(currentUrl, isLive)
    } else if (mode === 'hls') {
      try { hls?.destroy() } catch { /* noop */ }
      hls = null
      startHls(currentUrl)
    } else {
      video.removeAttribute('src')
      video.load()
      video.src = currentUrl
      video.play().catch(() => {})
      armConnectTimeout()
    }
  }, backoff)
}

const armConnectTimeout = () => {
  if (gotPlay || !currentUrl) return
  clearConnectTimer()
  connectTimer = setTimeout(() => {
    if (gotPlay || !currentUrl) return
    timedOut = true
    currentUrl = null
    try { mp?.destroy() } catch { /* noop */ }
    try { hls?.destroy() } catch { /* noop */ }
    mp = null
    hls = null
    video.pause()
    video.removeAttribute('src')
    video.load()
    emit({ type: 'error', message: 'No se encontró un servidor para este contenido' })
  }, CONNECT_TIMEOUT_MS)
}

export function stopPlayback() {
  clearConnectTimer()
  clearStallWatcher()
  gotPlay = false
  timedOut = false
  retryCount = 0
  try { mp?.destroy() } catch { /* noop */ }
  try { hls?.destroy() } catch { /* noop */ }
  mp = null
  hls = null
  currentUrl = null
  baseTime = 0
  mode = 'mse'
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

function startMse(url, _live = true) {
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

  mp.on(mpegts.Events.ERROR, () => {
    if (!currentUrl || !video) return
    restartPlayback('error')
  })

  mp.attachMediaElement(video)
  mp.load()
  mp.play().catch(() => { if (!timedOut) emit({ type: 'notice', message: 'Esperando señal…' }) })
  emit({ type: 'connecting' })
  armConnectTimeout()
}

function startHls(url) {
  hls = new Hls({ liveDurationInfinity: true, enableWorker: true })
  hls.on(Hls.Events.MANIFEST_PARSED, () => { video.play().catch(() => {}) })
  hls.on(Hls.Events.ERROR, (_e, d) => {
    if (!d.fatal || !currentUrl || !video) return
    if (d.type === Hls.ErrorTypes.NETWORK_ERROR) {
      restartPlayback('red hls')
    } else {
      hls.recoverMediaError()
      setTimeout(() => restartPlayback('hls media'), 2000)
    }
  })
  hls.loadSource(url)
  hls.attachMedia(video)
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
    isLive = true
    mode = 'hls'
    if (Hls.isSupported()) {
      startHls(url)
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      mode = 'native'
      video.src = url
      armConnectTimeout()
    } else {
      emit({ type: 'error', message: 'Tu navegador no soporta HLS' })
    }
    return
  }

  if (/\.(mp4|webm|mov|m4v|ogv|ogg)$/.test(low) || low.includes('/api/rt/vod/') || low.includes('/api/rt/series/')) {
    mode = 'native'
    isLive = false
    video.addEventListener('error', () => {
      if (timedOut || !currentUrl) return
      restartPlayback('error de video')
    })
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
    isLive = !low.includes('/api/stream/')
    mode = 'mse'
    if (opts.start > 0 && !isLive) baseTime = opts.start
    let target = url
    if (opts.start > 0 && !isLive) {
      target = url + (url.includes('?') ? '&' : '?') + 'from=' + Math.floor(opts.start)
    }
    startMse(target, isLive)
  } else {
    mode = 'native'
    video.src = url
    emit({ type: 'connecting' })
    armConnectTimeout()
  }
}