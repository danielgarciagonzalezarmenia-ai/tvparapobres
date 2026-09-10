@echo off
title TV Para Pobres - Servidor personal
echo.
echo  ================================================
echo   TV PARA POBRES - Arranque del servidor personal
echo  ================================================
echo.

cd /d "%~dp0"

rem ---- Verificar que existe node ----
where node >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Node.js no esta instalado o no esta en el PATH.
  echo         Instalalo desde https://nodejs.org y vuelve a intentar.
  pause
  exit /b 1
)

echo [1/3] Arrancando el backend (node server/index.js)...
echo       Deja esta ventana abierta mientras quieras que la web funcione.
echo.
start "TVPP-Backend" cmd /k "cd /d %~dp0 && node server/index.js"

rem ---- Verificar Tailscale ----
where tailscale >nul 2>nul
if errorlevel 1 goto needtailscale

:runtail
echo.
echo [2/3] Abriendo Tailscale Funnel (expone tu PC a internet)...
echo.
tailscale funnel 4000
if errorlevel 1 (
  echo.
  echo [INFO] Si Funnel no se abrio, asegurate de haber iniciado sesion primero:
  echo        Ejecuta:  tailscale login
  echo        y en el navegador completa el acceso. Luego vuelve a correr este script.
  echo.
)
echo.
echo [3/3] Listo. La web usa tu PC como servidor.
echo       Tu URL publica es la que muestra Tailscale (https://....ts.net)
echo       Copiala y pegala en Render como variable PC_BACKEND_URL
echo.
echo  IMPORTANTE: No suspendas ni cierres la tapa con este servicio activo,
echo  o la web mostrara "Sin servicio".
echo.
pause
exit /b 0

:needtailscale
echo [2/3] Tailscale no esta instalado. Abriendo el instalador...
echo       Instalalo y luego vuelve a ejecutar este script.
start "" "%~dp0tailscale-setup.msi"
echo.
echo  Despues de instalar, abre una terminal y ejecuta:
echo      tailscale login
echo  completa el acceso en el navegador, y vuelve a correr este script.
pause
exit /b 0