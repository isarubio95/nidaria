# Nidaria

Juego de mascota virtual para navegador, pensado primero para móvil. Crías una criatura 3D que evoluciona según cómo la cuidas.

Este repositorio contiene la Fase 1: el núcleo de un jugador. El mundo libre multijugador llegará en una fase posterior.

## Arrancar

Necesitas Node 22.13 o superior. Con nvm, `nvm use` elige la versión del proyecto.

```bash
npm install
npm start
```

El juego queda en http://localhost:3000. Sin configurar nada usa SQLite en `data/nidaria.db`; para usar Supabase, mira la sección siguiente.

Para probar en el móvil, abre desde él `http://<IP de tu ordenador>:3000` estando en la misma red.

## Base de datos en Supabase

1. Crea un proyecto en Supabase.
2. En *Project Settings > Database > Connect*, copia la cadena de conexión del *Session pooler* (o la directa si tu servidor tiene IPv6). El *Transaction pooler* (puerto 6543) también funciona, pero para un servidor que está siempre encendido es mejor el de sesión.
3. Copia `.env.example` como `.env` y pon esa cadena en `DATABASE_URL`, con tu contraseña.
4. `npm start`. Las tablas se crean solas al arrancar.

El servidor se conecta como propietario de las tablas y lleva sus propias cuentas y sesiones: no usa Supabase Auth ni la API REST. Por eso todas las tablas tienen RLS activado sin políticas, para que nadie pueda leerlas con la clave `anon` del proyecto.

Para que la conexión cifrada verifique el certificado, descárgalo en *Database > SSL Configuration* y pon su ruta en `DATABASE_CA_FILE`.

## Opciones

Se pasan como variables de entorno o en un fichero `.env` (ver `.env.example`).

| Variable | Por defecto | Para qué sirve |
| --- | --- | --- |
| `PORT` | `3000` | Puerto del servidor. |
| `DATABASE_URL` | sin definir | Cadena de conexión de PostgreSQL (Supabase). Si está, se usa en lugar de SQLite. |
| `DATABASE_CA_FILE` | sin definir | Certificado de Supabase para verificar la conexión. |
| `DB_FILE` | `data/nidaria.db` | Fichero de la base de datos SQLite, si no hay `DATABASE_URL`. |
| `TIME_SCALE` | `1` | Acelera el tiempo del juego. Con `60`, una hora pasa en un minuto. |
| `DEV_TOOLS` | sin activar | Con `1`, la ficha de la criatura muestra botones para adelantar el tiempo. No lo actives en producción. |
| `SECURE_COOKIES` | sin activar | Con `1`, la cookie de sesión solo viaja por HTTPS. Actívalo en producción. |
| `VAPID_PUBLIC_KEY` y `VAPID_PRIVATE_KEY` | sin definir | Claves de los avisos al móvil. Si faltan, se generan solas la primera vez y se guardan en la base de datos. |
| `VAPID_SUBJECT` | `mailto:avisos@nidaria.invalid` | Contacto que va en cada aviso: un `mailto:` o la dirección `https://` del juego. Pon uno real en producción. |

Ejemplo para ver evoluciones sin esperar:

```bash
DEV_TOOLS=1 npm start
```

## App instalable, avisos y clima

El juego se puede instalar en la pantalla de inicio (`public/manifest.webmanifest`) y manda avisos al móvil con Web Push aunque esté cerrado: si la criatura tiene hambre o enferma, si va a evolucionar, si la racha de misiones está en peligro a partir de las 20:00 o si la energía de juego se ha llenado. Cada jugador los activa en la ficha de la criatura y elige cuáles quiere. Por defecto no se manda nada de 23:00 a 8:00 en su hora local: lo pendiente llega por la mañana.

- Los avisos y la instalación necesitan HTTPS. `http://localhost` también vale, pero `http://<IP>:3000` desde el móvil no: para probarlos en el móvil hace falta servir el juego con HTTPS.
- En iPhone y iPad solo llegan si el juego está añadido a la pantalla de inicio (iOS 16.4 o posterior).
- El servidor mira cada medio minuto a quién le toca un aviso. Solo envía a los servicios de push de los navegadores (Google, Mozilla, Apple y Microsoft).
- Cada suscripción va ligada a la sesión: al salir de la cuenta, ese navegador deja de recibir avisos.
- Para fijar las claves en producción, genéralas con `npx web-push generate-vapid-keys` y ponlas en el `.env`. Si cambian, los navegadores suscritos se vuelven a suscribir solos la próxima vez que abran el juego.

La app de Android no usa Web Push: calcula los avisos en el propio teléfono con las mismas reglas (ver `android/README.md`). Qué avisos se quieren se guarda en la cuenta y vale para las dos.

El clima real es opcional y se activa en la ficha. El navegador pide la ubicación, la redondea a unos 10 km y pregunta el tiempo a [Open-Meteo](https://open-meteo.com) directamente. La ubicación no pasa por el servidor de Nidaria. La API gratuita de Open-Meteo es solo para uso no comercial: si el juego pasa a ganar dinero, hace falta su plan de pago.

## Pruebas

```bash
npm test
```

Cubren las reglas del juego (ADN, evolución, necesidades, muerte, economía) y la API completa contra una base de datos SQLite en memoria.

Para probar la API contra PostgreSQL, pasa una base de pruebas en `TEST_DATABASE_URL`. Cada prueba crea un esquema propio y lo borra al terminar, así que no toca las tablas de `public`; aun así, no uses la base de producción.

```bash
TEST_DATABASE_URL=postgresql://... npm test
```

## Cómo está organizado

- `shared/`: las reglas del juego. Las usan el servidor, como autoridad, y el navegador, para mover las barras entre visitas.
- `server/`: servidor Express, cuentas, API y acceso a datos.
- `public/`: la interfaz. HTML, CSS y módulos de JavaScript sin paso de compilación.
- `public/js/scene/`: la habitación 3D y el generador de criaturas, con Three.js.
- `public/js/games/`: los minijuegos.
- `test/`: las pruebas.

## Dónde tocar las reglas

- Misiones del día, sus premios y el premio de la racha: `shared/daily.js`.
- Qué se avisa al móvil, sus textos y las horas de silencio: `server/game/alerts.js`.
- Ritmo de las necesidades, enfermedad y muerte: `shared/needs.js`.
- Duración de cada etapa y de la vida adulta, y reglas de evolución: `shared/forms.js`.
- Precios y efectos de los artículos: `shared/catalog.js`.
- Dinero pasivo, energía de juego y premios de los minijuegos: `shared/economy.js`.

## Acceso a datos

Todo el SQL pasa por la interfaz de `server/db/` (`get`, `all`, `run`, `exec`, `tx`) y usa tipos y sintaxis que aceptan SQLite y PostgreSQL. Hay un adaptador para cada uno: `index.js` (SQLite) y `postgres.js` (PostgreSQL/Supabase), que convierte los `?` en `$1`, `$2`… En PostgreSQL las transacciones van en modo `SERIALIZABLE` y se repiten solas si chocan con otra.

Las migraciones están en `server/db/migrations/`. Un fichero `*.pg.sql` solo se aplica en PostgreSQL y uno `*.sqlite.sql` solo en SQLite.

## Cuentas

Las cuentas tienen solo nombre y contraseña. No hay correo, así que tampoco hay forma de recuperar una contraseña olvidada.
