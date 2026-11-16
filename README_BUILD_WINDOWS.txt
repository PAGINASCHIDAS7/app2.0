ANALISTA PRO ANDROID 1.1.2 — NOTA PARA WINDOWS

La APK se compila con GitHub Actions; no necesitas Android Studio, SDK ni Gradle
localmente si utilizas el flujo de GitHub incluido en .github/workflows.

Si deseas compilar localmente, necesitas:
- JDK 17
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Gradle 9.6.0

Comando:
  gradle --no-daemon :app:assembleDebug

APK resultante:
  app\build\outputs\apk\debug\app-debug.apk

Esta versión 1.1.2 corrige el acceso prematuro a WindowInsetsController en
dispositivos HONOR y evita que un informe antiguo bloquee el inicio.
La compilación real y la prueba en el teléfono deben confirmarse.
