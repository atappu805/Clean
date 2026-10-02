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

  // 7. Live Spring Physics, Lyric Scrubbing & Monet Color Extraction Loop
  const springCanvas = document.getElementById('spring-canvas');
  const motionVideo = document.querySelector('.motion-video');
  if (springCanvas) {
    const sCtx = springCanvas.getContext('2d');
    let sTime = 0;
    
    // Check if video can play; if not or error, activate spring canvas
    if (motionVideo) {
      motionVideo.addEventListener('error', () => {
        springCanvas.classList.add('is-active');
        motionVideo.style.display = 'none';
      });
      setTimeout(() => {
        if (motionVideo.readyState < 2) {
          springCanvas.classList.add('is-active');
          motionVideo.style.display = 'none';
        }
      }, 1500);
    } else {
      springCanvas.classList.add('is-active');
    }

    function renderSpringCanvas() {
      if (springCanvas.classList.contains('is-active')) {
        sTime += 1;
        const w = springCanvas.width = 300;
        const h = springCanvas.height = 650;
        const t = sTime * 0.03;
        
        // Monet-style color extraction background
        const r = 244 + Math.sin(t * 0.5) * 6;
        const g = 238 + Math.cos(t * 0.5) * 6;
        const b = 232 + Math.sin(t * 0.7) * 8;
        sCtx.fillStyle = `rgb(${r},${g},${b})`;
        sCtx.fillRect(0, 0, w, h);

        // Ambient artwork glow
        const gGrad = sCtx.createRadialGradient(150, 180, 10, 150, 180, 140);
        gGrad.addColorStop(0, `rgba(98, 82, 143, ${0.4 + Math.sin(t) * 0.15})`);
        gGrad.addColorStop(0.7, `rgba(154, 106, 63, ${0.25 + Math.cos(t) * 0.1})`);
        gGrad.addColorStop(1, 'transparent');
        sCtx.fillStyle = gGrad;
        sCtx.fillRect(0, 50, w, 260);

        // Header and track info
        sCtx.fillStyle = '#221f1d';
        sCtx.font = 'bold 15px "Space Grotesk", sans-serif';
        sCtx.textAlign = 'center';
        sCtx.fillText('Smooth Operator', 150, 220);
        sCtx.font = '11px "DM Sans", sans-serif';
        sCtx.fillStyle = '#7a736a';
        sCtx.fillText('Sade • Diamond Life', 150, 238);

        // Fluid spring physics wavy slider
        const barY = 275;
        const progressX = 60 + ((Math.sin(t * 0.8) + 1) / 2) * 180;
        sCtx.lineWidth = 3;
        sCtx.strokeStyle = '#62528f';
        sCtx.beginPath();
        for (let x = 30; x <= progressX; x += 2) {
          const wave = Math.sin((x * 0.08) - (t * 3)) * 4;
          if (x === 30) sCtx.moveTo(x, barY + wave);
          else sCtx.lineTo(x, barY + wave);
        }
        sCtx.stroke();

        // Thumb with spring bounce
        sCtx.fillStyle = '#62528f';
        sCtx.beginPath();
        sCtx.arc(progressX, barY + Math.sin((progressX * 0.08) - (t * 3)) * 4, 6 + Math.sin(t * 4), 0, Math.PI * 2);
        sCtx.fill();

        // Remaining track line
        sCtx.strokeStyle = '#d7cfc5';
        sCtx.beginPath();
        sCtx.moveTo(progressX + 4, barY);
        sCtx.lineTo(270, barY);
        sCtx.stroke();

        // Synchronized Live Lyrics Card
        sCtx.fillStyle = 'rgba(255,255,255,0.7)';
        sCtx.beginPath();
        if (sCtx.roundRect) {
          sCtx.roundRect(20, 310, 260, 270, 20);
        } else {
          sCtx.rect(20, 310, 260, 270);
        }
        sCtx.fill();

        // Lyrics header
        sCtx.fillStyle = '#58776e';
        sCtx.font = 'bold 9px "DM Sans", sans-serif';
        sCtx.textAlign = 'left';
        sCtx.fillText('LIVE KARAOKE LYRICS', 36, 335);

        // Animated lyric lines with vertical scrubbing
        const scrubY = Math.sin(t * 0.7) * 8;
        sCtx.font = '12px "DM Sans", sans-serif';
        sCtx.fillStyle = '#9b948b';
        sCtx.fillText('Diamond lights, call city nights', 36, 365 + scrubY);

        // Active line with word-by-word reveal
        sCtx.fillStyle = '#eadef5';
        sCtx.beginPath();
        if (sCtx.roundRect) {
          sCtx.roundRect(32, 380 + scrubY, 236, 32, 10);
        } else {
          sCtx.rect(32, 380 + scrubY, 236, 32);
        }
        sCtx.fill();

        sCtx.font = 'bold 13px "Space Grotesk", sans-serif';
        sCtx.fillStyle = '#62528f';
        sCtx.fillText('Coast to coast, LA to Chicago...', 40, 401 + scrubY);

        sCtx.font = '12px "DM Sans", sans-serif';
        sCtx.fillStyle = '#6e6760';
        sCtx.fillText('No place for beginners', 36, 436 + scrubY);
        sCtx.fillStyle = '#9b948b';
        sCtx.fillText('When sentiment is left to chance', 36, 464 + scrubY);

        // Equalizer bounce bars
        for (let b = 0; b < 14; b++) {
          const hEq = 5 + Math.abs(Math.sin(t * 2.5 + b * 0.4)) * 18;
          sCtx.fillStyle = '#58776e';
          sCtx.beginPath();
          if (sCtx.roundRect) {
            sCtx.roundRect(40 + b * 16, 530 - hEq, 6, hEq, 3);
          } else {
            sCtx.rect(40 + b * 16, 530 - hEq, 6, hEq);
          }
          sCtx.fill();
        }
      }
      if (!reduced) requestAnimationFrame(renderSpringCanvas);
    }
    renderSpringCanvas();
  }
})();
