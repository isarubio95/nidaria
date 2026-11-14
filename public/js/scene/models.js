// Criaturas de modelo (.glb hecho fuera, con su esqueleto) en vez de
// esculpidas. De momento es una prueba detrás de un interruptor de
// desarrollo: con él apagado, todo sigue esculpido como siempre.
//
// La línea de prueba es la de las pelusas ("zorros de pelo encendido"):
// la cría y la joven son `mascota` y las adultas, `brasa`. El resto de
// familias no tiene modelo y sale esculpida aunque el interruptor esté
// encendido.

import { getForm } from '/shared/forms.js';
import { MODEL_DATA } from './model-data.js';

const SWITCH = 'nidaria.modelos';

// Lo guarda cada navegador (Perfil > Pruebas, con DEV_TOOLS=1).
// Fuera del navegador (pruebas y herramientas en Node) nunca hay modelos.
export function modelsEnabled() {
  if (typeof window === 'undefined') return false;
  try {
    return window.localStorage.getItem(SWITCH) === '1';
  } catch {
    return false;
  }
}

export function setModelsEnabled(on) {
  try {
    if (on) window.localStorage.setItem(SWITCH, '1');
    else window.localStorage.removeItem(SWITCH);
  } catch {
    // Sin almacenamiento (modo privado): el interruptor no se queda puesto.
  }
}

// `hue`: tono (0-360) del pelo del modelo, para mover el color según el
// ADN. `arms`: 'swing' (de pie, los brazos se balancean) o 'legs'.
// `ears: false`: lo que va en los huesos de las orejas es rígido (cuernos).
export const MODELS = {
  mascota: { file: '/models/mascota.glb', hue: 22, arms: 'swing', ears: true },
  brasa: { file: '/models/brasa.glb', hue: 355, arms: 'swing', ears: false },
};

// Modelo y altura (en la habitación) de cada etapa.
const LINES = {
  pelusa: { cria: ['mascota', 0.72], joven: ['mascota', 0.92], adulto: ['brasa', 1.25] },
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
