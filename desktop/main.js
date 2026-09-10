// TV Para Pobres - App de escritorio (Electron)
// Arranca el servidor local (que habla directo con el proveedor Xtream)
// y abre la interfaz en una ventana nativa. Sin servidores intermedios.
import { app, BrowserWindow, shell } from 'electron'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SERVER = path.join(__dirname, '..', 'server', 'index.js')

// Puerto local fijo (evita los bloqueos de la web). Solo escucha en esta PC.
const PORT = Number(process.env.TVPP_PORT) || 41327

async function waitForServer(port, tries = 60) {
  const url = `http://127.0.0.1:${port}/health`
  for (let i = 0; i < tries; i++) {
    try {
      const r = await fetch(url)
      if (r.ok) return true
    } catch { /* still booting */ }
    await new Promise((r) => setTimeout(r, 250))
  }
  return false
}

let win = null

async function createWindow() {
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    backgroundColor: '#0a0a0c',
    autoHideMenuBar: true,
    icon: path.join(__dirname, '..', 'assets', 'icon.png'),
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url)
    return { action: 'deny' }
  })

  // La web abre los enlaces directos de video en una pestaña del navegador systema.
  win.webContents.on('will-navigate', (e, url) => {
    if (url.startsWith('http://127.0.0.1') || url.startsWith('http://localhost')) return
    e.preventDefault()
    if (/^https?:/.test(url)) shell.openExternal(url)
  })

  await win.loadURL(`http://127.0.0.1:${PORT}/app`)
}

app.whenReady().then(async () => {
  // Cache en la carpeta de datos del usuario (la carpeta de la app es de solo lectura en .exe)
  process.env.XTREAM_CACHE_DIR = path.join(app.getPath('userData'), 'cache')
  process.env.HOST = '127.0.0.1'
  process.env.PORT = String(PORT)

  try {
    await import(pathToFileURL(SERVER).href)
  } catch (e) {
    console.error('No se pudo arrancar el servidor local:', e)
    app.quit()
    return
  }

  const ok = await waitForServer(PORT)
  if (!ok) {
    console.error('El servidor local no respondió a tiempo')
    app.quit()
    return
  }

  await createWindow()

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => app.quit())