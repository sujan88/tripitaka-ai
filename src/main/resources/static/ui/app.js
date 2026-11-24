import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.esm.min.mjs';
import { marked } from 'https://cdn.jsdelivr.net/npm/marked@14/lib/marked.esm.js';
import DOMPurify from 'https://cdn.jsdelivr.net/npm/dompurify@3/dist/purify.es.mjs';

mermaid.initialize({
  startOnLoad: false,
  securityLevel: 'strict',
  theme: matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'neutral',
  themeVariables: { fontSize: '22px' },
  // useMaxWidth off: a big diagram scrolls instead of shrinking its text to fit.
  flowchart: { useMaxWidth: false, wrappingWidth: 240, padding: 20, nodeSpacing: 36, rankSpacing: 80 }
});

// The page picks the language (<html data-language="si"> on si.html); English is the default.
const LANGUAGE = document.documentElement.dataset.language === 'si' ? 'si' : 'en';
const STRINGS = {
  en: {
    enterQuestion: 'Enter a question first.', requestFailed: 'Request failed', searching: 'Searching the texts…',
    searchedFor: 'Searched for: ', mentalModel: 'Mental model', conceptMap: 'Concept map',
    diagramFailed: 'Diagram could not be drawn.', whereFrom: 'Where the passages came from',
    passages: (n, total, pct) => `${n} of ${total} passages (${pct}%)`, sources: 'Sources',
    unknowns: 'Not settled by these passages', thinking: 'Thinking…', ask: 'Ask',
    eachPart: 'Understanding each part', leadsTo: 'leads to',
  },
  si: {
    enterQuestion: 'පළමුව ප්‍රශ්නයක් ඇතුළත් කරන්න.', requestFailed: 'ඉල්ලීම අසාර්ථක විය',
    searching: 'ග්‍රන්ථ සොයමින්…', searchedFor: 'සෙවූ වචන: ', mentalModel: 'සංකල්ප සිතියම',
    conceptMap: 'සංකල්ප සිතියම', diagramFailed: 'රූප සටහන ඇඳිය නොහැකි විය.', whereFrom: 'ඡේද ලැබුණු තැන්',
    passages: (n, total, pct) => `ඡේද ${total} න් ${n} (${pct}%)`, sources: 'මූලාශ්‍ර',
    unknowns: 'මෙම ඡේදවලින් විසඳී නැති කරුණු', thinking: 'සිතමින්…', ask: 'අසන්න',
    eachPart: 'එක් එක් කොටස තේරුම් ගැනීම', leadsTo: 'යොමු වේ',
  },
}[LANGUAGE];

const thread = document.getElementById('thread');
const form = document.getElementById('ask-form');
const input = document.getElementById('question');
const askButton = document.getElementById('ask');
const formError = document.getElementById('form-error');

let conversationId = null;
let diagramCount = 0;
let busy = false;
let chatGeneration = 0;

document.getElementById('new-chat').addEventListener('click', () => {
  chatGeneration++;
  conversationId = null;
  thread.querySelectorAll('.turn').forEach(el => el.remove());
  document.getElementById('empty').hidden = false;
});

document.querySelectorAll('.chip').forEach(chip =>
  chip.addEventListener('click', () => { input.value = chip.textContent; form.requestSubmit(); }));

input.addEventListener('input', () => { formError.textContent = ''; });
input.addEventListener('keydown', e => {
  if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); form.requestSubmit(); }
});

form.addEventListener('submit', async event => {
  event.preventDefault();
  // Enter and the suggestion chips bypass the disabled button.
  if (busy) return;
  const generation = chatGeneration;
  const question = input.value.trim();
  if (!question) { formError.textContent = STRINGS.enterQuestion; return; }

  document.getElementById('empty').hidden = true;
  input.value = '';
  setBusy(true);
  const turn = addTurn(question);

  try {
    const response = await fetch('/api/ask', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question, conversationId, language: LANGUAGE })
    });
    const body = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(body.detail || STRINGS.requestFailed);
    // The user started a new conversation while this answer was loading.
    if (generation !== chatGeneration) return;
    conversationId = body.conversationId;
    await renderAnswer(turn, body);
  } catch (error) {
    turn.querySelector('.answer').innerHTML = `<p class="error">${escapeHtml(error.message)}</p>`;
  } finally {
    setBusy(false);
    input.focus();
  }
});

function addTurn(question) {
  const turn = document.createElement('article');
  turn.className = 'turn';
  turn.innerHTML = `
    <p class="question">${escapeHtml(question)}</p>
    <div class="answer"><p class="muted">${STRINGS.searching}</p></div>`;
  thread.appendChild(turn);
  turn.scrollIntoView({ behavior: 'smooth', block: 'start' });
  return turn;
}

async function renderAnswer(turn, data) {
  const answer = turn.querySelector('.answer');
  answer.innerHTML = '';

  if (data.searchQuery && data.searchQuery !== turn.querySelector('.question').textContent) {
    answer.insertAdjacentHTML('beforeend',
      `<p class="muted small">${STRINGS.searchedFor}${escapeHtml(data.searchQuery)}</p>`);
  }

  const prose = document.createElement('div');
  prose.className = 'prose';
  prose.innerHTML = DOMPurify.sanitize(marked.parse(data.answer || ''));
  answer.appendChild(prose);
  await drawInlineDiagrams(prose);

  if (data.keyConcepts?.length) {
    answer.insertAdjacentHTML('beforeend',
      `<div class="concepts">${data.keyConcepts.map(c => `<span class="tag">${escapeHtml(c)}</span>`).join('')}</div>`);
  }

  const panels = document.createElement('div');
  panels.className = 'panels';
  answer.appendChild(panels);

  if (data.mermaid) {
    const panel = document.createElement('section');
    panel.className = 'panel';
    panel.innerHTML = `<h3>${escapeHtml(data.mentalModel?.title || STRINGS.mentalModel)}</h3>
      <div class="diagram" tabindex="0" role="img" aria-label="${STRINGS.conceptMap}"></div>
      ${modelDetails(data.mentalModel)}`;
    panels.appendChild(panel);
    try {
      const { svg } = await mermaid.render(`mm-${++diagramCount}`, data.mermaid);
      panel.querySelector('.diagram').innerHTML = svg;
    } catch {
      panel.querySelector('.diagram').innerHTML = `<p class="muted">${STRINGS.diagramFailed}</p>`;
    }
  }

  const distribution = Object.entries(data.sourceDistribution || {});
  if (distribution.length) {
    const max = Math.max(...distribution.map(([, n]) => n));
    const total = distribution.reduce((sum, [, n]) => sum + n, 0);
    panels.insertAdjacentHTML('beforeend', `
      <section class="panel">
        <h3>${STRINGS.whereFrom}</h3>
        ${distribution.map(([nikaya, n]) => `
          <div class="bar-row">
            <span class="bar-label">${escapeHtml(nikaya)}</span>
            <span class="bar"><span style="width:${(n / max) * 100}%"></span></span>
            <span class="bar-value">${STRINGS.passages(n, total, Math.round((n / total) * 100))}</span>
          </div>`).join('')}
      </section>`);
  }

  if (data.citations?.length) {
    answer.insertAdjacentHTML('beforeend', `
      <section class="sources">
        <h3>${STRINGS.sources}</h3>
        ${data.citations.map(c => `
          <details>
            <summary>${LANGUAGE === 'si' ? '' : `<strong>${escapeHtml(c.textId.toUpperCase())}</strong> `}${escapeHtml(c.title)}
              <span class="muted small">· ${escapeHtml(c.nikaya)} · ${escapeHtml(c.reference)}</span></summary>
            <p>${escapeHtml(c.excerpt)}</p>
            ${LANGUAGE === 'si' ? tipitakaLkLink(c.textId) : suttaCentralLink(c.textId)}
          </details>`).join('')}
      </section>`);
  }

  if (data.unknowns?.length) {
    answer.insertAdjacentHTML('beforeend',
      `<aside class="unknowns"><h3>${STRINGS.unknowns}</h3><ul>${data.unknowns.map(u => `<li>${escapeHtml(u)}</li>`).join('')}</ul></aside>`);
  }
}

const DIAGRAM_START = /^\s*(flowchart|graph)\s+(TD|TB|BT|LR|RL)\b/;

/**
 * Fallback for diagram code the model writes into the answer despite the prompt:
 * draw it as a chart; if it isn't valid Mermaid, leave the text as it was.
 */
async function drawInlineDiagrams(prose) {
  for (const block of prose.querySelectorAll('pre > code')) {
    const source = block.textContent;
    const fenced = block.classList.contains('language-mermaid');
    if (!fenced && !DIAGRAM_START.test(source)) continue;
    try {
      const { svg } = await mermaid.render(`mm-${++diagramCount}`, source);
      const diagram = document.createElement('div');
      diagram.className = 'diagram inline-diagram';
      diagram.innerHTML = svg;
      block.parentElement.replaceWith(diagram);
    } catch {
      // Invalid Mermaid: keep the original text so nothing is lost.
    }
  }
}

/** One numbered card per diagram node: what it means and what it connects to. */
function modelDetails(model) {
  const nodes = model?.nodes || [];
  if (!nodes.some(node => node.detail)) return '';
  const labels = new Map(nodes.map(node => [node.id, node.label]));
  const cards = nodes.map((node, index) => {
    const links = (model.edges || []).filter(edge => edge.from === node.id).map(edge =>
      `<li>${escapeHtml(edge.label || STRINGS.leadsTo)}: <strong>${escapeHtml(labels.get(edge.to))}</strong></li>`);
    return `
      <li class="model-card">
        <span class="model-number" aria-hidden="true">${index + 1}</span>
        <div>
          <h4>${escapeHtml(node.label)}</h4>
          ${node.detail ? `<p>${escapeHtml(node.detail)}</p>` : ''}
          ${links.length ? `<ul class="model-links">${links.join('')}</ul>` : ''}
        </div>
      </li>`;
  });
  return `<h3 class="model-heading">${STRINGS.eachPart}</h3><ol class="model-cards">${cards.join('')}</ol>`;
}

/** Section keys from the tipitaka.lk reader (dn-1-1, an-1-1) are the site's own page addresses. */
function tipitakaLkLink(textId) {
  if (!/^(dn|mn|sn|an|kn)(-[a-z0-9]+)+$/.test(textId)) return '';
  return `<a href="https://tipitaka.lk/${encodeURIComponent(textId)}/sinh" target="_blank" rel="noopener">tipitaka.lk හි සම්පූර්ණ සූත්‍රය කියවන්න</a>`;
}

function suttaCentralLink(textId) {
  // Only bilara-style ids (mn10, sn56.11) have a SuttaCentral page; PDF ids like "volume1" do not.
  if (!/^(dn|mn|sn|an|dhp|snp|ud|iti|thag|thig|kp)\d[\d.-]*$/.test(textId)) return '';
  return `<a href="https://suttacentral.net/${encodeURIComponent(textId)}" target="_blank" rel="noopener">Read on SuttaCentral</a>`;
}

function setBusy(value) {
  busy = value;
  askButton.disabled = value;
  askButton.textContent = value ? STRINGS.thinking : STRINGS.ask;
}

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, ch =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}
