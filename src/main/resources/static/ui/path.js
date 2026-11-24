import {
  aiBadge, curatedBadge, errorBox, getJson, h, lessonUrl, levelPill, param, pathUrl, progress, progressBar,
  questionUrl, renderHeader,
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
          done ? 'Completed' : current ? 'You are here' : 'Up next')),
      h('p', { class: 'muted', style: { fontSize: '14px' } }, `Key text: ${lesson.keyText}`),
      h('div', { class: 'step-tags' }, ['Read', 'Ask', 'Reflect', 'Check yourself'].map(t => h('span', {}, t))),
      current ? h('div', { style: { marginTop: '6px' } },
        h('a', { class: 'btn btn-primary', href: lessonUrl(lesson.id) }, 'Start lesson')) : null));
}

function aside(path) {
  const sections = [];
  if (path.outcomes.length) {
    sections.push(h('section', { class: 'card' },
      h('h2', {}, 'By the end you can explain'),
      h('ul', { class: 'outcomes' }, path.outcomes.map(o => h('li', {}, o)))));
  }
  if (path.before.length || path.after.length) {
    const group = (label, refs) => refs.length ? h('div', { class: 'fits' },
      h('span', { class: 'label' }, label), refs.map(r => h('a', { href: pathUrl(r.id) }, r.title))) : null;
    sections.push(h('section', { class: 'card', style: { gap: '12px' } },
      h('h2', {}, 'Where this path fits'), group('Before', path.before), group('After', path.after)));
  }
  if (path.questions.length) {
    sections.push(h('section', { class: 'card card-sunk' },
      h('h2', {}, 'Questions people ask here'),
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
    path = await getJson(`/api/paths/${encodeURIComponent(id)}`);
  } catch (failure) {
    page.replaceChildren(errorBox(`Could not load this path. ${failure.message}`), h('a', { href: '/#paths' }, 'All learning paths'));
    return;
  }
  document.title = `Learning path: ${path.title} · Tripitaka companion`;
  const lessonIds = path.lessons.map(l => l.id);
  const done = progress.doneCount(lessonIds);
  const currentIndex = path.lessons.findIndex(l => !progress.isDone(l.id));

  const steps = path.available
    ? path.lessons.map((lesson, i) => lessonStep(lesson, i,
        progress.isDone(lesson.id) ? 'done' : i === currentIndex ? 'current' : 'next'))
    : [h('p', { class: 'notice' },
        `The ${path.lessonCount} lessons of this path are being written and reviewed. `,
        'Until then, ask about its key texts: ',
        path.keyTexts.map((t, i) => [i ? ', ' : '', h('a', { href: questionUrl(`What does ${t} teach?`) }, t)]),
        '.')];

  page.replaceChildren(
    h('nav', { 'aria-label': 'Breadcrumb', class: 'breadcrumb' },
      h('a', { href: '/' }, 'Home'), ' / ', h('a', { href: '/#paths' }, 'Learning paths'), ' / ', path.title),
    h('section', { class: 'section-head', style: { gap: '12px' } },
      h('div', { class: 'title-row', style: { gap: '8px' } },
        levelPill(path.level),
        h('span', { class: 'muted', style: { fontSize: '14px' } },
          `${path.lessonCount} lessons · about ${path.minutesPerLesson} minutes each`),
        curatedBadge(path.reviewedBy, 'Curated path'),
        aiBadge('AI explains inside each lesson')),
      h('h1', { class: 'page-title' }, path.title),
      h('p', { class: 'lead', style: { maxWidth: '720px' } }, path.description || path.blurb),
      path.available ? h('div', { class: 'progress-row' },
        progressBar(done, path.lessonCount),
        h('span', { class: 'muted', style: { fontSize: '14px' } }, `${done} of ${path.lessonCount} done`)) : null),
    h('div', { class: 'two-col' },
      h('section', { class: 'main-col', 'aria-label': 'Lessons' }, steps),
      aside(path)));
}

render();
