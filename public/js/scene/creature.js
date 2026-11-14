// Construye la criatura 3D (monstruo, perro o gato) y la anima. Cada
// especie esculpe su figura en su módulo (monster.js, pet.js), salvo las
// formas que tienen modelo (models.js, glb-creature.js); aquí se elige
// cuál y se le da vida: respira, parpadea, mira y reacciona.

import * as THREE from 'three';
import { gsap } from 'gsap';
import { clamp } from '/shared/rng.js';
import { isPet } from '/shared/dna.js';
import { getForm } from '/shared/forms.js';
import { buildMonster } from './monster.js';
import { buildPet } from './pet.js';
import { modelFor } from './models.js';
import { buildGlbCreature } from './glb-creature.js';
import { lidTexture, LID_OPEN, LID_SHUT } from './creature-kit.js';

export function buildCreature(formId, genes, options) {
  const model = modelFor(formId, genes);
  if (model) return buildGlbCreature(formId, genes, model);
  const family = getForm(formId).family ?? genes.family;
  return isPet(family) ? buildPet(formId, genes, options) : buildMonster(formId, genes, options);
}

// ── Animación ─────────────────────────────────────────────────

// Cierra el ojo según `lidValue` (0 abierto, 1 cerrado): con párpado, lo
// baja; sin él, aplasta la cuenta.
function setLid(eye) {
  if (eye.lid) eye.lid.rotation.x = LID_OPEN + (LID_SHUT - LID_OPEN) * eye.lidValue;
  else eye.open.scale.y = Math.max(0.08, 1 - eye.lidValue * 0.95);
}

// Envuelve una criatura construida y le da vida: respira, parpadea,
// mira y reacciona. `update` se llama en cada fotograma.
export class Actor {
  constructor(built) {
    Object.assign(this, built);
    this.mood = 'bien';
    this.blinkAt = 1.5;
    this.blink = 0;
    this.gaze = new THREE.Vector2();
    this.gazeTarget = new THREE.Vector2();
    this.gazeAt = 0;
    this.lean = 0;
    this.leanTarget = 0;
    this.busy = false;
    this.talk = 0;
    this.shiver = 0;
    this.squint = false;
    this.face = 'smile';
    this.wear = new Map();
    this.twitchAt = 3 + Math.random() * 4;
    this.twitch = 0;
    this.idleAt = 6 + Math.random() * 6;
    this.still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  }

  setMouth(name) {
    if (this.face === name) return;
    this.face = name;
    this.rig.mouth.material.map = this.rig.mouths[name] ?? this.rig.mouths.smile;
  }

  setMood(mood) {
    this.mood = mood;
    // Lo que arde (las llamas de los modelos) también sigue el humor.
    this.rig.fx?.mood(mood);
    this.applyEyes();
    if (!this.busy) this.setMouth(this.restMouth());
    const pale = mood === 'enferma';
    const blush = this.rig.blush ?? { rest: 0.62, happy: 0.8 };
    for (const cheek of this.rig.cheeks) {
      cheek.material.color.set(pale ? '#9ec3ea' : '#ff8fa9');
      cheek.material.opacity = pale ? Math.max(blush.happy, 0.45) : mood === 'feliz' ? blush.happy : blush.rest;
    }
  }

  // Ojos abiertos (cuentas) o cerrados (arcos pintados).
  applyEyes() {
    const asleep = this.mood === 'dormida';
    for (const eye of this.rig.eyes) {
      const shut = asleep || eye.content || this.squint;
      eye.open.visible = !shut;
      eye.closed.visible = shut;
      if (shut) eye.closed.material.map = lidTexture(this.squint && !asleep ? 'happy' : 'sleep');
    }
  }

  setSquint(on) {
    if (this.squint === on) return;
    this.squint = on;
    this.applyEyes();
  }

  restMouth() {
    if (this.mood === 'dormida') return 'flat';
    if (this.mood === 'mal' || this.mood === 'fatal' || this.mood === 'enferma') return 'sad';
    if (this.mood === 'regular') return 'flat';
    return 'smile';
  }

  lookAt(x, y) {
    this.gazeTarget.set(clamp(x, -1, 1), clamp(y, -1, 1));
    this.gazeAt = performance.now() / 1000 + 2.5;
  }

  update(t, dt) {
    const { rig, mood } = this;
    const asleep = mood === 'dormida';
    const low = mood === 'mal' || mood === 'fatal' || mood === 'enferma';
    const happy = mood === 'feliz';

    // Respiración: aplastar y estirar muy poco, más lento al dormir.
    const speed = asleep ? 1.1 : low ? 1.7 : 2.4;
    const depth = asleep ? 0.034 : 0.022;
    const breath = Math.sin(t * speed) * depth;
    if (!this.busy) {
      rig.squash.scale.set(1 - breath * 0.6, 1 + breath, 1 - breath * 0.6);
      rig.root.rotation.z = Math.sin(t * 0.9) * (happy ? 0.03 : 0.014);
    }

    // Mirada: sigue al dedo un rato y luego vaga sola.
    if (t > this.gazeAt) {
      this.gazeTarget.set((Math.random() - 0.5) * 0.9, (Math.random() - 0.5) * 0.4);
      this.gazeAt = t + 2 + Math.random() * 3;
    }
    this.gaze.lerp(this.gazeTarget, 1 - Math.exp(-dt * 5));
    this.lean += (this.leanTarget - this.lean) * (1 - Math.exp(-dt * 7));
    const droop = asleep ? 0.2 : low ? 0.16 : 0;
    if (!this.busy) {
      rig.headPivot.rotation.set(
        droop - this.gaze.y * 0.14,
        this.gaze.x * 0.3,
        this.lean * 0.22 + (asleep ? 0.1 : 0) + Math.sin(t * 0.7) * 0.025,
      );
    }
    for (const eye of rig.eyes) {
      // Con globo, mira girándolo; sin él, mueve la cuenta.
      if (eye.ball) eye.ball.rotation.set(-this.gaze.y * 0.3, this.gaze.x * 0.42, 0);
      else eye.open.position.set(this.gaze.x * eye.r * 0.3, this.gaze.y * eye.r * 0.2, 0);
    }

    // Parpadeo: la cuenta se aplasta un instante.
    if (!asleep && t > this.blinkAt) {
      this.blink = 0.12;
      this.blinkAt = t + 2.2 + Math.random() * 3.6;
    }
    this.blink = Math.max(0, this.blink - dt);
    const shut = this.blink > 0 ? 1 : low ? 0.32 : 0;
    for (const eye of rig.eyes) {
      const lid = Math.max(eye.restLid, shut);
      eye.lidValue += (lid - eye.lidValue) * (1 - Math.exp(-dt * 40));
      setLid(eye);
    }

    // Orejas, cola, alas y brazos. De vez en cuando una oreja da un respingo.
    if (t > this.twitchAt) {
      this.twitch = 1;
      this.twitchSide = Math.random() < 0.5 ? 0 : 1;
      this.twitchAt = t + 3 + Math.random() * 5;
    }
    this.twitch = Math.max(0, this.twitch - dt * 4);
    const wiggle = Math.sin(t * 1.3) * 0.05 + (happy ? Math.sin(t * 7) * 0.04 : 0);
    rig.ears.forEach((ear, i) => {
      const flick = i === this.twitchSide && !asleep ? Math.sin(this.twitch * Math.PI * 3) * 0.18 * this.twitch : 0;
      ear.pivot.rotation.z = ear.rest + ear.side * (wiggle - (low ? 0.22 : 0) + flick);
    });
    rig.tail.rotation.y = Math.sin(t * (happy ? 7 : 2.2)) * (asleep ? 0.03 : happy ? 0.5 : 0.2);
    for (const wing of rig.wings) wing.pivot.rotation.z = wing.rest + wing.side * Math.sin(t * (happy ? 9 : 2.6)) * (asleep ? 0.03 : 0.22);
    for (const arm of rig.arms) {
      // Las patas de los perros y gatos no se balancean: como mucho, dan la pata.
      if (arm.leg) arm.pivot.rotation.x = arm.restX;
      else arm.pivot.rotation.z = arm.rest + arm.side * Math.sin(t * 1.9 + arm.side) * 0.08;
    }
    if (rig.halo) rig.halo.position.y = rig.halo.userData.y + Math.sin(t * 1.6) * 0.012;

    if (this.shiver > 0 || mood === 'enferma') {
      this.shiver = Math.max(0, this.shiver - dt);
      rig.bounce.position.x = Math.sin(t * 46) * 0.006;
    } else {
      rig.bounce.position.x = 0;
    }

    // De vez en cuando hace algo por su cuenta, si está bien.
    if (t > this.idleAt) {
      this.idleAt = t + 7 + Math.random() * 8;
      if (!this.busy && !this.still && (mood === 'bien' || happy || mood === 'regular')) this.fidget(happy);
    }

    // Hablar: abre y cierra la boca mientras dura el bocadillo.
    if (this.talk > 0 && !this.busy) {
      this.talk -= dt;
      this.setMouth(Math.sin(t * 16) > 0 ? 'open' : this.restMouth());
      if (this.talk <= 0) this.setMouth(this.restMouth());
    }
  }

  // Reacciones de una vez. Devuelven la línea de tiempo de GSAP.
  act(fn) {
    this.timeline?.kill();
    this.busy = true;
    this.fidgeting = false;
    const tl = gsap.timeline({
      onComplete: () => {
        this.busy = false;
        this.setSquint(false);
        this.setMouth(this.restMouth());
      },
    });
    this.timeline = tl;
    fn(tl, this.rig);
    return tl;
  }

  hop(times = 1, height = 0.28) {
    return this.act((tl, rig) => {
      for (let i = 0; i < times; i++) {
        tl.to(rig.squash.scale, { x: 1.12, y: 0.86, z: 1.12, duration: 0.09, ease: 'power2.out' })
          .to(rig.squash.scale, { x: 0.92, y: 1.12, z: 0.92, duration: 0.12, ease: 'power2.out' })
          .to(rig.bounce.position, { y: height, duration: 0.2, ease: 'power2.out' }, '<')
          .to(rig.bounce.position, { y: 0, duration: 0.18, ease: 'power2.in' })
          .to(rig.squash.scale, { x: 1.14, y: 0.84, z: 1.14, duration: 0.07, ease: 'power1.out' })
          .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.3, ease: 'elastic.out(1, 0.5)' });
      }
      if (times > 1 && this.mood !== 'dormida') this.setSquint(true);
    });
  }

  eat(onBite) {
    this.setMouth('open');
    return this.act((tl, rig) => {
      for (let i = 0; i < 3; i++) {
        tl.to(rig.headPivot.rotation, { x: 0.34, duration: 0.14, ease: 'power2.out' })
          .call(() => { this.setMouth('flat'); this.setSquint(true); onBite?.(i); })
          .to(rig.squash.scale, { x: 1.06, y: 0.94, z: 1.06, duration: 0.08 }, '<')
          .to(rig.headPivot.rotation, { x: 0.02, duration: 0.16, ease: 'power2.inOut' })
          .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.14 }, '<')
          .call(() => { this.setSquint(false); this.setMouth(i < 2 ? 'open' : 'smile'); });
      }
      tl.to({}, { duration: 0.15 });
    });
  }

  shake() {
    this.setMouth('flat');
    return this.act((tl, rig) => {
      tl.to(rig.headPivot.rotation, { y: 0.4, duration: 0.11, ease: 'power2.out' })
        .to(rig.headPivot.rotation, { y: -0.4, duration: 0.18, ease: 'power2.inOut' })
        .to(rig.headPivot.rotation, { y: 0.3, duration: 0.16, ease: 'power2.inOut' })
        .to(rig.headPivot.rotation, { y: 0, duration: 0.2, ease: 'power2.out' });
    });
  }

  wash() {
    this.setMouth('open');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.root.rotation, { z: 0.12, duration: 0.12, ease: 'power1.inOut', yoyo: true, repeat: 7 })
        .to(rig.root.rotation, { z: 0, duration: 0.2, ease: 'power2.out' })
        .to(rig.squash.scale, { x: 1.1, y: 0.9, z: 1.1, duration: 0.1 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  nuzzle() {
    this.setMouth('smile');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.squash.scale, { x: 1.08, y: 0.92, z: 1.08, duration: 0.16, ease: 'power2.out' })
        .to(rig.headPivot.rotation, { z: this.leanTarget * 0.34, x: 0.1, duration: 0.2, ease: 'power2.out' }, '<')
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.5, ease: 'elastic.out(1, 0.4)' })
        .to(rig.headPivot.rotation, { z: 0, x: 0, duration: 0.35, ease: 'power2.inOut' }, '<0.1');
    });
  }

  spin() {
    this.setMouth('open');
    return this.act((tl, rig) => {
      this.setSquint(true);
      tl.to(rig.bounce.position, { y: 0.34, duration: 0.26, ease: 'power2.out' })
        .to(rig.root.rotation, { y: Math.PI * 2, duration: 0.6, ease: 'power2.inOut' }, '<')
        .to(rig.bounce.position, { y: 0, duration: 0.22, ease: 'power2.in' }, '>-0.24')
        .set(rig.root.rotation, { y: 0 })
        .to(rig.squash.scale, { x: 1.14, y: 0.84, z: 1.14, duration: 0.08 })
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.45)' });
    });
  }

  // Gestos de reposo: ladear la cabeza con curiosidad, balancearse o,
  // si está contenta, un saltito.
  // Se pueden cortar: un toque o un cuidado los sustituye (`fidgeting`).
  fidget(happy) {
    this.fidgetMove(happy);
    this.fidgeting = true;
  }

  fidgetMove(happy) {
    const pick = Math.random();
    if (happy && pick < 0.35) return this.hop(1, 0.1);
    const side = Math.random() < 0.5 ? -1 : 1;
    if (pick < 0.7) {
      return this.act((tl, rig) => {
        tl.to(rig.headPivot.rotation, { z: side * 0.26, y: side * 0.18, duration: 0.45, ease: 'back.out(2)' })
          .to(rig.headPivot.rotation, { z: side * 0.2, duration: 0.9, ease: 'sine.inOut' })
          .to(rig.headPivot.rotation, { z: 0, y: 0, duration: 0.5, ease: 'power2.inOut' });
      });
    }
    return this.act((tl, rig) => {
      tl.to(rig.root.rotation, { z: side * 0.08, duration: 0.3, ease: 'sine.inOut' })
        .to(rig.root.rotation, { z: -side * 0.08, duration: 0.5, ease: 'sine.inOut' })
        .to(rig.root.rotation, { z: 0, duration: 0.4, ease: 'sine.out' })
        .to(rig.squash.scale, { x: 1.05, y: 0.95, z: 1.05, duration: 0.12 }, '<')
        .to(rig.squash.scale, { x: 1, y: 1, z: 1, duration: 0.4, ease: 'elastic.out(1, 0.5)' });
    });
  }

  say(seconds) {
    this.talk = seconds;
  }

  // Para la foto: si está a mitad de parpadeo, abre los ojos ya.
  openEyes() {
    if (this.blink <= 0) return;
    this.blink = 0;
    for (const eye of this.rig.eyes) {
      eye.lidValue = eye.restLid;
      setLid(eye);
    }
  }
}
