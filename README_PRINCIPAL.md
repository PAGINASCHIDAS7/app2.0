# Analista Pro Android 1.1.2 — Startup Fix

Esta versión corrige el cierre al iniciar confirmado en el dispositivo HONOR BRP-NX3.

## Causa confirmada
`Window.getInsetsController()` se invocaba demasiado pronto durante `Activity.onCreate()`.
En ese firmware, el `DecorView` aún no tenía inicializado su `WindowInsetsController`, provocando:
`NullPointerException` en `PhoneWindow.getInsetsController()`.

## Corrección
- Se evita acceder al controlador de insets durante `configureSystemBars()`.
- Se conserva edge-to-edge y Safe Area.
- Mostrar las barras del sistema se difiere mediante `DecorView.post()` y se protege con try/catch.
- Los informes de crash anteriores se archivan automáticamente y no bloquean el nuevo inicio.
- Los errores nuevos de inicio siguen mostrando diagnóstico.

## Proyecto
- Android mínimo API 33 (Android 13)
- compileSdk / targetSdk API 36
- Java 17
- Gradle 9.6.0
- Application ID `com.analistapro.app`
- HTML locales en `app/src/main/assets/www/`
- Sin permiso INTERNET declarado

## Crear APK
1. Crea un repositorio privado vacío en GitHub.
2. Extrae este ZIP y sube el contenido a la raíz del repositorio.
3. Si `.github` no se sube, crea `.github/workflows/build-debug-apk.yml` y copia el workflow incluido.
4. Actions > Build Analista Pro APK > Run workflow.
5. Descarga `Analista-Pro-Android-1.1.2-StartupFix-APK`.

## Precaución
La APK de diagnóstico anterior y la nueva pueden tener firmas distintas si GitHub usa otra clave debug.
Si Android no permite actualizar, no desinstales la anterior sin respaldar los datos locales.

## Validación
Se hicieron pruebas estáticas. La compilación real y la prueba en HONOR BRP-NX3 deben confirmarse.
