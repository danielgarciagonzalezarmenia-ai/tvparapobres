import { useEffect, useMemo, useRef, useState } from 'react'
import { api, isServiceDown, itemId, itemName, itemLogo, imgProxy, cleanName, fmtTime, normName, NATIVE_EXTS } from './api.js'
import { attachPlayer, playURL, stopPlayback, getPlaybackState, onPlayerEvent } from './player.js'
import {
  PALETTE, AVATAR_COUNT, newId, loadProfiles, saveProfiles, activeProfileId,
  setActiveProfileId, applyTheme, namespaced
} from './profiles.js'
import Avatar from './Avatar.jsx'
import brandLogo from '../assets/logo_proyecto.png'

/* Iconos inline — sin dependencias externas, portables al APK */
const Ico = {
  menu:      <svg viewBox="0 0 24 24"><rect y="5.3" width="24" height="2.4" rx="1.2"/><rect y="10.8" width="24" height="2.4" rx="1.2"/><rect y="16.3" width="24" height="2.4" rx="1.2"/></svg>,
  search:    <svg viewBox="0 0 24 24"><path d="M10.5 3a7.5 7.5 0 1 0 4.64 13.36l4.25 4.25a1.1 1.1 0 0 0 1.56-1.56l-4.25-4.25A7.5 7.5 0 0 0 10.5 3zm0 2.2a5.3 5.3 0 1 1 0 10.6 5.3 5.3 0 0 1 0-10.6z"/></svg>,
  heart:     <svg viewBox="0 0 24 24"><path d="M12 21s-6.7-4.35-9.33-8.65C.9 9.6 2.4 5.6 6 4.6c2.05-.57 4.12.14 5.5 1.7a1.2 1.2 0 0 0 1 0A7.13 7.13 0 0 1 18 4.6c3.6 1 5.1 5 3.33 7.75C18.7 16.65 12 21 12 21z"/></svg>,
  star:      <svg viewBox="0 0 24 24"><path d="M12 3.6l2.56 5.05 5.78.78-4.24 4.05 1.08 5.7L12 16.7l-5.18 2.48 1.08-5.7L3.66 9.43l5.78-.78L12 3.6z"/></svg>,
  clock:     <svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zm0 2a7 7 0 1 1 0 14 7 7 0 0 1 0-14zm0 2a1 1 0 0 0-1 1v4a1 1 0 0 0 .3.7l3 3a1 1 0 0 0 1.4-1.4l-2.7-2.7V8a1 1 0 0 0-1-1z"/></svg>,
  fullscreen:<svg viewBox="0 0 24 24"><path d="M4 4h5v2H6v3H4V4zm11 0h5v5h-2V6h-3V4zM4 15h2v3h3v2H4v-5zm14 0h2v5h-5v-2h3v-3z"/></svg>,
  close:     <svg viewBox="0 0 24 24"><path d="M5.6 4.6L12 11 18.4 4.6l1.4 1.4L13.4 12l6.4 6.4-1.4 1.4L12 13.4 5.6 19.8 4.2 18.4 10.6 12 4.2 5.6z"/></svg>,
  pencil:    <svg viewBox="0 0 24 24"><path d="M4.5 19.5l.9-3.1 9.7-9.7a1.7 1.7 0 0 1 2.4 0l.8.8a1.7 1.7 0 0 1 0 2.4l-9.7 9.7-3.1.9zm9.7-11.9l-2.3 2.3 2 2 2.3-2.3z"/></svg>,
  chevL:     <svg viewBox="0 0 24 24"><path d="M14.6 5.6L8.2 12l6.4 6.4-1.6 1.4L5.2 12l5.8-6 1.6-1.4z"/></svg>,
  chevR:     <svg viewBox="0 0 24 24"><path d="M9.4 5.6L15.8 12 9.4 18.4l1.6 1.4L18.8 12l-5.8-6L9.4 5.6z"/></svg>,
  live:      <svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 0 0-6.36 15.36l1.42-1.42A7 7 0 1 1 12 5a7 7 0 0 1 4.94 11.94l1.42 1.42A9 9 0 0 0 12 3z"/><circle cx="12" cy="13" r="3"/><path d="M12 16l4 5h-8z"/></svg>,
  film:      <svg viewBox="0 0 24 24"><path d="M4 5a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2H4zm0 2h2v2H4V7zm4 0h3v2H8V7zm5 0h3v2h-3V7zm5 0h2v2h-2V7zM4 12h2v2H4v-2zm4 0h3v2H8v-2zm5 0h3v2h-3v-2zm5 0h2v2h-2v-2zm-16 5v-2h2v2H4zm4-2h3v2H8v-2zm5 0h3v2h-3v-2zm5 0h2v2h-2v-2z"/></svg>,
  series:    <svg viewBox="0 0 24 24"><path d="M4 5h16a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2zm0 5h10a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2zm0 5h7a1 1 0 0 1 0 2H4a1 1 0 0 1 0-2zm4-4l4 3-4 3V11z"/></svg>,
}

const NAV_ICONS = { live: Ico.live, vod: Ico.film, series: Ico.series }

const TABS = {
  live: { label: 'LIVE', sub: 'Canales', loadCats: api.liveCategories, loadItems: api.liveStreams, all: api.liveAll },
  vod: { label: 'PELÍCULAS', sub: 'Películas', loadCats: api.vodCategories, loadItems: api.vodStreams, all: api.vodAll },
  series: { label: 'SERIES', sub: 'Series', loadCats: api.seriesCategories, loadItems: api.series, all: api.seriesAll }
}

const ALL_CAT = { category_id: '__all__', category_name: 'Todos', __all__: true }

const isMobile = typeof window !== 'undefined' && /Android|iPhone|iPad|iPod|Mobi/i.test(navigator.userAgent)

const MAX_HIST = 120
const PAGE = 240

const EXPIRE_DATE = '2026-09-21'

function todayStr() {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}

const isExpired = () => todayStr() >= EXPIRE_DATE

const canonicId = (h) => {
  const id = h && h.id
  if (id === undefined || id === null || id === '') return null
  return `${h.type}:${id}`
}

const alphaCmp = (a, b) =>
  itemName(a).localeCompare(itemName(b), 'es', { numeric: true, sensitivity: 'base' })

function loadList(key, max) {
  try {
    const arr = JSON.parse(localStorage.getItem(key) || '[]')
    if (!Array.isArray(arr)) return []
    if (!max) return arr
    return arr.slice(0, max)
  } catch { return [] }
}

function normalizeHistory(arr) {
  if (!Array.isArray(arr)) return []
  const best = new Map()
  for (const h of arr) {
    if (!h || typeof h !== 'object') continue
    const k = canonicId(h) || h.key
    if (!k) continue
    const prev = best.get(k)
    if (!prev || (h.ts || 0) > (prev.ts || 0)) best.set(k, h)
  }
  return [...best.values()].sort((a, b) => (b.ts || 0) - (a.ts || 0)).slice(0, MAX_HIST)
}

const parseEpgTime = (v) => {
  const n = Number(v)
  if (isFinite(n)) return n
  const m = typeof v === 'string' ? v.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2})(?::(\d{2}))?/) : null
  if (!m) return NaN
  return new Date(+m[1], +m[2] - 1, +m[3], +m[4], +m[5], +(m[6] || 0)).getTime() / 1000
}

const epgTitle = (t) => {
  const s = String(t || '').trim()
  if (s.length >= 8 && s.length % 4 === 0 && /^[A-Za-z0-9+/]+={0,2}$/.test(s)) {
    try {
      const dec = new TextDecoder().decode(Uint8Array.from(atob(s), (c) => c.charCodeAt(0))).trim()
      if (dec.length >= 4 && !/[^\x09\x0A\x0D\x20-\x7E]/.test(dec) && /[\sÁÉÍÓÚÑáéíóúñ]/.test(dec)) return dec
    } catch { /* devolver título original */ }
  }
  return s
}

function computeEpg(res) {
  const list = Array.isArray(res?.epg_listings) ? res.epg_listings : []
  const now = Date.now() / 1000
  let progNow = null
  let progNext = null
  let bestNext = Infinity
  for (const l of list) {
    const s = parseEpgTime(l.start)
    const e = parseEpgTime(l.end)
    if (!isFinite(s)) continue
    if (s <= now && now < e) { progNow = l; continue }
    if (s > now && s < bestNext) { bestNext = s; progNext = l }
  }
  const fmt = (t) => new Date(t * 1000).toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' })
  const range = (l) => {
    const s = parseEpgTime(l.start)
    const e = parseEpgTime(l.end)
    return `${isFinite(s) ? fmt(s) : ''}${isFinite(e) ? ' – ' + fmt(e) : ''}`
  }
  return {
    now: progNow ? { title: cleanName(epgTitle(progNow.title)) || 'Sin título', at: range(progNow) } : null,
    next: progNext ? { title: cleanName(epgTitle(progNext.title)) || 'Sin título', at: fmt(parseEpgTime(progNext.start)) } : null
  }
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
  const [favs, setFavs] = useState([])
  const [hist, setHist] = useState([])
  const [showFavs, setShowFavs] = useState(false)
  const [showHist, setShowHist] = useState(false)
  const [playState, setPlayState] = useState('idle')
  const [playMsg, setPlayMsg] = useState('')
  const [error, setError] = useState('')
  const [modal, setModal] = useState(null)
  const [serviceDown, setServiceDown] = useState(false)
  const [page, setPage] = useState(1)
  const [epg, setEpg] = useState(null)
  const [profile, setProfile] = useState(null)
  const [gate, setGate] = useState(null)
  const videoRef = useRef(null)
  const wrapRef = useRef(null)
  const lastUrl = useRef(null)
  const histRef = useRef([])
  const currentRef = useRef(null)
  const lastSaveRef = useRef(0)
  const autoFsRef = useRef(false)
  const liveRef = useRef({ list: [], index: -1 })
  const profRef = useRef(null)

  useEffect(() => { currentRef.current = current }, [current])
  useEffect(() => { profRef.current = profile?.id || null }, [profile])
  useEffect(() => { histRef.current = hist }, [hist])

  const histStoreKey = () => namespaced('tvp_hist', profRef.current)
  const favStoreKey = () => namespaced('tvp_favs', profRef.current)

  /* ----- Boot de perfiles: siempre arranca en el selector ----- */
  useEffect(() => {
    const profiles = loadProfiles()
    if (profiles.length === 0) { setGate({ mode: 'welcome' }); return }
    setGate({ mode: 'pick' })
  }, [])

  const unlock = (p) => {
    setActiveProfileId(p.id)
    applyTheme(p)
    setProfile(p)
    setFavs(loadList(namespaced('tvp_favs', p.id)))
    const h = normalizeHistory(loadList(namespaced('tvp_hist', p.id)))
    histRef.current = h
    setHist(h)
    setGate(null)
  }

  const handleCreate = (data) => {
    const profiles = loadProfiles()
    const p = { id: newId(), name: data.name, color: data.color, avatar: data.avatar, pin: data.pin, photo: data.photo || '' }
    saveProfiles([...profiles, p])
    unlock(p)
  }

  const handlePick = (p) => {
    if (p.pin) { setGate({ mode: 'pin', pin: p }); return }
    unlock(p)
  }

  const handleDelete = (p) => {
    const profiles = loadProfiles()
    saveProfiles(profiles.filter((x) => x.id !== p.id))
    setGate((g) => ({ ...g, bump: (g?.bump || 0) + 1 }))
  }

  const handleEditStart = (p) => setGate({ mode: 'edit', edit: p, fromApp: false })

  const handleEdited = (data, id) => {
    const profiles = loadProfiles()
    const updated = { ...(profiles.find((x) => x.id === id) || {}), name: data.name, color: data.color, avatar: data.avatar, pin: data.pin, photo: data.photo || '' }
    saveProfiles(profiles.map((x) => (x.id === id ? updated : x)))
    if (profile?.id === id) unlock(updated)
    else setGate((g) => ({ ...g, mode: 'pick', bump: (g?.bump || 0) + 1 }))
  }

  const goPicker = () => {
    closePlayer()
    setGate({ mode: 'pick', fromApp: true })
  }

  /* ----- Datos y catálogo ----- */
  useEffect(() => {
    api.meta()
      .then((m) => { if (m && m.expire) setExpired(todayStr() >= String(m.expire).slice(0, 10)) })
      .catch(() => {})
  }, [])

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
    setPage(1)
    closePlayer()
    try {
      if (tab === 'live') {
        const list = await TABS.live.all()
        const sorted = [...list].sort(alphaCmp)
        liveRef.current = { list: sorted, index: -1 }
        setCats([])
        setActiveCat(ALL_CAT)
        setItems(sorted)
        setAllItems(sorted)
        setLoadingCats(false)
        return
      }
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
    localStorage.setItem(histStoreKey(), JSON.stringify(histRef.current))
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
    const id = meta.id ?? ''
    const key = meta.key || `live:${id}`
    const entry = {
      key, type: meta.type || 'live', id, name, logo, url,
      isLive, ext: meta.ext || '', position: meta.start || 0,
      duration: meta.duration || null, ts: Date.now()
    }
    setCurrent({ id, name, logo, url, isLive, start: entry.position, histKey: key })
    if (!isLive) saveHist(entry)
    return entry
  }

  const selectCat = async (cat) => {
    setActiveCat(cat)
    setShowFavs(false)
    setShowHist(false)
    setSideOpen(false)
    setPage(1)
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

  const isFav = (item) => favs.some((f) => f.id !== undefined && String(f.id) === String(itemId(item)))

  const toggleFav = (item) => {
    const id = String(itemId(item))
    const exists = favs.some((f) => f.id !== undefined && String(f.id) === id)
    const next = exists
      ? favs.filter((f) => !(f.id !== undefined && String(f.id) === id))
      : [...favs, {
          id, name: itemName(item), logo: itemLogo(item), ext: item.container_extension || '',
          type: showFavs ? item.type : tab
        }]
    setFavs(next)
    localStorage.setItem(favStoreKey(), JSON.stringify(next))
  }

  const typeOf = (item) => (showFavs ? item.type : tab)

  const playItem = async (item) => {
    setError('')
    const id = itemId(item)
    const t = typeOf(item)
    try {
      if (t === 'live') {
        if (!liveRef.current.list.length) {
          try {
            const list = await api.liveAll()
            liveRef.current = { list: [...list].sort(alphaCmp), index: -1 }
          } catch { /* noop */ }
        }
        const idx = liveRef.current.list.findIndex((x) => String(itemId(x)) === String(id))
        if (idx >= 0) liveRef.current.index = idx
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

  const switchChannel = async (d) => {
    const cur = currentRef.current
    if (!cur?.isLive) return
    let lr = liveRef.current
    if (!lr.list.length) {
      try {
        const list = await api.liveAll()
        lr = { list: [...list].sort(alphaCmp), index: -1 }
        liveRef.current = lr
      } catch { return }
    }
    let idx = lr.index
    if (idx < 0) {
      idx = lr.list.findIndex((x) => String(itemId(x)) === String(cur.id))
      if (idx < 0) return
    }
    const next = (idx + d + lr.list.length) % lr.list.length
    lr.index = next
    const item = lr.list[next]
    await playItem(item)
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
        setCurrent({ id: ep.id, name, logo, url: direct, start: 0, histKey: `ser:${ep.id}` })
        saveHist({ key: `ser:${ep.id}`, type: 'series', id: ep.id, name, logo, url: direct, isLive: false, ext: '', position: 0, duration: null, ts: Date.now() })
        return
      }
      const ext = (ep.container_extension || 'mp4').toLowerCase()
      const url = api.episodeStream(ep.id, ext)
      setModal(null)
      setCurrent({ id: ep.id, name, logo, url, start: 0, histKey: `ser:${ep.id}` })
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
      setCurrent({ id: e.id, name: e.name, logo: e.logo, url, isLive: false, start: entry.position, histKey: entry.key })
      saveHist(entry)
    } catch (err) {
      setError(err.message)
    }
  }

  const filtered = useMemo(() => {
    const q = normName(search.trim())
    let list
    if (showHist) list = hist.filter((h) => h.type !== 'live')
    else if (showFavs) list = favs
    else if (q) list = allItems.length ? allItems : items
    else list = items
    if (!q) return list
    return list.filter((i) => normName(itemName(i)).includes(q))
  }, [items, favs, hist, allItems, search, showFavs, showHist])

  useEffect(() => {
    if (!search.trim() || showHist || allItems.length) return
    let on = true
    TABS[tab].all().then((l) => { if (on) setAllItems(l) }).catch(() => {})
    return () => { on = false }
  }, [search, tab, showHist, allItems.length])

  useEffect(() => { setPage(1) }, [tab, activeCat?.category_id, search, showHist, showFavs])

  const shown = useMemo(() => filtered.slice(0, page * PAGE), [filtered, page])

  /* ----- EPG del canal actual ----- */
  useEffect(() => {
    if (!current?.isLive || !current?.id) { setEpg(null); return }
    let on = true
    const load = () => api.epg(current.id).then((l) => { if (on) setEpg(computeEpg(l)) }).catch(() => { if (on) setEpg(null) })
    load()
    const iv = setInterval(load, 30000)
    return () => { on = false; clearInterval(iv) }
  }, [current?.isLive, current?.id])

  const removeHist = (key) => {
    histRef.current = histRef.current.filter((h) => h.key !== key)
    setHist(histRef.current)
    localStorage.setItem(histStoreKey(), JSON.stringify(histRef.current))
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

  const gridTitle = showHist ? 'Historial' : showFavs ? 'Favoritos'
    : tab === 'live' ? `Canales` : cleanName(activeCat?.category_name || '...')

  return (
    <div className="app">
      <header className="topbar">
        {profile && (
          <button className="prochip" onClick={goPicker} title="Cambiar de perfil">
            <Avatar variant={profile.avatar} color={profile.color} size={30} photo={profile.photo} />
          </button>
        )}

        {tab !== 'live' && (
          <button className="sidebtn" onClick={() => setSideOpen((v) => !v)} aria-label="Categorías">
            {Ico.menu}
          </button>
        )}

        <nav>
          {Object.entries(TABS).map(([k, t]) => (
            <button key={k} className={`navbtn ${tab === k ? 'active' : ''}`} onClick={() => setTab(k)}>
              {NAV_ICONS[k]} {t.label}
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
            {Ico.heart} Apoyar
          </a>
          <button
            className={`favbtn ${showFavs ? 'active' : ''}`}
            onClick={() => { setShowFavs((v) => !v); if (!showFavs) setShowHist(false) }}
            title="Favoritos"
          >
            {Ico.star} <span className="tok">{favs.length}</span>
          </button>
          <button
            className={`histbtn ${showHist ? 'active' : ''}`}
            onClick={() => { setShowHist((v) => !v); if (!showHist) setShowFavs(false) }}
            title="Historial"
          >
            {Ico.clock} <span className="tok">{hist.length}</span>
          </button>
          <div className="searchwrap">
            {Ico.search}
            <input
              className="search"
              placeholder={tab === 'live' ? 'Buscar canal...' : 'Buscar...'}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
        </div>
      </header>

      <div className={`body ${tab === 'live' ? 'flat' : ''}`}>
        {tab !== 'live' && (
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
        )}
        {tab !== 'live' && <div className={`sideback ${sideOpen ? 'on' : ''}`} onClick={() => setSideOpen(false)} />}

        <main>
          {current && (
            <div className="playerwrap" ref={wrapRef}>
              <div className="playerinfo">
                <span className={`chip ${current.isLive ? 'live' : ''}`}>
                  <span className="pulse" /> {current.isLive ? 'EN VIVO' : 'REPRODUCIENDO'}
                </span>
                <div className="ptitle">{current.name}</div>
                {current.isLive && (
                  <span className="chpos">
                    {liveRef.current.index >= 0 ? `${liveRef.current.index + 1} / ${liveRef.current.list.length}` : ''}
                  </span>
                )}
                {current.isLive && (
                  <span className="chnav">
                    <button className="chbtn" title="Canal anterior" onClick={() => switchChannel(-1)}>
                      {Ico.chevL}
                    </button>
                    <button className="chbtn" title="Canal siguiente" onClick={() => switchChannel(1)}>
                      {Ico.chevR}
                    </button>
                  </span>
                )}
                <button
                  className="fsbtn"
                  title="Pantalla completa"
                  onClick={() => videoRef.current?.requestFullscreen()}
                >
                  {Ico.fullscreen}
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
                    {playState === 'error' && !current?.isLive && current?.url && (
                      <a href={current.url} target="_blank" rel="noreferrer">Abrir enlace directo</a>
                    )}
                  </div>
                )}
              </div>
              {epg && (epg.now || epg.next) && current?.isLive && (
                <div className="epgbar">
                  {epg.now && (
                    <span className="epg-item">
                      <span className="epg-tag now">Ahora</span>
                      <span className="epg-title">{epg.now.title}</span>
                      <span className="epg-at">{epg.now.at}</span>
                    </span>
                  )}
                  {epg.next && (
                    <span className="epg-item">
                      <span className="epg-tag">Después</span>
                      <span className="epg-title">{epg.next.title}</span>
                      <span className="epg-at">{epg.next.at}</span>
                    </span>
                  )}
                </div>
              )}
            </div>
          )}

          <div className="gridhead">
            <span className="gridtitle">{gridTitle}</span>
            <span className="count">{filtered.length}</span>
          </div>

          {loadingItems && !showHist ? (
            <div className="skelgrid">
              {Array.from({ length: 12 }).map((_, i) => (
                <div key={i} className="skel-card">
                  <div className="skel-img" />
                  <div className="skel-line" style={{ width: `${48 + (i % 4) * 14}%` }} />
                </div>
              ))}
            </div>
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
              {shown.map((h) => {
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
                          {Ico.close}
                        </span>
                      </span>
                    </span>
                  </button>
                )
              })}
            </div>
          ) : (
            <div className="grid">
              {shown.map((item, i) => (
                <button
                  key={`${itemId(item)}-${i}`}
                  className="card"
                  style={{ animationDelay: `${Math.min(i, 24) * 30}ms` }}
                  onClick={() => playItem(item)}
                >
                  <span
                    className={`cardfav ${isFav(item) ? 'on' : ''}`}
                    onClick={(e) => { e.stopPropagation(); toggleFav(item) }}
                    title={isFav(item) ? 'Quitar de favoritos' : 'Agregar a favoritos'}
                  >
                    {Ico.star}
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

          {filtered.length > shown.length && (
            <button className="morebtn" onClick={() => setPage((p) => p + 1)}>
              Ver más ({filtered.length - shown.length}) ↓
            </button>
          )}
        </main>
      </div>

      {gate && (
        <ProfileGate
          gate={gate}
          onGate={setGate}
          onPick={handlePick}
          onDelete={handleDelete}
          onCreated={handleCreate}
          onEdited={handleEdited}
          onEdit={handleEditStart}
          onPinOk={unlock}
        />
      )}

      {error && <div className="toast" onClick={() => setError('')}>{error}</div>}

      {modal && (
        <SeriesModal info={modal} onClose={() => setModal(null)} onPlay={playEpisode} current={current} />
      )}

      {serviceDown && (
        <div className="servlock">
          <div className="serv-brand">
            <svg viewBox="0 0 24 24"><rect x="2" y="6" width="20" height="13" rx="3.5" fill="#fff"/><rect x="5" y="9.5" width="14" height="7" rx="1.8" fill="var(--accent, #ff4d2e)"/><path d="M9.5 19h5l-1.2 2.5H10.7z" fill="#fff"/></svg>
          </div>
          <div className="serv-ring" />
          <div className="serv-title">Sin servicio por el momento</div>
          <div className="serv-sub">
            Nuestro equipo está realizando mantenimiento. El contenido volverá a estar disponible en unos minutos. ¡Gracias por tu paciencia!
          </div>
          <div className="serv-hint">Reintentando automáticamente en unos segundos...</div>
        </div>
      )}

      {expired && (
        <div className="servlock">
          <div className="serv-brand">
            <svg viewBox="0 0 24 24"><rect x="2" y="6" width="20" height="13" rx="3.5" fill="#fff"/><rect x="5" y="9.5" width="14" height="7" rx="1.8" fill="var(--accent, #ff4d2e)"/><path d="M9.5 19h5l-1.2 2.5H10.7z" fill="#fff"/></svg>
          </div>
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

/* ================= Perfiles ================= */

function BrandLockup() {
  return (
    <div className="lockup">
      <img className="lockup-logo" src={brandLogo} alt="TV Para Pobres" />
      <div className="lockup-sub">¿Quién está mirando?</div>
    </div>
  )
}

function ProfileSetupForm({ initial, submitLabel, onSave, onCancel }) {
  const [name, setName] = useState(initial?.name || '')
  const [color, setColor] = useState(initial?.color || PALETTE[0].color)
  const [avatar, setAvatar] = useState(initial?.avatar ?? 0)
  const [pin, setPin] = useState(initial?.pin || '')
  const [photo, setPhoto] = useState(initial?.photo || '')
  const [error, setError] = useState('')
  const fileRef = useRef(null)

  const onFile = (e) => {
    const f = e.target.files && e.target.files[0]
    e.target.value = ''
    if (!f || !/^image\//.test(f.type)) return
    const reader = new FileReader()
    reader.onload = () => {
      const img = new Image()
      img.onload = () => {
        const max = 384
        const sc = Math.min(1, max / Math.max(img.width, img.height))
        const w = Math.max(1, Math.round(img.width * sc))
        const h = Math.max(1, Math.round(img.height * sc))
        const c = document.createElement('canvas')
        c.width = w; c.height = h
        const cx = c.getContext('2d')
        cx.drawImage(img, 0, 0, w, h)
        setPhoto(c.toDataURL('image/jpeg', 0.85))
      }
      img.src = reader.result
    }
    reader.readAsDataURL(f)
  }

  const submit = () => {
    const n = name.trim()
    if (!n) { setError('Ponle un nombre a tu perfil.'); return }
    if (pin && !/^\d{1,4}$/.test(pin)) { setError('El PIN debe tener de 1 a 4 números.'); return }
    onSave({ name: n, color, avatar, pin, photo })
  }

  return (
    <div className="gatepanel">
      <div className="field gph-field">
        <label>Foto del perfil (opcional)</label>
        <div className="photo-row">
          <Avatar variant={avatar} color={color} size={64} photo={photo} />
          <div className="photo-btns">
            <input
              ref={fileRef}
              type="file"
              accept="image/*"
              style={{ display: 'none' }}
              onChange={onFile}
            />
            <button type="button" className="photobtn" onClick={() => fileRef.current && fileRef.current.click()}>
              {photo ? 'Cambiar foto' : 'Subir foto'}
            </button>
            {photo && <button type="button" className="photobtn ph-del" onClick={() => setPhoto('')}>Quitar foto</button>}
          </div>
        </div>
      </div>
      <div className="field">
        <label>Nombre del perfil</label>
        <input
          className="textfield"
          value={name}
          maxLength={18}
          placeholder="Ej. Mariana"
          onChange={(e) => setName(e.target.value)}
          autoFocus
        />
      </div>
      <div className="field">
        <label>Color del perfil</label>
        <div className="swatches">
          {PALETTE.map((p) => (
            <button
              key={p.id}
              className={`swatch ${color === p.color ? 'sel' : ''}`}
              style={{ background: p.color }}
              onClick={() => setColor(p.color)}
              title={p.name}
            />
          ))}
        </div>
      </div>
      <div className="field">
        <label>Avatar</label>
        <div className="avatars">
          {Array.from({ length: AVATAR_COUNT }).map((_, i) => (
            <button
              key={i}
              className={`avatar-btn ${avatar === i ? 'sel' : ''}`}
              onClick={() => setAvatar(i)}
            >
              <Avatar variant={i} color={color} size={34} />
            </button>
          ))}
        </div>
      </div>
      <div className="field">
        <label>PIN (opcional)</label>
        <input
          className="textfield pinfield"
          value={pin}
          inputMode="numeric"
          pattern="[0-9]*"
          maxLength={4}
          placeholder="Sin PIN = acceso libre"
          onChange={(e) => setPin(e.target.value.replace(/[^0-9]/g, ''))}
        />
      </div>
      {error && <div className="gate-error">{error}</div>}
      <div className="gate-actions">
        {onCancel && <button className="ghostbtn" onClick={onCancel}>Volver</button>}
        <button className="primarybtn" onClick={submit}>{submitLabel || 'Crear perfil'}</button>
      </div>
    </div>
  )
}

function PinPad({ label, onOk, onCancel, onCorrect }) {
  const [pin, setPin] = useState('')
  const [shake, setShake] = useState(false)

  const press = (d) => {
    if (pin.length >= 4) return
    setPin((p) => p + d)
  }

  const submit = () => {
    if (onOk(pin)) { setPin(''); onCorrect && onCorrect() }
    else {
      setShake(true); setPin('')
      setTimeout(() => setShake(false), 450)
    }
  }

  const clear = () => setPin('')

  return (
    <div className="gatepanel pinpanel">
      <div className="pin-title">{label || 'Ingresa tu PIN'}</div>
      <div className={`pindots ${shake ? 'shake' : ''}`}>
        {[0, 1, 2, 3].map((i) => (
          <span key={i} className={i < pin.length ? 'on' : ''} />
        ))}
      </div>
      <div className="keypad">
        {['1', '2', '3', '4', '5', '6', '7', '8', '9'].map((d) => (
          <button key={d} className="key" onClick={() => press(d)}>{d}</button>
        ))}
        <button className="key ghost" onClick={clear} title="Borrar">⌫</button>
        <button className="key" onClick={() => press('0')}>0</button>
        <button className="key ghost ok" onClick={submit} title="Aceptar">✓</button>
      </div>
      <div className="gate-actions">
        {onCancel && <button className="ghostbtn" onClick={onCancel}>Volver</button>}
      </div>
    </div>
  )
}

function ProfileGate({ gate, onGate, onPick, onDelete, onCreated, onEdited, onEdit, onPinOk }) {
  const profiles = useMemo(() => loadProfiles(), [gate?.bump])

  if (gate.mode === 'welcome') {
    return (
      <div className="gateback">
        <BrandLockup />
        <ProfileSetupForm onSave={(d) => onCreated(d)} submitLabel="Empezar a ver" />
      </div>
    )
  }

  if (gate.mode === 'create') {
    return (
      <div className="gateback">
        <BrandLockup />
        <ProfileSetupForm onSave={(d) => onCreated(d)} submitLabel="Guardar perfil" onCancel={() => onGate({ mode: 'pick' })} />
      </div>
    )
  }

  if (gate.mode === 'edit') {
    return (
      <div className="gateback">
        <BrandLockup />
        <ProfileSetupForm
          initial={gate.edit}
          onSave={(d) => onEdited(d, gate.edit.id)}
          submitLabel="Guardar cambios"
          onCancel={() => onGate({ mode: 'pick' })}
        />
      </div>
    )
  }

  if (gate.mode === 'pin') {
    return (
      <div className="gateback">
        <BrandLockup />
        <div className="gatepanel picker-mini">
          <Avatar variant={gate.pin.avatar} color={gate.pin.color} size={56} photo={gate.pin.photo} />
          <div className="pin-name">{gate.pin.name}</div>
        </div>
        <PinPad label={`PIN de ${gate.pin.name}`} onOk={(p) => p === gate.pin.pin} onCorrect={() => onPinOk(gate.pin)} onCancel={() => onGate({ mode: 'pick' })} />
      </div>
    )
  }

  return (
    <div className="gateback">
      <BrandLockup />
      <div className="pickgrid">
        {profiles.map((p) => (
          <div
            key={p.id}
            className="pickcard"
            role="button"
            tabIndex={0}
            onClick={() => onPick(p)}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onPick(p) } }}
          >
            <span className="pickavatar">
              <Avatar variant={p.avatar} color={p.color} size={78} photo={p.photo} />
              {p.pin && <span className="pinbadge">PIN</span>}
            </span>
            <span className="pickname">{p.name}</span>
            <button
              type="button"
              className="pickedit"
              title="Editar perfil"
              onClick={(e) => { e.stopPropagation(); onEdit(p) }}
            >
              {Ico.pencil}
            </button>
            {profiles.length > 1 && (
              <button
                type="button"
                className="pickdel"
                title="Eliminar perfil"
                onClick={(e) => { e.stopPropagation(); onDelete(p) }}
              >
                {Ico.close}
              </button>
            )}
          </div>
        ))}
        <div
          className="pickcard new"
          role="button"
          tabIndex={0}
          onClick={() => onGate({ mode: 'create' })}
          onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onGate({ mode: 'create' }) } }}
        >
          <span className="pickavatar plus">＋</span>
          <span className="pickname">Nuevo perfil</span>
        </div>
      </div>
      {gate.fromApp && (
        <button className="ghostbtn backdown" onClick={() => onGate(null)}>Cancelar</button>
      )}
    </div>
  )
}

/* ================= Series ================= */

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
        <button className="modalclose" onClick={onClose}>{Ico.close}</button>
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
                <button className={`seasonhead ${open ? 'open' : ''}`} onClick={() => setOpenSeason(open ? null : s.season)}>
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