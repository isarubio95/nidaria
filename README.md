# Nidaria

Juego de mascota virtual para navegador, pensado primero para móvil. Crías una criatura 3D que evoluciona según cómo la cuidas.

Este repositorio contiene la Fase 1: el núcleo de un jugador. El mundo libre multijugador llegará en una fase posterior.

## Arrancar

Necesitas Node 22.13 o superior. Con nvm, `nvm use` elige la versión del proyecto.

```bash
npm install
npm start
```

El juego queda en http://localhost:3000. La base de datos se crea sola en `data/nidaria.db`.

Para probar en el móvil, abre desde él `http://<IP de tu ordenador>:3000` estando en la misma red.

## Opciones

Se pasan como variables de entorno.

| Variable | Por defecto | Para qué sirve |
| --- | --- | --- |
| `PORT` | `3000` | Puerto del servidor. |
| `DB_FILE` | `data/nidaria.db` | Fichero de la base de datos SQLite. |
| `TIME_SCALE` | `1` | Acelera el tiempo del juego. Con `60`, una hora pasa en un minuto. |
| `DEV_TOOLS` | sin activar | Con `1`, la ficha de la criatura muestra botones para adelantar el tiempo. No lo actives en producción. |
| `SECURE_COOKIES` | sin activar | Con `1`, la cookie de sesión solo viaja por HTTPS. Actívalo en producción. |

Ejemplo para ver evoluciones sin esperar:

```bash
DEV_TOOLS=1 npm start
```

## Pruebas

```bash
npm test
```

Cubren las reglas del juego (ADN, evolución, necesidades, muerte, economía) y la API completa contra una base de datos en memoria.

## Cómo está organizado

- `shared/`: las reglas del juego. Las usan el servidor, como autoridad, y el navegador, para mover las barras entre visitas.
- `server/`: servidor Express, cuentas, API y acceso a datos.
- `public/`: la interfaz. HTML, CSS y módulos de JavaScript sin paso de compilación.
- `public/js/scene/`: la habitación 3D y el generador de criaturas, con Three.js.
- `public/js/games/`: los minijuegos.
- `test/`: las pruebas.

## Dónde tocar las reglas

- Ritmo de las necesidades, enfermedad y muerte: `shared/needs.js`.
- Duración de cada etapa y de la vida adulta, y reglas de evolución: `shared/forms.js`.
- Precios y efectos de los artículos: `shared/catalog.js`.
- Dinero pasivo, energía de juego y premios de los minijuegos: `shared/economy.js`.

## Pasar a PostgreSQL

Todo el SQL pasa por la interfaz de `server/db/index.js` y usa tipos y sintaxis que PostgreSQL acepta. Para migrar hay que escribir un adaptador con las mismas cinco funciones (`get`, `all`, `run`, `exec`, `tx`) que convierta los `?` en `$1`, `$2`…, y usarlo en `server/index.js`.

## Cuentas

Las cuentas tienen solo nombre y contraseña. No hay correo, así que tampoco hay forma de recuperar una contraseña olvidada.
