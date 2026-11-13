# Nidaria

Juego de mascota virtual para navegador, pensado primero para móvil. Crías una criatura 3D que evoluciona según cómo la cuidas.

Este repositorio contiene la Fase 1: el núcleo de un jugador. El mundo libre multijugador llegará en una fase posterior.

## Arrancar

Necesitas Node 22.13 o superior. Con nvm, `nvm use` elige la versión del proyecto.

```bash
npm install
npm start
```

El juego queda en http://localhost:3000. Sin configurar nada usa SQLite en `data/nidaria.db`; para usar PostgreSQL, mira la sección siguiente.

Para probar en el móvil, abre desde él `http://<IP de tu ordenador>:3000` estando en la misma red.

## Base de datos

Con `DATABASE_URL` el juego usa PostgreSQL; sin ella, SQLite. Las tablas se crean solas al arrancar. El servidor se conecta como dueño de las tablas y lleva sus propias cuentas y sesiones.

### En el servidor

En producción la base es un PostgreSQL 18 en Docker, en el mismo servidor que el juego. Se monta con:

```bash
deploy/db-setup.sh
```

El script instala Docker si falta y deja en el servidor:

- `/opt/nidaria/postgres/`: el `compose.yaml`, los datos (`data/`) y las contraseñas (`secrets/`, solo root).
- `nidaria-db.service`: arranca la base y espera a que responda. `nidaria.service` arranca después.
- `nidaria-db-backup.timer`: una copia cada noche en `/var/backups/nidaria`. Guarda las de los últimos 14 días.

Se puede repetir sin miedo: no toca los datos, las contraseñas ni el rol que ya existan. No cambia el `DATABASE_URL` del juego; al terminar dice cómo ponerlo en `/etc/nidaria/nidaria.env`.

La base solo escucha en `127.0.0.1:5432`, así que desde fuera del servidor no se puede llegar. El juego entra con el rol `nidaria`, que no es superusuario: solo es dueño de su base. El contenedor corre con el usuario `nidaria-db` del servidor y no con el 999 de la imagen, que en el servidor es el de Caddy.

Las copias están en el mismo servidor: si se pierde el disco, se pierden con él. Conviene bajarlas de vez en cuando:

```bash
rsync -a vps:/var/backups/nidaria/ copias-nidaria/
```

Para hacer una copia a mano, entrar en la base o restaurar una copia (en el servidor):

```bash
systemctl start nidaria-db-backup.service
docker compose -f /opt/nidaria/postgres/compose.yaml exec postgres psql -U nidaria

# Restaurar: para el juego, sustituye el contenido de la base y arráncalo.
systemctl stop nidaria
docker compose -f /opt/nidaria/postgres/compose.yaml exec -T postgres \
  pg_restore -U nidaria -d nidaria --clean --if-exists --no-owner --single-transaction \
  </var/backups/nidaria/nidaria-AAAAMMDD-HHMMSS.dump
systemctl start nidaria
```

Para pasar a otra versión menor de PostgreSQL 18, en `/opt/nidaria/postgres`: `docker compose pull && docker compose up -d --wait`. Para cambiar de versión mayor hace falta volcar y restaurar, o usar `pg_upgrade`.

## Opciones

Se pasan como variables de entorno o en un fichero `.env` (ver `.env.example`).

| Variable | Por defecto | Para qué sirve |
| --- | --- | --- |
| `PORT` | `3000` | Puerto del servidor. |
| `DATABASE_URL` | sin definir | Cadena de conexión de PostgreSQL. Si está, se usa en lugar de SQLite. |
| `DB_FILE` | `data/nidaria.db` | Fichero de la base de datos SQLite, si no hay `DATABASE_URL`. |
| `TIME_SCALE` | `1` | Acelera el tiempo del juego. Con `60`, una hora pasa en un minuto. |
| `DEV_TOOLS` | sin activar | Con `1`, la ficha de la criatura muestra botones para adelantar el tiempo. No lo actives en producción. |
| `SECURE_COOKIES` | sin activar | Con `1`, la cookie de sesión solo viaja por HTTPS. Actívalo en producción. |
| `TRUST_PROXY` | sin activar | Con `1`, se fía de la cabecera `X-Forwarded-For` de un proxy en la misma máquina, para limitar peticiones por la IP real de cada jugador. Actívalo si el juego va detrás de Caddy o nginx. |
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

## Sonido y tarjeta para compartir

Los efectos de sonido (comer, el baño, las monedas, los minijuegos…) vienen activados y se quitan en la ficha. El ajuste es de cada dispositivo. Los navegadores no dejan sonar nada hasta el primer toque en la página.

Desde la ficha, y en el aviso de que ha nacido o evolucionado, se puede hacer una tarjeta para compartir: una foto de la criatura en su habitación con su nombre, su forma, su ADN y la dirección del juego, en PNG de 1080 × 1350. En el móvil se comparte con el menú del sistema; donde el navegador no lo permite, se guarda la imagen.

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
- `public/js/scene/`: la habitación 3D y el generador de criaturas, con Three.js. Los papeles pintados y las alfombras están en `decor.js`; los muebles, con los demás artículos, en `props.js`.
- `public/js/games/`: los minijuegos.
- `public/js/audio/`: los efectos de sonido. Se generan con código (`sounds.js`), sin ficheros de audio; la app de Android tiene una copia en Kotlin que una prueba compara con esta.
- `test/`: las pruebas.

## Dónde tocar las reglas

- Misiones del día, sus premios y el premio de la racha: `shared/daily.js`.
- Qué se avisa al móvil, sus textos y las horas de silencio: `server/game/alerts.js`.
- Ritmo de las necesidades, enfermedad y muerte: `shared/needs.js`.
- Duración de cada etapa y de la vida adulta, y reglas de evolución: `shared/forms.js`.
- Precios y efectos de los artículos, y los sitios de la habitación que se pueden decorar: `shared/catalog.js`.
- Dinero pasivo, energía de juego y premios de los minijuegos: `shared/economy.js`. Ahí están también el juego del día (rota cada día y paga ×1,5), las ventajas que da cada rasgo de carácter en un juego y los juegos que solo puede jugar una rama, como Al escondite para las juguetonas.

## Acceso a datos

Todo el SQL pasa por la interfaz de `server/db/` (`get`, `all`, `run`, `exec`, `tx`) y usa tipos y sintaxis que aceptan SQLite y PostgreSQL. Hay un adaptador para cada uno: `index.js` (SQLite) y `postgres.js` (PostgreSQL), que convierte los `?` en `$1`, `$2`… En PostgreSQL las transacciones van en modo `SERIALIZABLE` y se repiten solas si chocan con otra.

Las migraciones están en `server/db/migrations/`. Un fichero `*.pg.sql` solo se aplica en PostgreSQL y uno `*.sqlite.sql` solo en SQLite.

## Cuentas

Las cuentas tienen solo nombre y contraseña. No hay correo: si se olvida la contraseña, la cuenta se recupera con un código de recuperación.

- Al crear la cuenta, el juego enseña el código una sola vez (16 letras y cifras, como `ABCD-EFGH-JKMN-PQRS`). El servidor solo guarda su hash.
- En la entrada, «¿Has olvidado la contraseña?» pide el nombre, el código y una contraseña nueva. El código se gasta: el juego da otro y cierra la cuenta en los demás dispositivos.
- Desde la ficha se puede crear un código nuevo, por ejemplo si se perdió el anterior o la cuenta es de antes de que existieran. Pide la contraseña y anula el código anterior.

Si alguien pierde la contraseña y el código, la cuenta no se puede recuperar.
