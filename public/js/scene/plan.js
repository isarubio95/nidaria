// De la forma (posición en el árbol) y el ADN sale un plano corporal:
// la familia pone la base, la rama de joven cambia el cuerpo y la rama
// de adulta añade una pieza grande. El ADN afina proporciones y color.

import { getForm } from '/shared/forms.js';

const FAMILY_BASE = {
  pelusa: { ears: 'round', top: 'tuft', tail: 'pom', finish: 'plush' },
  hoja: { ears: 'leaf', top: 'sprout', tail: 'leaf', finish: 'matte' },
  gota: { ears: 'fin', top: 'drop', tail: 'drop', finish: 'gloss' },
};

const STAGE_SCALE = { cria: 0.74, joven: 0.86, adulto: 1 };

export function planFor(formId, genes) {
  const form = getForm(formId);
  const family = form.family ?? genes.family;
  const base = FAMILY_BASE[family];
  const build = genes.build;
  const [b1, b2] = form.path;

  const plan = {
    stage: form.stage,
    family,
    finish: base.finish,
    scale: STAGE_SCALE[form.stage] ?? 1,
    blob: form.stage === 'cria',
    body: { w: 0.84 + build.round * 0.14, h: 0.64, d: 0.76 + build.round * 0.1 },
    head: { rx: 0.5 + build.head * 0.06, ry: 0.45 + build.head * 0.04, rz: 0.47 + build.head * 0.05 },
    ears: { type: base.ears, size: 0.85 + build.ears * 0.45, droop: 0 },
    top: { type: base.top, size: 1 },
    tail: { type: base.tail, size: 0.75 + build.tail * 0.6 },
    eyes: { size: 0.92 + build.eyes * 0.3, gap: 0.33 + build.eyeGap * 0.13, style: genes.eyeStyle, lids: 0 },
    mouth: 'smile',
    fang: false,
    brows: false,
    cheeks: 1,
    belly: genes.pattern === 'barriga',
    pattern: genes.pattern,
    patternAmt: genes.patternAmt,
    feet: 1,
    arms: form.stage !== 'cria',
    extra: null,
    tone: { sat: 0, light: 0 },
  };

  if (plan.blob) {
    plan.body = { w: 0.98 + build.round * 0.1, h: 0.86, d: 0.9 + build.round * 0.06 };
    plan.ears.size *= 0.9;
    plan.eyes.size *= 1.08;
  }

  if (b1 === 'gloton') {
    plan.body.w *= 1.2;
    plan.body.d *= 1.14;
    plan.body.h *= 0.96;
    plan.belly = true;
    plan.cheeks = 1.5;
    plan.tail.size *= 0.8;
  } else if (b1 === 'jugueton') {
    plan.body.w *= 0.9;
    plan.body.h *= 1.1;
    plan.ears.size *= 1.3;
    plan.tail.size *= 1.45;
    plan.feet = 1.3;
  } else if (b1 === 'tranquilo') {
    plan.ears.size *= 1.18;
    plan.ears.droop = 1;
    plan.top.size = 1.35;
    plan.eyes.lids = 0.42;
  } else if (b1 === 'arisco') {
    plan.ears.type = family === 'pelusa' ? 'pointy' : plan.ears.type;
    plan.ears.size *= 1.1;
    plan.top.type = 'spikes';
    plan.tail.type = 'spike';
    plan.brows = true;
    plan.fang = true;
    plan.cheeks = 0.5;
    plan.tone = { sat: 8, light: -9 };
  }

  if (b2 === 'gloton') {
    plan.extra = 'mane';
    plan.scale *= 1.08;
  } else if (b2 === 'jugueton') {
    plan.extra = 'wings';
    plan.tail.size *= 1.2;
  } else if (b2 === 'tranquilo') {
    plan.extra = 'crown';
    plan.eyes.lids = Math.max(plan.eyes.lids, 0.3);
  } else if (b2 === 'arisco') {
    plan.extra = 'horns';
    plan.brows = true;
    plan.tone = { sat: plan.tone.sat + 4, light: plan.tone.light - 4 };
  }

  return plan;
}
