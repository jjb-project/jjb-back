document.addEventListener('DOMContentLoaded', () => {
  ['toastSuccess', 'toastError'].forEach(id => {
    const el = document.getElementById(id);
    if (el) setTimeout(() => el.remove(), 3400);
  });
  const signupForm = document.querySelector('[data-signup-form]');
  if (signupForm) {
    const usernameInput = signupForm.querySelector('[data-username-input]');
    const checkButton = signupForm.querySelector('[data-username-check-button]');
    const submitButton = signupForm.querySelector('[data-signup-submit]');
    const message = signupForm.querySelector('[data-username-check-message]');
    let verifiedUsername = '';

    const normalizeUsername = (value) => value.trim().toLowerCase();
    const setMessage = (text, tone = '') => {
      if (!message) {
        return;
      }
      message.textContent = text;
      message.className = `form-help ${tone}`.trim();
    };
    const resetUsernameCheck = () => {
      verifiedUsername = '';
      if (submitButton) {
        submitButton.disabled = true;
      }
      setMessage('');
    };

    usernameInput.addEventListener('input', resetUsernameCheck);
    checkButton.addEventListener('click', async () => {
      const username = normalizeUsername(usernameInput.value);
      if (!username || !usernameInput.validity.valid) {
        resetUsernameCheck();
        setMessage('아이디는 영문/숫자 4자 이상으로 입력해주세요.', 'error');
        usernameInput.focus();
        return;
      }

      checkButton.disabled = true;
      checkButton.textContent = '확인 중';
      setMessage('아이디를 확인하고 있습니다.');
      try {
        const response = await fetch(`/api/members/username-availability?username=${encodeURIComponent(username)}`, {
          headers: { Accept: 'application/json' }
        });
        const data = await response.json();
        if (!response.ok) {
          throw new Error(data.message || '이메일 확인에 실패했습니다.');
        }
        if (data.available) {
          verifiedUsername = data.normalizedUsername;
          if (submitButton) {
            submitButton.disabled = false;
          }
          setMessage(data.message || '사용 가능한 아이디입니다.', 'success');
        } else {
          resetUsernameCheck();
          setMessage(data.message || '이미 가입된 아이디입니다.', 'error');
        }
      } catch (error) {
        resetUsernameCheck();
        setMessage(error.message || '아이디 확인에 실패했습니다.', 'error');
      } finally {
        checkButton.disabled = false;
        checkButton.textContent = '중복확인';
      }
    });

    signupForm.addEventListener('submit', (event) => {
      if (normalizeUsername(usernameInput.value) !== verifiedUsername) {
        event.preventDefault();
        setMessage('아이디 중복확인을 먼저 완료해주세요.', 'error');
        usernameInput.focus();
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

  document.querySelectorAll('[data-region-picker]').forEach((picker) => {
    const provinceSelect = picker.querySelector('[data-region-province]');
    const districtSelect = picker.querySelector('[data-region-district]');
    const detailInput = picker.querySelector('[data-region-detail]');
    const targetId = picker.dataset.regionTarget;
    const hiddenInput = targetId
      ? document.getElementById(targetId)
      : document.querySelector('[data-preferred-area-input]');
    const districtsByProvince = JSON.parse(picker.dataset.regionDistricts || '{}');
    const syncDistricts = () => {
      const province = provinceSelect.value;
      const districts = districtsByProvince[province] || [];
      districtSelect.innerHTML = '<option value="">구/시/군 선택</option>';
      districts.forEach((district) => {
        const option = document.createElement('option');
        option.value = district;
        option.textContent = district;
        districtSelect.appendChild(option);
      });
      syncArea();
    };
    const syncArea = () => {
      if (!hiddenInput) return;
      const detail = detailInput ? detailInput.value.trim() : '';
      const base = provinceSelect.value && districtSelect.value
        ? `${provinceSelect.value} ${districtSelect.value}`
        : '';
      hiddenInput.value = base && detail ? `${base} ${detail}` : base;
    };
    provinceSelect.addEventListener('change', syncDistricts);
    districtSelect.addEventListener('change', syncArea);
    if (detailInput) detailInput.addEventListener('input', syncArea);
  });

  document.querySelectorAll('[data-industry-picker]').forEach((picker) => {
    const groupSelect = picker.querySelector('[data-industry-group]');
    const detailSelect = picker.querySelector('[data-industry-detail]');
    const hiddenInput = document.querySelector('[data-experienced-industries-input]');
    const optionsByGroup = JSON.parse(picker.dataset.industryOptions || '{}');
    const syncDetails = () => {
      const group = groupSelect.value;
      const options = optionsByGroup[group] || [];
      detailSelect.innerHTML = '<option value="">세부 업무 선택</option>';
      options.forEach((detail) => {
        const option = document.createElement('option');
        option.value = detail;
        option.textContent = detail;
        detailSelect.appendChild(option);
      });
      syncExperiencedIndustry();
    };
    const syncExperiencedIndustry = () => {
      if (!hiddenInput) {
        return;
      }
      hiddenInput.value = groupSelect.value && detailSelect.value
        ? `${groupSelect.value}, ${detailSelect.value}`
        : '';
    };
    groupSelect.addEventListener('change', syncDetails);
    detailSelect.addEventListener('change', syncExperiencedIndustry);
  });

  document.querySelectorAll('[data-multi-toggle]').forEach((group) => {
    const targetId = group.dataset.multiToggle;
    const hiddenInput = document.getElementById(targetId) || document.querySelector(`[name="${targetId}"]`);
    const getVal = (btn) => btn.dataset.val || btn.textContent.trim();
    const syncValues = () => {
      if (!hiddenInput) return;
      const selected = [...group.querySelectorAll('.toggle-item.selected')].map(getVal);
      hiddenInput.value = selected.join(',');
    };
    group.querySelectorAll('.toggle-item').forEach((btn) => {
      btn.addEventListener('click', () => {
        btn.classList.toggle('selected');
        syncValues();
      });
    });
    syncValues();
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

function previewPhoto(input, targetId) {
  if (!input.files || !input.files[0]) return;
  const target = document.getElementById(targetId);
  if (!target) return;
  const reader = new FileReader();
  reader.onload = function(e) {
    target.style.backgroundImage = 'url(' + e.target.result + ')';
    target.classList.add('has-img');
    target.innerHTML = '';
  };
  reader.readAsDataURL(input.files[0]);
}

function aiFill(button, url, payload, targetId) {
  const target = document.getElementById(targetId);
  if (!target) return;
  const original = button.innerHTML;
  button.disabled = true;
  button.innerHTML = '<i class="fa-solid fa-spinner fa-spin"></i> 작성 중...';
  fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(payload)
  })
    .then((res) => {
      if (!res.ok) throw new Error('AI 생성에 실패했습니다.');
      return res.json();
    })
    .then((data) => {
      if (data && data.text) {
        target.value = data.text;
        target.focus();
      }
    })
    .catch((err) => {
      alert(err.message || 'AI 생성에 실패했습니다.');
    })
    .finally(() => {
      button.disabled = false;
      button.innerHTML = original;
    });
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}
