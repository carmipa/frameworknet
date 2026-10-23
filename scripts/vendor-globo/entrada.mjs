// Um pacote só, com UMA instância do three: o globo e as nuvens (geo-globe.js) usam a mesma.
// Duas instâncias davam os erros "Timer"/"determinantAffine" que travavam a rotação (10/07/2026).
export * as THREE from 'three';
export { default as Globe } from 'globe.gl';
