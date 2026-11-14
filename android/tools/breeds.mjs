// Vuelca a Kotlin lo que las reglas necesitan de las razas
// (shared/breeds.js): especies, nombres, capas y cuántos colores de ojos
// tiene cada raza. Así createGenes da lo mismo en los dos lados.
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
  '// Especies y razas: lo que necesitan las reglas (nombres, capas y ojos).',
  '',
  'package com.nidaria.app.rules',
  '',
  'class SpeciesInfo(val name: String, val plural: String, val blurb: String)',
  '',
  '// `second`: el color de la primera zona pintada distinta del fondo.',
  'class CoatInfo(val name: String, val base: String, val second: String?)',
  '',
  'class BreedInfo(val id: String, val species: String, val name: String, val coats: List<CoatInfo>, val eyes: Int)',
  '',
  `val SPECIES = listOf(${SPECIES.map(str).join(', ')})`,
  '',
  'val SPECIES_INFO = mapOf(',
  ...SPECIES.map((id) => `    ${str(id)} to SpeciesInfo(${str(SPECIES_INFO[id].name)}, ${str(SPECIES_INFO[id].plural)}, ${str(SPECIES_INFO[id].blurb)}),`),
  ')',
  '',
  'val BREEDS = listOf(',
  ...BREEDS.map((breed) => {
    const coats = breed.coats.map((coat) => `CoatInfo(${str(coat.name)}, ${str(coat.base)}, ${second(coat) ? str(second(coat)) : 'null'})`).join(', ');
    return `    BreedInfo(${str(breed.id)}, ${str(breed.species)}, ${str(breed.name)}, listOf(${coats}), ${breed.eyes.length}),`;
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
