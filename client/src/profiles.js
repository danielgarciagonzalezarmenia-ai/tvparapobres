const PROFILES_KEY = 'tvp_profiles'
const ACTIVE_KEY = 'tvp_active_profile'

export const PALETTE = [
  { id: 'orange', color: '#ff4d2e', name: 'Naranja' },
  { id: 'red', color: '#e5484d', name: 'Rojo' },
  { id: 'pink', color: '#e93d82', name: 'Rosa' },
  { id: 'purple', color: '#8e4ec6', name: 'Púrpura' },
  { id: 'blue', color: '#3e63dd', name: 'Azul' },
  { id: 'teal', color: '#12b5a5', name: 'Turquesa' },
  { id: 'green', color: '#3fb950', name: 'Verde' },
  { id: 'amber', color: '#f5a623', name: 'Ámbar' }
]

export const AVATAR_COUNT = 4

let seq = Date.now()
export const newId = () => `p${(seq++).toString(36)}`

export function loadProfiles() {
  try {
    const arr = JSON.parse(localStorage.getItem(PROFILES_KEY) || '[]')
    if (!Array.isArray(arr)) return []
    return arr.filter(
      (p) => p && typeof p.id === 'string' && typeof p.name === 'string'
    ).map((p) => ({
      id: p.id,
      name: String(p.name).slice(0, 18),
      color: typeof p.color === 'string' && p.color.startsWith('#') ? p.color : PALETTE[0].color,
      avatar: Number.isFinite(Number(p.avatar)) ? Math.max(0, Math.min(AVATAR_COUNT - 1, Number(p.avatar))) : 0,
      pin: typeof p.pin === 'string' ? p.pin : '',
      photo: typeof p.photo === 'string' && p.photo.startsWith('data:image') ? p.photo.slice(0, 300000) : ''
    }))
  } catch { return [] }
}

export function saveProfiles(list) {
  try { localStorage.setItem(PROFILES_KEY, JSON.stringify(list)) } catch { /* noop */ }
}

export function activeProfileId() {
  try { return localStorage.getItem(ACTIVE_KEY) || '' } catch { return '' }
}

export function setActiveProfileId(id) {
  try { localStorage.setItem(ACTIVE_KEY, id) } catch { /* noop */ }
}

export function currentProfile() {
  const id = activeProfileId()
  if (!id) return null
  return loadProfiles().find((p) => p.id === id) || null
}

const clamp = (v, a, b) => Math.max(a, Math.min(b, v))
const toHex = (v) => Math.round(clamp(v, 0, 255)).toString(16).padStart(2, '0')
const mix = (c, t, k) => clamp(c + (t - c) * k, 0, 255)

function hexRgb(hex) {
  const h = String(hex || PALETTE[0].color).replace('#', '')
  return [
    parseInt(h.slice(0, 2), 16),
    parseInt(h.slice(2, 4), 16),
    parseInt(h.slice(4, 6), 16)
  ]
}

export function themeFor(color) {
  const [r, g, b] = hexRgb(color)
  const strong = `#${toHex(mix(r, 255, 0.4))}${toHex(mix(g, 255, 0.4))}${toHex(mix(b, 255, 0.4))}`
  const light = `#${toHex(mix(r, 255, 0.55))}${toHex(mix(g, 255, 0.55))}${toHex(mix(b, 255, 0.55))}`
  return {
    accent: `#${toHex(r)}${toHex(g)}${toHex(b)}`,
    accent2: light,
    accentStrong: strong,
    accentPale: `rgba(${r},${g},${b},0.14)`,
    accentPale2: `rgba(${r},${g},${b},0.24)`,
    accentLine: `rgba(${r},${g},${b},0.45)`,
    accentGlow: `rgba(${r},${g},${b},0.38)`,
    accentRing: `rgba(${r},${g},${b},0.22)`,
    accentFocus: `rgba(${r},${g},${b},0.15)`,
    accentBorder: `rgba(${r},${g},${b},0.45)`,
    accentSoft: `rgba(${r},${g},${b},0.12)`
  }
}

export function applyTheme(profile) {
  const t = profile ? themeFor(profile.color) : themeFor('#ff4d2e')
  const s = document.documentElement.style
  s.setProperty('--accent', t.accent)
  s.setProperty('--accent-2', t.accent2)
  s.setProperty('--accent-strong', t.accentStrong)
  s.setProperty('--accent-pale', t.accentPale)
  s.setProperty('--accent-pale-2', t.accentPale2)
  s.setProperty('--accent-line', t.accentLine)
  s.setProperty('--accent-glow', t.accentGlow)
  s.setProperty('--accent-ring', t.accentRing)
  s.setProperty('--accent-focus', t.accentFocus)
  s.setProperty('--accent-border', t.accentBorder)
  s.setProperty('--accent-soft', t.accentSoft)
}

export const namespaced = (base, profileId) => (base + '_' + (profileId || 'x'))