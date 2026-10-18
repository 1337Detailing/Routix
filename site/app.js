/* Routix — progressive enhancements. The entire page works without these libraries. */
(() => {
  'use strict';

  const $ = (selector, scope = document) => scope.querySelector(selector);
  const $$ = (selector, scope = document) => [...scope.querySelectorAll(selector)];
  const mqReduced = window.matchMedia('(prefers-reduced-motion: reduce)');
  const mqMobile = window.matchMedia('(max-width: 750px)');
  const mqFinePointer = window.matchMedia('(pointer: fine)');
  const menuButton = $('.menu-toggle');
  const navLinks = $('#nav-links');
  const progress = $('.scroll-progress span');
  let lenisInstance = null;

  $('#year').textContent = new Date().getFullYear();

  const closeMenu = () => {
    navLinks?.classList.remove('is-open');
    menuButton?.setAttribute('aria-expanded', 'false');
    menuButton?.setAttribute('aria-label', 'Ouvrir le menu');
  };

  menuButton?.addEventListener('click', () => {
    const opening = menuButton.getAttribute('aria-expanded') !== 'true';
    navLinks.classList.toggle('is-open', opening);
    menuButton.setAttribute('aria-expanded', String(opening));
    menuButton.setAttribute('aria-label', opening ? 'Fermer le menu' : 'Ouvrir le menu');
  });
  $$('#nav-links a').forEach(link => link.addEventListener('click', closeMenu));
  document.addEventListener('keydown', event => { if (event.key === 'Escape') closeMenu(); });
  document.addEventListener('click', event => {
    if (menuButton && navLinks && !menuButton.contains(event.target) && !navLinks.contains(event.target)) closeMenu();
  });

  function updateProgress() {
    const available = document.documentElement.scrollHeight - window.innerHeight;
    if (progress) progress.style.transform = `scaleX(${available > 0 ? Math.min(1, window.scrollY / available) : 0})`;
  }
  window.addEventListener('scroll', updateProgress, { passive: true });
  window.addEventListener('resize', updateProgress, { passive: true });
  updateProgress();

  // Lenis + GSAP are optional: inaccessible CDNs never hide or disable content.
  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = src;
      script.async = true;
      script.onload = resolve;
      script.onerror = reject;
      document.head.appendChild(script);
    });
  }

  async function bootScroll() {
    if (mqReduced.matches) return;
    const loaded = await Promise.allSettled([
      loadScript('https://cdnjs.cloudflare.com/ajax/libs/gsap/3.13.0/gsap.min.js'),
      loadScript('https://cdnjs.cloudflare.com/ajax/libs/gsap/3.13.0/ScrollTrigger.min.js'),
      loadScript('https://cdn.jsdelivr.net/npm/lenis@1.3.11/dist/lenis.min.js')
    ]);
    const gsap = window.gsap;
    if (gsap && window.ScrollTrigger && loaded[0].status === 'fulfilled' && loaded[1].status === 'fulfilled') {
      gsap.registerPlugin(window.ScrollTrigger);
      const heroItems = $$('.hero-text [data-reveal]');
      gsap.fromTo(heroItems,
        { y: 25, opacity: 0 },
        { y: 0, opacity: 1, duration: .94, ease: 'power3.out', stagger: .13, clearProps: 'all' }
      );
      $$('[data-reveal]').filter(el => !el.closest('.hero-text')).forEach(el => {
        gsap.fromTo(el,
          { opacity: 0, y: 34 },
          { opacity: 1, y: 0, duration: .9, ease: 'power3.out', clearProps: 'all',
            scrollTrigger: { trigger: el, start: 'top 94%', once: true } }
        );
      });
      window.ScrollTrigger.refresh();
    } else {
      // The CSS base state is fully visible, so no JS fallback is necessary.
    }

    if (window.Lenis && !mqMobile.matches) {
      try {
        lenisInstance = new window.Lenis({ duration: 1.12, easing: t => 1 - Math.pow(1 - t, 4), smoothWheel: true, smoothTouch: false });
        function raf(time) { lenisInstance.raf(time); requestAnimationFrame(raf); }
        requestAnimationFrame(raf);
        lenisInstance.on('scroll', () => { updateProgress(); window.ScrollTrigger?.update(); });
        $$('a[href^="#"]').forEach(a => a.addEventListener('click', event => {
          const id = a.getAttribute('href');
          if (!id || id === '#') return;
          const target = $(id);
          if (!target) return;
          event.preventDefault();
          lenisInstance.scrollTo(target, { offset: -35 });
          history.replaceState(null, '', id);
        }));
      } catch { lenisInstance = null; }
    }
  }
  bootScroll().catch(() => {});

  // Upgrade download links only for a publicly published release with an APK.
  async function refreshDownload() {
    const fallback = 'https://github.com/1337Detailing/Routix/actions/workflows/build-debug-apk.yml';
    try {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 4500);
      let response;
      try { response = await fetch('https://api.github.com/repos/1337Detailing/Routix/releases/latest', { signal: controller.signal, headers: { Accept: 'application/vnd.github+json' } }); }
      finally { clearTimeout(timer); }
      if (!response.ok) return;
      const release = await response.json();
      if (release.draft || !release.tag_name) return;
      const apk = (release.assets || []).find(asset => /\.apk$/i.test(asset.name));
      if (!apk) return;
      const u = new URL(apk.browser_download_url);
      if (u.protocol !== 'https:' || u.hostname !== 'github.com' || !u.pathname.startsWith('/1337Detailing/Routix/releases/download/')) return;
      $$('.download-link').forEach(link => { link.href = u.href; });
      const note = $('.end-footnote');
      if (note) note.textContent = `${release.tag_name} · APK ANDROID · SOURCE SUR GITHUB`;
    } catch { $$('.download-link').forEach(link => { link.href ||= fallback; }); }
  }
  refreshDownload();

  // Three.js is lazily imported only when the hero approaches the viewport.
  const visual = $('.hero-visual');
  let threeStarted = false;
  let sceneCleanup = null;
  async function ensureThree() {
    if (threeStarted || !visual) return;
    threeStarted = true;
    try {
      if (!window.WebGLRenderingContext) return;
      const test = document.createElement('canvas');
      if (!test.getContext('webgl', { powerPreference: 'low-power' })) return;
      const THREE = await import('https://cdn.jsdelivr.net/npm/three@0.170.0/build/three.module.js');
      if (document.hidden) return;
      sceneCleanup = bootThree(THREE);
    } catch (err) {
      console.info('Routix: visualisation 3D indisponible, illustration statique conservée.');
    }
  }

  if ('IntersectionObserver' in window && visual) {
    const io = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) { ensureThree(); io.disconnect(); }
    }, { rootMargin: '350px' });
    io.observe(visual);
  } else ensureThree();

  function drawRounded(ctx, x, y, w, h, r, fill) {
    ctx.beginPath(); ctx.roundRect(x, y, w, h, r);
    ctx.fillStyle = fill; ctx.fill();
  }

  function buildScreen(THREE) {
    const c = document.createElement('canvas');
    c.width = 720; c.height = 1400;
    const g = c.getContext('2d');
    g.clearRect(0, 0, c.width, c.height);
    // Transparent rounded cutout follows the screen geometry.
    g.save(); g.beginPath(); g.roundRect(0, 0, 720, 1400, 78); g.clip();
    g.fillStyle = '#121511'; g.fillRect(0, 0, 720, 1400);
    // Street topology, with restrained geometric map styling.
    g.lineJoin = 'round'; g.lineCap = 'round';
    const streets = [
      [[-70,270],[900,350]], [[-30,520],[750,430]], [[-20,720],[830,810]],
      [[-80,1030],[800,950]], [[20,-50],[120,1500]], [[310,-90],[290,1470]],
      [[570,-50],[515,1500]], [[-90,170],[800,45]], [[-50,1180],[800,1270]],
      [[-50,580],[320,615],[790,590]], [[-70,870],[780,680]]
    ];
    streets.forEach((pts, i) => {
      g.beginPath(); pts.forEach(([x,y], j) => j ? g.lineTo(x,y) : g.moveTo(x,y));
      g.strokeStyle = i < 7 ? '#30352f' : '#292d29'; g.lineWidth = i < 7 ? 22 : 11; g.stroke();
      g.strokeStyle = '#51564c'; g.lineWidth = i < 7 ? 1.4 : 1; g.stroke();
    });
    g.save(); g.fillStyle='#777b70'; g.font='20px monospace'; g.translate(90, 710);g.rotate(-.12);g.fillText('RUE DU MOULIN',0,0);g.restore();
    g.save();g.translate(370,940);g.rotate(.17);g.fillText('RUE CENTRALE',0,0);g.restore();
    g.save();g.translate(480,525);g.rotate(-.05);g.fillText('RUE PRINCIPALE',0,0);g.restore();
    // Route follows a surveyed path, not a generic generated road.
    const route = [[130,1100],[215,1052],[215,885],[346,885],[346,641],[480,641],[480,457],[610,425]];
    for (const [width,color] of [[39,'#141711'],[13,'#d8c48a']]) {
      g.beginPath(); route.forEach(([x,y],i)=> i ? g.lineTo(x,y):g.moveTo(x,y));
      g.strokeStyle=color;g.lineWidth=width;g.stroke();
    }
    // Route markers and a simple active position arrow.
    g.beginPath();g.arc(130,1100,20,0,Math.PI*2);g.fillStyle='#161914';g.fill();g.lineWidth=8;g.strokeStyle='#d8c48a';g.stroke();
    g.beginPath();g.arc(610,425,18,0,Math.PI*2);g.fillStyle='#d8c48a';g.fill();
    g.save();g.translate(346,790);g.fillStyle='#f4eddb';g.strokeStyle='#11140f';g.lineWidth=8;g.beginPath();g.moveTo(0,-38);g.lineTo(24,20);g.lineTo(0,8);g.lineTo(-24,20);g.closePath();g.stroke();g.fill();g.restore();
    // Top and bottom interface panels are drawn inside the same texture.
    drawRounded(g,19,23,682,132,28,'#191e18');
    g.fillStyle='#e2dcc7';g.font='bold 26px monospace';g.fillText('R O U T I X',50,76);
    g.fillStyle='#d8c48a';g.font='21px monospace';g.fillText('●   GPS ACTIF',455,76);
    g.fillStyle='#777e70';g.font='20px monospace';g.fillText('ENREGISTREMENT / TRACE 01',50,125);
    drawRounded(g,22,1208,676,169,24,'#1a201a');
    g.fillStyle='#d8c48a';g.font='19px monospace';g.fillText('PROCHAIN REPÈRE',54,1260);
    g.fillStyle='#f0ecdf';g.font='bold 44px monospace';g.fillText('2 CÔTÉS',54,1322);
    g.fillStyle='#bdb9a5';g.font='21px monospace';g.fillText('↗',602,1320);
    g.restore();
    const texture = new THREE.CanvasTexture(c);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 4;
    return texture;
  }

  function roundedShape(THREE,w,h,r) {
    const s = new THREE.Shape();const x=-w/2,y=-h/2;
    s.moveTo(x+r,y);s.lineTo(x+w-r,y);s.quadraticCurveTo(x+w,y,x+w,y+r);
    s.lineTo(x+w,y+h-r);s.quadraticCurveTo(x+w,y+h,x+w-r,y+h);
    s.lineTo(x+r,y+h);s.quadraticCurveTo(x,y+h,x,y+h-r);
    s.lineTo(x,y+r);s.quadraticCurveTo(x,y,x+r,y);
    return s;
  }

  function bootThree(THREE) {
    const canvas = $('#three-canvas');
    if (!canvas || !visual) return null;
    const reduce = mqReduced.matches;
    const mobile = mqMobile.matches;
    const w = visual.clientWidth, h = visual.clientHeight;
    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(mobile?37:36, w/h, .1, 100);
    camera.position.set(0,0,mobile?10.8:10.2);
    const renderer = new THREE.WebGLRenderer({canvas,alpha:true,antialias:!mobile,powerPreference:'low-power'});
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, mobile?1.35:1.65));
    renderer.setSize(w,h,false);
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.45;
    scene.add(new THREE.AmbientLight(0xeae4cc,1.5));
    const key = new THREE.DirectionalLight(0xe5d4a6,3.4);key.position.set(-4,6,10);scene.add(key);
    const edge = new THREE.DirectionalLight(0xc0bdb4,2.1);edge.position.set(5,-2,-5);scene.add(edge);
    const group = new THREE.Group();scene.add(group);
    const outer = new THREE.MeshStandardMaterial({color:0x282821,metalness:.86,roughness:.3});
    const sideMat = new THREE.MeshStandardMaterial({color:0x5a5543,metalness:.94,roughness:.21});
    const backMat = new THREE.MeshStandardMaterial({color:0x111310,metalness:.42,roughness:.76});
    const gold = new THREE.MeshStandardMaterial({color:0xd8c48a,metalness:.75,roughness:.36,emissive:0x221b0b,emissiveIntensity:.15});
    const bezel = new THREE.Mesh(new THREE.ExtrudeGeometry(roundedShape(THREE,2.91,5.65,.38),{depth:.19,bevelEnabled:true,bevelSize:.065,bevelThickness:.062,bevelSegments:4,steps:1}),outer);
    group.add(bezel);
    const back = new THREE.Mesh(new THREE.ShapeGeometry(roundedShape(THREE,2.85,5.58,.34)),backMat);back.position.z=-.021;group.add(back);
    const screenMesh = new THREE.Mesh(new THREE.PlaneGeometry(2.72,5.35),new THREE.MeshBasicMaterial({map:buildScreen(THREE),transparent:true,toneMapped:false,depthWrite:false}));
    screenMesh.position.z=.255;group.add(screenMesh);
    const speaker = new THREE.Mesh(new THREE.BoxGeometry(.48,.025,.01),sideMat);speaker.position.set(0,2.69,.263);group.add(speaker);
    const cameraDot = new THREE.Mesh(new THREE.CircleGeometry(.043,28),new THREE.MeshStandardMaterial({color:0x050605,roughness:.3}));cameraDot.position.set(-.41,2.69,.269);group.add(cameraDot);
    for(const [pos,height] of [[[-1.49,.88,.09],.49],[[-1.49,.08,.09],.47],[[1.49,.73,.09],.75]]) {
      const button=new THREE.Mesh(new THREE.BoxGeometry(.048,height,.13),sideMat);button.position.set(...pos);group.add(button);
    }
    // Geometric coordinate satellites echo real route points without neon/glow.
    const satellites = new THREE.Group(); scene.add(satellites);
    const cubes = [];
    for (let i=0;i<15;i++) {
      const size = i%5===0?.105:.035 + (i%3)*.017;
      const mesh=new THREE.Mesh(new THREE.BoxGeometry(size,size,size),i%4===0?gold:outer);
      const angle=(i/15)*Math.PI*2;
      mesh.position.set(Math.cos(angle)*(2.45+(i%2)*.3),Math.sin(angle)*(2.65+(i%3)*.19),(-.5+(i%5)*.24));
      mesh.rotation.set(angle/4,angle/3,angle/2);
      satellites.add(mesh);cubes.push(mesh);
    }
    const cursor = {x:0,y:0};let scrollValue=0;
    function onMove(event){const bounds=visual.getBoundingClientRect();cursor.x=(event.clientX-(bounds.left+bounds.width/2))/bounds.width;cursor.y=(event.clientY-(bounds.top+bounds.height/2))/bounds.height;}
    function onLeave(){cursor.x=0;cursor.y=0;}
    function onScroll(){scrollValue=Math.max(-1,Math.min(1,(window.scrollY/(window.innerHeight||1))));}
    if(mqFinePointer.matches&&!reduce){visual.addEventListener('pointermove',onMove,{passive:true});visual.addEventListener('pointerleave',onLeave)}
    window.addEventListener('scroll',onScroll,{passive:true});
    let renderWidth=w, renderHeight=h;
    function resize(){
      if(!visual.isConnected)return;
      const rw=visual.clientWidth,rh=visual.clientHeight;
      if(!rw||!rh||(rw===renderWidth&&rh===renderHeight))return;
      renderWidth=rw;renderHeight=rh;camera.aspect=rw/rh;camera.position.z=rw<450?10.8:10.2;camera.updateProjectionMatrix();renderer.setSize(rw,rh,false);
    }
    window.addEventListener('resize',resize,{passive:true});
    let rafId=0;let running=true;let visible=true;let lastFrame=0;
    const observe = new IntersectionObserver(entries=>{visible=entries.some(e=>e.isIntersecting);if(visible&&!rafId&&!reduce)rafId=requestAnimationFrame(frame);},{threshold:0});observe.observe(visual);
    function renderFrame(time){
      const t=time*.001;
      const targetY=-.32+cursor.x*.23+scrollValue*.24;
      const targetX=-.1-cursor.y*.17+scrollValue*.12;
      group.rotation.y += (targetY-group.rotation.y)*.04;
      group.rotation.x += (targetX-group.rotation.x)*.04;
      group.rotation.z = Math.sin(t*.31)*.035-.10;
      group.position.y=Math.sin(t*.85)*.12;
      satellites.rotation.z=-t*.035;
      cubes.forEach((mesh,i)=>{mesh.rotation.y+=.0015+(i%3)*.0003;mesh.rotation.x+=.0006;});
      renderer.render(scene,camera);
    }
    function frame(time){rafId=0;if(!running||!visible||document.hidden)return;
      const interval=mobile?1000/34:1000/60;
      if(time-lastFrame>interval){lastFrame=time;renderFrame(time)}
      rafId=requestAnimationFrame(frame);
    }
    function visibilityChange(){if(!document.hidden&&visible&&!rafId&&!reduce)rafId=requestAnimationFrame(frame)}
    document.addEventListener('visibilitychange',visibilityChange);
    // Reduced-motion users get one static frame.
    if(reduce){group.rotation.set(-.12,-.3,-.10);renderer.render(scene,camera)}else rafId=requestAnimationFrame(frame);
    visual.classList.add('three-ready');
    return () => {
      running=false;cancelAnimationFrame(rafId);observe.disconnect();
      window.removeEventListener('resize',resize);window.removeEventListener('scroll',onScroll);
      document.removeEventListener('visibilitychange',visibilityChange);
      visual.removeEventListener('pointermove',onMove);visual.removeEventListener('pointerleave',onLeave);
      scene.traverse(obj=>{if(obj.geometry)obj.geometry.dispose();if(obj.material){const mats=Array.isArray(obj.material)?obj.material:[obj.material];mats.forEach(m=>{if(m.map)m.map.dispose();m.dispose()})}});
      renderer.dispose();
    };
  }
  window.addEventListener('pagehide',()=>{sceneCleanup?.();lenisInstance?.destroy?.()},{once:true});
})();
