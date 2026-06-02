document.addEventListener('DOMContentLoaded', () => {
  const signupForm = document.querySelector('[data-signup-form]');
  if (signupForm) {
    const emailInput = signupForm.querySelector('[data-email-input]');
    const checkButton = signupForm.querySelector('[data-email-check-button]');
    const submitButton = signupForm.querySelector('[data-signup-submit]');
    const message = signupForm.querySelector('[data-email-check-message]');
    let verifiedEmail = '';

    const normalizeEmail = (value) => value.trim().toLowerCase();
    const setMessage = (text, tone = '') => {
      if (!message) {
        return;
      }
      message.textContent = text;
      message.className = `form-help ${tone}`.trim();
    };
    const resetEmailCheck = () => {
      verifiedEmail = '';
      if (submitButton) {
        submitButton.disabled = true;
      }
      setMessage('');
    };

    emailInput.addEventListener('input', resetEmailCheck);
    checkButton.addEventListener('click', async () => {
      const email = normalizeEmail(emailInput.value);
      if (!email || !emailInput.validity.valid) {
        resetEmailCheck();
        setMessage('올바른 이메일을 입력해주세요.', 'error');
        emailInput.focus();
        return;
      }

      checkButton.disabled = true;
      checkButton.textContent = '확인 중';
      setMessage('이메일을 확인하고 있습니다.');
      try {
        const response = await fetch(`/api/members/email-availability?email=${encodeURIComponent(email)}`, {
          headers: { Accept: 'application/json' }
        });
        const data = await response.json();
        if (!response.ok) {
          throw new Error(data.message || '이메일 확인에 실패했습니다.');
        }
        if (data.available) {
          verifiedEmail = data.normalizedEmail;
          if (submitButton) {
            submitButton.disabled = false;
          }
          setMessage(data.message || '사용 가능한 이메일입니다.', 'success');
        } else {
          resetEmailCheck();
          setMessage(data.message || '이미 가입된 이메일입니다.', 'error');
        }
      } catch (error) {
        resetEmailCheck();
        setMessage(error.message || '이메일 확인에 실패했습니다.', 'error');
      } finally {
        checkButton.disabled = false;
        checkButton.textContent = '중복확인';
      }
    });

    signupForm.addEventListener('submit', (event) => {
      if (normalizeEmail(emailInput.value) !== verifiedEmail) {
        event.preventDefault();
        setMessage('이메일 중복확인을 먼저 완료해주세요.', 'error');
        emailInput.focus();
      }
    });
  }

  document.querySelectorAll('[data-star]').forEach((button) => {
    button.addEventListener('click', () => {
      const rating = Number(button.dataset.star);
      button.closest('[data-rating]')
        .querySelectorAll('[data-star]')
        .forEach((star) => star.classList.toggle('active', Number(star.dataset.star) <= rating));
    });
  });

  document.querySelectorAll('#evaluationTarget').forEach((select) => {
    const targetInput = document.querySelector('[data-target-id-input]');
    const syncTarget = () => {
      if (targetInput && select.selectedOptions.length > 0) {
        targetInput.value = select.selectedOptions[0].dataset.targetId;
      }
    };
    select.addEventListener('change', syncTarget);
    syncTarget();
  });

  document.querySelectorAll('[data-time-range]').forEach((range) => {
    const hiddenInput = document.querySelector('[data-available-time-input]');
    const startInput = range.querySelector('[data-time-start]');
    const endInput = range.querySelector('[data-time-end]');
    const syncAvailableTime = () => {
      if (!hiddenInput || !startInput || !endInput || !startInput.value || !endInput.value) {
        return;
      }
      hiddenInput.value = `오늘 ${startInput.value}-${endInput.value}`;
    };
    startInput.addEventListener('input', syncAvailableTime);
    endInput.addEventListener('input', syncAvailableTime);
    syncAvailableTime();
  });

  document.querySelectorAll('[data-area-option]').forEach((button) => {
    button.addEventListener('click', () => {
      const input = document.getElementById('preferredArea');
      if (!input) {
        return;
      }
      input.value = button.dataset.areaOption;
      button.parentElement
        .querySelectorAll('[data-area-option]')
        .forEach((item) => item.classList.toggle('active', item === button));
      input.focus();
    });
  });

  document.querySelectorAll('[data-industry-option]').forEach((button) => {
    button.addEventListener('click', () => {
      const input = document.getElementById('experiencedIndustries');
      const value = button.dataset.industryOption;
      if (!input || !value) {
        return;
      }
      const values = input.value.split(',')
        .map((item) => item.trim())
        .filter((item) => item.length > 0);
      if (!values.includes(value)) {
        values.push(value);
      }
      input.value = values.join(', ');
      button.classList.add('active');
      input.focus();
    });
  });

  document.querySelectorAll('[data-recommendation-trigger]').forEach((button) => {
    button.addEventListener('click', async () => {
      const target = document.getElementById(button.dataset.recommendationTarget);
      if (!target) {
        return;
      }
      button.disabled = true;
      const originalText = button.textContent;
      button.textContent = '추천 중';
      target.hidden = false;
      target.innerHTML = '<p class="recommendation-empty">추천 결과를 불러오고 있습니다.</p>';
      try {
        const response = await fetch(button.dataset.recommendationUrl, {
          headers: { Accept: 'application/json' }
        });
        if (!response.ok) {
          throw new Error('추천 결과를 불러오지 못했습니다.');
        }
        const recommendations = await response.json();
        if (!recommendations.length) {
          target.innerHTML = '<p class="recommendation-empty">아직 추천할 수 있는 대상이 없습니다.</p>';
          return;
        }
        target.innerHTML = recommendations
          .map((item) => `
            <article class="recommendation-card">
              <div>
                <strong>${escapeHtml(item.targetName)}</strong>
                <span>${escapeHtml(item.summary)}</span>
                <p>${escapeHtml(item.reason)}</p>
              </div>
              <span class="tag-chip active">추천 ${item.score}</span>
            </article>
          `)
          .join('');
      } catch (error) {
        target.innerHTML = `<p class="recommendation-empty">${escapeHtml(error.message)}</p>`;
      } finally {
        button.disabled = false;
        button.textContent = originalText;
      }
    });
  });

  if (document.querySelector('[data-live-refresh]') && window.EventSource) {
    const source = new EventSource('/live/updates');
    let firstMessage = true;
    source.addEventListener('jjb-update', (event) => {
      if (firstMessage || event.data === 'connected') {
        firstMessage = false;
        return;
      }
      window.setTimeout(() => window.location.reload(), 400);
    });
  }
});

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
