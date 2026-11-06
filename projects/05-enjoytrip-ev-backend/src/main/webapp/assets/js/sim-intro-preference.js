// Browser-local preference, valid only for the current date in Korea.
(() => {
  const key = 'enjoytrip.sim.intro.hiddenDate';
  const today = () => new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit'
  }).format(new Date());
  window.ETSimIntro = {
    shouldSkip() {
      try { return window.localStorage.getItem(key) === today(); }
      catch { return false; }
    },
    save(checked) {
      try {
        if (checked) window.localStorage.setItem(key, today());
        else window.localStorage.removeItem(key);
      } catch { /* Storage may be disabled; the intro still closes normally. */ }
    }
  };
})();
