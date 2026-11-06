// Server-confirmed reactions; failed requests leave the last confirmed state intact.
(() => {
  const section = document.querySelector('[data-board-id]');
  const message = document.querySelector('#reaction-message');
  const buttons = [...document.querySelectorAll('[data-reaction]')];
  for (const button of buttons) button.addEventListener('click', async () => {
    const active = button.getAttribute('aria-pressed') !== 'true';
    buttons.forEach(b => b.disabled = true);
    message.textContent = '';
    try {
      const result = await ET.post('/board?action=reaction', {
        id: section.dataset.boardId, kind: button.dataset.reaction, active: String(active)
      });
      if (typeof result?.liked !== 'boolean' || typeof result?.bookmarked !== 'boolean'
          || !Number.isSafeInteger(result.likeCount) || result.likeCount < 0) {
        throw new Error('저장 결과를 확인할 수 없습니다.');
      }
      document.querySelector('[data-reaction="like"]').setAttribute('aria-pressed', String(result.liked));
      document.querySelector('[data-reaction="bookmark"]').setAttribute('aria-pressed', String(result.bookmarked));
      document.querySelector('[data-like-count]').textContent = result.likeCount;
      message.textContent = '';
    } catch (error) {
      message.textContent = `${error.message} 다시 시도하거나 페이지를 새로고침하세요.`;
    } finally { buttons.forEach(b => b.disabled = false); }
  });
  document.querySelectorAll('form[data-login-url]').forEach(form => {
    form.addEventListener('submit', event => {
      event.preventDefault();
      alert('댓글을 작성하려면 로그인이 필요합니다.');
      window.location.assign(form.dataset.loginUrl);
    });
  });
  document.querySelectorAll('form[data-confirm]').forEach(form => {
    form.addEventListener('submit', event => { if (!confirm(form.dataset.confirm)) event.preventDefault(); });
  });
})();
