/**
 * main.js — Matching CV-Offres
 *
 *  1. Health check  → /api/health
 *  2. Navbar        → scroll shadow + burger menu mobile
 *  3. Tilt 3D       → profile cards (desktop seulement)
 *  4. Scroll-reveal → stats + cards (IntersectionObserver)
 *  5. Compteurs     → animation chiffres au scroll
 *  6. Boutons CTA   → feedback temporaire (UI statique)
 */

'use strict';

const isMobile = () => window.innerWidth < 768;

/* ═══════════════════════════════════════════════════════════
   1. HEALTH CHECK
═══════════════════════════════════════════════════════════ */

async function checkApiHealth() {
    try {
        const res  = await fetch('/api/health', { cache: 'no-cache' });
        const data = await res.json();
        if (res.ok && data.status === 'OK') {
            console.log('[MatchingCV] Backend API disponible ✓');
        }
    } catch (err) {
        console.warn('[MatchingCV] Backend API non disponible:', err.message);
    }
}

/* ═══════════════════════════════════════════════════════════
   2. NAVBAR — scroll shadow + burger menu
═══════════════════════════════════════════════════════════ */

function initNavbar() {
    const navbar = document.getElementById('navbar');
    const burger = document.getElementById('nav-burger');
    if (!navbar) return;

    /* Ombre après 20px de scroll */
    const onScroll = () => {
        navbar.classList.toggle('scrolled', window.scrollY > 20);
    };
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll(); // état initial

    /* Burger menu mobile */
    if (burger) {
        burger.addEventListener('click', () => {
            const isOpen = navbar.classList.toggle('menu-open');
            burger.classList.toggle('open', isOpen);
            burger.setAttribute('aria-expanded', String(isOpen));
        });

        /* Ferme le menu au clic sur un lien */
        navbar.querySelectorAll('.nav-link, .btn-nav-outline, .btn-nav-filled').forEach(link => {
            link.addEventListener('click', () => {
                navbar.classList.remove('menu-open');
                burger.classList.remove('open');
                burger.setAttribute('aria-expanded', 'false');
            });
        });
    }

}

/* ═══════════════════════════════════════════════════════════
   3. TILT 3D — cartes profil (desktop ≥ 768px uniquement)
═══════════════════════════════════════════════════════════ */

function initCardTilt() {
    if (isMobile()) return;

    const cards  = document.querySelectorAll('.profile-card');
    const MAX    = 8;     // degrés max
    const SCALE  = 1.02;

    cards.forEach(card => {

        card.addEventListener('mouseenter', () => {
            card.style.transition = 'box-shadow .2s ease, border-color .2s ease';
            card.style.borderColor = 'transparent';
        });

        card.addEventListener('mousemove', (e) => {
            const r  = card.getBoundingClientRect();
            const cx = (e.clientX - r.left)  / r.width  - 0.5;
            const cy = (e.clientY - r.top)   / r.height - 0.5;

            const rX = -cy * MAX * 2;
            const rY =  cx * MAX * 2;
            const sX = cx * 18;
            const sY = cy * 18;
            const blur = 28 + Math.abs(cx + cy) * 18;
            const op   = (0.16 + Math.abs(cx + cy) * 0.09).toFixed(2);

            card.style.transform =
                `perspective(1200px) rotateX(${rX}deg) rotateY(${rY}deg) scale3d(${SCALE},${SCALE},${SCALE})`;
            card.style.boxShadow =
                `${sX}px ${sY}px ${blur}px rgba(31,56,100,${op})`;
        });

        card.addEventListener('mouseleave', () => {
            card.style.transition =
                'transform .45s cubic-bezier(.4,0,.2,1), ' +
                'box-shadow .45s cubic-bezier(.4,0,.2,1), ' +
                'border-color .3s ease';
            card.style.transform  = 'scale3d(1,1,1)';
            card.style.boxShadow  = '0 2px 8px rgba(31,56,100,.08)';
            card.style.borderColor = '';
        });
    });
}

/* ═══════════════════════════════════════════════════════════
   4. SCROLL-REVEAL — fade-in + translateY avec stagger
═══════════════════════════════════════════════════════════ */

function initScrollReveal() {
    const statItems = Array.from(document.querySelectorAll('.stat-item'));
    const cards     = Array.from(document.querySelectorAll('.profile-card'));

    [...statItems, ...cards].forEach(el => el.classList.add('reveal-hidden'));

    const makeObs = (items, delay) => new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            const i = items.indexOf(entry.target);
            setTimeout(() => {
                entry.target.classList.remove('reveal-hidden');
                entry.target.classList.add('reveal-visible');
            }, i * delay);
            obs.unobserve(entry.target);
        });
    }, { threshold: 0.15 });

    // Note: obs référencé avant declaration — on crée les obs séparément
    const obsStats = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            const i = statItems.indexOf(entry.target);
            setTimeout(() => {
                entry.target.classList.remove('reveal-hidden');
                entry.target.classList.add('reveal-visible');
            }, i * 90);
            obsStats.unobserve(entry.target);
        });
    }, { threshold: 0.15 });

    const obsCards = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (!entry.isIntersecting) return;
            const i = cards.indexOf(entry.target);
            setTimeout(() => {
                entry.target.classList.remove('reveal-hidden');
                entry.target.classList.add('reveal-visible');
            }, i * 120);
            obsCards.unobserve(entry.target);
        });
    }, { threshold: 0.12 });

    statItems.forEach(el => obsStats.observe(el));
    cards.forEach(el => obsCards.observe(el));
}

/* ═══════════════════════════════════════════════════════════
   5. COMPTEURS ANIMÉS
═══════════════════════════════════════════════════════════ */

function animateCounter(el, target, duration = 1600) {
    const t0 = performance.now();
    const tick = (now) => {
        const p    = Math.min((now - t0) / duration, 1);
        const ease = p === 1 ? 1 : 1 - Math.pow(2, -10 * p);
        el.textContent = Math.round(target * ease).toLocaleString('fr-FR') + (el.dataset.suffix || '');
        if (p < 1) requestAnimationFrame(tick);
    };
    requestAnimationFrame(tick);
}

function initCounters() {
    const counters = document.querySelectorAll('[data-count]');
    const obs = new IntersectionObserver((entries) => {
        entries.forEach(e => {
            if (!e.isIntersecting) return;
            animateCounter(e.target, parseInt(e.target.dataset.count, 10));
            obs.unobserve(e.target);
        });
    }, { threshold: 0.5 });
    counters.forEach(el => obs.observe(el));
}

/* ═══════════════════════════════════════════════════════════
   6. FEEDBACK BOUTONS CTA (UI statique)
═══════════════════════════════════════════════════════════ */

function initCtaFeedback() {
    const targets = [
        '#btn-etudiant-signup', '#btn-etudiant-login',
        '#btn-recruteur-signup', '#btn-recruteur-login',
        '#nav-btn-login', '#nav-btn-signup',
        '#hero-btn-signup',
    ];
    targets.forEach(sel => {
        const el = document.querySelector(sel);
        if (!el) return;
        el.addEventListener('click', (e) => {
            e.preventDefault();
            const orig = el.innerHTML;
            el.innerHTML  = '🔒 Disponible prochainement';
            el.style.opacity = '0.7';
            setTimeout(() => {
                el.innerHTML  = orig;
                el.style.opacity = '';
            }, 2200);
        });
    });
}

/* ═══════════════════════════════════════════════════════════
   INIT
═══════════════════════════════════════════════════════════ */

document.addEventListener('DOMContentLoaded', () => {
    checkApiHealth();
    initNavbar();
    initScrollReveal();
    initCardTilt();
    initCounters();
    initCtaFeedback();
});
