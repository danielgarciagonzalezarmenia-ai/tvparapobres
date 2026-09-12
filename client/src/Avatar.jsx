export const FACE_COUNT = 4

const FACE = {
  // 0 — sonriendo
  smile: (
    <g key="smile" fill="currentColor" stroke="currentColor">
      <circle cx="12" cy="12" r="8.6" fill="none" strokeWidth="2" />
      <circle cx="8.4" cy="10.6" r="1.5" stroke="none" />
      <circle cx="15.6" cy="10.6" r="1.5" stroke="none" />
      <path d="M8 14.6 Q12 18.4 16 14.6" fill="none" strokeWidth="2.2" strokeLinecap="round" />
    </g>
  ),
  // 1 — triste
  sad: (
    <g key="sad" fill="currentColor" stroke="currentColor">
      <circle cx="12" cy="12" r="8.6" fill="none" strokeWidth="2" />
      <circle cx="8.4" cy="10.6" r="1.5" stroke="none" />
      <circle cx="15.6" cy="10.6" r="1.5" stroke="none" />
      <path d="M8 18.2 Q12 14.4 16 18.2" fill="none" strokeWidth="2.2" strokeLinecap="round" />
    </g>
  ),
  // 2 — seria
  serious: (
    <g key="serious" fill="currentColor" stroke="currentColor">
      <circle cx="12" cy="12" r="8.6" fill="none" strokeWidth="2" />
      <circle cx="8.4" cy="10.6" r="1.5" stroke="none" />
      <circle cx="15.6" cy="10.6" r="1.5" stroke="none" />
      <path d="M8.4 16.2h7.2" fill="none" strokeWidth="2.2" strokeLinecap="round" />
    </g>
  ),
  // 3 — sorprendida
  surprised: (
    <g key="surprised" fill="currentColor" stroke="currentColor">
      <circle cx="12" cy="12" r="8.6" fill="none" strokeWidth="2" />
      <circle cx="8.4" cy="10.2" r="2" stroke="none" />
      <circle cx="15.6" cy="10.2" r="2" stroke="none" />
      <path d="M12 13.2c-2.5 0-3.6 1.3-3.6 3.3s1.2 3.4 3.6 3.4 3.6-1.4 3.6-3.4S14.5 13.2 12 13.2zm0 2.5c.7 0 1 .6 1 .8s-.3.8-1 .8-1-.6-1-.8.3-.8 1-.8z" fill="currentColor" fillRule="evenodd" />
    </g>
  )
}

const GLYPHS = [FACE.smile, FACE.sad, FACE.serious, FACE.surprised]

export default function Avatar({ variant = 0, color = '#ff4d2e', size = 44, photo = '' }) {
  const idx = Math.max(0, Math.min(GLYPHS.length - 1, Number(variant) || 0))
  return (
    <span
      className="avatar"
      style={{
        width: size,
        height: size,
        background: 'linear-gradient(165deg, #23232b, #111116)',
        boxShadow: `0 0 0 2px rgba(255,255,255,0.07), 0 0 20px ${color}36, inset 0 0 0 1px ${color}22`
      }}
      aria-hidden="true"
    >
      {photo
        ? <img className="avatar-photo" src={photo} alt="" />
        : <span className="avatar-glyph" style={{ color }}>
            <svg viewBox="0 0 24 24" aria-hidden="true">{GLYPHS[idx]}</svg>
          </span>}
    </span>
  )
}

export function avatarColor(color) {
  return { background: color }
}