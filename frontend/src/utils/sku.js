const CATEGORY_CODES = [
  ['bateria', 'BAT'],
  ['pantalla', 'PAN'],
  ['display', 'DIS'],
  ['modulo', 'MOD'],
  ['flex', 'FLEX'],
  ['camara', 'CAM'],
  ['cargador', 'CRG'],
  ['cable', 'CAB'],
];

const QUALITY_CODES = {
  original: 'ORI',
  premium: 'PRE',
  incell: 'INC',
  oled: 'OLED',
  amoled: 'AMOLED',
  compatible: 'COM',
  generico: 'GEN',
};

function stripAccents(value) {
  return String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '');
}

function sanitize(value) {
  const normalized = stripAccents(value).toUpperCase().trim().replace(/\s+/g, ' ');
  return normalized || '';
}

function shortCode(value, length = 3) {
  const base = sanitize(value).replace(/\s+/g, '');
  return base ? base.slice(0, Math.max(length, 1)) : '';
}

function categoryCode(value) {
  const base = sanitize(value).toLowerCase();
  const match = CATEGORY_CODES.find(([token]) => base.includes(token));
  return match ? match[1] : shortCode(value, 3);
}

function qualityCode(value) {
  const base = sanitize(value).toLowerCase();
  return QUALITY_CODES[base] || shortCode(value, Math.min(Math.max(base.length, 3), 4));
}

export function normalizeSku(value) {
  const sanitized = sanitize(value)
    .replace(/[_/]/g, '-')
    .replace(/\s+/g, '-')
    .replace(/[^A-Z0-9-]/g, '')
    .replace(/-+/g, '-')
    .replace(/^-|-$/g, '');
  return sanitized;
}

export function isValidSku(value) {
  const normalized = normalizeSku(value);
  return /^[A-Z0-9]+(?:-[A-Z0-9]+)*$/.test(normalized);
}

export function suggestSku({ categoria, marca, nombreModelo, calidad }) {
  const segments = [
    categoryCode(categoria) || 'ITEM',
    shortCode(marca, 3) || 'GEN',
    normalizeSku(nombreModelo) || 'ITEM',
  ];

  const quality = qualityCode(calidad);
  if (quality) {
    segments.push(quality);
  }

  return normalizeSku(segments.join('-'));
}
