// Clima real. Si el jugador lo activa, el navegador pide su ubicación
// aproximada (redondeada a unos 10 km) y pregunta el tiempo a Open-Meteo.
// La ubicación se queda en este dispositivo: al servidor de Nidaria no
// llega nunca.

const ON = 'nidaria:clima';
const PLACE = 'nidaria:clima:sitio';
const CACHE = 'nidaria:clima:dato';
const MAX_AGE = 30 * 60 * 1000;
const API = 'https://api.open-meteo.com/v1/forecast';

const saved = {
  get(key) {
    try {
      return JSON.parse(localStorage.getItem(key));
    } catch {
      return null;
    }
  },
  set(key, value) {
    try {
      if (value === null) localStorage.removeItem(key);
      else localStorage.setItem(key, JSON.stringify(value));
    } catch {
      // Sin almacenamiento, el clima dura lo que la visita.
    }
  },
};

export const WEATHER_NAMES = {
  despejado: 'despejado', nubes: 'algunas nubes', cubierto: 'nublado', niebla: 'niebla',
  lluvia: 'lluvia', nieve: 'nieve', tormenta: 'tormenta',
};

// Códigos WMO de Open-Meteo, agrupados en lo que se ve por la ventana.
export function weatherKind(code) {
  if (code === 2) return 'nubes';
  if (code === 3) return 'cubierto';
  if (code === 45 || code === 48) return 'niebla';
  if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return 'lluvia';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'nieve';
  if (code >= 95 && code <= 99) return 'tormenta';
  return 'despejado';
}

let latest = null;
let timer = null;
const listeners = new Set();
const emit = () => {
  for (const fn of listeners) fn(latest);
};

export const weatherOn = () => saved.get(ON) === true;
export const currentWeather = () => latest;

export function onWeather(fn) {
  listeners.add(fn);
  fn(latest);
  return () => listeners.delete(fn);
}

function position() {
  return new Promise((resolve, reject) => {
    if (!('geolocation' in navigator)) {
      reject(new Error('Sin geolocalización'));
      return;
    }
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => resolve({
        lat: Math.round(coords.latitude * 10) / 10,
        lon: Math.round(coords.longitude * 10) / 10,
      }),
      reject,
      { enableHighAccuracy: false, maximumAge: 6 * 60 * 60 * 1000, timeout: 15000 },
    );
  });
}

async function fetchWeather(place) {
  const url = new URL(API);
  url.search = new URLSearchParams({
    latitude: String(place.lat),
    longitude: String(place.lon),
    current: 'weather_code,temperature_2m',
  }).toString();
  const response = await fetch(url, { credentials: 'omit', referrerPolicy: 'no-referrer' });
  if (!response.ok) throw new Error(`Open-Meteo respondió ${response.status}`);
  const data = await response.json();
  return {
    kind: weatherKind(Number(data.current?.weather_code)),
    temp: Math.round(Number(data.current?.temperature_2m)),
    at: Date.now(),
  };
}

async function refresh() {
  if (!weatherOn()) {
    latest = null;
    emit();
    return;
  }
  const cached = saved.get(CACHE);
  if (cached && Date.now() - cached.at < MAX_AGE) {
    latest = cached;
    emit();
    return;
  }
  const place = saved.get(PLACE);
  if (!place) return;
  try {
    latest = await fetchWeather(place);
    saved.set(CACHE, latest);
  } catch (error) {
    console.warn('Clima:', error);
    latest = cached ?? null;
  }
  emit();
}

// Si el permiso sigue concedido, la ubicación se actualiza en silencio
// (por si el jugador está de viaje). Nunca pregunta sin que lo pida él.
async function refreshPlace() {
  try {
    const status = await navigator.permissions?.query({ name: 'geolocation' });
    if (status?.state !== 'granted') return;
    const place = await position();
    const old = saved.get(PLACE);
    if (!old || old.lat !== place.lat || old.lon !== place.lon) {
      saved.set(PLACE, place);
      saved.set(CACHE, null);
    }
  } catch {
    // Se queda con la ubicación que tenía.
  }
}

export function startWeather() {
  if (timer) return;
  if (weatherOn()) refreshPlace().finally(refresh);
  timer = setInterval(refresh, MAX_AGE);
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) refresh();
  });
}

// Pide la ubicación. Lanza un error si el jugador no la da.
export async function enableWeather() {
  const place = await position();
  saved.set(PLACE, place);
  saved.set(CACHE, null);
  saved.set(ON, true);
  await refresh();
  return latest;
}

export function disableWeather() {
  saved.set(ON, null);
  saved.set(PLACE, null);
  saved.set(CACHE, null);
  latest = null;
  emit();
}
