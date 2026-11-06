const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
function preference(at, storage) {
  const window = { localStorage: storage };
  class Clock extends Date { constructor(...args) { super(...(args.length ? args : [at])); } }
  vm.runInNewContext(fs.readFileSync('src/main/webapp/assets/js/sim-intro-preference.js', 'utf8'), { window, Date: Clock, Intl });
  return window.ETSimIntro;
}
function memory() {
  const data = new Map();
  return { getItem: key => data.get(key) ?? null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) };
}
test('hide choice survives reload until Korean midnight, not for 24 hours', () => {
  const storage = memory();
  const before = preference('2026-10-30T14:59:00Z', storage);
  assert.equal(before.shouldSkip(), false); before.save(true);
  assert.equal(preference('2026-10-30T14:59:59Z', storage).shouldSkip(), true);
  assert.equal(preference('2026-10-30T15:00:00Z', storage).shouldSkip(), false);
});
test('unchecking clears todays preference', () => {
  const p = preference('2026-10-30T02:00:00Z', memory()); p.save(true); p.save(false);
  assert.equal(p.shouldSkip(), false);
});
test('blocked browser storage does not break the page', () => {
  const storage = { getItem() { throw new Error('blocked'); }, setItem() { throw new Error('blocked'); }, removeItem() { throw new Error('blocked'); } };
  const p = preference('2026-10-30T02:00:00Z', storage);
  assert.equal(p.shouldSkip(), false); assert.doesNotThrow(() => p.save(true)); assert.doesNotThrow(() => p.save(false));
});

function introPage(skip) {
  const events = {};
  const element = () => ({ hidden: true, checked: false, dataset: {}, classList: { remove() {}, add() {}, toggle() {} },
    addEventListener(type, fn) { this[type] = fn; }, querySelector() { return null; } });
  const scenes = [element(), element()];
  const dots = { children: [{classList:{toggle(){}}},{classList:{toggle(){}}}] };
  const ids = Object.fromEntries(['intro','intro-hide-today','btn-skip','btn-replay','btn-press','vehicles'].map(id => [id, element()]));
  ids.intro.dataset.autoOpen = 'true'; ids.intro.querySelectorAll = () => scenes; ids.intro.querySelector = () => dots;
  const saved = [];
  const document = { getElementById: id => ids[id], addEventListener: (type, fn) => events[type] = fn, removeEventListener: type => delete events[type] };
  const full = fs.readFileSync('src/main/webapp/assets/js/sim.js','utf8');
  const code = full.slice(0, full.indexOf('  /* ---------- 실행 ---------- */')) + '})();';
  vm.runInNewContext(code, { document, window: { ETSimIntro: { shouldSkip: () => skip, save: flag => saved.push(flag) } }, requestAnimationFrame: fn => fn() });
  return { ids, scenes, events, saved };
}
test('saved choice skips automatic intro but explicit replay works and unchecking clears it', () => {
  const p = introPage(true); assert.equal(p.ids.intro.hidden, true);
  p.ids['btn-replay'].click(); assert.equal(p.ids.intro.hidden, false); assert.equal(p.ids['intro-hide-today'].checked, true);
  p.ids['intro-hide-today'].checked = false; p.ids['intro-hide-today'].change(); assert.deepEqual(p.saved, [false]);
});
test('checkbox click and space do not advance scenes; closing records the checkbox', () => {
  const p = introPage(false); assert.equal(p.scenes[0].hidden, false);
  const target = { closest: () => ({}) };
  p.ids.intro.click({target}); p.events.keydown({target, code:'Space', preventDefault() { throw new Error('checkbox key blocked'); }});
  assert.equal(p.scenes[0].hidden, false);
  p.ids['intro-hide-today'].checked = true; p.ids['btn-skip'].click();
  assert.equal(p.ids.intro.hidden, true); assert.deepEqual(p.saved, [true]);
});
