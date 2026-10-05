/**
 * auth.js — Gestion de l'authentification (connexion & inscription)
 * Matching CV-Offres | GI3, ENSA Tétouan
 *
 * Fonctions exportées :
 *   - initLoginPage()    : à appeler sur login.html
 *   - initRegisterPage() : à appeler sur register.html
 */

'use strict';

/* ─── Utilitaires ───────────────────────────────────────────── */
function escapeHtml(text) {
    if (!text) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function isValidEmail(email) {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function showAlert(containerId, message, type = 'danger') {
    const el = document.getElementById(containerId);
    if (!el) return;

    const icons = {
        success: `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`,
        danger:  `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
        info:    `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`
    };

    el.className = `auth-alert auth-alert-${type}`;
    el.innerHTML = `${icons[type] || icons.info}<span>${escapeHtml(message)}</span>`;
    el.style.display = 'flex';
    el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    if (type === 'success') {
        setTimeout(() => { el.style.display = 'none'; }, 6000);
    }
}

function hideAlert(containerId) {
    const el = document.getElementById(containerId);
    if (el) { el.style.display = 'none'; el.innerHTML = ''; }
}

function setFieldError(errorId, message) {
    const el = document.getElementById(errorId);
    if (el) el.textContent = message || '';
}

function clearFieldErrors(ids) {
    ids.forEach(id => setFieldError(id, ''));
}

function setInputState(inputEl, state) {
    if (!inputEl) return;
    inputEl.classList.remove('is-valid', 'is-invalid');
    if (state) inputEl.classList.add(state);
}

function setSubmitting(btnId, isSubmitting) {
    const btn = document.getElementById(btnId);
    if (!btn) return;
    btn.disabled = isSubmitting;
    btn.classList.toggle('loading', isSubmitting);
}

function togglePasswordVisibility(inputId, btnId) {
    const input = document.getElementById(inputId);
    const btn = document.getElementById(btnId);
    if (!input || !btn) return;
    btn.addEventListener('click', () => {
        const isText = input.type === 'text';
        input.type = isText ? 'password' : 'text';
        btn.setAttribute('aria-label', isText ? 'Voir le mot de passe' : 'Masquer le mot de passe');
        btn.innerHTML = isText
            ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`
            : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`;
    });
}

function initNavbar() {
    const navbar = document.getElementById('navbar');
    const burger = document.getElementById('nav-burger');
    if (!navbar) return;
    const onScroll = () => navbar.classList.toggle('scrolled', window.scrollY > 20);
    window.addEventListener('scroll', onScroll, { passive: true });
    onScroll();
    if (burger) {
        burger.addEventListener('click', () => {
            const isOpen = navbar.classList.toggle('menu-open');
            burger.classList.toggle('open', isOpen);
            burger.setAttribute('aria-expanded', String(isOpen));
        });
    }
}

/* ════════════════════════════════════════════════════════════
   PAGE CONNEXION
════════════════════════════════════════════════════════════ */
function initLoginPage() {
    initNavbar();
    togglePasswordVisibility('login-password', 'toggle-pwd');

    const form = document.getElementById('login-form');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert('login-alert');

        const email     = document.getElementById('login-email').value.trim();
        const motDePasse = document.getElementById('login-password').value;

        // Validation côté client
        let hasError = false;
        clearFieldErrors(['error-email', 'error-password']);

        if (!email || !isValidEmail(email)) {
            setFieldError('error-email', 'Veuillez saisir une adresse email valide.');
            setInputState(document.getElementById('login-email'), 'is-invalid');
            hasError = true;
        } else {
            setInputState(document.getElementById('login-email'), 'is-valid');
        }

        if (!motDePasse) {
            setFieldError('error-password', 'Le mot de passe est obligatoire.');
            setInputState(document.getElementById('login-password'), 'is-invalid');
            hasError = true;
        }

        if (hasError) return;

        setSubmitting('btn-login', true);

        try {
            const res = await fetch('/api/auth/connexion', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({ email, motDePasse })
            });

            const data = await res.json().catch(() => null);

            if (res.ok && data) {
                // Sauvegarde de la session en localStorage
                localStorage.setItem('matchingcv_user', JSON.stringify({
                    id: data.id,
                    prenom: data.prenom,
                    nom: data.nom,
                    email: data.email,
                    role: data.role
                }));

                showAlert('login-alert', data.message || 'Connexion réussie ! Redirection...', 'success');

                // Redirection selon le rôle
                setTimeout(() => {
                    const role = data.role;
                    if (role === 'ETUDIANT') {
                        window.location.href = '/dashboard-etudiant.html';
                    } else if (role === 'RECRUTEUR') {
                        window.location.href = '/dashboard-recruteur.html';
                    } else if (role === 'ADMIN') {
                        window.location.href = '/competences.html';
                    } else {
                        window.location.href = '/index.html';
                    }
                }, 1200);

            } else {
                const msg = (data && data.message) ? data.message : 'Identifiants incorrects. Vérifiez votre email et mot de passe.';
                showAlert('login-alert', msg, 'danger');
                setInputState(document.getElementById('login-password'), 'is-invalid');
            }

        } catch (err) {
            console.error('[Auth] Erreur réseau connexion:', err);
            showAlert('login-alert', 'Erreur réseau. Vérifiez votre connexion et réessayez.', 'danger');
        } finally {
            setSubmitting('btn-login', false);
        }
    });

    // Réinitialiser les erreurs au changement de champ
    document.getElementById('login-email')?.addEventListener('input', () => {
        setFieldError('error-email', '');
        setInputState(document.getElementById('login-email'), null);
        hideAlert('login-alert');
    });
    document.getElementById('login-password')?.addEventListener('input', () => {
        setFieldError('error-password', '');
        setInputState(document.getElementById('login-password'), null);
    });
}

/* ════════════════════════════════════════════════════════════
   PAGE INSCRIPTION
════════════════════════════════════════════════════════════ */
function initRegisterPage() {
    initNavbar();
    togglePasswordVisibility('reg-password', 'toggle-reg-pwd');
    initRoleToggle();
    initPasswordStrength();
    initEmailCheck();

    const form = document.getElementById('register-form');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert('register-alert');

        const prenom      = document.getElementById('reg-prenom')?.value.trim() || '';
        const nom         = document.getElementById('reg-nom')?.value.trim() || '';
        const email       = document.getElementById('reg-email')?.value.trim() || '';
        const motDePasse  = document.getElementById('reg-password')?.value || '';
        const role        = document.getElementById('register-role')?.value || 'ETUDIANT';
        const nomEntreprise   = document.getElementById('reg-entreprise')?.value.trim() || null;
        const secteurActivite = document.getElementById('reg-secteur')?.value.trim() || null;
        const filiere         = document.getElementById('reg-filiere')?.value.trim() || null;
        const etablissement   = document.getElementById('reg-etablissement')?.value.trim() || null;

        // Validation
        let hasError = false;
        clearFieldErrors(['error-prenom','error-nom','error-reg-email','error-reg-password']);

        if (!prenom) {
            setFieldError('error-prenom', 'Le prénom est obligatoire.');
            setInputState(document.getElementById('reg-prenom'), 'is-invalid');
            hasError = true;
        } else {
            setInputState(document.getElementById('reg-prenom'), 'is-valid');
        }

        if (!nom) {
            setFieldError('error-nom', 'Le nom est obligatoire.');
            setInputState(document.getElementById('reg-nom'), 'is-invalid');
            hasError = true;
        } else {
            setInputState(document.getElementById('reg-nom'), 'is-valid');
        }

        if (!email || !isValidEmail(email)) {
            setFieldError('error-reg-email', 'Adresse email invalide.');
            setInputState(document.getElementById('reg-email'), 'is-invalid');
            hasError = true;
        }

        if (!motDePasse || motDePasse.length < 6) {
            setFieldError('error-reg-password', 'Le mot de passe doit contenir au moins 6 caractères.');
            setInputState(document.getElementById('reg-password'), 'is-invalid');
            hasError = true;
        }

        if (hasError) return;

        setSubmitting('btn-register', true);

        const payload = { prenom, nom, email, motDePasse, role, nomEntreprise, secteurActivite, filiere, etablissement };

        try {
            const res = await fetch('/api/auth/inscription', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify(payload)
            });

            const data = await res.json().catch(() => null);

            if (res.status === 201 && data) {
                localStorage.setItem('matchingcv_user', JSON.stringify({
                    id: data.id,
                    prenom: data.prenom,
                    nom: data.nom,
                    email: data.email,
                    role: data.role
                }));

                showAlert('register-alert', data.message || 'Compte créé avec succès ! Redirection...', 'success');

                setTimeout(() => {
                    if (data.role === 'ETUDIANT') {
                        window.location.href = '/dashboard-etudiant.html';
                    } else if (data.role === 'RECRUTEUR') {
                        window.location.href = '/dashboard-recruteur.html';
                    } else {
                        window.location.href = '/index.html';
                    }
                }, 1500);

            } else {
                const msg = (data && data.message)
                    ? data.message
                    : (res.status === 409
                        ? 'Un compte existe déjà avec cette adresse email.'
                        : 'Erreur lors de la création du compte. Vérifiez vos informations.');
                showAlert('register-alert', msg, 'danger');
                if (res.status === 409) {
                    setInputState(document.getElementById('reg-email'), 'is-invalid');
                    setFieldError('error-reg-email', 'Email déjà utilisé.');
                }
            }

        } catch (err) {
            console.error('[Auth] Erreur réseau inscription:', err);
            showAlert('register-alert', 'Erreur réseau. Vérifiez votre connexion et réessayez.', 'danger');
        } finally {
            setSubmitting('btn-register', false);
        }
    });
}

/* ── Bascule Étudiant / Recruteur ────────────────────────────── */
function initRoleToggle() {
    const roleInput    = document.getElementById('register-role');
    const recruteurFields = document.getElementById('recruteur-fields');
    const btnEtudiant  = document.getElementById('role-etudiant');
    const btnRecruteur = document.getElementById('role-recruteur');

    if (!roleInput || !btnEtudiant || !btnRecruteur) return;

    [btnEtudiant, btnRecruteur].forEach(btn => {
        btn.addEventListener('click', () => {
            const role = btn.getAttribute('data-role');
            roleInput.value = role;

            btnEtudiant.classList.toggle('active',  role === 'ETUDIANT');
            btnRecruteur.classList.toggle('active', role === 'RECRUTEUR');

            if (recruteurFields) {
                recruteurFields.style.display = role === 'RECRUTEUR' ? 'flex' : 'none';
            }
            const etudiantFields = document.getElementById('etudiant-fields');
            if (etudiantFields) {
                etudiantFields.style.display = role === 'ETUDIANT' ? 'block' : 'none';
            }
        });
    });
}

/* ── Indicateur de force du mot de passe ────────────────────── */
function initPasswordStrength() {
    const input = document.getElementById('reg-password');
    const bar   = document.getElementById('pwd-strength-bar');
    const fill  = document.getElementById('pwd-strength-fill');
    const label = document.getElementById('pwd-strength-label');

    if (!input || !bar || !fill || !label) return;

    input.addEventListener('input', () => {
        const val = input.value;
        if (!val) {
            bar.classList.remove('visible');
            fill.className = 'pwd-strength-fill';
            label.textContent = 'Au moins 6 caractères';
            return;
        }

        bar.classList.add('visible');

        let strength = 0;
        if (val.length >= 6)  strength++;
        if (val.length >= 10) strength++;
        if (/[A-Z]/.test(val) && /[0-9]/.test(val)) strength++;

        fill.className = 'pwd-strength-fill';
        if (strength === 1) {
            fill.classList.add('weak');
            label.textContent = 'Mot de passe faible';
        } else if (strength === 2) {
            fill.classList.add('fair');
            label.textContent = 'Mot de passe moyen';
        } else {
            fill.classList.add('strong');
            label.textContent = 'Mot de passe fort ✓';
        }
    });
}

/* ── Vérification email en temps réel ───────────────────────── */
function initEmailCheck() {
    const input   = document.getElementById('reg-email');
    const status  = document.getElementById('email-status');
    if (!input) return;

    let debounceTimer;

    input.addEventListener('blur', async () => {
        const email = input.value.trim();
        if (!email || !isValidEmail(email)) return;
        clearTimeout(debounceTimer);

        try {
            const res = await fetch(`/api/auth/check-email?email=${encodeURIComponent(email)}`);
            const data = await res.json();
            if (!data.disponible) {
                setInputState(input, 'is-invalid');
                setFieldError('error-reg-email', 'Cette adresse email est déjà associée à un compte.');
                if (status) status.textContent = '';
            } else {
                setInputState(input, 'is-valid');
                setFieldError('error-reg-email', '');
            }
        } catch (_) { /* Pas bloquant */ }
    });

    input.addEventListener('input', () => {
        setInputState(input, null);
        setFieldError('error-reg-email', '');
    });
}
