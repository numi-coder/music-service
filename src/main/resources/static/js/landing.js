/*
 * Resona AI landing page.
 * - Scrolling down moves the tour panels sideways (the page stays pinned meanwhile).
 * - Smooth, eased scrolling with a mouse wheel, like the original Framer page.
 * - Elements fade/slide in the first time they come into view.
 * - Animated light ("god rays") behind some panels, drawn with WebGL.
 * - FAQ accordion, jingle players and the request form.
 */
(function () {
    'use strict';

    const root = document.documentElement;
    root.classList.add('js');

    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const phone = window.matchMedia('(max-width: 899px), (orientation: portrait) and (max-width: 1100px)');

    const tour = document.getElementById('tour');
    const pin = tour.querySelector('.lp-pin');
    const track = document.getElementById('track');
    const after = document.getElementById('after');
    const jingles = document.getElementById('jingles');
    const faq = document.getElementById('faq');

    // ---------- layout: on phones the jingles and FAQ sit below the tour ----------
    function placeSections() {
        const target = phone.matches ? after : track;
        if (jingles.parentElement !== target) {
            target.appendChild(jingles);
            target.appendChild(faq);
        }
    }

    // ---------- the sideways tour ----------
    let travel = 0; // how far the track moves sideways, in px
    let tourTop = 0;

    function measure() {
        // Desktop panels are designed at 1440 x 900 and zoomed to fit the screen.
        const s = phone.matches ? 1 : Math.min(1.6, Math.max(0.6, Math.min(window.innerWidth / 1440, window.innerHeight / 900)));
        root.style.setProperty('--s', s.toFixed(4));
        placeSections();
        const panels = [...track.children];
        const last = panels[panels.length - 1];
        const pinHeight = Math.max(window.innerHeight, last.offsetHeight);
        pin.style.setProperty('--pin-h', pinHeight + 'px');
        travel = Math.max(0, track.scrollWidth - window.innerWidth);
        tour.style.height = travel + pinHeight + 'px';
        tourTop = tour.getBoundingClientRect().top + window.scrollY;
        update();
    }

    function update() {
        const x = Math.min(travel, Math.max(0, window.scrollY - tourTop));
        track.style.transform = `translate3d(${-x}px,0,0)`;
    }

    // Moving focus with the keyboard into a panel that is off to the side brings it into view.
    track.addEventListener('focusin', (event) => {
        const panel = event.target.closest('[data-panel]');
        if (!panel || panel.parentElement !== track) return;
        const target = tourTop + Math.min(travel, panel.offsetLeft);
        if (Math.abs(window.scrollY - target) > 4) scroller.jump(target);
    });

    // ---------- smooth wheel scrolling ----------
    const scroller = (() => {
        let target = window.scrollY;
        let current = window.scrollY;
        let running = false;
        let ownScroll = false;

        const maxScroll = () => document.documentElement.scrollHeight - window.innerHeight;

        function step() {
            current += (target - current) * 0.1;
            if (Math.abs(target - current) < 0.5) current = target;
            ownScroll = true;
            window.scrollTo(0, current);
            if (current !== target) requestAnimationFrame(step);
            else running = false;
        }

        function start() {
            if (!running) {
                running = true;
                requestAnimationFrame(step);
            }
        }

        if (!reducedMotion) {
            window.addEventListener('wheel', (event) => {
                if (event.ctrlKey || event.defaultPrevented) return;
                // Let scrollable areas inside the page (none today) keep their own wheel.
                event.preventDefault();
                const unit = event.deltaMode === 1 ? 32 : event.deltaMode === 2 ? window.innerHeight : 1;
                if (!running) current = window.scrollY;
                target = Math.min(maxScroll(), Math.max(0, target + event.deltaY * unit));
                start();
            }, { passive: false });
        }

        // Keyboard, scrollbar and touch scrolling move the page directly; follow them.
        window.addEventListener('scroll', () => {
            if (ownScroll) {
                ownScroll = false;
            } else if (!running) {
                target = current = window.scrollY;
            }
        }, { passive: true });

        return {
            jump(y) {
                target = current = y;
                window.scrollTo(0, y);
            },
        };
    })();

    let ticking = false;
    window.addEventListener('scroll', () => {
        if (!ticking) {
            ticking = true;
            requestAnimationFrame(() => {
                ticking = false;
                update();
            });
        }
    }, { passive: true });

    window.addEventListener('resize', measure);
    phone.addEventListener('change', measure);
    window.addEventListener('load', measure);
    if (document.fonts) document.fonts.ready.then(measure);
    measure();

    // ---------- appear animations ----------
    const appear = new IntersectionObserver((entries) => {
        for (const entry of entries) {
            if (!entry.isIntersecting) continue;
            const el = entry.target;
            el.style.transitionDelay = (el.dataset.delay || 0) + 'ms';
            el.classList.add('is-in');
            appear.unobserve(el);
        }
    }, { threshold: 0.1 });
    document.querySelectorAll('[data-appear]').forEach((el) => appear.observe(el));

    // ---------- spinning rings in the "how it works" cards ----------
    document.querySelectorAll('.lp-rings').forEach((box) => {
        for (let i = 0; i < 8; i++) {
            const ring = document.createElement('span');
            ring.className = 'lp-ring';
            const size = 80 + i * 16.8;
            ring.style.width = ring.style.height = size + 'px';
            ring.style.opacity = (0.8 - i * 0.064).toFixed(3);
            ring.style.animationDelay = (-i * 0.12) + 's';
            box.appendChild(ring);
        }
    });

    // ---------- god rays ----------
    const VERTEX = 'attribute vec2 p;void main(){gl_Position=vec4(p,0.,1.);}';
    const FRAGMENT = `
        precision mediump float;
        uniform vec2 res;
        uniform float time;
        uniform vec3 back;
        uniform vec3 ray;
        uniform float strength;
        float hash(vec2 p){return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453);}
        float noise(vec2 p){
            vec2 i=floor(p),f=fract(p);
            f=f*f*(3.-2.*f);
            return mix(mix(hash(i),hash(i+vec2(1.,0.)),f.x),mix(hash(i+vec2(0.,1.)),hash(i+vec2(1.,1.)),f.x),f.y);
        }
        void main(){
            vec2 uv=(gl_FragCoord.xy-.5*res)/min(res.x,res.y);
            float d=length(uv);
            vec2 dir=uv/max(d,1e-4);
            float t=time*.12;
            float rays=noise(dir*5.+vec2(t,-t))*.6+noise(dir*11.-vec2(t*1.3,t*.7))*.4;
            rays=pow(smoothstep(.25,1.,rays),1.6);
            float spots=noise(uv*3.+t*.5);
            float fade=smoothstep(1.25,.05,d);
            float glow=smoothstep(.75,0.,d);
            float v=clamp(rays*fade*(.55+.45*spots)+glow*.22,0.,1.);
            gl_FragColor=vec4(mix(back,ray,v*strength),1.);
        }`;

    function startRays(canvas) {
        const gl = canvas.getContext('webgl', { antialias: false, premultipliedAlpha: false });
        const color = (attr) => canvas.dataset[attr].split(',').map((n) => n / 255);
        if (!gl) {
            canvas.parentElement.style.background = `rgb(${canvas.dataset.back})`;
            return;
        }
        const shader = (type, source) => {
            const s = gl.createShader(type);
            gl.shaderSource(s, source);
            gl.compileShader(s);
            return s;
        };
        const program = gl.createProgram();
        gl.attachShader(program, shader(gl.VERTEX_SHADER, VERTEX));
        gl.attachShader(program, shader(gl.FRAGMENT_SHADER, FRAGMENT));
        gl.linkProgram(program);
        if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
            canvas.parentElement.style.background = `rgb(${canvas.dataset.back})`;
            return;
        }
        gl.useProgram(program);
        gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
        gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
        const loc = gl.getAttribLocation(program, 'p');
        gl.enableVertexAttribArray(loc);
        gl.vertexAttribPointer(loc, 2, gl.FLOAT, false, 0, 0);
        const u = (name) => gl.getUniformLocation(program, name);
        gl.uniform3fv(u('back'), color('back'));
        gl.uniform3fv(u('ray'), color('ray'));
        gl.uniform1f(u('strength'), Number(canvas.dataset.strength || .5));

        let visible = false;
        const started = performance.now() - Math.random() * 20000;

        function draw(now) {
            // Half resolution is plenty for a soft blur and keeps phones cool.
            const scale = Math.min(window.devicePixelRatio || 1, 2) * 0.5;
            const w = Math.max(1, Math.round(canvas.clientWidth * scale));
            const h = Math.max(1, Math.round(canvas.clientHeight * scale));
            if (canvas.width !== w || canvas.height !== h) {
                canvas.width = w;
                canvas.height = h;
                gl.viewport(0, 0, w, h);
            }
            gl.uniform2f(u('res'), w, h);
            gl.uniform1f(u('time'), (now - started) / 1000);
            gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
            if (visible && !reducedMotion) requestAnimationFrame(draw);
        }

        new IntersectionObserver(([entry]) => {
            const was = visible;
            visible = entry.isIntersecting;
            if (visible && !was) requestAnimationFrame(draw);
        }).observe(canvas);
        requestAnimationFrame(draw);
    }

    document.querySelectorAll('canvas.lp-rays').forEach(startRays);

    // ---------- logo ticker: two copies so it loops seamlessly ----------
    document.querySelectorAll('.lp-ticker-row').forEach((row) => {
        const copy = row.innerHTML;
        row.innerHTML = copy + copy.replace(/alt="[^"]*"/g, 'alt="" aria-hidden="true"');
    });

    // ---------- FAQ ----------
    document.querySelectorAll('.lp-faq-q').forEach((button) => {
        button.addEventListener('click', () => {
            const open = button.getAttribute('aria-expanded') === 'true';
            button.setAttribute('aria-expanded', String(!open));
            // The FAQ panel grows; the tour's length depends on it.
            setTimeout(measure, 480);
        });
    });

    // ---------- jingles ----------
    let playing = null;
    document.querySelectorAll('.lp-jingle').forEach((card) => {
        const audio = new Audio();
        audio.preload = 'none';
        audio.src = card.dataset.audio;
        audio.addEventListener('ended', () => card.classList.remove('is-playing'));
        card.addEventListener('click', () => {
            if (playing && playing.card !== card) {
                playing.audio.pause();
                playing.card.classList.remove('is-playing');
            }
            if (audio.paused) {
                audio.play().then(() => {
                    card.classList.add('is-playing');
                    playing = { card, audio };
                }).catch(() => card.classList.remove('is-playing'));
            } else {
                audio.pause();
                card.classList.remove('is-playing');
            }
        });
    });

    // ---------- request form ----------
    const form = document.getElementById('requestForm');
    const status = form.querySelector('.lp-form-status');
    const send = form.querySelector('.lp-send');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        status.classList.remove('is-error');
        const data = Object.fromEntries(new FormData(form));
        const emailOk = /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(String(data.email || '').trim());
        if (!String(data.companyName || '').trim() || !emailOk) {
            status.textContent = form.dataset.invalid;
            status.classList.add('is-error');
            return;
        }
        send.disabled = true;
        status.textContent = form.dataset.sending;
        try {
            const response = await fetch(form.action, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(data),
            });
            if (!response.ok) throw new Error('HTTP ' + response.status);
            form.classList.add('is-done');
            status.textContent = form.dataset.thanks;
        } catch (e) {
            status.textContent = form.dataset.error;
            status.classList.add('is-error');
            send.disabled = false;
        }
    });
})();
