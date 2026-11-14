// Vuelca a Kotlin las razas de shared/breeds.js: lo que necesitan las
// reglas (especies, nombres, capas, ojos) y el plano de cada raza para el
// escultor de perros y gatos (scene/Pet.kt). Así los dos lados sacan lo mismo.
// Uso: node tools/breeds.mjs  (desde android/)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { SPECIES, SPECIES_INFO, BREEDS } from '../../shared/breeds.js';

const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, '..', 'app', 'src', 'main', 'kotlin', 'com', 'nidaria', 'app', 'rules', 'Breeds.kt');
const str = (text) => JSON.stringify(text);

// El segundo color de una capa: la primera zona pintada de otro color
// (como coatTone en shared/dna.js).
const second = (coat) => coat.paint.find(([, color]) => typeof color === 'string' && color !== coat.base)?.[1] ?? null;

const lines = [
  '// GENERADO por android/tools/breeds.mjs a partir de shared/breeds.js. No editar a mano.',
  '// Especies y razas: nombres, capas, ojos y el plano de cada raza.',
  '',
  'package com.nidaria.app.rules',
  '',
  'class SpeciesInfo(val name: String, val plural: String, val blurb: String)',
  '',
  '// Una zona pintada de la capa: un color o varios (manchas).',
  'class CoatPaint(val region: String, val color: String?, val colors: List<String>?)',
  '',
  '// `second`: el color de la primera zona pintada distinta del fondo.',
  'class CoatInfo(val name: String, val base: String, val second: String?, val paint: List<CoatPaint> = emptyList())',
  '',
  'class BreedBody(val chunk: Double, val leg: Double, val chest: Double, val long: Double)',
  'class BreedHead(val size: Double, val dome: Double, val width: Double)',
  'class BreedSnout(val len: Double, val width: Double, val drop: Double, val stop: Double)',
  'class BreedEars(val type: String, val size: Double, val set: Double)',
  'class BreedTail(val type: String, val size: Double)',
  '',
  'class BreedInfo(',
  '    val id: String,',
  '    val species: String,',
  '    val name: String,',
  '    val coats: List<CoatInfo>,',
  '    val eyeColors: List<String>,',
  '    val size: Double,',
  '    val body: BreedBody,',
  '    val head: BreedHead,',
  '    val snout: BreedSnout,',
  '    val ears: BreedEars,',
  '    val coat: String,',
  '    val tail: BreedTail,',
  '    val extras: List<String>,',
  '    val nose: String,',
  ') {',
  '    val eyes: Int get() = eyeColors.size',
  '}',
  '',
  `val SPECIES = listOf(${SPECIES.map(str).join(', ')})`,
  '',
  'val SPECIES_INFO = mapOf(',
  ...SPECIES.map((id) => `    ${str(id)} to SpeciesInfo(${str(SPECIES_INFO[id].name)}, ${str(SPECIES_INFO[id].plural)}, ${str(SPECIES_INFO[id].blurb)}),`),
  ')',
  '',
  'val BREEDS = listOf(',
  ...BREEDS.map((b) => {
    const num = (n) => (Number.isInteger(n) ? `${n}.0` : String(n));
    const list = (xs) => `listOf(${xs.map(str).join(', ')})`;
    const paint = (coat) => `listOf(${coat.paint.map(([region, color]) => (Array.isArray(color)
      ? `CoatPaint(${str(region)}, null, ${list(color)})`
      : `CoatPaint(${str(region)}, ${str(color)}, null)`)).join(', ')})`;
    const coats = b.coats.map((coat) => `CoatInfo(${str(coat.name)}, ${str(coat.base)}, ${second(coat) ? str(second(coat)) : 'null'}, ${paint(coat)})`).join(', ');
    return [
      `    BreedInfo(`,
      `        ${str(b.id)}, ${str(b.species)}, ${str(b.name)},`,
      `        listOf(${coats}),`,
      `        ${list(b.eyes)}, ${num(b.size)},`,
      `        BreedBody(${num(b.body.chunk)}, ${num(b.body.leg)}, ${num(b.body.chest)}, ${num(b.body.long)}),`,
      `        BreedHead(${num(b.head.size)}, ${num(b.head.dome)}, ${num(b.head.width)}),`,
      `        BreedSnout(${num(b.snout.len)}, ${num(b.snout.width)}, ${num(b.snout.drop)}, ${num(b.snout.stop)}),`,
      `        BreedEars(${str(b.ears.type)}, ${num(b.ears.size)}, ${num(b.ears.set)}),`,
      `        ${str(b.coat)}, BreedTail(${str(b.tail.type)}, ${num(b.tail.size)}), ${list(b.extras)}, ${str(b.nose)},`,
      `    ),`,
    ].join('\n');
  }),
  ')',
  '',
  'private val BREED_MAP = BREEDS.associateBy { it.id }',
  '',
  'fun getBreed(id: String?): BreedInfo? = id?.let { BREED_MAP[it] }',
  '',
  'fun breedsOf(species: String): List<BreedInfo> = BREEDS.filter { it.species == species }',
  '',
];
fs.writeFileSync(out, lines.join('\n'));
console.log('Breeds.kt:', BREEDS.length, 'razas');
