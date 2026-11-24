import { getJson, h, lessonUrl, progress, renderHeader } from '/ui/learn.js';

renderHeader('saved');

const lessons = new Map();
const lesson = id => {
  if (!lessons.has(id)) lessons.set(id, getJson(`/api/lessons/${encodeURIComponent(id)}`).catch(() => null));
  return lessons.get(id);
};

async function render() {
  const notes = Object.entries(progress.notes());
  const notesBox = document.getElementById('notes');
  notesBox.replaceChildren(...(notes.length ? await Promise.all(notes.map(async ([id, text]) => {
    const l = await lesson(id);
    return h('article', { class: 'card', style: { gap: '8px' } },
      h('a', { href: lessonUrl(id), style: { fontWeight: 600 } }, l ? `${l.title} (${l.keyText})` : id),
      l ? h('p', { class: 'faint' }, l.reflectPrompt) : null,
      h('p', { style: { whiteSpace: 'pre-wrap', fontSize: '15px' } }, text));
  })) : [h('p', { class: 'muted' }, 'No notes yet. Each lesson has a private note under “Reflect”.')]));

  const done = Object.entries(progress.doneLessons()).sort((a, b) => String(b[1].at).localeCompare(String(a[1].at)));
  const doneBox = document.getElementById('done');
  doneBox.replaceChildren(...(done.length ? await Promise.all(done.map(async ([id, info]) => {
    const l = await lesson(id);
    return h('a', { class: 'question-link', href: lessonUrl(id), style: { border: '1px solid var(--border)' } },
      `${l ? `${l.title} · ${l.keyText}` : id}${info.at ? ` · ${info.at}` : ''}`);
  })) : [h('p', { class: 'muted' }, 'No lessons finished yet.')]));
}

render();
