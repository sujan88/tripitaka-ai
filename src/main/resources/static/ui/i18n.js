// English / Sinhala for the guided learning pages. The choice is kept in this browser; ?lang=si or ?lang=en in a
// link sets it. Same wording as the mobile app (tripitaka-mobile/src/i18n.ts).

const KEY = 'tripitaka.language';

export function lang() {
  const fromUrl = new URLSearchParams(location.search).get('lang');
  if (fromUrl === 'si' || fromUrl === 'en') {
    try { localStorage.setItem(KEY, fromUrl); } catch { /* not kept */ }
    return fromUrl;
  }
  try { return localStorage.getItem(KEY) === 'si' ? 'si' : 'en'; } catch { return 'en'; }
}

export function setLang(value) {
  try { localStorage.setItem(KEY, value); } catch { /* not kept */ }
}

const en = {
  appTitle: 'Tripitaka companion',
  navHome: 'Home', navPaths: 'Learning paths', navAsk: 'Ask', navSaved: 'Saved', navChat: 'Conversation',
  navPathsShort: 'Paths',
  // badges
  ai: 'AI', aiAnswers: 'AI answers', aiAnswer: 'AI answer', aiExplains: 'AI explains inside each lesson',
  aiExplanation: 'AI explanation · based on the sources below', aiDiagram: 'AI-drawn diagram',
  aiSuggestions: 'AI suggestions', curated: 'Curated', curatedPath: 'Curated path', curatedLesson: 'Curated lesson',
  reviewed: 'reviewed by a teacher', originalTexts: 'Original texts · not AI',
  levelAll: 'All', beginner: 'Beginner', intermediate: 'Intermediate', advanced: 'Advanced',
  // home
  homeTitle: 'Learn the Dhamma, step by step',
  homeLead: 'Follow a guided path, or ask any question. Every answer cites the suttas it comes from.',
  askLabel: 'Ask a question', askButton: 'Ask',
  askPlaceholder: 'Ask anything about the Dhamma, e.g. “What is reborn if there is no soul?”',
  legendAi: 'Answers are written by AI from the Tripitaka and always show their sources.',
  legendCurated: 'Paths, lessons and daily suttas are chosen by people.',
  choosePath: 'Choose a learning path', pathsLead: 'Each path is a short series of lessons: read a sutta, ask, reflect, practise.',
  dhammaQuestions: 'Dhamma questions, answered from the texts',
  questionsLead: 'Questions chosen by people; the answer is written by AI from the texts, with a concept map and what to read next.',
  footerTexts: 'Texts: SuttaCentral (CC0) and the Buddha Jayanthi Tripitaka via tipitaka.lk (CC BY-ND 4.0).',
  footerAid: 'Answers are study aids. Check important points with a teacher.',
  lessonsComing: 'Lessons coming soon', notStarted: 'Not started', completed: 'Completed',
  nOfMLessonsDone: (d, t) => `${d} of ${t} lessons done`, nLessons: n => `${n} lessons`, keyTexts: 'Key texts',
  noPathsHere: 'No paths at this level yet.', loadPathsError: 'Could not load the learning paths.',
  startAPath: 'Start a path', lessonsBeingWritten: 'Lessons are being written. Meanwhile, ask any question above.',
  continuePath: 'Continue your path', startHere: 'Start here',
  lessonNofM: (n, m) => `Lesson ${n} of ${m}`, continueLesson: 'Continue lesson', startLesson: 'Start lesson',
  seeWholePath: 'See the whole path', loadYourPathError: 'Could not load your path.',
  todaysSutta: 'Today’s sutta', readReflect: 'Read and reflect', loadDailyError: 'Could not load today’s sutta.',
  yourStudy: 'Your study', lessonsDone: 'lessons done', suttasRead: 'suttas read', daysInRow: 'days in a row',
  progressNote: 'Your progress is kept in this browser. Finish a lesson to start a streak.',
  nextSuggestion: 'Next suggestion: finish ', thenTry: ', then try ', loadQuestionsError: 'Could not load the questions.',
  // path
  home: 'Home', lessonsMinutes: (n, m) => `${n} lessons · about ${m} minutes each`,
  nOfMDone: (d, t) => `${d} of ${t} done`, youAreHere: 'You are here', upNext: 'Up next', keyText: 'Key text',
  stepTags: ['Read', 'Ask', 'Reflect', 'Check yourself'],
  pathBeingWritten: n => `The ${n} lessons of this path are being written and reviewed. Until then, ask about its key texts: `,
  whatDoesTeach: t => `What does ${t} teach?`,
  byTheEnd: 'By the end you can explain', whereFits: 'Where this path fits', before: 'Before', after: 'After',
  questionsPeopleAsk: 'Questions people ask here', loadPathError: 'Could not load this path.', allPaths: 'All learning paths',
  learningPath: 'Learning path',
  // lesson and answers
  depths: { simple: 'Simple', standard: 'Standard', deep: 'Go deeper', practice: 'Practise it' },
  headings: { simple: 'In simple words', standard: 'What the texts say', deep: 'Going deeper', practice: 'Putting it into practice' },
  answer: 'Answer', suttas: 'Suttas',
  interpretation: layer => `${layer} (interpretation, not the Buddha’s words)`,
  noCitation: 'No passage was cited, so treat this answer as unverified.',
  notSettled: 'Not settled by these passages', sources: 'Sources', keyConcepts: 'Key concepts',
  readOnSuttaCentral: 'Read the whole sutta on SuttaCentral', readOnTipitakaLk: 'Read it on tipitaka.lk',
  searchingWriting: 'Searching the texts and writing an explanation…', searching: 'Searching the texts…',
  couldNotAnswer: 'Could not get an answer.', tryAgain: 'Try again',
  askFollowUp: 'Ask a follow-up', typeOwn: 'Or type your own', yourQuestionPlaceholder: 'Your question',
  checkYourself: 'Check yourself', right: 'Right.', notQuite: k => `Not quite. Look again at ${k} and try another answer.`,
  reflect: 'Reflect', privateNote: 'Private note · kept in this browser only',
  share: 'Share this lesson', shareText: 'Makes an image card with the key point and the sutta reference.',
  createShareCard: 'Create share card', shareFooter: 'Tripitaka companion · curated lesson',
  previous: t => `Previous: ${t}`, backToPath: 'Back to the path', next: t => `Next: ${t}`,
  markDoneNext: t => `Mark done · Next: ${t}`, markDoneFinish: 'Mark done · Finish the path',
  lessonKey: (n, k) => `Lesson ${n} · Key text ${k}`, loadLessonError: 'Could not load this lesson.',
  yourQuestion: 'Your question', learnStepByStep: 'Learn it step by step',
  pathsHelp: 'Curated paths take you through the key suttas in order.', choosePathButton: 'Choose a learning path',
  askTitle: 'Ask a question', askLead: 'The answer is written by AI from the Tripitaka, with a concept map and the sources it used.',
  askLegend: 'Answers are written by AI and always show their sources.',
  mentalModel: 'Mental model', leadsTo: 'leads to', leadsBack: 'leads back to', cycleContinues: 'so the cycle continues.',
  conceptMap: 'Concept map', then: ', then ',
  layers: { Sutta: 'Sutta', Abhidhamma: 'Abhidhamma', Commentary: 'Commentary', Visuddhimagga: 'Visuddhimagga' },
  // saved
  savedLead: 'Your private notes and the lessons you have finished. They are kept in this browser only.',
  yourNotes: 'Your notes', noNotes: 'No notes yet. Each lesson has a private note under “Reflect”.',
  lessonsDoneTitle: 'Lessons done', noLessonsDone: 'No lessons finished yet.',
};

const si = {
  appTitle: 'ත්‍රිපිටක සහායකය',
  navHome: 'මුල් පිටුව', navPaths: 'ඉගෙනුම් මාර්ග', navAsk: 'අසන්න', navSaved: 'සුරැකි', navChat: 'සංවාදය',
  navPathsShort: 'මාර්ග',
  ai: 'AI', aiAnswers: 'AI පිළිතුරු', aiAnswer: 'AI පිළිතුර', aiExplains: 'සෑම පාඩමකම AI පැහැදිලි කිරීමක්',
  aiExplanation: 'AI පැහැදිලි කිරීම · පහත මූලාශ්‍ර ඇසුරින්', aiDiagram: 'AI ඇඳි රූප සටහන',
  aiSuggestions: 'AI යෝජනා', curated: 'තෝරාගත්', curatedPath: 'තෝරාගත් මාර්ගය', curatedLesson: 'තෝරාගත් පාඩම',
  reviewed: 'ගුරුවරයෙකු පරීක්ෂා කළ', originalTexts: 'මුල් ග්‍රන්ථ · AI නොවේ',
  levelAll: 'සියල්ල', beginner: 'ආරම්භක', intermediate: 'මධ්‍යම', advanced: 'උසස්',
  homeTitle: 'ධර්මය පියවරෙන් පියවර ඉගෙන ගන්න',
  homeLead: 'මඟ පෙන්වන මාර්ගයක් අනුගමනය කරන්න, නැතහොත් ඕනෑම ප්‍රශ්නයක් අසන්න. සෑම පිළිතුරක්ම එය ගත් සූත්‍ර උපුටා දක්වයි.',
  askLabel: 'ප්‍රශ්නයක් අසන්න', askButton: 'අසන්න',
  askPlaceholder: 'ධර්මය ගැන ඕනෑම දෙයක් අසන්න, උදා. “ආත්මයක් නැත්නම් නැවත උපදින්නේ කුමක්ද?”',
  legendAi: 'පිළිතුරු ත්‍රිපිටකය ඇසුරින් AI විසින් ලියනු ලබන අතර සැමවිටම මූලාශ්‍ර පෙන්වයි.',
  legendCurated: 'මාර්ග, පාඩම් සහ දිනපතා සූත්‍ර මිනිසුන් විසින් තෝරා ගනී.',
  choosePath: 'ඉගෙනුම් මාර්ගයක් තෝරන්න', pathsLead: 'සෑම මාර්ගයක්ම කෙටි පාඩම් මාලාවකි: සූත්‍රයක් කියවන්න, අසන්න, මෙනෙහි කරන්න, පුහුණු වන්න.',
  dhammaQuestions: 'ධර්ම ප්‍රශ්න, ග්‍රන්ථ ඇසුරින් පිළිතුරු',
  questionsLead: 'ප්‍රශ්න මිනිසුන් විසින් තෝරා ගත් ඒවාය; පිළිතුර ග්‍රන්ථ ඇසුරින් AI විසින් සංකල්ප සිතියමක් සහ ඊළඟට කියවිය යුතු දේ සමඟ ලියයි.',
  footerTexts: 'ග්‍රන්ථ: SuttaCentral (CC0) සහ tipitaka.lk හරහා බුද්ධ ජයන්ති ත්‍රිපිටකය (CC BY-ND 4.0).',
  footerAid: 'පිළිතුරු අධ්‍යයන ආධාරක පමණි. වැදගත් කරුණු ගුරුවරයෙකුගෙන් විමසන්න.',
  lessonsComing: 'පාඩම් ළඟදීම', notStarted: 'ආරම්භ කර නැත', completed: 'අවසන්',
  nOfMLessonsDone: (d, t) => `පාඩම් ${t} න් ${d} ක් අවසන්`, nLessons: n => `පාඩම් ${n}`, keyTexts: 'ප්‍රධාන ග්‍රන්ථ',
  noPathsHere: 'මෙම මට්ටමේ තවම මාර්ග නැත.', loadPathsError: 'ඉගෙනුම් මාර්ග පූරණය කළ නොහැකි විය.',
  startAPath: 'මාර්ගයක් අරඹන්න', lessonsBeingWritten: 'පාඩම් ලියමින් පවතී. ඒ අතරතුර ඉහත ඕනෑම ප්‍රශ්නයක් අසන්න.',
  continuePath: 'ඔබේ මාර්ගය දිගටම', startHere: 'මෙතැනින් අරඹන්න',
  lessonNofM: (n, m) => `පාඩම ${n} / ${m}`, continueLesson: 'පාඩම දිගටම කරන්න', startLesson: 'පාඩම අරඹන්න',
  seeWholePath: 'සම්පූර්ණ මාර්ගය බලන්න', loadYourPathError: 'ඔබේ මාර්ගය පූරණය කළ නොහැකි විය.',
  todaysSutta: 'අද සූත්‍රය', readReflect: 'කියවා මෙනෙහි කරන්න', loadDailyError: 'අද සූත්‍රය පූරණය කළ නොහැකි විය.',
  yourStudy: 'ඔබේ අධ්‍යයනය', lessonsDone: 'පාඩම් අවසන්', suttasRead: 'සූත්‍ර කියවූ', daysInRow: 'අඛණ්ඩ දින',
  progressNote: 'ඔබේ ප්‍රගතිය මෙම බ්‍රවුසරයේ සුරැකේ. අඛණ්ඩ දින ගණන ඇරඹීමට පාඩමක් අවසන් කරන්න.',
  nextSuggestion: 'ඊළඟ යෝජනාව: ', thenTry: ' අවසන් කර, ඉන්පසු ', loadQuestionsError: 'ප්‍රශ්න පූරණය කළ නොහැකි විය.',
  home: 'මුල් පිටුව', lessonsMinutes: (n, m) => `පාඩම් ${n} · එකකට මිනිත්තු ${m} පමණ`,
  nOfMDone: (d, t) => `${t} න් ${d} ක් අවසන්`, youAreHere: 'ඔබ මෙතැන', upNext: 'ඊළඟට', keyText: 'ප්‍රධාන ග්‍රන්ථය',
  stepTags: ['කියවන්න', 'අසන්න', 'මෙනෙහි කරන්න', 'පරීක්ෂා කරන්න'],
  pathBeingWritten: n => `මෙම මාර්ගයේ පාඩම් ${n} ලියමින් හා පරීක්ෂා කරමින් පවතී. ඒ තෙක් එහි ප්‍රධාන ග්‍රන්ථ ගැන අසන්න: `,
  whatDoesTeach: t => `${t} හි ඉගැන්වෙන්නේ කුමක්ද?`,
  byTheEnd: 'අවසානයේ ඔබට පැහැදිලි කළ හැකි වනු ඇත', whereFits: 'මෙම මාර්ගය ගැළපෙන තැන', before: 'පෙර', after: 'පසුව',
  questionsPeopleAsk: 'මෙහිදී අසන ප්‍රශ්න', loadPathError: 'මෙම මාර්ගය පූරණය කළ නොහැකි විය.', allPaths: 'සියලු ඉගෙනුම් මාර්ග',
  learningPath: 'ඉගෙනුම් මාර්ගය',
  depths: { simple: 'සරලව', standard: 'සාමාන්‍ය', deep: 'ගැඹුරින්', practice: 'පුහුණුවට' },
  headings: { simple: 'සරල වචනවලින්', standard: 'ග්‍රන්ථවල දැක්වෙන්නේ', deep: 'ගැඹුරින්', practice: 'ප්‍රායෝගිකව' },
  answer: 'පිළිතුර', suttas: 'සූත්‍ර',
  interpretation: layer => `${layer} (අර්ථකථනය, බුද්ධ වචනය නොවේ)`,
  noCitation: 'කිසිදු ඡේදයක් උපුටා දක්වා නැත; මෙම පිළිතුර තහවුරු නොකළ එකක් ලෙස සලකන්න.',
  notSettled: 'මෙම ඡේදවලින් විසඳී නැති කරුණු', sources: 'මූලාශ්‍ර', keyConcepts: 'ප්‍රධාන සංකල්ප',
  readOnSuttaCentral: 'SuttaCentral හි සම්පූර්ණ සූත්‍රය කියවන්න', readOnTipitakaLk: 'tipitaka.lk හි සම්පූර්ණ සූත්‍රය කියවන්න',
  searchingWriting: 'ග්‍රන්ථ සොයමින් පැහැදිලි කිරීමක් ලියමින්…', searching: 'ග්‍රන්ථ සොයමින්…',
  couldNotAnswer: 'පිළිතුරක් ලබා ගත නොහැකි විය.', tryAgain: 'නැවත උත්සාහ කරන්න',
  askFollowUp: 'තවත් ප්‍රශ්නයක් අසන්න', typeOwn: 'නැතහොත් ඔබේම ප්‍රශ්නය ලියන්න', yourQuestionPlaceholder: 'ඔබේ ප්‍රශ්නය',
  checkYourself: 'ඔබම පරීක්ෂා කරන්න', right: 'නිවැරදියි.', notQuite: k => `තවම නැත. ${k} නැවත බලා වෙනත් පිළිතුරක් උත්සාහ කරන්න.`,
  reflect: 'මෙනෙහි කරන්න', privateNote: 'පෞද්ගලික සටහන · මෙම බ්‍රවුසරයේ පමණක් සුරැකේ',
  share: 'මෙම පාඩම බෙදා ගන්න', shareText: 'ප්‍රධාන කරුණ සහ සූත්‍රයේ නම සමඟ රූප කාඩ්පතක් සාදයි.',
  createShareCard: 'බෙදා ගැනීමේ කාඩ්පත සාදන්න', shareFooter: 'ත්‍රිපිටක සහායකය · තෝරාගත් පාඩම',
  previous: t => `පෙර: ${t}`, backToPath: 'මාර්ගයට ආපසු', next: t => `ඊළඟ: ${t}`,
  markDoneNext: t => `අවසන් කර ඊළඟට: ${t}`, markDoneFinish: 'අවසන් කර මාර්ගය නිම කරන්න',
  lessonKey: (n, k) => `පාඩම ${n} · ${k}`, loadLessonError: 'මෙම පාඩම පූරණය කළ නොහැකි විය.',
  yourQuestion: 'ඔබේ ප්‍රශ්නය', learnStepByStep: 'පියවරෙන් පියවර ඉගෙන ගන්න',
  pathsHelp: 'තෝරාගත් මාර්ග ප්‍රධාන සූත්‍ර පිළිවෙළින් ඔබ රැගෙන යයි.', choosePathButton: 'ඉගෙනුම් මාර්ගයක් තෝරන්න',
  askTitle: 'ප්‍රශ්නයක් අසන්න', askLead: 'පිළිතුර ත්‍රිපිටකය ඇසුරින් AI විසින්, සංකල්ප සිතියමක් සහ භාවිත කළ මූලාශ්‍ර සමඟ ලියයි.',
  askLegend: 'පිළිතුරු AI විසින් ලියනු ලබන අතර සැමවිටම මූලාශ්‍ර පෙන්වයි.',
  mentalModel: 'සංකල්ප සිතියම', leadsTo: 'යොමු වේ', leadsBack: 'නැවත යොමු වේ', cycleContinues: 'එසේ චක්‍රය දිගටම පවතී.',
  conceptMap: 'සංකල්ප සිතියම', then: ', ඉන්පසු ',
  layers: { Sutta: 'සූත්‍ර', Abhidhamma: 'අභිධර්මය', Commentary: 'අටුවාව', Visuddhimagga: 'විසුද්ධිමාර්ගය' },
  savedLead: 'ඔබේ පෞද්ගලික සටහන් සහ ඔබ අවසන් කළ පාඩම්. මේවා මෙම බ්‍රවුසරයේ පමණක් සුරැකේ.',
  yourNotes: 'ඔබේ සටහන්', noNotes: 'තවම සටහන් නැත. සෑම පාඩමකම "මෙනෙහි කරන්න" යටතේ පෞද්ගලික සටහනක් ඇත.',
  lessonsDoneTitle: 'අවසන් කළ පාඩම්', noLessonsDone: 'තවම අවසන් කළ පාඩම් නැත.',
};

/** The strings of the chosen language (fixed for the page; switching reloads it). */
export const T = lang() === 'si' ? si : en;
export const LANG = lang();

// Every page using these strings says which language it is in, for screen readers and Sinhala line height.
document.documentElement.lang = LANG;

/** Fills elements marked data-i18n="key" (text) and data-i18n-placeholder="key" in static HTML. */
export function applyStaticText(root = document) {
  root.querySelectorAll('[data-i18n]').forEach(el => { el.textContent = T[el.dataset.i18n]; });
  root.querySelectorAll('[data-i18n-placeholder]').forEach(el => { el.placeholder = T[el.dataset.i18nPlaceholder]; });
}
