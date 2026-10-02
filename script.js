(() => {
  // 1. Ambient musical particle field
  const canvas = document.getElementById('music-field');
  const ctx = canvas.getContext('2d');
  let width = 0, height = 0, dpr = 1, time = 0;
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const particles = Array.from({length: 34}, (_, i) => ({
    x: Math.random(), y: Math.random(), r: 1 + Math.random() * 2.5,
    speed: .00008 + Math.random() * .00015, phase: Math.random() * Math.PI * 2,
    drift: .0002 + Math.random() * .00035, alpha: .06 + Math.random() * .12,
    hue: i % 3
  }));

  function resize() {
    dpr = Math.min(window.devicePixelRatio || 1, 2);
    width = window.innerWidth; height = window.innerHeight;
    canvas.width = width * dpr; canvas.height = height * dpr;
    canvas.style.width = width + 'px'; canvas.style.height = height + 'px';
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function draw() {
    time += 1;
    ctx.clearRect(0, 0, width, height);

    const cx = width * .72, cy = height * .28;
    ctx.lineWidth = 1;
    for (let j = 0; j < 5; j++) {
      const radius = 110 + j * 54 + Math.sin(time * .006 + j) * 5;
      ctx.beginPath();
      ctx.ellipse(cx, cy, radius * 1.8, radius * .62, -.35 + j * .03, 0, Math.PI * 2);
      ctx.strokeStyle = j % 2 ? 'rgba(98,82,143,.055)' : 'rgba(88,119,110,.045)';
      ctx.stroke();
    }

    particles.forEach(p => {
      p.y -= p.speed;
      p.x += Math.sin(time * .004 + p.phase) * p.drift;
      if (p.y < -.02) p.y = 1.02;
      if (p.x > 1.02) p.x = -.02;
      if (p.x < -.02) p.x = 1.02;
      ctx.beginPath();
      ctx.arc(p.x * width, p.y * height, p.r, 0, Math.PI * 2);
      ctx.fillStyle = p.hue === 0 ? `rgba(98,82,143,${p.alpha})` : p.hue === 1 ? `rgba(154,106,63,${p.alpha * .75})` : `rgba(88,119,110,${p.alpha})`;
      ctx.fill();
    });

    if (!reduced) requestAnimationFrame(draw);
  }
  window.addEventListener('resize', resize, {passive:true});
  resize(); draw();

  // 2. Scroll Reveal Observer
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      }
    });
  }, {threshold:.12, rootMargin:'0px 0px -35px 0px'});
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el));

  // 3. Dynamic Sticky Header Download Trigger
  const topbar = document.getElementById('site-header');
  const heroSection = document.getElementById('hero');
  const headerCta = document.getElementById('header-cta');
  const ctaText = headerCta ? headerCta.querySelector('.top-action-text') : null;
  const ctaIcon = headerCta ? headerCta.querySelector('.top-action-icon') : null;

  if (heroSection && topbar && headerCta) {
    const heroObserver = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) {
          // Scrolled past hero: transform button to direct download trigger
          topbar.classList.add('scrolled-past-hero');
          if (ctaText) ctaText.textContent = 'Download APK (v4.4.6)';
          if (ctaIcon) ctaIcon.textContent = '↓';
          headerCta.setAttribute('title', 'Direct APK Download · v4.4.6 (Code 31)');
        } else {
          // In hero: return to standard Get App label
          topbar.classList.remove('scrolled-past-hero');
          if (ctaText) ctaText.textContent = 'Get PixelMusic';
          if (ctaIcon) ctaIcon.textContent = '↗';
          headerCta.removeAttribute('title');
        }
      });
    }, { threshold: 0.1 });
    heroObserver.observe(heroSection);
  }

  // 4. Interactive Showcase Screen Tabs with Smooth Crossfade
  const tabButtons = document.querySelectorAll('.tab-pill');
  const panels = document.querySelectorAll('.showcase-panel');

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const targetId = btn.getAttribute('aria-controls');
      const targetPanel = document.getElementById(targetId);

      // Deactivate all tabs
      tabButtons.forEach(b => {
        b.classList.remove('active');
        b.setAttribute('aria-selected', 'false');
      });

      // Activate clicked tab
      btn.classList.add('active');
      btn.setAttribute('aria-selected', 'true');

      // Crossfade panels
      panels.forEach(p => {
        p.classList.remove('active');
      });

      if (targetPanel) {
        targetPanel.classList.add('active');
        // Restart video playback if it's the video tab
        const vid = targetPanel.querySelector('video');
        if (vid && vid.paused) {
          vid.play().catch(() => {});
        }
      }
    });
  });

  // 5. Checksum Copy Button Handler
  const copyBtn = document.getElementById('copy-hash-btn');
  const hashElement = document.getElementById('apk-hash');
  if (copyBtn && hashElement) {
    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(hashElement.textContent.trim());
        const originalText = copyBtn.textContent;
        copyBtn.textContent = 'Copied!';
        setTimeout(() => {
          copyBtn.textContent = originalText;
        }, 2000);
      } catch (err) {
        // Fallback for non-secure contexts
        const textarea = document.createElement('textarea');
        textarea.value = hashElement.textContent.trim();
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        copyBtn.textContent = 'Copied!';
        setTimeout(() => { copyBtn.textContent = 'Copy'; }, 2000);
      }
    });
  }

  // 6. Gentle Pointer Parallax on Hero Artwork
  const stage = document.querySelector('.hero-stage');
  if (stage && !reduced && window.matchMedia('(pointer:fine)').matches) {
    stage.addEventListener('pointermove', e => {
      const rect = stage.getBoundingClientRect();
      const x = (e.clientX - rect.left) / rect.width - .5;
      const y = (e.clientY - rect.top) / rect.height - .5;
      stage.querySelectorAll('.hero-phone').forEach((phone, i) => {
        const strength = i ? 7 : 11;
        phone.style.marginLeft = (x * strength) + 'px';
        phone.style.marginTop = (y * strength) + 'px';
      });
      const glow = stage.querySelector('.ambient-art-glow');
      if (glow) {
        glow.style.transform = `translate(${x * 20}px, ${y * 20}px)`;
      }
    });
    stage.addEventListener('pointerleave', () => {
      stage.querySelectorAll('.hero-phone').forEach(phone => {
        phone.style.marginLeft = '0px'; phone.style.marginTop = '0px';
      });
      const glow = stage.querySelector('.ambient-art-glow');
      if (glow) glow.style.transform = 'none';
    });
  }
})();
