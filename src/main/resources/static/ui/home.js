import {
  aiBadge, curatedBadge, errorBox, getJson, h, lessonUrl, levelName, levelPill, pathUrl, progress, progressBar,
  questionUrl, renderHeader, LANG, T,
} from '/ui/learn.js';
import { applyStaticText } from '/ui/i18n.js';

applyStaticText();
document.title = `${T.appTitle} · ${T.homeTitle}`;

renderHeader('home');

document.getElementById('legend').append(
  aiBadge(T.ai), h('span', {}, T.legendAi),
  curatedBadge(null), h('span', {}, T.legendCurated));
document.getElementById('paths-title').append(curatedBadge(null));
document.getElementById('questions-title').append(aiBadge(T.aiAnswers));

const LEVELS = ['all', 'beginner', 'intermediate', 'advanced'];
const LEVEL_KEY = 'tripitaka.level';

// ---------- Learning paths, filtered by level on the server ----------

function pathStatus(path, done) {
  if (!path.available) return T.lessonsComing;
  if (done === 0) return T.notStarted;
  return done >= path.lessonCount ? T.completed : T.nOfMLessonsDone(done, path.lessonCount);
}

function pathCard(path) {
  const done = progress.doneCount(path.lessonIds);
  return h('a', { class: 'path-card', href: pathUrl(path.id) },
    h('div', { class: 'row' }, levelPill(path.level), h('span', {}, T.nLessons(path.lessonCount))),
    h('h3', {}, path.title),
    h('p', { class: 'blurb' }, path.blurb),
    h('p', { class: 'faint' }, `${T.keyTexts}: ${path.keyTexts.join(', ')}`),
    progressBar(done, path.lessonCount, path.title),
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
    const paths = await getJson(`/api/paths?level=${encodeURIComponent(level)}&lang=${LANG}`);
    if (request !== levelRequest) return; // a newer filter click won
    grid.replaceChildren(...(paths.length ? paths.map(pathCard) : [h('p', { class: 'muted' }, T.noPathsHere)]));
  } catch (failure) {
    grid.replaceChildren(errorBox(`${T.loadPathsError} ${failure.message}`));
  }
}

const savedLevel = (() => { try { return localStorage.getItem(LEVEL_KEY); } catch { return null; } })();
document.getElementById('level-filter').append(...LEVELS.map(level => h('button', {
  type: 'button', class: 'toggle', 'data-level': level, 'aria-pressed': 'false', onclick: () => showLevel(level),
}, levelName(level))));
showLevel(LEVELS.includes(savedLevel) ? savedLevel : 'all');

// ---------- Continue, today's sutta, your study ----------

async function where() {
  const all = await getJson(`/api/paths?lang=${LANG}`);
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
    card.replaceChildren(h('span', { class: 'eyebrow' }, T.startAPath),
      h('p', { class: 'muted' }, T.lessonsBeingWritten));
    return;
  }
  const done = progress.doneCount(path.lessonIds);
  const nextId = path.lessonIds.find(id => !progress.isDone(id)) || path.lessonIds[0];
  const lesson = await getJson(`/api/lessons/${encodeURIComponent(nextId)}?lang=${LANG}`);
  card.replaceChildren(
    h('span', { class: 'eyebrow' }, current ? T.continuePath : T.startHere),
    h('h2', {}, path.title),
    h('p', { class: 'muted', style: { fontSize: '15px' } },
      `${T.lessonNofM(lesson.number, lesson.of)} · ${lesson.title} (${lesson.keyText})`),
    progressBar(done, path.lessonCount),
    h('div', { class: 'btn-row' },
      h('a', { class: 'btn btn-primary', href: lessonUrl(lesson.id) }, current ? T.continueLesson : T.startLesson),
      h('a', { class: 'btn', href: pathUrl(path.id) }, T.seeWholePath)));
}

async function dailyCard() {
  const card = document.getElementById('daily-card');
  try {
    const sutta = await getJson(`/api/daily-sutta?lang=${LANG}`);
    card.replaceChildren(
      h('div', { class: 'card-head' }, h('span', { class: 'eyebrow' }, T.todaysSutta), curatedBadge(sutta.reviewedBy)),
      h('h2', {}, sutta.title),
      h('p', { class: 'quote' }, sutta.text),
      sutta.note ? h('p', { class: 'faint' }, sutta.note) : null,
      h('div', { class: 'btn-row', style: { marginTop: 'auto' } },
        h('a', { class: 'btn', href: questionUrl(sutta.question), onclick: () => progress.readSutta(sutta.title) },
          T.readReflect)));
  } catch (failure) {
    card.replaceChildren(errorBox(`${T.loadDailyError} ${failure.message}`));
  }
}

async function studyCard({ current }) {
  const card = document.getElementById('study-card');
  const stat = (value, label) => h('div', { class: 'stat' }, h('strong', {}, String(value)), h('span', {}, label));
  let suggestion = h('p', { class: 'muted', style: { fontSize: '14px' } },
    T.progressNote);
  if (current) {
    const detail = await getJson(`/api/paths/${encodeURIComponent(current.id)}?lang=${LANG}`);
    const after = detail.after[0];
    suggestion = h('p', { class: 'muted', style: { fontSize: '14px' } }, T.nextSuggestion,
      h('strong', { style: { color: 'var(--text)', fontWeight: 600 } }, current.title),
      after ? [T.thenTry, h('a', { href: pathUrl(after.id) }, after.title), '.'] : '.');
  }
  card.replaceChildren(
    h('span', { class: 'eyebrow' }, T.yourStudy),
    h('div', { class: 'stats' },
      stat(progress.lessonsDone(), T.lessonsDone),
      stat(progress.suttasRead(), T.suttasRead),
      stat(progress.streak(), T.daysInRow)),
    suggestion);
}

where().then(state => Promise.all([continueCard(state), studyCard(state)])).catch(failure => {
  document.getElementById('continue-card').replaceChildren(errorBox(`${T.loadYourPathError} ${failure.message}`));
  studyCard({ current: null });
});
dailyCard();

// ---------- Dhamma questions ----------

getJson(`/api/themes?lang=${LANG}`).then(themes => {
  document.getElementById('themes').replaceChildren(...themes.map(theme => h('div', { class: 'theme-card' },
    h('h3', {}, theme.name),
    theme.questions.map(q => h('a', { class: 'question-link', href: questionUrl(q) }, q)))));
}).catch(failure => {
  document.getElementById('themes').replaceChildren(errorBox(`${T.loadQuestionsError} ${failure.message}`));
});
