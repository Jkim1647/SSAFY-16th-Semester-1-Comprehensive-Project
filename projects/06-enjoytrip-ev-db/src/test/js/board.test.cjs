const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/assets/js/board.js', 'utf8');

function page(post) {
  const element = (pressed) => ({
    disabled: false, textContent: '', dataset: {}, handlers: {},
    getAttribute() { return String(this.pressed); },
    setAttribute(name, value) { if (name === 'aria-pressed') this.pressed = value; },
    addEventListener(name, fn) { this.handlers[name] = fn; }, pressed
  });
  const like = element('false'), bookmark = element('false');
  like.dataset.reaction = 'like'; bookmark.dataset.reaction = 'bookmark';
  const message = element(), count = element(); count.textContent = 0;
  const document = {
    querySelector(selector) {
      if (selector === '[data-board-id]') return { dataset: { boardId: '42' } };
      if (selector === '#reaction-message') return message;
      if (selector === '[data-reaction="like"]') return like;
      if (selector === '[data-reaction="bookmark"]') return bookmark;
      if (selector === '[data-like-count]') return count;
      throw new Error(selector);
    },
    querySelectorAll(selector) { return selector === '[data-reaction]' ? [like, bookmark] : []; }
  };
  vm.runInNewContext(source, { document, ET: { post }, confirm: () => true });
  return { like, bookmark, message, count, click: () => like.handlers.click() };
}

test('pending request disables both buttons; success applies the server state', async () => {
  let complete;
  const p = page((url, body) => {
    assert.equal(url, '/board?action=reaction'); assert.equal(body.id, '42');
    assert.equal(body.kind, 'like'); assert.equal(body.active, 'true');
    return new Promise(resolve => { complete = resolve; });
  });
  const request = p.click();
  assert.equal(p.like.disabled, true); assert.equal(p.bookmark.disabled, true);
  complete({ liked: true, bookmarked: false, likeCount: 4 }); await request;
  assert.equal(p.like.pressed, 'true'); assert.equal(p.count.textContent, 4);
  assert.equal(p.like.disabled, false); assert.equal(p.bookmark.disabled, false);
});

test('network error preserves state, unlocks buttons, and retries the same intent', async () => {
  const intents = [];
  const p = page(async (_, body) => {
    intents.push(body.active);
    if (intents.length === 1) throw new Error('Connection lost');
    return { liked: true, bookmarked: false, likeCount: 1 };
  });
  await p.click();
  assert.equal(p.like.pressed, 'false'); assert.equal(p.count.textContent, 0);
  assert.match(p.message.textContent, /Connection lost/); assert.equal(p.like.disabled, false);
  await p.click(); assert.deepEqual(intents, ['true', 'true']); assert.equal(p.like.pressed, 'true');
});

test('malformed success response cannot corrupt the confirmed selection', async () => {
  const p = page(async () => ({})); await p.click();
  assert.equal(p.like.pressed, 'false'); assert.equal(p.bookmark.pressed, 'false');
  assert.equal(p.count.textContent, 0); assert.equal(p.like.disabled, false);
  assert.notEqual(p.message.textContent, '저장했습니다.');
});
