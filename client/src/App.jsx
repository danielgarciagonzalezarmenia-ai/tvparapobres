import { useEffect, useMemo, useRef, useState } from 'react'
import { api, isServiceDown, itemId, itemName, itemLogo, imgProxy, cleanName, fmtTime, NATIVE_EXTS } from './api.js'
import { attachPlayer, playURL, stopPlayback, getPlaybackState, onPlayerEvent } from './player.js'

const TABS = {
  live: { label: 'LIVE', sub: 'Canales', loadCats: api.liveCategories, loadItems: api.liveStreams, all: api.liveAll },
  vod: { label: 'PELÍCULAS', sub: 'Películas', loadCats: api.vodCategories, loadItems: api.vodStreams, all: api.vodAll },
  series: { label: 'SERIES', sub: 'Series', loadCats: api.seriesCategories, loadItems: api.series, all: api.seriesAll }
}

const ALL_CAT = { category_id: '__all__', category_name: 'Todos', __all__: true }

const isMobile = typeof window !== 'undefined' && /Android|iPhone|iPad|iPod|Mobi/i.test(navigator.userAgent)

const FAV_KEY = 'tvp_favs'
const HIST_KEY = 'tvp_hist'
const MAX_HIST = 120

// Vencimiento del usuario del panel. El cliente pide la fecha al server (/api/meta);
// este valor es solo el fallback local si el server no responde.
const EXPIRE_DATE = '2026-09-21'

function todayStr() {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}

const isExpired = () => todayStr() >= EXPIRE_DATE

// Identidad canónica de una entrada de historial: la misma peli/episodio/canal
// (por type:id) debe contar una sola vez aunque su "key" vieja difiera.
const canonicId = (h) => {
  const id = h && h.id
  if (id === undefined || id === null || id === '') return null
  return `${h.type}:${id}`
}

function loadFavs() {
  try { return JSON.parse(localStorage.getItem(FAV_KEY) || '[]') } catch { return [] }
}

function loadHistory() {
  try {
    const arr = JSON.parse(localStorage.getItem(HIST_KEY) || '[]')
    if (!Array.isArray(arr)) return []
    const best = new Map()
    for (const h of arr) {
      if (!h || typeof h !== 'object') continue
      const k = canonicId(h) || h.key
      if (!k) continue
      const prev = best.get(k)
      if (!prev || (h.ts || 0) > (prev.ts || 0)) best.set(k, h)
    }
    return [...best.values()]
      .sort((a, b) => (b.ts || 0) - (a.ts || 0))
      .slice(0, MAX_HIST)
  } catch { return [] }
}

export default function App() {
  const [tab, setTab] = useState('live')
  const [expired, setExpired] = useState(isExpired)
  const [sideOpen, setSideOpen] = useState(false)
  const [cats, setCats] = useState([])
  const [activeCat, setActiveCat] = useState(null)
  const [items, setItems] = useState([])
  const [allItems, setAllItems] = useState([])
  const [loadingCats, setLoadingCats] = useState(true)
  const [loadingItems, setLoadingItems] = useState(false)
  const [search, setSearch] = useState('')
  const [current, setCurrent] = useState(null)
  const [favs, setFavs] = useState(loadFavs)
  const [hist, setHist] = useState(loadHistory)
  const [showFavs, setShowFavs] = useState(false)
  const [showHist, setShowHist] = useState(false)
  const [playState, setPlayState] = useState('idle')
  const [playMsg, setPlayMsg] = useState('')
  
  const [error, setError] = useState('')
  const [modal, setModal] = useState(null)
  const [serviceDown, setServiceDown] = useState(false)
  const videoRef = useRef(null)
  const wrapRef = useRef(null)
  const lastUrl = useRef(null)
  const histRef = useRef(hist)
  const currentRef = useRef(current)
  const lastSaveRef = useRef(0)
  const autoFsRef = useRef(false)

  useEffect(() => { currentRef.current = current }, [current])

  useEffect(() => {
    api.meta()
      .then((m) => { if (m && m.expire) setExpired(todayStr() >= String(m.expire).slice(0, 10)) })
      .catch(() => {})
  }, [])

  // Vigila que el backend (la PC) esté online. Si se cae, muestra la pantalla
  // completa de "Sin servicio"; cuando vuelve, refresca el catálogo.
  useEffect(() => {
    let stopped = false
    const check = async (first = false) => {
      if (stopped) return
      try {
        await api.meta()
        if (stopped) return
        if (serviceDown) {
          setServiceDown(false)
          if (!first) refreshCats()
        }
      } catch (e) {
        if (stopped) return
        if (isServiceDown(e)) setServiceDown(true)
      }
    }
    check(true)
    const id = setInterval(() => check(), 15000)
    return () => { stopped = true; clearInterval(id) }
  }, [serviceDown])

  useEffect(() => { histRef.current = hist }, [hist])

  useEffect(() => { attachPlayer(videoRef.current) }, [current])

  useEffect(() => {
    if (!current) return
    wrapRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }, [current])

  useEffect(() => {
    if (!current?.url || current.url === lastUrl.current) return
    lastUrl.current = current.url
    autoFsRef.current = false
    playURL(current.url, { start: current.start || 0 })
  }, [current])

  useEffect(
    () =>
      onPlayerEvent((e) => {
        setPlayState(e.type)
        if (e.type === 'error') setPlayMsg(e.message || 'Error al reproducir')
        else if (e.type === 'notice') setPlayMsg(e.message)
        else if (e.type === 'idle') setPlayMsg('')
      }),
    []
  )

  const refreshCats = async () => {
    setLoadingCats(true)
    setError('')
    setActiveCat(null)
    setItems([])
    setAllItems([])
    setShowFavs(false)
    setShowHist(false)
    closePlayer()
    try {
      const list = await TABS[tab].loadCats()
      setCats(list)
      setLoadingCats(false)
      if (list.length) selectCat(list[0])
    } catch (e) {
      setError(e.message)
      setLoadingCats(false)
    }
  }

  useEffect(() => { refreshCats() }, [tab])

  const saveHist = (entry) => {
    const idKey = canonicId(entry)
    histRef.current = [
      entry,
      ...histRef.current.filter((h) => h.key !== entry.key && (!idKey || canonicId(h) !== idKey))
    ].slice(0, MAX_HIST)
    setHist(histRef.current)
    localStorage.setItem(HIST_KEY, JSON.stringify(histRef.current))
  }

  const persistPos = () => {
    const c = currentRef.current
    if (!c?.histKey) return
    const prev = histRef.current.find((h) => h.key === c.histKey)
    if (!prev) return
    if (c.isLive) {
      saveHist({ ...prev, ts: Date.now() })
      return
    }
    const st = getPlaybackState()
    if (!st) return
    saveHist({ ...prev, position: Math.floor(st.position), duration: st.duration, ts: Date.now() })
  }

  useEffect(() => {
    const h = () => persistPos()
    window.addEventListener('beforeunload', h)
    return () => window.removeEventListener('beforeunload', h)
  }, [])

  const closePlayer = () => {
    persistPos()
    stopPlayback()
    lastUrl.current = null
    setCurrent(null)
  }

  const doPlay = async (name, logo, urlBuilder, isLive = false, meta = {}) => {
    const { url } = await urlBuilder()
    const key = meta.key || `live:${meta.id ?? ''}`
    const entry = {
      key, type: meta.type || 'live', id: meta.id, name, logo, url,
      isLive, ext: meta.ext || '', position: meta.start || 0,
      duration: meta.duration || null,
      ts: Date.now()
    }
    setCurrent({ name, logo, url, isLive, start: entry.position, histKey: key })
    if (!isLive) saveHist(entry)
  }

  const selectCat = async (cat) => {
    setActiveCat(cat)
    setShowFavs(false)
    setShowHist(false)
    setSideOpen(false)
    setLoadingItems(true)
    setError('')
    closePlayer()
    try {
      if (cat.__all__) {
        let list = allItems
        if (!list.length) {
          list = await TABS[tab].all()
          setAllItems(list)
        }
        setItems(list)
      } else {
        setItems(await TABS[tab].loadItems(cat.category_id))
      }
    } catch (e) {
      setError(e.message)
    } finally {
      setLoadingItems(false)
    }
  }

  const isFav = (item) => favs.some((f) => f.k === String(itemId(item)))

  const toggleFav = (item) => {
    const k = String(itemId(item))
    const exists = favs.some((f) => f.k === k)
    const next = exists
      ? favs.filter((f) => f.k !== k)
      : [...favs, { k, name: itemName(item), logo: itemLogo(item), type: tab }]
    setFavs(next)
    localStorage.setItem(FAV_KEY, JSON.stringify(next))
  }

  const playItem = async (item) => {
    setError('')
    const id = itemId(item)
    const t = showFavs ? item.type : tab
    try {
      if (t === 'live') {
        return doPlay(itemName(item), itemLogo(item), () => api.liveUrl(id), true, { key: `live:${id}`, type: 'live', id })
      }
      if (t === 'vod') {
        const ext = (item.container_extension || 'mp4').toLowerCase()
        return doPlay(itemName(item), itemLogo(item), () => ({ url: api.vodStream(id, ext) }), false, { key: `vod:${id}`, type: 'vod', id, ext })
      }
      if (t === 'series') return openSeries(item)
    } catch (e) {
      setError(e.message)
    }
  }

  const openSeries = async (item) => {
    setError('')
    const id = itemId(item)
    try {
      const info = await api.seriesInfo(id)
      setModal({ ...info, _id: id, _name: itemName(item), _logo: itemLogo(item) })
    } catch (e) {
      setError(e.message)
    }
  }

  const playEpisode = async (ep) => {
    setError('')
    try {
      const title = cleanName(ep.info?.title || ep.title || `Capítulo ${ep.episode_num ?? ''}`.trim())
      const name = `${cleanName(modal?.info?.name) || modal?._name || 'Serie'} — ${title}`
      const logo = modal?.info?.cover || modal?._logo
      const direct = ep.direct_source || ep.stream_url
      if (direct) {
        setModal(null)
        setCurrent({ name, logo, url: direct, start: 0, histKey: `ser:${ep.id}` })
        saveHist({ key: `ser:${ep.id}`, type: 'series', id: ep.id, name, logo, url: direct, isLive: false, ext: '', position: 0, duration: null, ts: Date.now() })
        return
      }
      const ext = (ep.container_extension || 'mp4').toLowerCase()
      const url = api.episodeStream(ep.id, ext)
      setModal(null)
      setCurrent({ name, logo, url, start: 0, histKey: `ser:${ep.id}` })
      saveHist({ key: `ser:${ep.id}`, type: 'series', id: ep.id, name, logo, url, isLive: false, ext, position: 0, duration: null, ts: Date.now() })
    } catch (e) {
      setError(e.message)
    }
  }

  const resumeEntry = async (e) => {
    setError('')
    setModal(null)
    setShowHist(false)
    try {
      if (e.type === 'live') {
        return doPlay(e.name, e.logo, () => api.liveUrl(e.id), true, { key: `live:${e.id}`, type: 'live', id: e.id })
      }
      const ext = e.ext || 'mp4'
      const builder = () => ({ url: (e.type === 'series' ? api.episodeStream(e.id, ext) : api.vodStream(e.id, ext)) })
      const { url } = await builder()
      const entry = { ...e, name: e.name, logo: e.logo, position: e.position || 0, duration: e.duration || null, ts: Date.now() }
      setCurrent({ name: e.name, logo: e.logo, url, isLive: false, start: entry.position, histKey: entry.key })
      saveHist(entry)
    } catch (err) {
      setError(err.message)
    }
  }

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    let list
    if (showHist) list = hist.filter((h) => h.type !== 'live')
    else if (showFavs) list = favs
    else if (q) list = allItems.length ? allItems : items
    else list = items
    if (!q) return list
    return list.filter((i) => itemName(i).toLowerCase().includes(q))
  }, [items, favs, hist, allItems, search, showFavs, showHist])

  // Búsqueda global: al escribir, carga el catálogo completo de la pestaña (si aún no está).
  useEffect(() => {
    if (!search.trim() || showHist || allItems.length) return
    let on = true
    TABS[tab].all().then((l) => { if (on) setAllItems(l) }).catch(() => {})
    return () => { on = false }
  }, [search, tab, showHist, allItems.length])

  const removeHist = (key) => {
    histRef.current = histRef.current.filter((h) => h.key !== key)
    setHist(histRef.current)
    localStorage.setItem(HIST_KEY, JSON.stringify(histRef.current))
  }

  const videoHandlers = {
    onPlaying: () => {
      setPlayState('playing')
      if (isMobile && !autoFsRef.current) {
        autoFsRef.current = true
        try {
          const v = videoRef.current
          if (v?.requestFullscreen) { const p = v.requestFullscreen(); if (p && p.catch) p.catch(() => {}) }
          else if (v?.webkitEnterFullscreen) v.webkitEnterFullscreen()
        } catch { /* noop */ }
      }
    },
    onWaiting: () => setPlayState('connecting'),
    onStalled: () => setPlayState('connecting'),
    onPause: () => persistPos(),
    onEnded: () => {
      const c = currentRef.current
      if (c?.histKey && !c.isLive) {
        const prev = histRef.current.find((h) => h.key === c.histKey)
        if (prev) saveHist({ ...prev, position: 0, ts: Date.now() })
      }
      setPlayState('ended')
    },
    onTimeUpdate: () => {
      const now = Date.now()
      if (now - lastSaveRef.current > 5000) {
        lastSaveRef.current = now
        persistPos()
      }
    }
  }

  return (
    <div className="app">
      <header>
        <div className="brand">
          <span className="brand-dot" />
          TV PARA POBRES
        </div>

        <button className="sidebtn" onClick={() => setSideOpen((v) => !v)} aria-label="Categorías">
          ☰
        </button>

        <nav>
          {Object.entries(TABS).map(([k, t]) => (
            <button key={k} className={`navbtn ${tab === k ? 'active' : ''}`} onClick={() => setTab(k)}>
              {t.label}
            </button>
          ))}
        </nav>

        <div className="hright">
          <a
            className="donatebtn"
            href="https://tvparapobres.tipsterpage.com/kttCMjB2"
            target="_blank"
            rel="noreferrer"
            title="Apoyar el proyecto"
          >
            ♥ Apoyar
          </a>
          <button
            className={`histbtn ${showHist ? 'active' : ''}`}
            onClick={() => {
              setShowHist((v) => !v)
              if (!showHist) setShowFavs(false)
            }}
            title="Historial"
          >
            <span className="histicon">◷</span> {hist.length}
          </button>
          <button
            className={`favbtn ${showFavs ? 'active' : ''}`}
            onClick={() => {
              setShowFavs((v) => !v)
              if (!showFavs) setShowHist(false)
            }}
          >
            <span className="star">★</span> {favs.length}
          </button>
          <input
            className="search"
            placeholder="Buscar…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </header>

      <div className="body">
        <aside className={sideOpen ? 'open' : ''}>
          <div className="asidetitle">{TABS[tab].sub}</div>
          {loadingCats ? (
            <div className="spinner" />
          ) : (
            <>
              <button
                className={`catbtn ${activeCat?.__all__ ? 'active' : ''}`}
                onClick={() => selectCat(ALL_CAT)}
              >
                Todos
              </button>
              {cats.map((c) => (
                <button
                  key={c.category_id}
                  className={`catbtn ${!activeCat?.__all__ && activeCat?.category_id === c.category_id ? 'active' : ''}`}
                  onClick={() => selectCat(c)}
                >
                  {cleanName(c.category_name)}
                </button>
              ))}
            </>
          )}
        </aside>
        <div className={`sideback ${sideOpen ? 'on' : ''}`} onClick={() => setSideOpen(false)} />

        <main>
          {current && (
            <div className="playerwrap" ref={wrapRef}>
              <div className="playerinfo">
                <span className={`chip ${current.isLive ? 'live' : ''}`}>
                  <span className="pulse" /> {current.isLive ? 'EN VIVO' : 'REPRODUCIENDO'}
                </span>
                <div className="ptitle">{current.name}</div>
                <button
                  className="fsbtn"
                  title="Pantalla completa"
                  onClick={() => videoRef.current?.requestFullscreen()}
                >
                  ⛶
                </button>
              </div>
              <div className="player">
                <video ref={videoRef} controls playsInline {...videoHandlers} />
                {(playState === 'idle' || playState === 'loading' || playState === 'connecting' || playState === 'notice') && (
                  <div className="overlay">
                    <div className="connect">
                      <div className="connect-dots">
                        <i style={{ animationDelay: '0ms' }} />
                        <i style={{ animationDelay: '140ms' }} />
                        <i style={{ animationDelay: '280ms' }} />
                      </div>
                      <div className="connect-title">Conectando a los servidores</div>
                      <div className="connect-sub">
                        {playState === 'notice' ? playMsg : 'preparando la señal, esto puede tardar unos segundos'}
                      </div>
                    </div>
                  </div>
                )}
                {playState === 'error' && (
                  <div className="overlay err">
                    <span className="big">⚠</span>
                    <span>{playMsg}</span>
                    {current?.url && <a href={current.url} target="_blank" rel="noreferrer">Abrir enlace directo</a>}
                  </div>
                )}
              </div>
            </div>
          )}

          <div className="gridhead">
            <span className="gridtitle">
              {showHist ? 'Historial' : showFavs ? 'Favoritos' : cleanName(activeCat?.category_name || '…')}
            </span>
            <span className="count">{filtered.length}</span>
          </div>

          {loadingItems && !showHist ? (
            <div className="centerload"><div className="spinner" /></div>
          ) : filtered.length === 0 ? (
            <div className="centerload empty">
              {showHist
                ? 'Aún no hay nada en tu historial.'
                : showFavs
                  ? 'Aún no tienes favoritos. Toca ★ en un canal para guardarlo.'
                  : 'Sin resultados.'}
            </div>
          ) : showHist ? (
            <div className="grid">
              {filtered.map((h) => {
                const pct = h.duration ? Math.min(100, Math.round((h.position / h.duration) * 100)) : null
                return (
                  <button key={h.key} className="card hcard" onClick={() => resumeEntry(h)}>
                    <span className="cardlogo">
                      {h.logo ? (
                        <img src={imgProxy(h.logo)} alt="" loading="lazy" onError={(e) => { e.currentTarget.style.display = 'none' }} />
                      ) : null}
                    </span>
                    <span className="cardname">{h.name}</span>
                    <span className="hmeta">
                      {pct != null && (
                        <span className="hbar"><span className="hfill" style={{ width: `${pct}%` }} /></span>
                      )}
                      <span className="hcont">
                        {h.type === 'live' ? (
                          <span className="hlive">● Canal</span>
                        ) : (
                          <span className="hresume">
                            ▶ Continuar{h.position > 0 ? ` ${fmtTime(h.position)}` : ''}
                            {h.duration ? ` / ${fmtTime(h.duration)}` : ''}
                          </span>
                        )}
                        <span
                          className="hdel"
                          title="Quitar del historial"
                          onClick={(e) => { e.stopPropagation(); removeHist(h.key) }}
                        >
                          ✕
                        </span>
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          ) : (
            <div className="grid">
              {filtered.map((item, i) => (
                <button
                  key={`${itemId(item)}-${i}`}
                  className="card"
                  onClick={() => playItem(item)}
                >
                  <span
                    className={`cardfav ${isFav(item) ? 'on' : ''}`}
                    onClick={(e) => { e.stopPropagation(); toggleFav(item) }}
                    title={isFav(item) ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                  >
                    ★
                  </span>
                  <span className="cardlogo">
                    {itemLogo(item) ? (
                      <img src={imgProxy(itemLogo(item))} alt="" loading="lazy" onError={(e) => { e.currentTarget.style.display = 'none' }} />
                    ) : null}
                  </span>
                  <span className="cardname">{itemName(item)}</span>
                </button>
              ))}
            </div>
          )}
        </main>
      </div>

      {error && <div className="toast" onClick={() => setError('')}>{error}</div>}

      {modal && (
        <SeriesModal info={modal} onClose={() => setModal(null)} onPlay={playEpisode} current={current} />
      )}

      {serviceDown && (
        <div className="servlock">
          <div className="serv-icon">📡</div>
          <div className="serv-brand" />
          <div className="serv-ring" />
          <div className="serv-title">Sin servicio por el momento</div>
          <div className="serv-sub">
            Nuestro equipo está realizando mantenimiento. El contenido volverá a estar disponible en unos minutos. ¡Gracias por tu paciencia!
          </div>
          <div className="serv-hint">Reintentando automáticamente en unos segundos…</div>
        </div>
      )}

      {expired && (
        <div className="servlock">
          <div className="serv-brand" />
          <div className="serv-ring" />
          <div className="serv-title">Actualizando servidores</div>
          <div className="serv-sub">
            Estamos actualizando los servidores para seguir disfrutando de contenido gratis. Gracias por tu paciencia.
          </div>
        </div>
      )}
    </div>
  )
}

function normalizeSeasons(info) {
  const eps = info.episodes
  let seasons = []
  if (Array.isArray(eps)) {
    if (eps.length && typeof eps[0] === 'object' && 'episodes' in eps[0]) {
      seasons = eps.map((s) => ({ season: s.season ?? s.name ?? '0', episodes: s.episodes }))
    } else if (eps.length) {
      seasons = [{ season: '0', episodes: eps }]
    }
  } else if (eps && typeof eps === 'object') {
    seasons = Object.entries(eps).map(([k, v]) => ({ season: k, episodes: v }))
  }
  return seasons.filter((s) => s.episodes?.length)
}

function SeriesModal({ info, onClose, onPlay, current }) {
  const seasons = useMemo(() => normalizeSeasons(info), [info])
  const [openSeason, setOpenSeason] = useState(seasons[0]?.season ?? null)

  const meta = info.info || {}

  return (
    <div className="modalback" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <button className="modalclose" onClick={onClose}>✕</button>
        <div className="modalhead">
          <img className="poster" src={imgProxy(meta.cover || meta.poster || info._logo)} alt="" onError={(e) => (e.currentTarget.style.display = 'none')} />
          <div className="modalmeta">
            <h2>{cleanName(meta.name) || info._name}</h2>
            <div className="meta-row">
              {meta.releaseDate || meta.year ? <span>{meta.releaseDate || meta.year}</span> : null}
              {meta.rating ? <span className="rate">★ {meta.rating}/10</span> : null}
              {meta.genre ? <span>{Array.isArray(meta.genre) ? meta.genre.join(', ') : meta.genre}</span> : null}
            </div>
            {meta.plot && <p className="plot">{meta.plot}</p>}
            {meta.cast && <p className="cast">{meta.cast}</p>}
          </div>
        </div>

        {seasons.length === 0 ? (
          <div className="epempty">No hay episodios disponibles.</div>
        ) : (
          seasons.map((s) => {
            const metaForSeason = (info.seasons || []).find((m) => String(m.season_number) === String(s.season))
            const label = s.season === '0'
              ? 'Episodios'
              : cleanName(metaForSeason?.name) || (s.season === '1' ? 'Temporada 1' : `Temporada ${s.season}`)
            const open = String(openSeason) === String(s.season)
            return (
              <div key={s.season} className="season">
                <button className="seasonhead" onClick={() => setOpenSeason(open ? null : s.season)}>
                  {label}
                  <span className="seas-toggle">{open ? '▾' : '▸'}</span>
                </button>
                {open && (
                  <div className="episodes">
                    {s.episodes.map((ep) => {
                      const t = cleanName(ep.info?.title || ep.title)
                      const dur = ep.info?.duration
                      return (
                        <button key={ep.id ?? t} className="ep" onClick={() => onPlay(ep)}>
                          <span className="epnum">{String(ep.episode_num ?? '').padStart(2, '0')}</span>
                          <span className="eptitle">{t || `Capítulo ${ep.episode_num}`}</span>
                          {dur && <span className="epdur">{dur}</span>}
                          <span className="epplay">▶</span>
                        </button>
                      )
                    })}
                  </div>
                )}
              </div>
            )
          })
        )}
      </div>
    </div>
  )
}