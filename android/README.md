# Nidaria para Android

La app nativa de Nidaria. Está escrita en Kotlin: la interfaz con Jetpack Compose y la habitación 3D con un motor propio sobre OpenGL ES 3.0. No lleva ningún navegador dentro.

Juega contra el mismo servidor que la web, así que la cuenta y la criatura son las mismas en los dos sitios.

## Qué hace falta

- JDK 21.
- El SDK de Android con la plataforma 37.2. Si las licencias están aceptadas, Gradle descarga lo que falte al compilar.
- El servidor de Nidaria en marcha (`npm start` en la raíz del repositorio).
- Un móvil o tableta con Android 9 o posterior y OpenGL ES 3.0.

En el ordenador donde se creó el proyecto, el JDK está en `~/.jdks` y el SDK en `~/Android/Sdk`. Si no hay JDK en `~/.jdks`, vale el que Gradle descarga solo en `~/.gradle/jdks`. Antes de usar Gradle en una terminal nueva:

```bash
export JAVA_HOME="$(ls -d ~/.jdks/jdk-21* ~/.gradle/jdks/*-21-* 2>/dev/null | grep -v '\.lock$' | head -1)"
export ANDROID_HOME=~/Android/Sdk
```

## Compilar

Desde esta carpeta:

```bash
./gradlew assembleRelease     # versión para instalar
./gradlew assembleDebug       # versión de depuración
./gradlew testDebugUnitTest   # pruebas
```

La versión para instalar queda en `app/build/outputs/apk/release/app-release.apk`. Va optimizada y pesa unos 3 MB. La de depuración pesa diez veces más y se mueve peor; sirve para depurar, no para jugar.

Si no hay clave de subida (ver abajo), la versión para instalar se firma con la clave de depuración de Android, que basta para instalarla en un móvil propio.

## Publicar en Google Play

Google Play pide un AAB firmado con una clave de subida y que apunte al servidor público.

**1. Crear la clave de subida** (una sola vez). Guárdala fuera del repositorio y haz copia de seguridad de ella y de sus contraseñas: sin ella no se pueden subir más versiones hasta que Google la restablezca.

```bash
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore ~/nidaria-upload.jks -alias upload \
  -keyalg RSA -keysize 4096 -validity 10000
```

**2. Decirle a Gradle dónde está.** Copia `keystore.properties.example` como `keystore.properties` y rellénalo. Ese fichero no se sube a git. En CI se pueden usar en su lugar las variables `NIDARIA_KEYSTORE`, `NIDARIA_KEYSTORE_PASSWORD`, `NIDARIA_KEY_ALIAS` y `NIDARIA_KEY_PASSWORD`.

**3. Indicar el servidor público** en `local.properties`:

```properties
nidaria.releaseServer=https://tu-dominio
```

o con `-Pnidaria.releaseServer=https://…` al compilar. Solo afecta a la release; la de depuración sigue usando `nidaria.server`.

**4. Generar el AAB:**

```bash
./gradlew bundleRelease
```

Queda en `app/build/outputs/bundle/release/app-release.aab`. La tarea se niega a generarlo si falta la clave o si el servidor no empieza por `https://`.

Antes de cada subida hay que aumentar `versionCode` en `app/build.gradle.kts`: Google Play no acepta dos subidas con el mismo número. En la consola de Play, activa la firma de apps de Google Play (Play App Signing), que es la opción por defecto: Google guarda la clave con la que se firma la app final y la tuya solo sirve para subir.

## A qué servidor se conecta

La app trae una dirección por defecto y deja cambiarla desde la pantalla de entrada y desde el aviso de «No hay conexión».

La dirección por defecto se fija al compilar. Se toma, por este orden, de:

1. La opción `-Pnidaria.server=…` de Gradle.
2. La línea `nidaria.server=…` de `local.properties`.
3. `http://10.0.2.2:3000`, que desde el emulador es el ordenador que lo ejecuta.

Para un móvil en la misma wifi, la dirección es la IP del ordenador en la red y el puerto del servidor, por ejemplo `http://192.168.1.20:3000`. Esa IP puede cambiar cuando el router la reasigna; si la app deja de conectar, se corrige con el botón «Cambiar».

Al cambiar de servidor la sesión se cierra, porque la cuenta vive en el servidor.

### HTTP sin cifrar

El servidor corre por ahora en la red de casa y sin HTTPS, así que la app admite conexiones sin cifrar. Cuando el servidor esté publicado con HTTPS, hay que poner `cleartextTrafficPermitted="false"` en `app/src/main/res/xml/network_security_config.xml`.

## Instalar en el móvil

Con el móvil conectado por USB y la depuración USB activada:

```bash
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/release/app-release.apk
```

También se puede copiar el APK al móvil y abrirlo desde el gestor de archivos.

## Probar en el emulador

`tools/emulator.sh` maneja un emulador sin ventana:

```bash
tools/emulator.sh create     # una vez: descarga la imagen y crea el dispositivo
tools/emulator.sh start      # lo arranca y espera a que esté listo
tools/emulator.sh install    # instala la versión de depuración y la abre
tools/emulator.sh shot captura.png
tools/emulator.sh stop
```

`tools/ui.py` maneja la app desde la terminal: toca un elemento por su texto, escribe, espera a que aparezca algo y hace capturas, sueltas o en ráfaga. Con `tools/ui.py` sin argumentos sale la ayuda.

El emulador funciona con o sin virtualización por hardware. Sin ella (no existe `/dev/kvm`, por ejemplo porque está apagada en la BIOS) tarda unos cuatro minutos en arrancar y la interfaz se mueve a pocos fotogramas por segundo: sirve para comprobar que todo funciona y se ve bien, no para juzgar la fluidez. Activar la virtualización en la BIOS (SVM en AMD, VT-x en Intel) lo deja usable de verdad.

Para que la app del emulador hable con un servidor de pruebas y no con la partida real:

```bash
PORT=3111 DEV_TOOLS=1 DB_FILE=/tmp/nidaria-pruebas.db node ../server/index.js
./gradlew assembleDebug -Pnidaria.server=http://10.0.2.2:3111
```

Con `DEV_TOOLS=1` la ficha de la criatura enseña los botones para adelantar el tiempo, igual que en la web, y uno para probar los avisos: repasa si hay algo que avisar diez segundos después, así que hay que salir de la app justo después de pulsarlo.

El emulador de `tools/emulator.sh` es Android 11, que no pide permiso para enseñar avisos. Para probar ese permiso (Android 13 o posterior) hace falta otro dispositivo virtual con una imagen más nueva, por ejemplo `system-images;android-35;default;x86_64`.

## Avisos y clima

La app avisa como la web (hambre, enfermedad, evolución, racha en peligro, energía de juego llena), pero sin servidor de push: guarda la última foto de la partida que vio el jugador y, cada cuarto de hora, la simula hasta ahora con las mismas reglas del servidor. Si hay algo nuevo, lo enseña. De 23:00 a 8:00 no avisa, salvo que el jugador lo cambie.

- Qué avisos se quieren se guarda en la cuenta, así que la web y la app comparten esa elección. Activarlos o no es cosa de cada teléfono.
- Si el jugador juega desde la web, el teléfono no se entera hasta que se abre la app. Hasta entonces avisa según lo último que vio aquí.
- Android 13 o posterior pide permiso para enseñar avisos. La app lo pide al activarlos.
- Las alarmas son inexactas para gastar poca batería: un aviso puede llegar hasta un cuarto de hora tarde, o más si el teléfono está en reposo profundo.

El clima real usa la ubicación aproximada (el permiso de ubicación aproximada, nunca el de la precisa), la redondea a unos 10 km y pregunta a Open-Meteo. No pasa por el servidor de Nidaria.

## Cómo está organizado

Todo el código está en `app/src/main/kotlin/com/nidaria/app/`.

- `rules/`: las reglas del juego, portadas de `shared/`. El servidor sigue siendo la autoridad; la app las usa para mover las barras entre visitas y para dibujar cada criatura a partir de su ADN.
- `gl/`: el motor 3D. Matemática y grafo de escena con los convenios de Three.js, geometrías generadas por código, animaciones con la semántica de GSAP y el renderizador (sombras, materiales físicos, luz de entorno).
- `scene/`: la habitación, el generador de criaturas, los modelos de los artículos y las miniaturas. Es el equivalente de `public/js/scene/`.
- `data/`: el cliente de la API, el estado de la partida y el clima real.
- `notify/`: los avisos. `Alerts.kt` decide qué avisar (la misma regla que `server/game/alerts.js`) y `Notifier.kt` lo programa y lo enseña.
- `ui/`: la interfaz. Tema, componentes, pantallas, hojas y minijuegos.
- `Game.kt`: el hilo conductor. Decide qué pantalla toca, encadena las animaciones y guarda lo que la interfaz enseña encima.

`app/src/main/assets/env.bin` es la luz de entorno de la habitación, horneada a partir del entorno que usa la web.

## Mantenerla al día con la web

La app repite en Kotlin lo que la web hace en JavaScript, así que un cambio en un lado hay que llevarlo al otro. Hay dos ayudas para que no se desvíen sin avisar.

**Reglas.** `tools/golden.mjs` ejecuta las reglas originales de `shared/` y guarda sus resultados en `app/src/test/resources/golden.json`. Las pruebas comparan las reglas de Kotlin con ese fichero: generador aleatorio, ADN, formas, simulación de necesidades, economía y catálogo. Tras tocar `shared/`:

```bash
node tools/golden.mjs          # con Node 22 o posterior
./gradlew testDebugUnitTest    # falla hasta que rules/ haga lo mismo
```

**Luz de entorno.** Si cambia el entorno de la escena web, se vuelve a hornear con el servidor web en marcha:

```bash
node tools/cdp.mjs http://localhost:3000/ tools/bake-env.page.mjs
```

Necesita Google Chrome instalado.

Las pruebas cubren además el motor (matrices, cámara, geometrías, animaciones) y construyen las 63 formas de criatura, todos los artículos y toda la ropa para comprobar que ninguna se rompe.

## Diferencias con la web

- En teléfonos la app solo se usa en vertical. En tabletas gira.
- El botón «atrás» del sistema cierra lo último que se abrió: un diálogo, una hoja o la partida de un minijuego, esta con confirmación.
- La pantalla de entrada tiene un apartado para elegir el servidor.
- Los avisos los calcula el propio teléfono en vez de mandarlos el servidor (ver «Avisos y clima»).
- Cuando una criatura se despide, su sombra se va con ella. En la web se queda en la alfombra.
- En las casillas de artículo, la etiqueta de efecto se ajusta al ancho de la casilla en lugar de sobresalir.

## Tipografía

La app incluye Zen Maru Gothic en tres pesos, recortada a los caracteres latinos. Se distribuye bajo la licencia SIL Open Font 1.1, que va en `app/src/main/assets/ZenMaruGothic-OFL.txt`.
