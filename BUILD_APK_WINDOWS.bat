@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo No se encontro Gradle en PATH.
  echo Para compilar localmente necesitas Gradle 9.6.0, JDK 17,
  echo Android SDK Platform 36 y Build Tools 36.0.0.
  echo Si solo quieres generar el APK, utiliza GitHub Actions.
  pause
  exit /b 1
)
gradle --no-daemon :app:assembleDebug
if errorlevel 1 exit /b %errorlevel%
echo.
echo APK creado en: app\build\outputs\apk\debug\app-debug.apk
pause
