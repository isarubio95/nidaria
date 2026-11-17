// Criaturas de modelo (.glb hecho fuera, con su esqueleto) en vez de
// esculpidas. Salen por defecto; el interruptor de desarrollo las apaga
// para comparar con las esculpidas.
//
// Cada familia de monstruos tiene un modelo por etapa (cría, joven y
// adulta); las cuatro ramas de una etapa comparten modelo y se distinguen
// por el ADN (color, tamaño, gestos). Perros y gatos aún no tienen modelo
// y salen esculpidos aunque el interruptor esté encendido.

import { getForm } from '/shared/forms.js';
import { MODEL_DATA } from './model-data.js';

const SWITCH = 'nidaria.modelos';

// Lo guarda cada navegador (Perfil > Pruebas, con DEV_TOOLS=1): solo se
// apunta el apagado. Fuera del navegador (pruebas y herramientas en Node)
// nunca hay modelos.
export function modelsEnabled() {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(SWITCH) !== '0';
  } catch {
    return true;
  }
}

export function setModelsEnabled(on) {
  try {
    if (on) window.localStorage.removeItem(SWITCH);
    else window.localStorage.setItem(SWITCH, '0');
  } catch {
    // Sin almacenamiento (modo privado): el interruptor no se queda puesto.
  }
}

// `hue`: tono (0-360) del pelo del modelo, para mover el color según el
// ADN. `arms`: 'swing' (de pie, los brazos se balancean) o 'legs'.
// `ears: false`: lo que va en los huesos de las orejas es rígido (cuernos).
// En las hoja el pelo es castaño y lo verde son las hojas: su tono es el
// de las hojas, dentro de la banda de la familia, y `hueScale` frena el
// giro del ADN para que el castaño no se vuelva rosa ni verde. En las gota
// frena también el salto a la banda índigo (las branquias, coral).
export const MODELS = {
  mascota: { file: '/models/mascota.glb', hue: 22, arms: 'swing', ears: true },
  ascua: { file: '/models/ascua.glb', hue: 22, arms: 'swing', ears: true },
  brasa: { file: '/models/brasa.glb', hue: 355, arms: 'swing', ears: false },
  brote: { file: '/models/brote.glb', hue: 100, hueScale: 0.3, arms: 'swing', ears: true },
  trepa: { file: '/models/trepa.glb', hue: 100, hueScale: 0.3, arms: 'swing', ears: false },
  bosque: { file: '/models/bosque.glb', hue: 100, hueScale: 0.3, arms: 'swing', ears: false },
  gotita: { file: '/models/gotita.glb', hue: 192, hueScale: 0.5, arms: 'swing', ears: true },
  chapo: { file: '/models/chapo.glb', hue: 192, hueScale: 0.5, arms: 'swing', ears: true },
  marea: { file: '/models/marea.glb', hue: 200, hueScale: 0.5, arms: 'swing', ears: true },
};

// Modelo y altura (en la habitación) de cada etapa.
const LINES = {
  pelusa: { cria: ['mascota', 0.72], joven: ['ascua', 0.95], adulto: ['brasa', 1.25] },
  hoja: { cria: ['brote', 0.72], joven: ['trepa', 0.95], adulto: ['bosque', 1.3] },
  gota: { cria: ['gotita', 0.7], joven: ['chapo', 0.95], adulto: ['marea', 1.28] },
};

// El modelo de esta forma, o null si va esculpida.
export function modelFor(formId, genes, enabled = modelsEnabled()) {
  if (!enabled) return null;
  const form = getForm(formId);
  const pick = LINES[form.family ?? genes?.family]?.[form.stage];
  if (!pick) return null;
  const [id, height] = pick;
  return { id, height, ...MODELS[id], data: MODEL_DATA[id] };
}
