import {
  aiBadge, curatedBadge, errorBox, getJson, h, lessonUrl, levelPill, param, pathUrl, progress, progressBar,
  questionUrl, renderHeader, LANG, T,
} from '/ui/learn.js';

renderHeader('paths');
const page = document.getElementById('path-page');

function lessonStep(lesson, index, state) {
  const done = state === 'done';
  const current = state === 'current';
  return h('article', { class: `lesson-step ${state}`, 'aria-current': current ? 'step' : null },
    h('div', { class: 'step-dot', 'aria-hidden': 'true' }, done ? '✓' : String(index + 1)),
    h('div', { class: 'step-body' },
      h('div', { class: 'top' },
        h('h2', {}, h('a', { href: lessonUrl(lesson.id) }, `${index + 1}. ${lesson.title}`)),
        h('span', { class: 'faint', style: { fontSize: '13px', color: 'var(--muted)' } },
          done ? T.completed : current ? T.youAreHere : T.upNext)),
      h('p', { class: 'muted', style: { fontSize: '14px' } }, `${T.keyText}: ${lesson.keyText}`),
      h('div', { class: 'step-tags' }, T.stepTags.map(t => h('span', {}, t))),
      current ? h('div', { style: { marginTop: '6px' } },
        h('a', { class: 'btn btn-primary', href: lessonUrl(lesson.id) }, T.startLesson)) : null));
}

function aside(path) {
  const sections = [];
  if (path.outcomes.length) {
    sections.push(h('section', { class: 'card' },
      h('h2', {}, T.byTheEnd),
      h('ul', { class: 'outcomes' }, path.outcomes.map(o => h('li', {}, o)))));
  }
  if (path.before.length || path.after.length) {
    const group = (label, refs) => refs.length ? h('div', { class: 'fits' },
      h('span', { class: 'label' }, label), refs.map(r => h('a', { href: pathUrl(r.id) }, r.title))) : null;
    sections.push(h('section', { class: 'card', style: { gap: '12px' } },
      h('h2', {}, T.whereFits), group(T.before, path.before), group(T.after, path.after)));
  }
  if (path.questions.length) {
    sections.push(h('section', { class: 'card card-sunk' },
      h('h2', {}, T.questionsPeopleAsk),
      path.questions.map(q => h('a', { class: 'question-link', href: questionUrl(q) }, q))));
  }
  return h('aside', {}, sections);
}

async function render() {
  const id = param('id');
  if (!id) {
    location.replace('/#paths');
    return;
  }
  let path;
  try {
    path = await getJson(`/api/paths/${encodeURIComponent(id)}?lang=${LANG}`);
  } catch (failure) {
    page.replaceChildren(errorBox(`${T.loadPathError} ${failure.message}`), h('a', { href: '/#paths' }, T.allPaths));
    return;
  }
  document.title = `${T.learningPath}: ${path.title} · ${T.appTitle}`;
  const lessonIds = path.lessons.map(l => l.id);
  const done = progress.doneCount(lessonIds);
  const currentIndex = path.lessons.findIndex(l => !progress.isDone(l.id));

  const steps = path.available
    ? path.lessons.map((lesson, i) => lessonStep(lesson, i,
        progress.isDone(lesson.id) ? 'done' : i === currentIndex ? 'current' : 'next'))
    : [h('p', { class: 'notice' },
        T.pathBeingWritten(path.lessonCount),
        path.keyTexts.map((t, i) => [i ? ', ' : '', h('a', { href: questionUrl(T.whatDoesTeach(t)) }, t)]),
        '.')];

  page.replaceChildren(
    h('nav', { 'aria-label': 'Breadcrumb', class: 'breadcrumb' },
      h('a', { href: '/' }, T.home), ' / ', h('a', { href: '/#paths' }, T.navPaths), ' / ', path.title),
    h('section', { class: 'section-head', style: { gap: '12px' } },
      h('div', { class: 'title-row', style: { gap: '8px' } },
        levelPill(path.level),
        h('span', { class: 'muted', style: { fontSize: '14px' } },
          T.lessonsMinutes(path.lessonCount, path.minutesPerLesson)),
        curatedBadge(path.reviewedBy, T.curatedPath),
        aiBadge(T.aiExplains)),
      h('h1', { class: 'page-title' }, path.title),
      h('p', { class: 'lead', style: { maxWidth: '720px' } }, path.description || path.blurb),
      path.available ? h('div', { class: 'progress-row' },
        progressBar(done, path.lessonCount),
        h('span', { class: 'muted', style: { fontSize: '14px' } }, T.nOfMDone(done, path.lessonCount))) : null),
    h('div', { class: 'two-col' },
      h('section', { class: 'main-col', 'aria-label': 'Lessons' }, steps),
      aside(path)));
}

render();
