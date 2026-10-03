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
   6. GESTION SESSION & NAVIGATION AUTH
═══════════════════════════════════════════════════════════ */

function getSessionUser() {
    try {
        const raw = localStorage.getItem('matchingcv_user');
        return raw ? JSON.parse(raw) : null;
    } catch (_) { return null; }
}

function initAuthNavigation() {
    const user = getSessionUser();

    /* ── Liens de déconnexion / profil si session active ── */
    const navLogin  = document.getElementById('nav-btn-login');
    const navSignup = document.getElementById('nav-btn-signup');

    if (user) {
        const dashUrl = user.role === 'ETUDIANT' ? '/dashboard-etudiant.html'
                      : user.role === 'RECRUTEUR' ? '/dashboard-recruteur.html'
                      : '/competences.html';

        const logo = document.getElementById('nav-logo') || document.querySelector('.nav-logo');
        if (logo) logo.href = dashUrl;

        if (navLogin && navSignup) {
            navLogin.textContent = user.prenom + ' ' + user.nom;
            navLogin.href = dashUrl;
            navLogin.removeAttribute('id');
            navSignup.textContent = 'Se d\u00e9connecter';
            navSignup.href = '#';
            navSignup.addEventListener('click', (e) => {
                e.preventDefault();
                localStorage.removeItem('matchingcv_user');
                window.location.reload();
            });
        }

        // Si l'utilisateur est étudiant, masquer l'option et la carte recruteur
        if (user.role === 'ETUDIANT') {
            const cardRecruteur = document.getElementById('card-recruteur');
            if (cardRecruteur) cardRecruteur.style.display = 'none';
            const btnRecruteurSignup = document.getElementById('btn-recruteur-signup');
            if (btnRecruteurSignup) btnRecruteurSignup.style.display = 'none';
            const btnRecruteurLogin = document.getElementById('btn-recruteur-login');
            if (btnRecruteurLogin) btnRecruteurLogin.style.display = 'none';
        } else if (user.role === 'RECRUTEUR') {
            const cardEtudiant = document.getElementById('card-etudiant');
            if (cardEtudiant) cardEtudiant.style.display = 'none';
            const btnEtudiantSignup = document.getElementById('btn-etudiant-signup');
            if (btnEtudiantSignup) btnEtudiantSignup.style.display = 'none';
            const btnEtudiantLogin = document.getElementById('btn-etudiant-login');
            if (btnEtudiantLogin) btnEtudiantLogin.style.display = 'none';
        }
        return;
    }

    /* ── Boutons "Se connecter" → login.html ── */
    const loginTargets = ['nav-btn-login', 'btn-etudiant-login', 'btn-recruteur-login'];
    loginTargets.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.href = '/login.html';
            el.removeEventListener('click', blockClick);
        }
    });

    /* ── Boutons "Créer un compte" / "S'inscrire" → register.html ── */
    const signupTargets = [
        'nav-btn-signup', 'hero-btn-signup',
        'btn-etudiant-signup', 'btn-recruteur-signup'
    ];
    signupTargets.forEach(id => {
        const el = document.getElementById(id);
        if (el) {
            el.href = '/register.html';
            el.removeEventListener('click', blockClick);
        }
    });
}

function blockClick(e) { e.preventDefault(); }

/* ═══════════════════════════════════════════════════════════
   INIT
═══════════════════════════════════════════════════════════ */

document.addEventListener('DOMContentLoaded', () => {
    checkApiHealth();
    initNavbar();
    initScrollReveal();
    initCardTilt();
    initCounters();
    initAuthNavigation();
});
