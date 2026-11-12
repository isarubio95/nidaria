---
name: Nidaria
description: Juego de mascota virtual con una criatura 3D en su habitación y una interfaz redonda de caramelo.
colors:
  sky: "#bfe3ff"
  cream: "#fff8ec"
  cream-deep: "#f3e7d3"
  paper: "#fffdf8"
  ink: "#5b3a29"
  ink-soft: "#84644f"
  ink-faint: "#b89f8c"
  line: "#eadfce"
  track: "#f0e6d6"
  pink: "#ff8fb1"
  pink-edge: "#e2648b"
  pink-wash: "#ffe3ec"
  mint: "#7ed6a5"
  mint-edge: "#4fae7c"
  mint-wash: "#dff5e8"
  sun: "#ffd166"
  sun-edge: "#e0a038"
  sun-wash: "#fff1cc"
  need-hunger: "#ffb45c"
  need-hunger-edge: "#e08f2e"
  need-energy: "#9db7ff"
  need-energy-edge: "#6f8de6"
  need-health: "#ff7a7a"
  need-health-edge: "#df5252"
  danger: "#c2403f"
typography:
  wordmark:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "2.25rem"
    fontWeight: 900
    lineHeight: 1
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "1.625rem"
    fontWeight: 900
    lineHeight: 1.15
    letterSpacing: "0em"
  heading:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "1.25rem"
    fontWeight: 900
    lineHeight: 1.15
    letterSpacing: "0em"
  body:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "1rem"
    fontWeight: 500
    lineHeight: 1.45
    letterSpacing: "0em"
  label:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 700
    lineHeight: 1.25
    letterSpacing: "0em"
  caption:
    fontFamily: "Zen Maru Gothic, ui-rounded, system-ui, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 900
    lineHeight: 1.2
    letterSpacing: "0em"
rounded:
  sm: "12px"
  md: "16px"
  lg: "28px"
  pill: "999px"
spacing:
  s1: "4px"
  s2: "8px"
  s3: "12px"
  s4: "16px"
  s5: "20px"
  s6: "24px"
  s8: "32px"
  gutter: "16px"
components:
  button-primary:
    backgroundColor: "{colors.pink}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "11px 22px 15px"
    height: "48px"
  button-confirm:
    backgroundColor: "{colors.mint}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "11px 22px 15px"
    height: "48px"
  button-reward:
    backgroundColor: "{colors.sun}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "11px 22px 15px"
    height: "48px"
  button-quiet:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    padding: "13px 20px"
    height: "48px"
  action-disc:
    backgroundColor: "{colors.need-hunger}"
    textColor: "{colors.ink}"
    rounded: "{rounded.pill}"
    size: "60px"
  need-bar:
    backgroundColor: "{colors.track}"
    rounded: "{rounded.pill}"
    height: "14px"
  panel:
    backgroundColor: "{colors.paper}"
    textColor: "{colors.ink}"
    rounded: "{rounded.lg}"
    padding: "12px 16px"
  item-tile:
    backgroundColor: "{colors.cream}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "8px 6px 12px"
  item-tile-selected:
    backgroundColor: "{colors.pink-wash}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
  chip:
    backgroundColor: "{colors.track}"
    textColor: "{colors.ink}"
    typography: "{typography.caption}"
    rounded: "{rounded.pill}"
    padding: "3px 9px 4px"
  input:
    backgroundColor: "{colors.cream}"
    textColor: "{colors.ink}"
    rounded: "{rounded.md}"
    padding: "10px 14px 12px"
    height: "48px"
  toast:
    backgroundColor: "{colors.ink}"
    textColor: "{colors.cream}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "10px 18px 12px"
---

# Design System: Nidaria

## Overview

**Creative North Star: "El cuarto de juguetes"**

Nidaria se ve como el género manda: una habitación acogedora en tonos pastel, botones redondos de caramelo y barras de colores. Es una decisión del usuario, que eligió el estándar del género con Animal Crossing como listón de acabado. El sistema no busca sorprender; busca que todo se sienta blando, claro y bien hecho.

La criatura es la pantalla. Vive en una escena 3D a sangre y la interfaz es un marco callado a su alrededor: una píldora de monedas, un botón de tienda, una tarjeta con cinco barras y cinco botones redondos al alcance del pulgar. Todo lo demás espera en hojas que suben desde abajo.

**Key Characteristics:**

- Una escena 3D de materiales mates de juguete ocupa todo el fondo; la interfaz flota encima en paneles blancos.
- Todo lo pulsable lleva un canto más oscuro por abajo y se hunde en él al tocarlo.
- Una sola tinta, marrón cacao, para todo el texto y todos los iconos.
- Cada necesidad tiene un color fijo que comparten su barra, su icono y su botón.
- Letra gótica redondeada en tres pesos, sin mayúsculas forzadas ni adornos.

## Colors

Pastel saturado sobre crema, con tinta cacao. Los colores hacen trabajo: dicen qué necesidad es cuál y qué se puede pulsar.

### Primary

- **Rosa caramelo** (`pink`, con su canto `pink-edge` y su lavado `pink-wash`): la acción principal de cada pantalla, el botón de la tienda, la alfombra de la habitación y la casilla elegida.

### Secondary

- **Menta** (`mint`, `mint-edge`, `mint-wash`): confirmar y ponerse algo; también la higiene.
- **Sol** (`sun`, `sun-edge`, `sun-wash`): el dinero. Monedas, premios, energía de juego y recoger ingresos.

### Tertiary

- **Naranja comida** (`need-hunger`), **azul energía** (`need-energy`) y **coral salud** (`need-health`), cada uno con su canto. Solo se usan para su necesidad.

### Neutral

- **Cielo** (`sky`): la pared de la habitación y el fondo de la página antes de que cargue la escena.
- **Crema** (`cream`, `cream-deep`): el suelo, las casillas de artículo y los campos de texto.
- **Papel** (`paper`): paneles, hojas, píldoras y diálogos.
- **Cacao** (`ink`, `ink-soft`, `ink-faint`): texto principal, texto secundario y detalles apagados.
- **Línea y carril** (`line`, `track`): separadores, bordes de campo y el fondo de barras y pestañas.
- **Peligro** (`danger`): necesidades críticas, errores de formulario y avisos de error.

### Named Rules

**La regla de la tinta única.** Todo el texto y todos los iconos van en cacao. Nunca negro, nunca gris. Sobre un color, el texto sigue siendo cacao, no blanco.

**La regla de una necesidad, un color.** Comida es naranja, energía es azul, higiene es menta, diversión es rosa y salud es coral, en la barra, en el icono y en el botón. Esos colores no se usan para otra cosa dentro de la pantalla de juego.

**La regla del canto.** Cada color pulsable tiene una versión más oscura (`-edge`) que solo aparece como canto inferior. Un color sin canto no es un botón.

## Typography

**Familia única:** Zen Maru Gothic, servida desde el propio proyecto, con `ui-rounded` y la fuente del sistema como respaldo.

**Carácter:** redonda, tranquila y legible en pequeño. Los tres pesos hacen toda la jerarquía; no hay cursivas, versalitas ni mayúsculas forzadas.

### Hierarchy

- **Wordmark** (900, 2.25rem, interlínea 1): el nombre del juego en la pantalla de entrada.
- **Title** (900, 1.625rem, interlínea 1.15): título de cada hoja, diálogo y tarjeta.
- **Heading** (900, 1.25rem, interlínea 1.15): nombre de la criatura, nombre de un artículo elegido, títulos de sección.
- **Body** (500, 1rem, interlínea 1.45): texto corrido.
- **Label** (700, 0.875rem): nombres de necesidades, textos de apoyo, pestañas y casillas.
- **Caption** (900, 0.75rem): etiquetas bajo los botones de acción, fichas de efecto y contadores.

### Named Rules

**La regla de las cifras quietas.** Todo número que cambia (monedas, precios, tiempo, puntuación) usa cifras tabulares para que no baile el texto de al lado.

## Layout

Móvil primero, con una mano. La escena 3D va a sangre y la interfaz se ancla a los bordes: arriba, monedas a la izquierda y tienda a la derecha; debajo, centrada, la placa con el nombre; a la derecha, dos accesos pequeños; abajo, un muelle con la tarjeta de necesidades y la fila de acciones.

- **Margen lateral:** 16px. El muelle mide como mucho 520px y se centra.
- **Ritmo:** múltiplos de 4px. Grupos apretados (4 a 8px) y separación generosa entre grupos (12 a 24px).
- **Pantallas bajas** (menos de 700px de alto): las barras pasan a dos columnas sin texto y los botones de acción encogen a 52px.
- **Escritorio:** gana espacio, no funciones. La habitación enseña más muebles y las hojas flotan centradas con 560px de ancho.
- **Zonas seguras:** cabecera y muelle respetan los márgenes del dispositivo.

La cámara 3D encuadra a la criatura en el hueco libre entre la placa y el muelle, y vuelve a encuadrar cuando una hoja baja deja sitio encima.

## Elevation & Depth

Dos recursos, cada uno con su trabajo: la sombra separa del fondo y el canto dice que algo se pulsa.

### Shadow Vocabulary

- **Sombra baja** (`0 2px 4px` más `0 6px 14px -6px`, cacao translúcido): píldoras, casillas y botones.
- **Sombra media** (`0 3px 6px` más `0 14px 28px -10px`): la tarjeta de necesidades y los avisos.
- **Sombra alta** (`0 6px 12px` más `0 28px 56px -16px`): hojas, diálogos y tarjetas de pantalla.
- **Canto** (sombra interior de 4 a 5px por abajo, del color `-edge`): solo en lo pulsable.

### Named Rules

**La regla de la sombra cacao.** Las sombras son marrones y translúcidas, siempre con desplazamiento y difuminado. Nunca un halo de color ni un bloque duro sin difuminar.

**La regla del hundimiento.** Al pulsar, el elemento encoge a 0.96, baja 2 o 3px y su canto se reduce a 1px. Es la única respuesta al toque; no hay brillos ni ondas.

## Shapes

Todo es redondo. Las píldoras y los círculos son la forma de lo que se pulsa; los rectángulos muy redondeados, la de lo que contiene.

- **Píldora** (999px): botones, monedas, fichas, pestañas, barras y avisos.
- **Círculo:** botones de acción y botón de cerrar.
- **Radio grande** (28px): paneles, hojas y diálogos.
- **Radio medio** (16px): casillas de artículo, campos de texto y accesos laterales.
- **Radio pequeño** (12px): casillas del Criadex y el anillo de foco.

**La regla de los radios concéntricos.** Un elemento dentro de otro lleva el radio del exterior menos el relleno que los separa: panel de 28px con relleno de 12px contiene piezas de 16px.

Los iconos son trazos propios de 2.2px con extremos redondos, en cacao, dibujados sobre una rejilla de 24px.

## Components

### Buttons

- **Forma:** píldora de 48px de alto mínimo, texto en peso 900, con 4px menos de relleno arriba que abajo para compensar el canto.
- **Principal** (rosa): una por pantalla u hoja. Entrar, comprar, jugar, empezar.
- **Confirmar** (menta) y **premio** (sol): variantes con el mismo cuerpo para ponerse algo o recoger monedas.
- **Callado:** sin canto, con un contorno interior de 2px en `line`. Para la salida secundaria de una hoja.
- **Estados:** al pulsar, la regla del hundimiento. Desactivado, al 50 % de opacidad y sin sombra exterior. Con ratón, un punto más de brillo.

### Action discs

Cinco círculos de 60px, uno por cuidado, del color de su necesidad y con canto de 5px. Debajo, su nombre en una ficha de papel. Son la acción principal de la pantalla de juego.

### Chips

Fichas de carril con icono de 14px y texto en caption. Dicen un efecto ("+14 comida") y no se pulsan.

### Cards / Containers

- **Panel:** papel, radio de 28px, sombra media. Agrupa las cinco barras.
- **Hoja:** panel anclado abajo que sube 72px mientras aparece, con velo cacao detrás. Lleva título, botón de cerrar, pestañas opcionales, cuerpo con desplazamiento y pie fijo.
- **Casilla de artículo:** crema, radio de 16px, miniatura 3D de 76px, nombre y precio. Elegida, pasa a lavado rosa con contorno interior rosa de 3px.

### Inputs / Fields

Campo crema con borde de 2px en `line` y radio de 16px. Con foco, el borde pasa a cacao. Con error, pasa a `danger`, el campo se sacude una vez y el mensaje aparece debajo.

### Navigation

- **Pestañas:** carril con una píldora de papel que se desliza hasta la opción activa.
- **Placa:** el nombre de la criatura y su estado en una píldora centrada; al pulsarla abre su ficha.
- **Accesos laterales:** dos casillas pequeñas con icono y nombre.

### Need bars

Barra de 14px sobre carril, del color de su necesidad y con canto. Cuando la necesidad es crítica, el nombre pasa a `danger` y la barra gana un contorno interior del mismo color: el estado nunca depende solo del largo de la barra.

## Do's and Don'ts

### Do:

- **Do** dejar que la criatura mande: nada en la pantalla de juego puede taparla en reposo.
- **Do** usar el canto en todo lo que se pulsa y solo en eso.
- **Do** escribir en cacao sobre cualquier fondo, también sobre rosa, menta y sol.
- **Do** llevar cada tarea secundaria a una hoja y cerrar con una sola acción principal rosa.
- **Do** dibujar los artículos y las formas con sus modelos 3D reales, nunca con un icono genérico.
- **Do** acompañar un estado crítico con texto y contorno, no solo con color o movimiento.

### Don't:

- **Don't** añadir banners, ofertas emergentes, globos de aviso ni filas de monedas: el ruido del género es lo que este sistema rechaza.
- **Don't** usar negro, gris neutro ni texto blanco sobre color.
- **Don't** usar los colores de las necesidades para decorar otra cosa en la pantalla de juego.
- **Don't** poner sombras duras sin difuminar ni halos de color.
- **Don't** usar emojis ni glifos como iconos.
- **Don't** animar con rebote lo que se usa a cada momento; el rebote se reserva a la criatura.
