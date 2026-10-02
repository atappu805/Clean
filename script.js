(() => {
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

    // A quiet, paper-like musical field: thin arcs and particles rather than neon.
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

  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('is-visible');
        observer.unobserve(entry.target);
      }
    });
  }, {threshold:.12, rootMargin:'0px 0px -35px 0px'});
  document.querySelectorAll('.reveal').forEach(el => observer.observe(el));

  // Gentle pointer parallax on the hero artwork.
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
    });
    stage.addEventListener('pointerleave', () => {
      stage.querySelectorAll('.hero-phone').forEach(phone => {
        phone.style.marginLeft = '0px'; phone.style.marginTop = '0px';
      });
    });
  }
})();