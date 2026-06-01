document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('[data-otp-trigger]').forEach((button) => {
    button.addEventListener('click', () => {
      const panel = document.querySelector('[data-otp-panel]');
      if (panel) {
        panel.hidden = false;
      }
    });
  });

  document.querySelectorAll('[data-star]').forEach((button) => {
    button.addEventListener('click', () => {
      const rating = Number(button.dataset.star);
      button.closest('[data-rating]')
        .querySelectorAll('[data-star]')
        .forEach((star) => star.classList.toggle('active', Number(star.dataset.star) <= rating));
    });
  });
});
