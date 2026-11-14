---
name: Nidaria
description: Juego de mascota virtual con un perro, un gato o un monstruo 3D en su habitación y una interfaz redonda de colores vivos.
colors:
  sky: "#a9c4d6"
  cream: "#f1eee8"
  cream-deep: "#e2ddd3"
  paper: "#fcfbf8"
  ink: "#1f2638"
  ink-soft: "#4d5670"
  ink-faint: "#979eb0"
  line: "#dfdbd2"
  track: "#e9e5dc"
  primary: "#ff6a3d"
  primary-edge: "#d2461d"
  primary-wash: "#ffe3d8"
  mint: "#3fc79e"
  mint-edge: "#1f9a76"
  mint-wash: "#d6f3e8"
  sun: "#ffc23d"
  sun-edge: "#d48f17"
  sun-wash: "#fff0c6"
  need-hunger: "#ff9f43"
  need-hunger-edge: "#d27a1b"
  need-energy: "#5b8def"
  need-energy-edge: "#3a69cc"
  need-fun: "#8d6cff"
  need-fun-edge: "#6447dc"
  need-health: "#ff5c64"
  need-health-edge: "#d23a43"
  danger: "#d0332f"
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
    backgroundColor: "{colors.primary}"
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
    backgroundColor: "{colors.primary-wash}"
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

**Creative North Star: "El cuarto del explorador"**

Nidaria se ve como un juego de criaturas para jóvenes y adultos, no como un juguete de bebé: una habitación acogedora de madera y tablas azules, botones redondos de colores vivos, tinta azul marino y criaturas con carácter. El usuario pidió en noviembre de 2026 dejar atrás el pastel ("que no parezca para niñas") y dar a los monstruos un toque de criatura de aventura. El listón de acabado sigue siendo Animal Crossing. El sistema busca que todo se sienta sólido, claro y bien hecho.

La criatura es la pantalla. Vive en una escena 3D a sangre y la interfaz es un marco callado a su alrededor: una píldora de monedas, un botón de tienda, una tarjeta con cinco barras y cinco botones redondos al alcance del pulgar. Todo lo demás espera en hojas que suben desde abajo.

**Key Characteristics:**

- Una habitación 3D con acabado de juguete de vinilo ocupa todo el fondo: luz de sol suave, tarima clara, friso de madera y criaturas esculpidas como figuras de arcilla. La interfaz flota encima en paneles blancos.
- Todo lo pulsable lleva un canto más oscuro por abajo y se hunde en él al tocarlo.
- Una sola tinta, azul marino, para todo el texto y todos los iconos.
- Cada necesidad tiene un color fijo que comparten su barra, su icono y su botón.
- Letra gótica redondeada en tres pesos, sin mayúsculas forzadas ni adornos.

## Colors

Colores vivos sobre blanco roto, con tinta azul marino. Nada de rosa chicle ni pastel desvaído. Los colores hacen trabajo: dicen qué necesidad es cuál y qué se puede pulsar.

### Primary

- **Naranja brasa** (`primary`, con su canto `primary-edge` y su lavado `primary-wash`): la acción principal de cada pantalla, el botón de la tienda y la casilla elegida.

### Secondary

- **Menta** (`mint`, `mint-edge`, `mint-wash`): confirmar y ponerse algo; también la higiene.
- **Sol** (`sun`, `sun-edge`, `sun-wash`): el dinero. Monedas, premios, energía de juego y recoger ingresos.

### Tertiary

- **Naranja comida** (`need-hunger`), **azul energía** (`need-energy`), **violeta diversión** (`need-fun`) y **rojo salud** (`need-health`), cada uno con su canto. Solo se usan para su necesidad.

### Neutral

- **Pizarra** (`sky`): el tono de la pared de tablas y el fondo de la página antes de que cargue la escena.
- **Blanco roto** (`cream`, `cream-deep`): las casillas de artículo, las tarjetas de especie y los campos de texto. El suelo es tarima de arce claro.
- **Papel** (`paper`): paneles, hojas, píldoras y diálogos.
- **Azul marino** (`ink`, `ink-soft`, `ink-faint`): texto principal, texto secundario y detalles apagados.
- **Línea y carril** (`line`, `track`): separadores, bordes de campo y el fondo de barras y pestañas.
- **Peligro** (`danger`): necesidades críticas, errores de formulario y avisos de error.

### Named Rules

**La regla de la tinta única.** Todo el texto y todos los iconos van en azul marino. Nunca negro puro, nunca gris. Sobre un color, el texto sigue siendo azul marino, no blanco.

**La regla de una necesidad, un color.** Comida es naranja, energía es azul, higiene es menta, diversión es violeta y salud es rojo, en la barra, en el icono y en el botón. Esos colores no se usan para otra cosa dentro de la pantalla de juego.

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

Móvil primero, con una mano. La escena 3D va a sangre y la interfaz se ancla a los bordes: arriba, monedas a la izquierda y tienda a la derecha; debajo, centrada, la placa con el nombre; a la derecha, una columna de accesos pequeños (Misiones, Armario, Casa, Amigos y Criadex); abajo, un muelle con la tarjeta de necesidades y la fila de acciones.

- **Margen lateral:** 16px. El muelle mide como mucho 520px y se centra.
- **Ritmo:** múltiplos de 4px. Grupos apretados (4 a 8px) y separación generosa entre grupos (12 a 24px).
- **Pantallas bajas** (menos de 700px de alto): las barras pasan a dos columnas sin texto y los botones de acción encogen a 52px.
- **Escritorio:** gana espacio, no funciones. La habitación enseña más muebles y las hojas flotan centradas con 560px de ancho.
- **Zonas seguras:** cabecera y muelle respetan los márgenes del dispositivo.

La cámara 3D encuadra a la criatura en el hueco libre entre la placa y el muelle, y vuelve a encuadrar cuando una hoja baja deja sitio encima.

## Elevation & Depth

Dos recursos, cada uno con su trabajo: la sombra separa del fondo y el canto dice que algo se pulsa.

### Shadow Vocabulary

- **Sombra baja** (`0 2px 4px` más `0 6px 14px -6px`, azul tinta translúcido): píldoras, casillas y botones.
- **Sombra media** (`0 3px 6px` más `0 14px 28px -10px`): la tarjeta de necesidades y los avisos.
- **Sombra alta** (`0 6px 12px` más `0 28px 56px -16px`): hojas, diálogos y tarjetas de pantalla.
- **Canto** (sombra interior de 4 a 5px por abajo, del color `-edge`): solo en lo pulsable.

### Named Rules

**La regla de la sombra tinta.** Las sombras son azul marino y translúcidas, siempre con desplazamiento y difuminado. Nunca un halo de color ni un bloque duro sin difuminar.

**La regla del hundimiento.** Al pulsar, el elemento encoge a 0.96, baja 2 o 3px y su canto se reduce a 1px. Es la única respuesta al toque; no hay brillos ni ondas.

## Shapes

Todo es redondo. Las píldoras y los círculos son la forma de lo que se pulsa; los rectángulos muy redondeados, la de lo que contiene.

- **Píldora** (999px): botones, monedas, fichas, pestañas, barras y avisos.
- **Círculo:** botones de acción y botón de cerrar.
- **Radio grande** (28px): paneles, hojas y diálogos.
- **Radio medio** (16px): casillas de artículo, campos de texto y accesos laterales.
- **Radio pequeño** (12px): casillas del Criadex y el anillo de foco.

**La regla de los radios concéntricos.** Un elemento dentro de otro lleva el radio del exterior menos el relleno que los separa: panel de 28px con relleno de 12px contiene piezas de 16px.

Los iconos son trazos propios de 2.2px con extremos redondos, en azul marino, dibujados sobre una rejilla de 24px.

## Components

### Buttons

- **Forma:** píldora de 48px de alto mínimo, texto en peso 900, con 4px menos de relleno arriba que abajo para compensar el canto.
- **Principal** (naranja brasa): una por pantalla u hoja. Entrar, comprar, jugar, empezar.
- **Confirmar** (menta) y **premio** (sol): variantes con el mismo cuerpo para ponerse algo o recoger monedas.
- **Callado:** sin canto, con un contorno interior de 2px en `line`. Para la salida secundaria de una hoja.
- **Estados:** al pulsar, la regla del hundimiento. Desactivado, al 50 % de opacidad y sin sombra exterior. Con ratón, un punto más de brillo.

### Action discs

Cinco círculos de 60px, uno por cuidado, del color de su necesidad y con canto de 5px. Debajo, su nombre en una ficha de papel. Son la acción principal de la pantalla de juego.

### Chips

Fichas de carril con icono de 14px y texto en caption. Dicen un efecto ("+14 comida") y no se pulsan.

### Cards / Containers

- **Panel:** papel, radio de 28px, sombra media. Agrupa las cinco barras.
- **Hoja:** panel anclado abajo que sube 72px mientras aparece, con velo azul tinta detrás. Lleva título, botón de cerrar, pestañas opcionales, cuerpo con desplazamiento y pie fijo.
- **Casilla de artículo:** crema, radio de 16px, miniatura 3D de 76px, nombre y precio. Elegida, pasa a lavado naranja con contorno interior naranja de 3px.

### Inputs / Fields

Campo crema con borde de 2px en `line` y radio de 16px. Con foco, el borde pasa a azul marino. Con error, pasa a `danger`, el campo se sacude una vez y el mensaje aparece debajo.

- **Código para guardar:** el código de recuperación va en una caja crema con borde discontinuo de 2px en `line`, en peso 900 con cifras tabulares y algo de espacio entre letras. Se selecciona entero con un toque. El borde discontinuo dice «esto es para copiar», no «esto se escribe». El código de amigo usa la misma caja, con los botones de compartir y copiar debajo.

### Navigation

- **Pestañas:** carril con una píldora de papel que se desliza hasta la opción activa.
- **Placa:** el nombre de la criatura y su estado en una píldora centrada; al pulsarla abre su ficha.
- **Accesos laterales:** casillas pequeñas con icono y nombre, en columna. En el móvil tapan el rincón derecho de la habitación por encima de medio metro: lo que se ponga ahí debe ser bajo.
- **Enlace de texto:** para la salida terciaria de un formulario («¿Has olvidado la contraseña?»). Azul marino suave en peso 700, subrayado de 2px y 44px de alto para el pulgar. No lleva canto porque no es un botón.

### Need bars

Barra de 14px sobre carril, del color de su necesidad y con canto. Cuando la necesidad es crítica, el nombre pasa a `danger` y la barra gana un contorno interior del mismo color: el estado nunca depende solo del largo de la barra.

### Share card

Imagen vertical de 1080 × 1350 sobre blanco roto, para mandar fuera del juego. Arriba, la foto real de la habitación desde la escena 3D, con radio de 40px y canto de papel; nunca un dibujo aparte. Debajo, el nombre en peso 900, la forma y la etapa en azul marino suave, una ficha con la raza (o la familia, en los monstruos) en lavado menta y una por rasgo de carácter en lavado sol. Cierra un pie sobre una línea: el ADN a la izquierda y, a la derecha, la dirección del juego y «Nidaria». En la app se enseña dentro de un diálogo, con radio de 16px y sombra baja.

## La escena 3D

**Creative North Star de la escena: "Figuras de vinilo en su cuarto".** Las criaturas y los objetos parecen figuras de colección de arcilla o vinilo: formas que se funden entre sí sin costuras, colores con fuerza y una luz que envuelve.

- **Tres especies.** Perros y gatos (`scene/pet.js`) son figuras sentadas, y cada raza es un plano en datos (`shared/breeds.js`): proporciones, hocico, orejas, pelo, cola y capas con sus zonas pintadas (máscara, silla, puntas de siamés, manchas de dálmata, atigrado…). Los monstruos (`scene/monster.js`) son de tres familias: un zorro de fuego con cola de llama (pelusa), un dinosaurio con hojas por cresta (hoja) y un anfibio con aletas (gota). De crías son una bola con patitas; de jóvenes y adultos se ponen de pie, con garras, y la crianza les añade melena, alas, corona o cuernos.
- **Toque de criatura de aventura, diseño propio.** Silueta clara, colores intensos con barriga clara, iris que contrasta con el cuerpo y adultos con mirada decidida. Nunca se copia un personaje existente.

- **Escultura, no primitivas.** Criaturas, comida, juguetes, muebles y ropa se esculpen por código como plastilina (`scene/sdf.js`): las piezas se funden con un pliegue suave y la malla lleva pintado su color y horneada la sombra de sus pliegues. Nada de esferas y conos sueltos que se cruzan.
- **Ojos de verdad.** Un globo con el iris pintado (pupila redonda en perros y monstruos, rasgada en gatos; con blanco en los monstruos de pie), dos brillos fijos que hacen de reflejo y un párpado del color de la piel con su línea de pestañas. Al mirar gira el globo; al parpadear baja el párpado. La boca es un trazo fino en tinta (en ω en los gatos, con surco bajo la nariz en las mascotas); los coloretes, casi invisibles. Los ojos cerrados son arcos (◡ dormida o tranquila, ^ cuando ríe).
- **Una luz, la del sol.** El sol entra cálido por arriba a la izquierda con sombras suaves; el cielo rellena y un contraluz frío recorta a la criatura contra la pared. De noche, luna fría por la ventana y una lámpara cálida dentro. Tono neutro (sin quemar los colores).
- **Materiales por familia.** Perros y gatos de pelo largo o tupido, aterciopelados; de pelo corto, mate; la esfinge, piel con brillo; las narices, húmedas y brillantes. Pelusa y hoja, arcilla mate; gota, vinilo con algo de barniz. Cerámica con barniz, madera mate, tela con brillo de fibra y metal dorado solo en coronas y cascabeles.
- **Todo se apoya.** Cada mueble lleva su sombra de contacto y el rincón entre suelo y pared se oscurece un poco.

## Do's and Don'ts

### Do:

- **Do** dejar que la criatura mande: nada en la pantalla de juego puede taparla en reposo.
- **Do** usar el canto en todo lo que se pulsa y solo en eso.
- **Do** escribir en azul marino sobre cualquier fondo, también sobre naranja, menta y sol.
- **Do** llevar cada tarea secundaria a una hoja y cerrar con una sola acción principal naranja.
- **Do** dibujar los artículos y las formas con sus modelos 3D reales, nunca con un icono genérico.
- **Do** acompañar un estado crítico con texto y contorno, no solo con color o movimiento.

### Don't:

- **Don't** añadir banners, ofertas emergentes, globos de aviso ni filas de monedas: el ruido del género es lo que este sistema rechaza.
- **Don't** usar negro, gris neutro ni texto blanco sobre color.
- **Don't** volver al rosa chicle, al pastel desvaído ni a los lunares: el usuario lo descartó por infantil.
- **Don't** usar los colores de las necesidades para decorar otra cosa en la pantalla de juego.
- **Don't** poner sombras duras sin difuminar ni halos de color.
- **Don't** usar emojis ni glifos como iconos.
- **Don't** animar con rebote lo que se usa a cada momento; el rebote se reserva a la criatura.
