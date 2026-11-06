const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

const source = fs.readFileSync(path.join(__dirname, '../../main/webapp/assets/js/plan-editor.js'), 'utf8');

function setup() {
  const elements = new Map();
  function element(selector) {
    if (!elements.has(selector)) {
      elements.set(selector, {
        value: '', textContent: '', innerHTML: '', hidden: false, disabled: false, checked: false,
        listeners: {}, classList: { toggle() {}, add() {}, remove() {}, contains() { return false; } },
        addEventListener(type, fn) { this.listeners[type] = fn; },
        querySelectorAll() { return []; },
      });
    }
    return elements.get(selector);
  }
  const requests = [];
  const layer = () => ({ addTo() { return this; }, clearLayers() {} });
  const marker = () => ({ bindTooltip() { return this; }, addTo() { return this; } });
  const ctx = {
    URLSearchParams, console,
    document: { querySelector: element },
    window: { addEventListener() {} },
    ET: {
      data: () => ({ stops: [], title: '', startDate: '', endDate: '', startSoc: 80, memo: '' }),
      escapeHtml: String,
      createMap: () => ({ fitBounds() {}, setView() {} }),
      get: (url) => new Promise((resolve, reject) => requests.push({ url, resolve, reject })),
    },
    L: { layerGroup: layer, circleMarker: marker, divIcon: () => ({}), latLngBounds: () => ({ pad() { return this; } }) },
  };
  vm.runInNewContext(source, ctx);
  const click = (selector, type = 'click') => element(selector).listeners[type]({ preventDefault() {} });
  const flush = () => new Promise((resolve) => setImmediate(resolve));
  return { element, requests, click, flush };
}

const result = (page, totalPages, title) => ({
  page, totalPages, total: totalPages === 1 ? 1 : 21,
  items: [{ id: page, title, lat: 35, lng: 127, type: '', addr: '', image: '' }],
});

test('search pages preserve submitted filters and replace old results', async () => {
  const t = setup();
  t.element('#find-keyword').value = '담양';
  t.element('#find-type').value = '12';
  t.click('#find-form', 'submit');
  assert.match(t.requests[0].url, /page=1/);
  t.requests[0].resolve(result(1, 2, 'first result'));
  await t.flush();
  assert.equal(t.element('#find-pages').hidden, false);
  assert.equal(t.element('#find-prev').disabled, true);
  assert.match(t.element('#find-list').innerHTML, /first result/);
  t.element('#find-keyword').value = '다른 검색어';
  t.click('#find-next');
  assert.equal(t.element('#find-list').innerHTML, '');
  assert.equal(new URLSearchParams(t.requests[1].url.split('?')[1]).get('keyword'), '담양');
  assert.match(t.requests[1].url, /page=2/);
  t.requests[1].resolve(result(2, 2, 'second result'));
  await t.flush();
  assert.equal(t.element('#find-next').disabled, true);
  assert.match(t.element('#find-list').innerHTML, /second result/);
  t.click('#find-form', 'submit');
  assert.match(t.requests[2].url, /page=1/);
  assert.equal(new URLSearchParams(t.requests[2].url.split('?')[1]).get('keyword'), '다른 검색어');
  t.requests[2].resolve(result(1, 1, 'new search'));
  await t.flush();
  assert.equal(t.element('#find-pages').hidden, true);
});

test('late responses cannot overwrite a newer search; failures clear results', async () => {
  const t = setup();
  t.click('#find-form', 'submit');
  t.element('#find-keyword').value = '새 검색';
  t.click('#find-form', 'submit');
  t.requests[1].resolve(result(1, 1, 'new result'));
  await t.flush();
  t.requests[0].resolve(result(1, 2, 'stale result'));
  await t.flush();
  assert.match(t.element('#find-list').innerHTML, /new result/);
  assert.doesNotMatch(t.element('#find-list').innerHTML, /stale result/);
  t.click('#find-form', 'submit');
  t.requests[2].reject(new Error('검색 실패'));
  await t.flush();
  assert.equal(t.element('#find-list').innerHTML, '');
  assert.equal(t.element('#find-meta').textContent, '검색 실패');
  assert.equal(t.element('#find-pages').hidden, true);
});
