// Plano corporal de un monstruo. La familia pone la base (zorro de fuego,
// dinosaurio de hoja o anfibio de agua), la etapa las proporciones (cría
// redonda, joven y adulto de pie), la rama de joven cambia el cuerpo y la
// de adulto añade una pieza grande. Las formas ocultas tienen su aspecto
// propio. El ADN afina proporciones y color.

import { getForm } from '/shared/forms.js';

export const FAMILY_BASE = {
  pelusa: { ears: 'fox', top: 'flame', tail: 'flame', finish: 'plush', snout: 0.55 },
  hoja: { ears: 'leaf', top: 'leaf', tail: 'leaf', finish: 'matte', snout: 0.45 },
  gota: { ears: 'fin', top: 'fin', tail: 'fish', finish: 'gloss', snout: 0.12 },
};

const STAGE_SCALE = { cria: 0.8, joven: 0.92, adulto: 1.06 };

export function planFor(formId, genes) {
  const form = getForm(formId);
  const family = form.family ?? genes.family;
  const base = FAMILY_BASE[family] ?? FAMILY_BASE.pelusa;
  const build = genes.build;
  const [b1, b2] = form.path;
  const adult = form.stage === 'adulto';

  const plan = {
    stage: form.stage,
    family,
    finish: base.finish,
    scale: STAGE_SCALE[form.stage] ?? 1,
    blob: form.stage === 'cria',
    body: adult
      ? { w: 0.27 + build.round * 0.05, h: 0.46, d: 0.25 + build.round * 0.03 }
      : { w: 0.25 + build.round * 0.04, h: 0.36, d: 0.23 + build.round * 0.03 },
    legs: adult ? 0.2 : 0.14,
    head: adult
      ? { rx: 0.32 + build.head * 0.03, ry: 0.29 + build.head * 0.025, rz: 0.29 + build.head * 0.03 }
      : { rx: 0.34 + build.head * 0.03, ry: 0.31 + build.head * 0.025, rz: 0.3 + build.head * 0.03 },
    snout: base.snout,
    ears: { type: base.ears, size: 0.85 + build.ears * 0.35, droop: 0 },
    top: { type: form.stage === 'cria' && family === 'hoja' ? 'sprout' : base.top, size: 1 },
    tail: { type: base.tail, size: 0.8 + build.tail * 0.45 },
    eyes: { size: 0.95 + build.eyes * 0.2, gap: 0.36 + build.eyeGap * 0.1, style: genes.eyeStyle, lids: 0 },
    fang: adult,
    brows: false,
    cheeks: 1,
    spikes: 0,
    belly: true,
    pattern: genes.pattern,
    patternAmt: genes.patternAmt,
    arms: form.stage !== 'cria',
    extra: null,
    tone: { sat: 0, light: 0 },
  };

  if (plan.blob) {
    plan.body = { w: 0.92 + build.round * 0.1, h: 0.82, d: 0.84 + build.round * 0.06 };
    plan.ears.size *= 0.9;
    plan.eyes.size *= 1.12;
    plan.tail.size *= 0.8;
  }

  if (b1 === 'gloton') {
    plan.body.w *= 1.25;
    plan.body.d *= 1.18;
    plan.legs *= 0.85;
    plan.cheeks = 1.4;
    plan.tail.size *= 0.85;
  } else if (b1 === 'jugueton') {
    plan.body.w *= 0.9;
    plan.legs *= 1.3;
    plan.ears.size *= 1.25;
    plan.tail.size *= 1.4;
  } else if (b1 === 'tranquilo') {
    plan.ears.size *= 1.12;
    plan.ears.droop = 0.8;
    plan.top.size = 1.3;
    plan.eyes.lids = 0.38;
  } else if (b1 === 'arisco') {
    plan.ears.size *= 1.1;
    plan.spikes = 1;
    plan.brows = true;
    plan.fang = true;
    plan.cheeks = 0.4;
    plan.tone = { sat: 6, light: -7 };
  }

  if (b2 === 'gloton') {
    plan.extra = 'mane';
    plan.scale *= 1.06;
  } else if (b2 === 'jugueton') {
    plan.extra = 'wings';
    plan.tail.size *= 1.15;
  } else if (b2 === 'tranquilo') {
    plan.extra = 'crown';
    plan.eyes.lids = Math.max(plan.eyes.lids, 0.28);
  } else if (b2 === 'arisco') {
    plan.extra = 'horns';
    plan.brows = true;
    plan.spikes = Math.max(plan.spikes, 0.7);
    plan.tone = { sat: plan.tone.sat + 4, light: plan.tone.light - 4 };
  }

  if (form.secret) secretLook(plan, form);
  return plan;
}

function secretLook(plan, form) {
  if (form.secret === 'radiante') {
    // Aureola dorada, colores vivos y ojos con doble brillo.
    plan.extra = 'halo';
    plan.tone = { sat: 6, light: 6 };
    plan.eyes.style = 2;
    plan.ears.size *= 1.12;
    plan.top.size = 1.25;
    plan.tail.size *= 1.3;
    plan.scale *= 1.04;
  } else if (form.secret === 'sibarita') {
    // Redonda como un postre, con nata y guinda en la cabeza.
    plan.body.w *= 1.35;
    plan.body.d *= 1.25;
    plan.legs *= 0.8;
    plan.cheeks = 1.8;
    plan.eyes.lids = 0.3;
    plan.top = { type: 'cherry', size: 1.1 };
    plan.tail.size *= 0.75;
    plan.scale *= 1.06;
  } else if (form.secret === 'lunar') {
    // Una luna creciente en la cabeza, colores de noche y ojos de sueño.
    plan.top = { type: 'moon', size: 1.35 };
    plan.eyes.lids = 0.42;
    plan.ears.size *= 1.15;
    plan.ears.droop = 0.6;
    plan.tail.size *= 1.3;
    plan.tone = { sat: -6, light: -8 };
  } else if (form.secret === 'mestiza') {
    // Orejas y cola de la familia de la que viene.
    const other = FAMILY_BASE[form.origin];
    plan.ears.type = other.ears;
    plan.ears.size *= 1.1;
    plan.tail.type = other.tail;
    plan.tail.size *= 1.2;
    plan.body.w *= 1.05;
    plan.scale *= 1.04;
  }
}
