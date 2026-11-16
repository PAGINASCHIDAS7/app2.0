# Cambios 1.1.1 — Diagnóstico de inicio y Safe Area

- Se usa el MainActivity de diagnóstico proporcionado para este proyecto.
- Se conserva la corrección de Safe Area en el FrameLayout raíz.
- Se conserva el acceso local offline a los cuatro HTML.
- Se registran excepciones Java no capturadas.
- Se registran errores de carga principal, errores HTTP, mensajes de consola JavaScript y caída del renderer WebView.
- Se presenta una pantalla de diagnóstico con botón para copiar el informe y botón para borrar el informe/reintentar.
- Se incrementa versionCode a 3 y versionName a 1.1.1-diagnostic.

Estado: esta versión permite recopilar evidencia del cierre. No declara resuelto el crash hasta revisar el informe de un dispositivo real.
