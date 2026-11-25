// Lesson and answer page (docs/design-learning-ui/Lesson.dc.html).
//   /lesson.html?id=<lessonId>  a curated lesson: AI explanation of its question, quiz, reflection, next lesson
//   /lesson.html?q=<question>   a Dhamma question or a typed question, answered the same way
//   /lesson.html                an ask box
import { marked } from 'https://cdn.jsdelivr.net/npm/marked@14/lib/marked.esm.js';
import DOMPurify from 'https://cdn.jsdelivr.net/npm/dompurify@3/dist/purify.es.mjs';
import {
  aiBadge, curatedBadge, errorBox, getJson, h, lessonUrl, originalBadge, param, pathUrl, progress, progressBar,
  renderBottomNav, renderHeader, LANG, T,
} from '/ui/learn.js';

const page = document.getElementById('lesson-page');
const DEPTHS = ['simple', 'standard', 'deep', 'practice'];

// ---------- Talking to /api/ask ----------

async function ask(body) {
  const response = await fetch('/api/ask', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ language: LANG, ...body }),
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.detail || `Request failed (${response.status})`);
  return data;
}

// ---------- Rendering an answer ----------

/** "mn135" → "MN 135", "an6.63" → "AN 6.63"; other ids (e.g. tipitaka.lk keys) are shown as they are. */
export function formatRef(textId) {
  const m = /^([a-z]+)(\d[\d.-]*)$/.exec(textId || '');
  return m ? `${m[1].toUpperCase()} ${m[2]}` : textId;
}

function sourceLink(textId) {
  if (/^(dn|mn|sn|an|dhp|snp|ud|iti|thag|thig|kp)\d[\d.-]*$/.test(textId)) {
    return h('a', { href: `https://suttacentral.net/${encodeURIComponent(textId)}`, target: '_blank', rel: 'noopener' },
      T.readOnSuttaCentral);
  }
  if (/^(dn|mn|sn|an|kn|ap|atta-ap|anya-vm)(-[a-z0-9]+)+$/.test(textId)) {
    return h('a', { href: `https://tipitaka.lk/${encodeURIComponent(textId)}/sinh`, target: '_blank', rel: 'noopener' },
      T.readOnTipitakaLk);
  }
  return null;
}

const shortLayer = layer => (layer || 'Sutta').replace(/ \(.*\)$/, '');
const layerName = layer => T.layers[layer] || layer;

/** "Suttas: MN 135, AN 6.63. Commentary: …" from the citations, so the basis is visible at a glance. */
function basis(citations) {
  const byLayer = new Map();
  for (const c of citations) {
    const layer = shortLayer(c.layer);
    if (!byLayer.has(layer)) byLayer.set(layer, new Set());
    // tipitaka.lk keys (dn-1-2) mean nothing to a reader; their titles do.
    byLayer.get(layer).add(c.textId.includes('-') ? c.title : formatRef(c.textId));
  }
  if (!byLayer.size) return T.noCitation;
  return [...byLayer].map(([layer, refs]) =>
    `${layer === 'Sutta' ? T.suttas : T.interpretation(layerName(layer))}: ${[...refs].join(', ')}.`).join(' ');
}

function explanation(data, heading) {
  const prose = h('div', { class: 'prose' });
  prose.innerHTML = DOMPurify.sanitize(marked.parse(data.answer || ''));
  return h('section', { class: 'ai-card', 'aria-label': heading },
    h('div', { class: 'card-head' }, h('h2', {}, heading), aiBadge(T.aiExplanation)),
    prose,
    data.keyConcepts?.length ? h('div', { class: 'concepts', 'aria-label': T.keyConcepts },
      data.keyConcepts.map(c => h('span', {}, c))) : null,
    h('p', { class: 'faint' }, basis(data.citations || [])),
    data.unknowns?.length ? h('div', {},
      h('p', { class: 'faint', style: { fontWeight: 600 } }, T.notSettled),
      h('ul', { class: 'unknowns' }, data.unknowns.map(u => h('li', {}, u)))) : null);
}

const arrow = () => {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('width', '22');
  svg.setAttribute('height', '22');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  svg.classList.add('mm-arrow');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', 'M5 12h13M13 6l6 6-6 6');
  path.setAttribute('fill', 'none');
  path.setAttribute('stroke', 'currentColor');
  path.setAttribute('stroke-width', '2');
  path.setAttribute('stroke-linecap', 'round');
  svg.append(path);
  return svg;
};

/** The AI's concept map: nodes in reading order as boxes, then what each part means and how it connects. */
function mentalModel(model) {
  const nodes = model?.nodes || [];
  if (!nodes.length) return null;
  const edges = model.edges || [];
  const label = new Map(nodes.map(n => [n.id, n.label]));
  const linked = (a, b) => edges.some(e => e.from === a && e.to === b);
  const flow = [];
  nodes.forEach((node, i) => {
    if (i > 0) flow.push(linked(nodes[i - 1].id, node.id) ? arrow() : h('span', { class: 'mm-arrow', 'aria-hidden': 'true' }, '·'));
    flow.push(h('div', { class: 'mm-node' }, h('strong', {}, node.label)));
  });
  const loop = model.type === 'cycle'
    ? edges.find(e => nodes.findIndex(n => n.id === e.to) < nodes.findIndex(n => n.id === e.from))
    : null;
  return h('section', { class: 'card', 'aria-labelledby': 'mm-h' },
    h('div', { class: 'card-head' }, h('h2', { id: 'mm-h', style: { fontSize: '17px' } }, model.title || T.mentalModel),
      aiBadge(T.aiDiagram)),
    h('div', { class: 'mm-flow', role: 'img', 'aria-label': `${T.conceptMap}: ${nodes.map(n => n.label).join(T.then)}` }, flow),
    loop ? h('p', { class: 'muted', style: { fontSize: '14px' } },
      `↻ ${label.get(loop.from)} ${loop.label || T.leadsBack} ${label.get(loop.to)}, ${T.cycleContinues}`) : null,
    nodes.some(n => n.detail) ? h('ol', { class: 'mm-parts' }, nodes.map(node => {
      const out = edges.filter(e => e.from === node.id && label.has(e.to));
      return h('li', {}, h('strong', {}, node.label), node.detail ? [': ', node.detail] : null,
        out.length ? h('div', { class: 'links' },
          out.map((e, i) => [i ? '; ' : '', `${e.label || T.leadsTo} → ${label.get(e.to)}`])) : null);
    })) : null);
}

function sources(citations) {
  if (!citations?.length) return null;
  return h('section', { 'aria-labelledby': 'src-h', style: { display: 'flex', flexDirection: 'column', gap: '12px' } },
    h('div', { class: 'card-head' }, h('h2', { id: 'src-h', style: { fontSize: '17px' } }, T.sources), originalBadge()),
    citations.map(c => h('details', { class: 'source' },
      h('summary', {},
        h('span', { class: `layer ${shortLayer(c.layer) === 'Sutta' ? 'layer-sutta' : 'layer-other'}` }, layerName(shortLayer(c.layer))),
        h('strong', {}, formatRef(c.textId)),
        h('span', { class: 'title' }, [c.title, c.nikaya].filter(Boolean).join(' · '))),
      h('p', {}, c.excerpt),
      sourceLink(c.textId))));
}

const loading = text => h('div', { class: 'loading', role: 'status' }, text);

// ---------- One question: depth switch + answer, mental model, sources, follow-ups ----------

/**
 * Renders the answer area for one question into `slot`, and the follow-up panel into `followSlot`.
 * Each depth is asked once and kept, so switching back is free.
 */
function answerArea({ question, lessonId, slot, followSlot }) {
  const answers = new Map();
  let depth = 'standard';
  let conversationId = null;
  const turns = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '22px' } });
  const switcher = h('div', { role: 'group', 'aria-label': 'Explanation depth', class: 'toggle-group' });
  const body = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '22px' }, 'aria-live': 'polite' });
  slot.replaceChildren(switcher, body, turns);

  function drawSwitcher() {
    switcher.replaceChildren(...DEPTHS.map(id => h('button', {
      type: 'button', class: 'toggle', 'aria-pressed': String(id === depth), onclick: () => show(id),
    }, T.depths[id])));
  }

  async function show(next) {
    depth = next;
    drawSwitcher();
    body.setAttribute('aria-busy', 'true');
    body.replaceChildren(loading(T.searchingWriting));
    try {
      if (!answers.has(depth)) answers.set(depth, ask({ question, lessonId, depth }));
      const data = await answers.get(depth);
      if (next !== depth) return; // another depth was picked meanwhile
      conversationId = data.conversationId;
      body.replaceChildren(explanation(data, T.headings[depth]), mentalModel(data.mentalModel), sources(data.citations));
      followUps(data.followUps || []);
    } catch (failure) {
      answers.delete(next);
      if (next !== depth) return;
      body.replaceChildren(errorBox(`${T.couldNotAnswer} ${failure.message}`),
        h('div', {}, h('button', { type: 'button', class: 'btn', onclick: () => show(next) }, T.tryAgain)));
    } finally {
      body.removeAttribute('aria-busy');
    }
  }

  async function askFollowUp(text) {
    const turn = h('section', { style: { display: 'flex', flexDirection: 'column', gap: '16px' } },
      h('h2', { class: 'turn-q' }, text), loading(T.searching));
    turns.append(turn);
    turn.scrollIntoView({ behavior: 'smooth', block: 'start' });
    try {
      const data = await ask({ question: text, lessonId, depth, conversationId });
      conversationId = data.conversationId;
      turn.replaceChildren(h('h2', { class: 'turn-q' }, text), explanation(data, T.answer),
        mentalModel(data.mentalModel), sources(data.citations));
      followUps(data.followUps || []);
    } catch (failure) {
      turn.replaceChildren(h('h2', { class: 'turn-q' }, text), errorBox(`${T.couldNotAnswer} ${failure.message}`));
    }
  }

  function followUps(suggestions) {
    const input = h('input', { id: 'fu', type: 'text', class: 'text-input', placeholder: T.yourQuestionPlaceholder, maxlength: 1000, required: true });
    followSlot.replaceChildren(h('section', { class: 'card' },
      h('div', { class: 'card-head' }, h('h2', {}, T.askFollowUp), aiBadge(T.aiSuggestions)),
      suggestions.map(s => h('button', {
        type: 'button', class: 'question-link on-white', style: { border: 0, font: 'inherit', textAlign: 'left', cursor: 'pointer' },
        onclick: () => askFollowUp(s),
      }, s)),
      h('form', {
        class: 'follow-up-form', onsubmit: event => {
          event.preventDefault();
          const text = input.value.trim();
          if (text) { input.value = ''; askFollowUp(text); }
        },
      }, h('label', { for: 'fu', class: 'faint', style: { color: 'var(--muted)' } }, T.typeOwn), input,
        h('button', { type: 'submit', class: 'btn' }, T.askButton))));
  }

  drawSwitcher();
  followSlot.replaceChildren();
  show(depth);
}

// ---------- Curated parts of a lesson ----------

function quiz(lesson) {
  const q = lesson.quiz;
  if (!q) return null;
  const feedback = h('p', { style: { fontSize: '15px', color: 'var(--text-2)' }, 'aria-live': 'polite' });
  const options = q.options.map((text, i) => h('button', {
    type: 'button', class: 'quiz-option', onclick: event => {
      options.forEach(o => o.removeAttribute('data-result'));
      const right = i === q.answer;
      event.currentTarget.dataset.result = right ? 'right' : 'wrong';
      feedback.textContent = right
        ? `${T.right} ${q.explanation || ''}`
        : T.notQuite(lesson.keyText);
    },
  }, text));
  return h('section', { class: 'card', 'aria-labelledby': 'check-h' },
    h('div', { class: 'card-head' }, h('h2', { id: 'check-h', style: { fontSize: '17px' } }, T.checkYourself),
      curatedBadge(q.reviewedBy)),
    h('p', { style: { fontSize: '16px' } }, q.question),
    h('div', { class: 'quiz-options' }, options),
    feedback);
}

function reflect(lesson) {
  let timer;
  const note = h('textarea', { id: 'note', rows: 4, class: 'note', oninput: event => {
    clearTimeout(timer);
    const text = event.currentTarget.value;
    timer = setTimeout(() => progress.setNote(lesson.id, text.trim()), 400);
  } });
  note.value = progress.note(lesson.id);
  return h('section', { class: 'card card-sunk' },
    h('h2', {}, T.reflect),
    h('p', { style: { fontSize: '15px', lineHeight: 1.6 } }, lesson.reflectPrompt),
    h('label', { for: 'note', class: 'faint', style: { color: 'var(--muted)' } }, T.privateNote),
    note);
}

function lessonNav(lesson) {
  const finish = () => progress.markDone(lesson.id, lesson.path.id, lesson.keyText);
  const done = progress.isDone(lesson.id);
  return h('nav', { class: 'lesson-nav', 'aria-label': 'Lesson navigation' },
    lesson.previous
      ? h('a', { class: 'btn', href: lessonUrl(lesson.previous.id) }, T.previous(lesson.previous.title))
      : h('a', { class: 'btn', href: pathUrl(lesson.path.id) }, T.backToPath),
    lesson.next
      ? h('a', { class: 'btn btn-primary', href: lessonUrl(lesson.next.id), onclick: finish },
          done ? T.next(lesson.next.title) : T.markDoneNext(lesson.next.title))
      : h('a', { class: 'btn btn-primary', href: pathUrl(lesson.path.id), onclick: finish },
          done ? T.backToPath : T.markDoneFinish));
}

/** A square image with the lesson's curated key point and its sutta reference, saved as a PNG. */
function shareCard(lesson) {
  const make = () => {
    const size = 1080;
    const canvas = h('canvas', { width: size, height: size });
    const ctx = canvas.getContext('2d');
    ctx.fillStyle = '#FAF8F4';
    ctx.fillRect(0, 0, size, size);
    ctx.fillStyle = '#B4560F';
    ctx.fillRect(0, 0, size, 16);
    const wrap = (text, x, y, maxWidth, lineHeight, font, color) => {
      ctx.font = font;
      ctx.fillStyle = color;
      let line = '';
      for (const word of text.split(/\s+/)) {
        const test = line ? `${line} ${word}` : word;
        if (ctx.measureText(test).width > maxWidth && line) {
          ctx.fillText(line, x, y);
          line = word;
          y += lineHeight;
        } else {
          line = test;
        }
      }
      if (line) ctx.fillText(line, x, y);
      return y + lineHeight;
    };
    const font = 'Inter, "Noto Sans Sinhala", system-ui, sans-serif';
    let y = wrap(lesson.path.title.toUpperCase(), 90, 150, 900, 40, `600 30px ${font}`, '#7A3608');
    y = wrap(lesson.title, 90, y + 30, 900, 78, `600 64px ${font}`, '#1F1D1A');
    wrap(lesson.quiz?.explanation || lesson.question, 90, y + 50, 900, 56, `400 40px ${font}`, '#2E2A25');
    ctx.font = `600 36px ${font}`;
    ctx.fillStyle = '#8A3F0A';
    ctx.fillText(lesson.keyText, 90, size - 150);
    ctx.font = `400 26px ${font}`;
    ctx.fillStyle = '#6B655B';
    ctx.fillText(T.shareFooter, 90, size - 95);
    canvas.toBlob(blob => {
      const url = URL.createObjectURL(blob);
      const link = h('a', { href: url, download: `${lesson.id}.png` });
      document.body.append(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    }, 'image/png');
  };
  return h('section', { class: 'card', style: { gap: '8px' } },
    h('h2', {}, T.share),
    h('p', { class: 'muted', style: { fontSize: '14px' } }, T.shareText),
    h('button', { type: 'button', class: 'btn', onclick: make }, T.createShareCard));
}

function lessonBar(lesson) {
  const header = document.getElementById('site-header');
  header.className = 'lesson-bar';
  header.replaceChildren(h('div', { class: 'inner' },
    h('a', { class: 'back', href: pathUrl(lesson.path.id) }, '‹ ', lesson.path.title),
    progressBar(lesson.number, lesson.of, lesson.path.title),
    h('span', { class: 'muted', style: { fontSize: '14px' } }, T.lessonNofM(lesson.number, lesson.of))));
  renderBottomNav('paths');
}

// ---------- Modes ----------

async function lessonMode(id) {
  let lesson;
  try {
    lesson = await getJson(`/api/lessons/${encodeURIComponent(id)}?lang=${LANG}`);
  } catch (failure) {
    renderHeader('paths');
    page.replaceChildren(h('article', {}, errorBox(`${T.loadLessonError} ${failure.message}`),
      h('a', { href: '/#paths' }, T.allPaths)));
    return;
  }
  document.title = `${lesson.title} · ${T.appTitle}`;
  lessonBar(lesson);
  progress.visit(lesson.path.id, lesson.id);

  const answerSlot = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '22px' } });
  const followSlot = h('div');
  page.replaceChildren(
    h('article', {},
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '8px' } },
        h('div', { class: 'title-row' },
          h('span', { class: 'eyebrow' }, T.lessonKey(lesson.number, lesson.keyText)),
          curatedBadge(lesson.reviewedBy, T.curatedLesson)),
        h('h1', { class: 'lesson-title' }, lesson.title),
        h('p', { class: 'muted' }, lesson.question)),
      answerSlot,
      quiz(lesson),
      lessonNav(lesson)),
    h('aside', {}, reflect(lesson), followSlot, shareCard(lesson)));
  answerArea({ question: lesson.question, lessonId: lesson.id, slot: answerSlot, followSlot });
}

function questionMode(question) {
  renderHeader('ask');
  document.title = `${question} · ${T.appTitle}`;
  const answerSlot = h('div', { style: { display: 'flex', flexDirection: 'column', gap: '22px' } });
  const followSlot = h('div');
  page.replaceChildren(
    h('article', {},
      h('div', { style: { display: 'flex', flexDirection: 'column', gap: '8px' } },
        h('div', { class: 'title-row' }, h('span', { class: 'eyebrow' }, T.yourQuestion), aiBadge(T.aiAnswer)),
        h('h1', { class: 'lesson-title' }, question)),
      answerSlot),
    h('aside', {}, followSlot,
      h('section', { class: 'card card-sunk' }, h('h2', {}, T.learnStepByStep),
        h('p', { style: { fontSize: '15px' } }, T.pathsHelp),
        h('a', { class: 'btn', href: '/#paths' }, T.choosePathButton))));
  answerArea({ question, lessonId: null, slot: answerSlot, followSlot });
}

function askMode() {
  renderHeader('ask');
  const input = h('input', { id: 'ask', name: 'q', class: 'text-input', type: 'text', maxlength: 1000, required: true,
    placeholder: T.askPlaceholder });
  page.replaceChildren(h('article', {},
    h('h1', { class: 'lesson-title' }, T.askTitle),
    h('p', { class: 'lead' }, T.askLead),
    h('form', { class: 'ask-form', action: '/lesson.html', method: 'get' },
      h('label', { for: 'ask', class: 'sr-only' }, T.askLabel), input,
      h('button', { class: 'btn btn-primary', type: 'submit' }, T.askButton)),
    h('div', { class: 'legend' }, aiBadge(T.ai), h('span', {}, T.askLegend))));
  input.focus();
}

const lessonId = param('id');
const question = param('q')?.trim();
if (lessonId) lessonMode(lessonId);
else if (question) questionMode(question.slice(0, 1000));
else askMode();

