# CodeMobile — MVP

Editor de código nativo para Android, pensado para programar cómodo desde el teléfono:
explorador de archivos sobre almacenamiento general, pestañas identificadas por color
(elegido por vos al crear el archivo), autocompletado de pares con pila de cierres
pendientes, autoindentación básica, resaltado de sintaxis por regex, gestos (pinch
para indentar/desindentar, long-press en el margen derecho para seleccionar línea),
y ejecución integrada de Python y JavaScript sin depender de Termux.

## Compilar con GitHub Actions

1. Subí este contenido a un repositorio de GitHub (puede ser privado).
2. El workflow en `.github/workflows/build.yml` corre automáticamente en cada push
   a `main`/`master`, o manualmente desde la pestaña **Actions → Build APK → Run workflow**.
3. Cuando termina, descargá el artefacto `codemobile-debug-apk` desde la página del run.
4. Instalá el `.apk` en tu teléfono (activando "Instalar apps de fuentes desconocidas"
   si Android lo pide).

## Compilar localmente (alternativa)

Si tenés Android Studio instalado, simplemente abrí la carpeta del proyecto —
Android Studio genera el wrapper de Gradle automáticamente si no está presente.

## Primer uso en el teléfono

Al abrir la app por primera vez, Android va a pedir el permiso de "acceso a todos
los archivos" (MANAGE_EXTERNAL_STORAGE) — es necesario para que el explorador y el
editor puedan leer/escribir en cualquier carpeta del almacenamiento, igual que
haría un gestor de archivos. Se concede una sola vez desde Ajustes.

## Qué incluye este MVP

- **Explorador de archivos**: navegación simple por carpetas del almacenamiento general.
- **Pestañas por color**: elegís el color al crear/guardar un archivo (paleta de 16).
  La pestaña activa se ensancha y muestra el nombre; las inactivas solo la extensión.
- **Autocompletado de pares** `{ } ( ) [ ] " "` con una pila de cierres pendientes
  por archivo: la celda dedicada de la barra de controles muestra el cierre más
  reciente pendiente (con un contador si hay varios), y tocarla lo inserta.
- **Autoindentación básica**: hereda la sangría de la línea anterior, y la aumenta
  un nivel si esa línea termina en `:` (Python) o en `{ ( [` (JS/genérico).
- **Resaltado de sintaxis por regex**: palabras clave, strings, comentarios,
  números y nombres de función, para Python y JavaScript.
- **Gestos**: pinch sobre el gutter izquierdo para indentar/desindentar la línea
  actual; long-press + drag sobre el margen derecho para seleccionar una o varias
  líneas completas.
- **Barra de controles bajo el teclado** (nunca encima): grupos de símbolos
  expandibles, flechas de dirección, toggle "Select" (con él activo, cualquier
  tap o flecha extiende la selección en vez de mover el cursor), y la celda de
  cierre pendiente.
- **Modo landscape**: botón para forzar la orientación sin tocar el ajuste
  global del teléfono; con el teclado activo en landscape se ocultan la barra
  de pestañas y la ruta del archivo para maximizar el código visible.
- **Ejecución integrada**: Python vía Chaquopy (CPython embebido) y JavaScript
  vía Rhino (motor JS puro JVM, sin necesidad de NDK). La salida y los errores
  aparecen en un panel de consola debajo del editor. No se valida nada antes
  de ejecutar — si el código tiene un error, el traceback real es la señal.

## Pendiente / próximos pasos sugeridos

- Restauración de sesión al reabrir la app (pestañas, cursor, scroll).
- Guardado automático / indicador más visible de cambios sin guardar.
- Un lenguaje más allá de Python/JS si hace falta (requeriría otro runtime embebido).
- Afinar el resaltado de sintaxis para strings multilínea.
