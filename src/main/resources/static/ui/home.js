import {
  aiBadge, curatedBadge, errorBox, getJson, h, lessonUrl, levelName, levelPill, pathUrl, progress, progressBar,
  questionUrl, renderHeader,
} from '/ui/learn.js';

renderHeader('home');

document.getElementById('legend').append(
  aiBadge('AI'), h('span', {}, 'Answers are written by AI from the Tripitaka and always show their sources.'),
  curatedBadge(null), h('span', {}, 'Paths, lessons and daily suttas are chosen by people.'));
document.getElementById('paths-title').append(curatedBadge(null));
document.getElementById('questions-title').append(aiBadge('AI answers'));

const LEVELS = ['all', 'beginner', 'intermediate', 'advanced'];
const LEVEL_KEY = 'tripitaka.level';

// ---------- Learning paths, filtered by level on the server ----------

function pathStatus(path, done) {
  if (!path.available) return 'Lessons coming soon';
  if (done === 0) return 'Not started';
  return done >= path.lessonCount ? 'Completed' : `${done} of ${path.lessonCount} lessons done`;
}

function pathCard(path) {
  const done = progress.doneCount(path.lessonIds);
  return h('a', { class: 'path-card', href: pathUrl(path.id) },
    h('div', { class: 'row' }, levelPill(path.level), h('span', {}, `${path.lessonCount} lessons`)),
    h('h3', {}, path.title),
    h('p', { class: 'blurb' }, path.blurb),
    h('p', { class: 'faint' }, `Key texts: ${path.keyTexts.join(', ')}`),
    progressBar(done, path.lessonCount, `${path.title} progress`),
    h('span', { class: 'status' }, pathStatus(path, done)));
}

let levelRequest = 0;
async function showLevel(level) {
  const request = ++levelRequest;
  try { localStorage.setItem(LEVEL_KEY, level); } catch { /* not kept */ }
  document.querySelectorAll('#level-filter .toggle').forEach(button =>
    button.setAttribute('aria-pressed', String(button.dataset.level === level)));
  const grid = document.getElementById('path-grid');
  try {
    const paths = await getJson(`/api/paths?level=${encodeURIComponent(level)}`);
    if (request !== levelRequest) return; // a newer filter click won
    grid.replaceChildren(...(paths.length ? paths.map(pathCard) : [h('p', { class: 'muted' }, 'No paths at this level yet.')]));
  } catch (failure) {
    grid.replaceChildren(errorBox(`Could not load the learning paths. ${failure.message}`));
  }
}

const savedLevel = (() => { try { return localStorage.getItem(LEVEL_KEY); } catch { return null; } })();
document.getElementById('level-filter').append(...LEVELS.map(level => h('button', {
  type: 'button', class: 'toggle', 'data-level': level, 'aria-pressed': 'false', onclick: () => showLevel(level),
}, level === 'all' ? 'All' : levelName(level))));
showLevel(LEVELS.includes(savedLevel) ? savedLevel : 'all');

// ---------- Continue, today's sutta, your study ----------

async function where() {
  const all = await getJson('/api/paths');
  const available = all.filter(p => p.available);
  const unfinished = p => progress.doneCount(p.lessonIds) < p.lessonIds.length;
  const last = progress.last();
  const current = available.find(p => last && p.id === last.pathId && unfinished(p))
    || available.find(p => progress.doneCount(p.lessonIds) > 0 && unfinished(p))
    || null;
  return { all, available, current };
}

async function continueCard({ available, current }) {
  const card = document.getElementById('continue-card');
  const path = current || available[0];
  if (!path) {
    card.replaceChildren(h('span', { class: 'eyebrow' }, 'Start a path'),
      h('p', { class: 'muted' }, 'Lessons are being written. Meanwhile, ask any question above.'));
    return;
  }
  const done = progress.doneCount(path.lessonIds);
  const nextId = path.lessonIds.find(id => !progress.isDone(id)) || path.lessonIds[0];
  const lesson = await getJson(`/api/lessons/${encodeURIComponent(nextId)}`);
  card.replaceChildren(
    h('span', { class: 'eyebrow' }, current ? 'Continue your path' : 'Start here'),
    h('h2', {}, path.title),
    h('p', { class: 'muted', style: { fontSize: '15px' } },
      `Lesson ${lesson.number} of ${lesson.of} · ${lesson.title} (${lesson.keyText})`),
    progressBar(done, path.lessonCount),
    h('div', { class: 'btn-row' },
      h('a', { class: 'btn btn-primary', href: lessonUrl(lesson.id) }, current ? 'Continue lesson' : 'Start lesson'),
      h('a', { class: 'btn', href: pathUrl(path.id) }, 'See the whole path')));
}

async function dailyCard() {
  const card = document.getElementById('daily-card');
  try {
    const sutta = await getJson('/api/daily-sutta');
    card.replaceChildren(
      h('div', { class: 'card-head' }, h('span', { class: 'eyebrow' }, 'Today’s sutta'), curatedBadge(sutta.reviewedBy)),
      h('h2', {}, sutta.title),
      h('p', { class: 'quote' }, sutta.text),
      sutta.note ? h('p', { class: 'faint' }, sutta.note) : null,
      h('div', { class: 'btn-row', style: { marginTop: 'auto' } },
        h('a', { class: 'btn', href: questionUrl(sutta.question), onclick: () => progress.readSutta(sutta.title) },
          'Read and reflect')));
  } catch (failure) {
    card.replaceChildren(errorBox(`Could not load today’s sutta. ${failure.message}`));
  }
}

async function studyCard({ current }) {
  const card = document.getElementById('study-card');
  const stat = (value, label) => h('div', { class: 'stat' }, h('strong', {}, String(value)), h('span', {}, label));
  let suggestion = h('p', { class: 'muted', style: { fontSize: '14px' } },
    'Your progress is kept in this browser. Finish a lesson to start a streak.');
  if (current) {
    const detail = await getJson(`/api/paths/${encodeURIComponent(current.id)}`);
    const after = detail.after[0];
    suggestion = h('p', { class: 'muted', style: { fontSize: '14px' } }, 'Next suggestion: finish ',
      h('strong', { style: { color: 'var(--text)', fontWeight: 600 } }, current.title),
      after ? [', then try ', h('a', { href: pathUrl(after.id) }, after.title), '.'] : '.');
  }
  card.replaceChildren(
    h('span', { class: 'eyebrow' }, 'Your study'),
    h('div', { class: 'stats' },
      stat(progress.lessonsDone(), 'lessons done'),
      stat(progress.suttasRead(), 'suttas read'),
      stat(progress.streak(), 'days in a row')),
    suggestion);
}

where().then(state => Promise.all([continueCard(state), studyCard(state)])).catch(failure => {
  document.getElementById('continue-card').replaceChildren(errorBox(`Could not load your path. ${failure.message}`));
  studyCard({ current: null });
});
dailyCard();

// ---------- Dhamma questions ----------

getJson('/api/themes').then(themes => {
  document.getElementById('themes').replaceChildren(...themes.map(theme => h('div', { class: 'theme-card' },
    h('h3', {}, theme.name),
    theme.questions.map(q => h('a', { class: 'question-link', href: questionUrl(q) }, q)))));
}).catch(failure => {
  document.getElementById('themes').replaceChildren(errorBox(`Could not load the questions. ${failure.message}`));
});
