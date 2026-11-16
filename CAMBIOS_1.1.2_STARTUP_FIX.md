# Analista Pro Android 1.1.2 — Startup Fix

## Causa confirmada
En dispositivos HONOR, `Window.getInsetsController()` se llamaba durante `onCreate()`
antes de que el `DecorView` y su controlador de insets estuvieran inicializados.
El framework lanzaba `NullPointerException` en `PhoneWindow.getInsetsController()`.

## Correcciones
- Se eliminó la consulta temprana a `Window.getInsetsController()` de `configureSystemBars()`.
- Se conserva `setDecorFitsSystemWindows(false)` y la gestión de Safe Area mediante WindowInsets.
- La solicitud para mostrar las barras del sistema se realiza desde el `DecorView` y se difiere con `post()`, con protección ante errores del fabricante.
- Los informes de un arranque anterior se archivan y limpian automáticamente; ya no bloquean el arranque mostrando "Borrar informe y reintentar".
- Los errores nuevos capturados durante el arranque siguen mostrando la pantalla de diagnóstico.
- Se incrementó versionCode a 4 y versionName a 1.1.2.

## Estado de pruebas
Se realizaron verificaciones estáticas del proyecto, XML, YAML y las condiciones del cambio.
No se afirma que la APK se haya compilado o probado físicamente en un HONOR: debe confirmarse mediante GitHub Actions y el dispositivo.
