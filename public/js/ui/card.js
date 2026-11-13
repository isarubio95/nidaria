// Tarjeta para compartir: una foto de la criatura en su habitación con su
// nombre, su forma y su ADN, lista para mandar o guardar.

import { h } from '../dom.js';
import { FAMILY_INFO, TRAITS } from '/shared/dna.js';
import { dialog } from './dialog.js';
import { toast } from './toast.js';
import { sfx } from '../audio/sfx.js';

// Formato vertical 4:5: el que mejor se ve en redes y en el móvil.
const W = 1080;
const H = 1350;
const PAD = 48;
const PHOTO_H = 860;
const C = {
  cream: '#fff8ec', paper: '#fffdf8', ink: '#5b3a29', inkSoft: '#84644f', inkFaint: '#b89f8c',
  track: '#f0e6d6', line: '#eadfce', pink: '#ff8fb1', pinkWash: '#ffe3ec', mintWash: '#dff5e8', sunWash: '#fff1cc',
};
const FONT = '"Zen Maru Gothic", ui-rounded, system-ui, sans-serif';

// A mano y no con ctx.roundRect, que Safari 15 no tiene.
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// Reduce el cuerpo de letra hasta que el texto quepa en `max` píxeles.
function fitFont(ctx, text, weight, size, max) {
  let px = size;
  ctx.font = `${weight} ${px}px ${FONT}`;
  while (px > 20 && ctx.measureText(text).width > max) {
    px -= 2;
    ctx.font = `${weight} ${px}px ${FONT}`;
  }
  return px;
}

// Ficha redonda con texto, como las del juego. Devuelve dónde acaba.
function chip(ctx, x, y, text, fill) {
  ctx.font = `900 30px ${FONT}`;
  const w = ctx.measureText(text).width + 44;
  ctx.fillStyle = fill;
  roundRect(ctx, x, y, w, 56, 28);
  ctx.fill();
  ctx.fillStyle = C.ink;
  ctx.textBaseline = 'middle';
  ctx.fillText(text, x + 22, y + 29);
  ctx.textBaseline = 'alphabetic';
  return x + w;
}

// Dónde se juega, para quien vea la tarjeta: la dirección sin «https://».
const playAt = () => location.host;

export async function makeCard(room, creature) {
  const sample = `${creature.name}${creature.formName}${creature.code}Nidaria`;
  await Promise.all(['900', '700'].map((weight) => document.fonts.load(`${weight} 40px "Zen Maru Gothic"`, sample).catch(() => null)));

  const canvas = document.createElement('canvas');
  canvas.width = W;
  canvas.height = H;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = C.cream;
  ctx.fillRect(0, 0, W, H);

  // La foto, con las esquinas redondas y un canto de papel.
  const photo = room.snapshot(W - PAD * 2, PHOTO_H);
  ctx.save();
  roundRect(ctx, PAD, PAD, W - PAD * 2, PHOTO_H, 40);
  ctx.clip();
  ctx.drawImage(photo, PAD, PAD);
  ctx.restore();
  ctx.strokeStyle = C.paper;
  ctx.lineWidth = 6;
  roundRect(ctx, PAD + 3, PAD + 3, W - PAD * 2 - 6, PHOTO_H - 6, 37);
  ctx.stroke();

  const left = PAD + 24;
  const inner = W - left * 2;
  let y = PAD + PHOTO_H + 100;

  ctx.fillStyle = C.ink;
  fitFont(ctx, creature.name, 900, 84, inner);
  ctx.fillText(creature.name, left, y);

  y += 58;
  const sub = creature.stage === 'huevo' ? 'Huevo' : `${creature.formName} · ${creature.stageName}`;
  ctx.fillStyle = C.inkSoft;
  fitFont(ctx, sub, 700, 40, inner);
  ctx.fillText(sub, left, y);

  // Familia y carácter.
  y += 34;
  let x = chip(ctx, left, y, `Familia ${FAMILY_INFO[creature.genes.family].name}`, C.mintWash);
  for (const trait of creature.genes.traits) {
    const name = TRAITS[trait]?.name;
    if (!name) continue;
    ctx.font = `900 30px ${FONT}`;
    if (x + 16 + ctx.measureText(name).width + 44 > left + inner) break;
    x = chip(ctx, x + 16, y, name, C.sunWash);
  }

  // Pie: ADN a un lado y el nombre del juego al otro, sobre una línea.
  const footY = H - PAD - 12;
  ctx.fillStyle = C.line;
  ctx.fillRect(left, footY - 70, inner, 3);
  ctx.textAlign = 'left';
  ctx.fillStyle = C.inkFaint;
  ctx.font = `900 28px ${FONT}`;
  ctx.fillText('ADN', left, footY);
  const labelW = ctx.measureText('ADN ').width;
  ctx.fillStyle = C.ink;
  ctx.font = `900 38px ${FONT}`;
  ctx.fillText(creature.code, left + labelW + 4, footY);

  ctx.textAlign = 'right';
  ctx.fillStyle = C.ink;
  ctx.font = `900 46px ${FONT}`;
  ctx.fillText('Nidaria', left + inner, footY - 2);
  const brandW = ctx.measureText('Nidaria').width;
  ctx.fillStyle = C.inkSoft;
  ctx.font = `700 26px ${FONT}`;
  ctx.fillText(playAt(), left + inner - brandW - 20, footY - 4);
  ctx.textAlign = 'left';

  return canvas;
}

const toBlob = (canvas) => new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));

const fileName = (name) => `nidaria-${name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase() || 'criatura'}.png`;

function download(blob, name) {
  const url = URL.createObjectURL(blob);
  const link = h('a', { href: url, download: name });
  document.body.append(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10000);
}

// Hace la foto y enseña la tarjeta con los botones de compartir y guardar.
export async function shareCard(room, creature) {
  sfx('foto');
  let blob;
  try {
    blob = await toBlob(await makeCard(room, creature));
  } catch (error) {
    console.error(error);
  }
  if (!blob) {
    toast('No se ha podido hacer la foto. Inténtalo de nuevo.', 'error');
    return;
  }
  const name = fileName(creature.name);
  const file = new File([blob], name, { type: 'image/png' });
  const url = URL.createObjectURL(blob);
  const data = {
    files: [file],
    title: `${creature.name} en Nidaria`,
    text: `${creature.name} ${creature.stage === 'huevo' ? 'todavía es un huevo' : `es ${creature.formName}`}. Su ADN es ${creature.code}. Cría la tuya en ${location.origin}`,
  };
  const canShare = Boolean(navigator.canShare?.(data));

  // Compartir tiene que llamarse dentro del toque: por eso va en `run` y
  // no al cerrar el diálogo.
  const actions = [];
  if (canShare) {
    actions.push({
      label: 'Compartir', tone: 'pink', keep: true,
      run: () => navigator.share(data).catch((error) => {
        if (error?.name !== 'AbortError') toast('No se ha podido compartir. Prueba a guardarla.', 'error');
      }),
    });
  }
  actions.push({ label: 'Guardar imagen', tone: canShare ? null : 'pink', keep: true, run: () => download(blob, name) });
  actions.push({ label: 'Cerrar', value: null });

  await dialog({
    title: `La tarjeta de ${creature.name}`,
    extra: h('img', { class: 'card-preview', src: url, alt: `Tarjeta de ${creature.name}: ${creature.formName}, ADN ${creature.code}.`, width: String(W), height: String(H) }),
    actions,
  });
  URL.revokeObjectURL(url);
}
