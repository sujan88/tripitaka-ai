// Shared helpers for the guided learning pages: DOM building, AI/Curated badges, header, progress in localStorage.
// All text from the API is inserted as text nodes, never as HTML.
import { LANG, setLang, T } from '/ui/i18n.js';

export { LANG, T };

/** h('a', {href: '/', class: 'btn'}, 'Home', child, ...) builds an element; null/false children are skipped. */
export function h(tag, props = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(props || {})) {
    if (value === null || value === undefined || value === false) continue;
    if (key === 'class') el.className = value;
    else if (key === 'style' && typeof value === 'object') Object.assign(el.style, value);
    else if (key.startsWith('on') && typeof value === 'function') el.addEventListener(key.slice(2), value);
    else el.setAttribute(key, value === true ? '' : String(value));
  }
  for (const child of children.flat(Infinity)) {
    if (child === null || child === undefined || child === false) continue;
    el.append(child instanceof Node ? child : document.createTextNode(String(child)));
  }
  return el;
}

export async function getJson(url) {
  const response = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!response.ok) {
    let detail = '';
    try { detail = (await response.json()).detail || ''; } catch { /* not JSON */ }
    throw new Error(detail || `Request failed (${response.status})`);
  }
  return response.json();
}

export const param = name => new URLSearchParams(location.search).get(name);

// ---------- Badges (docs/HANDOFF-learning-ui.md: "AI vs curated labelling") ----------

const SVG_NS = 'http://www.w3.org/2000/svg';

function sparkle() {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('width', '14');
  svg.setAttribute('height', '14');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute('d', 'M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z');
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'currentColor');
  path.setAttribute('stroke-width', '1.8');
  path.setAttribute('stroke-linejoin', 'round');
  svg.append(path);
  return svg;
}

/** Blue AI badge: answers, mental models, follow-up suggestions, "AI explains inside each lesson". */
export const aiBadge = (text = T.ai) => h('span', { class: 'badge badge-ai' }, sparkle(), text);

/** Green Curated badge: paths, lessons, daily sutta, quiz. Says "reviewed by a teacher" once reviewed. */
export function curatedBadge(reviewedBy, text = T.curated) {
  return h('span', { class: 'badge badge-curated', title: reviewedBy || null },
    reviewedBy ? `${text} · ${T.reviewed}` : text);
}

/** Grey badge on the sources list. */
export const originalBadge = () => h('span', { class: 'badge badge-original' }, T.originalTexts);

// ---------- Small pieces ----------

export const levelName = level => (level === 'all' ? T.levelAll : T[level] || level);
export const levelPill = level => h('span', { class: `pill pill-${level}` }, levelName(level));

export function progressBar(done, total, label = 'Path progress') {
  const pct = total ? Math.round((100 * done) / total) : 0;
  return h('div', {
    class: 'progress', role: 'progressbar', 'aria-label': label,
    'aria-valuenow': done, 'aria-valuemin': 0, 'aria-valuemax': total,
  }, h('div', { style: { width: `${pct}%` } }));
}

export const lessonUrl = id => `/lesson.html?id=${encodeURIComponent(id)}`;
export const questionUrl = question => `/lesson.html?q=${encodeURIComponent(question)}`;
export const pathUrl = id => `/path.html?id=${encodeURIComponent(id)}`;

function logo() {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('width', '28');
  svg.setAttribute('height', '28');
  svg.setAttribute('viewBox', '0 0 28 28');
  svg.setAttribute('aria-hidden', 'true');
  const circle = document.createElementNS(SVG_NS, 'circle');
  Object.entries({ cx: 14, cy: 14, r: 12, fill: 'none', stroke: 'var(--accent-bar)', 'stroke-width': 2 })
    .forEach(([k, v]) => circle.setAttribute(k, v));
  const spokes = document.createElementNS(SVG_NS, 'path');
  Object.entries({ d: 'M14 6v16M6 14h16M8.3 8.3l11.4 11.4M19.7 8.3L8.3 19.7', stroke: 'var(--accent-bar)', 'stroke-width': 1.5 })
    .forEach(([k, v]) => spokes.setAttribute(k, v));
  svg.append(circle, spokes);
  return svg;
}

// The free conversation page is per language: the English chat or the Sinhala chat (si.html).
export const NAV = [
  { id: 'home', label: T.navHome, href: '/' },
  { id: 'paths', label: T.navPaths, href: '/#paths' },
  { id: 'ask', label: T.navAsk, href: '/lesson.html' },
  { id: 'saved', label: T.navSaved, href: '/saved.html' },
  { id: 'chat', label: T.navChat, href: LANG === 'si' ? '/si.html' : '/chat.html' },
];

// Bottom navigation on phones (docs/design-learning-ui/Mobile.dc.html): Home · Paths · Ask · Saved.
const BOTTOM_NAV = [
  { id: 'home', label: T.navHome, href: '/', icon: 'M4 11l8-7 8 7v9h-5v-6H9v6H4z' },
  { id: 'paths', label: T.navPathsShort, href: '/#paths', icon: 'M5 19c3-6 11-2 14-14M5 19h4M19 5v4' },
  { id: 'ask', label: T.navAsk, href: '/lesson.html', icon: 'M4 5h16v11H9l-5 4z' },
  { id: 'saved', label: T.navSaved, href: '/saved.html', icon: 'M6 4h12v16l-6-4-6 4z' },
];

function navIcon(d) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('width', '22');
  svg.setAttribute('height', '22');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS(SVG_NS, 'path');
  path.setAttribute('d', d);
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'currentColor');
  path.setAttribute('stroke-width', '1.8');
  path.setAttribute('stroke-linejoin', 'round');
  path.setAttribute('stroke-linecap', 'round');
  svg.append(path);
  return svg;
}

/** Adds the phone bottom navigation (hidden on wider screens by learn.css). */
export function renderBottomNav(active) {
  document.querySelector('.bottom-nav')?.remove();
  document.body.classList.add('has-bottom-nav');
  document.body.append(h('nav', { class: 'bottom-nav', 'aria-label': 'Main' },
    BOTTOM_NAV.map(item => h('a', { href: item.href, 'aria-current': item.id === active ? 'page' : null },
      navIcon(item.icon), item.label))));
}

/** Fills <header id="site-header"> with the brand, main navigation and language switch. */
export function renderHeader(active) {
  const header = document.getElementById('site-header');
  header.className = 'site-header';
  header.replaceChildren(h('div', { class: 'inner' },
    h('a', { class: 'brand', href: '/' }, logo(), T.appTitle),
    h('nav', { class: 'main-nav', 'aria-label': 'Main' },
      NAV.map(item => h('a', { href: item.href, 'aria-current': item.id === active ? 'page' : null }, item.label))),
    languageSwitch()));
  renderBottomNav(active);
}

/** Same page in the other language: the link sets ?lang=, which i18n.js remembers. */
function languageLink(value) {
  const url = new URL(location.href);
  url.searchParams.set('lang', value);
  return url.pathname + url.search + url.hash;
}

function languageSwitch() {
  const option = (value, long, short, label) => h('a', {
    href: languageLink(value), lang: value, 'aria-current': LANG === value ? 'true' : null, 'aria-label': label,
    onclick: () => setLang(value),
  }, h('span', { class: 'long' }, long), h('span', { class: 'short', 'aria-hidden': 'true' }, short));
  return h('div', { class: 'lang-switch', role: 'group', 'aria-label': 'Language / භාෂාව' },
    option('en', 'English', 'EN', 'English'),
    option('si', 'සිංහල', 'සිං', 'සිංහල (Sinhala)'));
}

export function errorBox(message) {
  return h('p', { class: 'error-box', role: 'alert' }, message);
}

// ---------- Progress, kept in this browser until accounts exist ----------

const KEY = 'tripitaka.progress.v1';
const today = () => new Date().toLocaleDateString('en-CA'); // YYYY-MM-DD in local time

function blank() {
  return { done: {}, notes: {}, days: [], suttas: [], last: null };
}

function load() {
  try {
    const stored = JSON.parse(localStorage.getItem(KEY));
    return stored && typeof stored === 'object' ? { ...blank(), ...stored } : blank();
  } catch {
    return blank();
  }
}

function save(state) {
  try { localStorage.setItem(KEY, JSON.stringify(state)); } catch { /* storage full or blocked: progress not kept */ }
}

function touch(state) {
  const day = today();
  if (!state.days.includes(day)) state.days = [...state.days, day].slice(-400);
}

export const progress = {
  isDone: lessonId => Boolean(load().done[lessonId]),
  doneCount: lessonIds => lessonIds.filter(id => load().done[id]).length,
  lessonsDone: () => Object.keys(load().done).length,
  suttasRead: () => load().suttas.length,
  last: () => load().last,

  /** Days in a row with any study, counting today or, if nothing yet today, up to yesterday. */
  streak() {
    const days = new Set(load().days);
    const cursor = new Date();
    if (!days.has(cursor.toLocaleDateString('en-CA'))) cursor.setDate(cursor.getDate() - 1);
    let count = 0;
    while (days.has(cursor.toLocaleDateString('en-CA'))) {
      count++;
      cursor.setDate(cursor.getDate() - 1);
    }
    return count;
  },

  visit(pathId, lessonId) {
    const state = load();
    state.last = { pathId, lessonId };
    touch(state);
    save(state);
  },

  markDone(lessonId, pathId, keyText) {
    const state = load();
    state.done[lessonId] = { pathId, at: today() };
    if (keyText && !state.suttas.includes(keyText)) state.suttas.push(keyText);
    state.last = { pathId, lessonId };
    touch(state);
    save(state);
  },

  readSutta(id) {
    const state = load();
    if (!state.suttas.includes(id)) state.suttas.push(id);
    touch(state);
    save(state);
  },

  note: lessonId => load().notes[lessonId] || '',
  setNote(lessonId, text) {
    const state = load();
    if (text) state.notes[lessonId] = text; else delete state.notes[lessonId];
    save(state);
  },
  notes: () => load().notes,
  doneLessons: () => load().done,
};
