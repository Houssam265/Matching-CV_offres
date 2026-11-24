'use strict';

// CV généré depuis le profil : consultation, édition, export PDF (impression navigateur).
document.addEventListener('DOMContentLoaded', () => {
    const user = window.MatchingCVSession?.read();
    if (!user || user.role === 'ADMIN') return;
    const recruiter = user.role === 'RECRUTEUR';
    const candidatureId = Number(new URLSearchParams(location.search).get('candidature'));

    const sheet = document.getElementById('cv-sheet');
    const status = document.getElementById('cv-status');
    const toolbar = document.getElementById('cv-toolbar');
    const actions = document.getElementById('cv-actions');
    const editor = document.getElementById('cv-editor');
    const checklist = document.getElementById('cv-checklist');
    const photoToggle = document.getElementById('cv-photo-toggle');
    const PHOTO_PREF = 'matchingcv_cv_photo';

    const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null && text !== '') e.textContent = text; return e; };
    const month = v => v ? new Date(v + 'T00:00:00').toLocaleDateString('fr-FR', { month: 'short', year: 'numeric' }) : '';
    const period = (a, b) => `${month(a)} – ${b ? month(b) : 'aujourd’hui'}`;
    const btn = (label, cls, onClick, href) => {
        const b = el(href ? 'a' : 'button', cls, label);
        if (href) b.href = href; else { b.type = 'button'; b.addEventListener('click', onClick); }
        return b;
    };
    const safeUrl = u => { try { const x = new URL(u); return ['https:', 'http:'].includes(x.protocol) ? x.href : null; } catch (_) { return null; } };
    const shortUrl = href => href.replace(/^https?:\/\/(www\.)?/, '').replace(/\/$/, '');

    // Icônes (constantes, jamais de données utilisateur dedans)
    const ICONS = {
        email: '<path d="M4 6h16v12H4z"/><path d="m4 7 8 6 8-6"/>',
        phone: '<path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2"/>',
        ville: '<path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/>',
        linkedin: '<rect x="3" y="3" width="18" height="18" rx="2"/><path d="M8 10v7M8 7v.01M12 17v-4a2 2 0 0 1 4 0v4M12 10v7"/>',
        github: '<path d="M9 19c-4 1.5-4-2-6-2.5M15 21v-3.5c0-1 .1-1.4-.5-2 2.8-.3 5.5-1.4 5.5-6a4.6 4.6 0 0 0-1.3-3.2 4.2 4.2 0 0 0-.1-3.2s-1.1-.3-3.5 1.3a12 12 0 0 0-6.2 0C6.5 2.8 5.4 3.1 5.4 3.1a4.2 4.2 0 0 0-.1 3.2A4.6 4.6 0 0 0 4 9.5c0 4.6 2.7 5.7 5.5 6-.6.6-.6 1.2-.5 2V21"/>',
        portfolio: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
        web: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>'
    };
    function contactItem(kind, label, text, href) {
        const li = el('li', 'cv-contact-item');
        const icon = el('span', 'cv-contact-icon');
        icon.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[kind] || ICONS.web}</svg>`;
        const value = href ? el('a', 'cv-contact-text', text) : el('span', 'cv-contact-text', text);
        if (href) { value.href = href; if (!href.startsWith('mailto:') && !href.startsWith('tel:')) { value.target = '_blank'; value.rel = 'noopener noreferrer'; } }
        value.title = label;
        li.append(icon, el('span', 'visually-hidden', label + ' : '), value);
        return li;
    }

    async function api(url, options) {
        const r = await fetch(url, options);
        const body = await r.json().catch(() => null);
        if (!r.ok) throw new Error(body?.message || 'Le CV n’a pas pu être chargé. Réessayez.');
        return body;
    }

    function section(title, children) {
        const list = children.filter(Boolean);
        if (!list.length) return null;
        const s = el('section', 'cv-section');
        s.append(el('h2', 'cv-section-title', title), ...list);
        return s;
    }

    function itemTop(titleNode, dateText) {
        const top = el('div', 'cv-item-top');
        top.append(titleNode);
        if (dateText) top.append(el('span', 'cv-item-date', dateText));
        return top;
    }

    function render(cv) {
        sheet.replaceChildren();
        const fullName = `${cv.prenom || ''} ${cv.nom || ''}`.trim();

        // ---------- En-tête : photo · identité · coordonnées (une par ligne)
        const head = el('div', 'cv-head'); // pas de <header> : les règles génériques des en-têtes ne s'y appliquent pas
        const photo = el('div', 'cv-photo');
        photo.setAttribute('role', 'img');
        photo.setAttribute('aria-label', `Photo de ${fullName}`);
        photo.dataset.photoUser = cv.etudiantId;
        window.MatchingCVPhoto?.paint(photo, cv.etudiantId);

        const id = el('div', 'cv-identity');
        id.append(el('h1', 'cv-name', fullName || 'Candidat'));
        if (cv.titre) id.append(el('p', 'cv-title', cv.titre));
        if (cv.disponibilite) id.append(el('p', 'cv-availability', cv.disponibilite));

        const contact = el('ul', 'cv-contact');
        if (cv.email) contact.append(contactItem('email', 'Email', cv.email, `mailto:${cv.email}`));
        if (cv.telephone) contact.append(contactItem('phone', 'Téléphone', cv.telephone, `tel:${cv.telephone.replace(/[^+0-9]/g, '')}`));
        if (cv.ville) contact.append(contactItem('ville', 'Ville', cv.ville));
        for (const l of cv.liens || []) {
            const href = safeUrl(l.url);
            if (href) contact.append(contactItem(l.type, l.libelle, shortUrl(href), href));
        }
        head.append(photo, id);
        if (contact.children.length) head.append(contact);
        sheet.append(head);

        // ---------- Sections : formation d'abord (profil étudiant)
        const parts = [];
        if (cv.resume) parts.push(section('Profil', [el('p', 'cv-summary', cv.resume)]));

        const f = cv.formation || {};
        if (f.filiere || f.etablissement) {
            const item = el('div', 'cv-item');
            item.append(itemTop(el('h3', 'cv-item-title', f.filiere || 'Formation'), f.periode || ''));
            if (f.etablissement) item.append(el('p', 'cv-item-org-line', f.etablissement));
            parts.push(section('Formation', [item]));
        }

        parts.push(section('Expérience professionnelle', (cv.experiences || []).map(x => {
            const item = el('div', 'cv-item');
            const what = el('h3', 'cv-item-title', x.poste);
            item.append(itemTop(what, period(x.dateDebut, x.dateFin)));
            item.append(el('p', 'cv-item-org-line', x.entreprise));
            if (x.competences?.length) item.append(el('p', 'cv-item-skills', x.competences.join(' · ')));
            return item;
        })));

        parts.push(section('Projets', (cv.projets || []).map(p => {
            const item = el('div', 'cv-item');
            item.append(el('h3', 'cv-item-title', p.titre));
            if (p.description) item.append(el('p', 'cv-item-text', p.description));
            if (p.competences?.length) item.append(el('p', 'cv-item-skills', p.competences.join(' · ')));
            return item;
        })));

        parts.push(section('Compétences', (cv.competences || []).map(g => {
            const row = el('div', 'cv-skill-row');
            row.append(el('span', 'cv-skill-cat', g.categorie), el('span', 'cv-skill-list', g.competences.join(', ')));
            return row;
        })));

        if (cv.langues?.length) {
            const ul = el('ul', 'cv-list-2col');
            cv.langues.forEach(l => ul.append(el('li', null, l)));
            parts.push(section('Langues', [ul]));
        }
        if (cv.interets?.length) parts.push(section('Centres d’intérêt', [el('p', 'cv-item-text', cv.interets.join(' · '))]));

        sheet.append(...parts.filter(Boolean));
        sheet.hidden = false;
        document.title = `CV — ${fullName || 'Candidat'}`;
        applyPhotoPref();
        if (!recruiter) renderChecklist(cv);
    }

    // ---------- Points à compléter (écran seulement, jamais imprimés)
    function renderChecklist(cv) {
        if (!checklist) return;
        const missing = [];
        if (!cv.titre || cv.titre.startsWith('Étudiant·e en')) missing.push(['Un titre professionnel', 'header']);
        if (!cv.resume) missing.push(['Un résumé de 3 à 4 phrases', 'header']);
        if (!cv.telephone) missing.push(['Un numéro de téléphone', 'header']);
        if (!(cv.liens || []).length) missing.push(['Un lien LinkedIn, GitHub ou portfolio', 'header']);
        if (!cv.formation?.periode) missing.push(['Les années de votre formation', 'header']);
        if (!(cv.langues || []).length) missing.push(['Les langues que vous parlez', 'header']);
        if (!(cv.experiences || []).length) missing.push(['Au moins une expérience (stage, job…)', 'profil']);
        if (!(cv.projets || []).length) missing.push(['Au moins un projet', 'profil']);
        if (!(cv.competences || []).length) missing.push(['Des compétences validées', 'profil']);
        checklist.replaceChildren();
        checklist.hidden = missing.length === 0;
        if (!missing.length) return;
        checklist.append(el('h2', null, `Pour un CV plus convaincant (${missing.length} point${missing.length > 1 ? 's' : ''})`));
        const ul = el('ul');
        for (const [label, where] of missing) {
            const li = el('li');
            if (where === 'header') li.append(btn(label, 'cv-check-link', openEditor));
            else { const a = el('a', 'cv-check-link', label); a.href = '/mon-compte.html'; li.append(a); }
            ul.append(li);
        }
        checklist.append(ul);
    }

    function applyPhotoPref() { sheet.classList.toggle('no-photo', !photoToggle.checked); }
    try { photoToggle.checked = localStorage.getItem(PHOTO_PREF) !== 'false'; } catch (_) { /* défaut : photo affichée */ }
    photoToggle.addEventListener('change', () => {
        try { localStorage.setItem(PHOTO_PREF, String(photoToggle.checked)); } catch (_) { /* préférence non mémorisée */ }
        applyPhotoPref();
    });

    function print() { status.textContent = ''; window.print(); }

    // Téléchargement d'un vrai fichier PDF généré par le serveur (texte sélectionnable, liens cliquables).
    async function download(event) {
        const button = event?.currentTarget;
        const url = (recruiter ? `/api/candidatures/${candidatureId}/cv-pdf` : '/api/account/cv-pdf') + `?photo=${photoToggle.checked}`;
        if (button) { button.disabled = true; button.dataset.label = button.textContent; button.textContent = 'Préparation du PDF…'; }
        status.textContent = '';
        try {
            const r = await fetch(url);
            if (!r.ok) {
                const body = await r.json().catch(() => null);
                throw new Error(body?.message || 'Le PDF n’a pas pu être généré. Réessayez.');
            }
            const blob = await r.blob();
            const disposition = r.headers.get('Content-Disposition') || '';
            const match = /filename\*=UTF-8''([^;]+)/i.exec(disposition) || /filename="?([^";]+)"?/i.exec(disposition);
            const name = match ? decodeURIComponent(match[1]) : 'CV.pdf';
            const link = document.createElement('a');
            link.href = URL.createObjectURL(blob);
            link.download = name;
            document.body.append(link);
            link.click();
            link.remove();
            setTimeout(() => URL.revokeObjectURL(link.href), 10000);
            status.textContent = `CV téléchargé : ${name}`;
        } catch (e) {
            status.textContent = e.message;
        } finally {
            if (button) { button.disabled = false; button.textContent = button.dataset.label || 'Télécharger le PDF'; }
        }
    }

    // ---------- Édition (étudiant)
    const FIELDS = ['headline', 'biography', 'availability', 'contactEmail', 'telephone', 'ville',
        'professionalUrl', 'githubUrl', 'portfolioUrl', 'formationPeriode', 'langues', 'interets'];
    let account = null;
    async function openEditor() {
        const form = document.getElementById('cv-editor-form');
        const out = document.getElementById('cv-editor-status');
        out.textContent = 'Chargement…';
        editor.hidden = false;
        editor.scrollIntoView({ behavior: 'smooth', block: 'start' });
        try {
            account = await api('/api/account');
            const p = account.profile || {};
            for (const name of FIELDS) if (form[name]) form[name].value = p[name] || '';
            form.contactEmail.placeholder = account.email || 'vous@exemple.com';
            out.textContent = '';
            form.headline.focus({ preventScroll: true });
        } catch (e) { out.textContent = e.message; }
    }

    async function load() {
        try {
            const cv = recruiter ? await api(`/api/candidatures/${candidatureId}/cv`) : await api('/api/account/cv-data');
            status.textContent = '';
            render(cv);
            toolbar.hidden = false;
            return cv;
        } catch (e) {
            status.textContent = e.message;
            return null;
        }
    }

    if (recruiter) {
        document.getElementById('cv-page-title').textContent = 'CV du candidat';
        document.querySelector('.topbar-bc-current')?.replaceChildren('CV du candidat');
        document.title = 'CV du candidat — MatchingCV';
        document.getElementById('cv-sub').textContent = 'CV généré à partir du profil du candidat. Ses coordonnées restent privées : contactez-le via la messagerie.';
        if (!Number.isSafeInteger(candidatureId) || candidatureId <= 0) { status.textContent = 'Ouvrez le CV depuis une candidature.'; return; }
        load().then(cv => {
            if (!cv) return;
            actions.append(btn('Envoyer un message', 'cv-btn', null, `/messages.html?c=${candidatureId}`),
                btn('Imprimer', 'cv-btn', print),
                btn('Télécharger le PDF', 'cv-btn cv-btn-primary', download));
        });
        return;
    }

    load().then(cv => {
        if (!cv) return;
        actions.append(btn('Modifier le CV', 'cv-btn', openEditor),
            btn('Projets & expériences', 'cv-btn', null, '/mon-compte.html'),
            btn('Imprimer', 'cv-btn', print),
            btn('Télécharger le PDF', 'cv-btn cv-btn-primary', download));
    });

    document.getElementById('cv-editor-cancel').addEventListener('click', () => { editor.hidden = true; });
    document.getElementById('cv-editor-form').addEventListener('submit', async e => {
        e.preventDefault();
        const form = e.currentTarget;
        const out = document.getElementById('cv-editor-status');
        for (const name of ['professionalUrl', 'githubUrl', 'portfolioUrl']) {
            const v = form[name].value.trim();
            if (v && !/^https:\/\/\S+$/.test(v)) { out.textContent = 'Les liens doivent commencer par https://'; form[name].focus(); return; }
        }
        const email = form.contactEmail.value.trim();
        if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) { out.textContent = 'Email de contact invalide.'; form.contactEmail.focus(); return; }
        if (form.telephone.value.trim() && !/^[+0-9 ().-]{6,25}$/.test(form.telephone.value.trim())) { out.textContent = 'Numéro de téléphone invalide.'; form.telephone.focus(); return; }
        if (!account) { out.textContent = 'Rechargez la page puis réessayez.'; return; }
        const save = document.getElementById('cv-editor-save');
        save.disabled = true; out.textContent = 'Enregistrement…';
        const body = { prenom: account.prenom, nom: account.nom, notificationsEnabled: account.profile?.notificationsEnabled !== false };
        for (const name of FIELDS) body[name] = form[name].value.trim();
        try {
            await api('/api/account', { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
            out.textContent = '';
            editor.hidden = true;
            await load();
            sheet.scrollIntoView({ behavior: 'smooth', block: 'start' });
        } catch (err) { out.textContent = err.message; }
        finally { save.disabled = false; }
    });
});
