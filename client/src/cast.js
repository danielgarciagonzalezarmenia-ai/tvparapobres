let initialized = false
let initPromise = null

function loadCastScript() {
  return new Promise((resolve, reject) => {
    if (typeof window === 'undefined') return reject(new Error('no-window'))
    if (typeof window.cast !== 'undefined') return resolve()
    const s = document.createElement('script')
    s.src = 'https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1'
    s.async = true
    s.onload = resolve
    s.onerror = () => reject(new Error('No se pudo cargar Chromecast'))
    document.head.appendChild(s)
  })
}

export async function ensureCast() {
  if (initialized) return true
  if (initPromise) return initPromise
  initPromise = (async () => {
    try {
      await loadCastScript()
      if (window.cast && window.cast.framework && window.chrome && window.chrome.cast) {
        window.cast.framework.CastContext.getInstance().setOptions({
          receiverApplicationId: 'CC1AD845', // Default Media Receiver (Chromecast/Android TV)
          autoJoinPolicy: window.chrome.cast.AutoJoinPolicy.ORIGIN_SCOPED
        })
        initialized = true
      }
    } catch { /* normal si el navegador no soporta Cast */ }
    return initialized
  })()
  return initPromise
}

function guessType(url = '') {
  const u = String(url).toLowerCase()
  if (u.includes('.m3u8')) return 'application/x-mpegURL'
  if (/\.(mp4|m4v|mov|webm|ogg)$/.test(u)) return 'video/mp4'
  return 'video/mp2t'
}

export async function castMedia(url, title = '') {
  await ensureCast()
  if (!initialized) throw new Error('Chromecast no está disponible en este navegador')
  const media = new window.chrome.cast.media.MediaInfo(url, guessType(url))
  media.metadata = new window.chrome.cast.media.GenericMediaMetadata()
  media.metadata.title = title || 'Reproduciendo'
  const request = new window.chrome.cast.media.LoadRequest(media)
  const context = window.cast.framework.CastContext.getInstance()
  const session = await context.requestSession()
  await session.loadMedia(request)
}