'use strict';

/**
 * Page "Mes candidatures" — liste filtrée par statut, avec score snapshot et lien offre.
 */
document.addEventListener('DOMContentLoaded', () => {
    const user = MatchingCVSession.protect('ETUDIANT');
    if (!user) return;

    // ── Navbar ──────────────────────────────────────────────
    const navUserName = document.getElementById('nav-user-name');
    if (navUserName) {
        navUserName.textContent = `${user.prenom || ''} ${user.nom || ''}`.trim() || 'Mon Compte';
        navUserName.addEventListener('click', () => { window.location.href = '/dashboard-etudiant.html'; });
    }
    document.getElementById('nav-logout')?.addEventListener('click', () => MatchingCVSession.logout());
    const burger = document.getElementById('nav-burger');
    const navLinks = document.getElementById('nav-links');
    if (burger && navLinks) {
        burger.addEventListener('click', () => {
            const open = navLinks.classList.toggle('nav-open');
            burger.setAttribute('aria-expanded', String(open));
        });
    }

    // ── State ────────────────────────────────────────────────
    let toutes = [];
    let filtreActif = 'ALL';

    const listEl    = document.getElementById('cand-list');
    const countEl   = document.getElementById('cand-count');

    // ── Chargement ────────────────────────────────────────────
    async function charger() {
        listEl.innerHTML = renderLoading();
        try {
            const res = await fetch(`/api/etudiants/${user.id}/candidatures`);
            if (!res.ok) throw new Error('Erreur réseau');
            toutes = await res.json();
            afficher();
        } catch (e) {
            listEl.innerHTML = renderErreur(e.message);
        }
    }

    // ── Affichage ─────────────────────────────────────────────
    function afficher() {
        const liste = filtreActif === 'ALL'
            ? toutes
            : toutes.filter(c => c.statut === filtreActif);

        const total = toutes.length;
        countEl.textContent = `${total} candidature${total > 1 ? 's' : ''}`;

        if (liste.length === 0) {
            listEl.innerHTML = renderVide();
            return;
        }

        listEl.innerHTML = liste.map(renderCarte).join('');
    }

    // ── Carte individuelle ────────────────────────────────────
    function renderCarte(c) {
        const scoreDisp = c.score !== null && c.score !== undefined;
        const scoreVal  = scoreDisp ? Math.round(c.score) : null;
        let orbClass = 'na';
        if (scoreDisp) {
            if (scoreVal >= 75) orbClass = 'high';
            else if (scoreVal >= 50) orbClass = 'med';
            else orbClass = 'low';
        }

        const cardClass = {
            ENVOYEE:  'env',
            VUE:      'vue',
            ACCEPTEE: 'acc',
            REFUSEE:  'ref'
        }[c.statut] || '';

        const statutLabel = {
            ENVOYEE:  '&#128338; Envoyée',
            VUE:      '&#128065; Vue par le recruteur',
            ACCEPTEE: '&#10003; Acceptée',
            REFUSEE:  '&#10007; Refusée'
        }[c.statut] || c.statut;

        const cloture = c.statutOffre && c.statutOffre !== 'ACTIVE';
        const contratLabel = c.typeContrat ? formatContrat(c.typeContrat) : '';
        const dateStr = c.dateCandidature ? formaterDate(c.dateCandidature) : '';
        const initiale = (c.entreprise || 'E').charAt(0).toUpperCase();

        return `
        <div class="cand-card ${cardClass}" data-id="${c.id}">
            <div>
                <div class="cand-header">
                    <div class="cand-logo">${escapeHtml(initiale)}</div>
                    <div>
                        <div class="cand-title">${escapeHtml(c.offreTitre || 'Offre')}</div>
                        <div class="cand-company">
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>
                            ${escapeHtml(c.entreprise || '—')}
                        </div>
                    </div>
                </div>
                <div class="cand-meta">
                    ${c.domaine ? `<span class="meta-pill">${escapeHtml(c.domaine)}</span>` : ''}
                    ${c.localisation ? `<span class="meta-pill">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>
                        ${escapeHtml(c.localisation)}</span>` : ''}
                    ${contratLabel ? `<span class="meta-pill">${contratLabel}</span>` : ''}
                    ${cloture ? `<span class="meta-pill cloture">Offre clôturée</span>` : ''}
                </div>
                <div><span class="statut-badge ${c.statut}">${statutLabel}</span></div>
                ${dateStr ? `<div class="cand-date">Postulé le ${dateStr}</div>` : ''}
                <a href="/offres-recommandees.html?offreId=${c.offreId}" class="btn-link-offre">
                    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>
                    Voir l'offre
                </a>
            </div>
            <div class="score-orb ${orbClass}" title="${scoreDisp ? 'Score au moment de la candidature' : 'Score non calculé'}">
                <span class="sv">${scoreDisp ? `${scoreVal}%` : 'N/D'}</span>
                <span class="sl">${scoreDisp ? 'Score' : '—'}</span>
            </div>
        </div>`;
    }

    // ── Empty / loading / error ───────────────────────────────
    function renderLoading() {
        return `<div class="empty-state">
            <div class="empty-icon"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/></svg></div>
            <h3>Chargement en cours…</h3><p>Récupération de vos candidatures.</p>
        </div>`;
    }
    function renderVide() {
        const msg = filtreActif === 'ALL'
            ? 'Vous n\'avez pas encore postulé à une offre.'
            : `Aucune candidature avec le statut « ${filtreActif} ».`;
        return `<div class="empty-state">
            <div class="empty-icon"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg></div>
            <h3>${msg}</h3>
            <p>Explorez les offres recommandées pour postuler en un clic.</p>
            <a href="/offres-recommandees.html"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg> Explorer les offres</a>
        </div>`;
    }
    function renderErreur(msg) {
        return `<div class="empty-state">
            <div class="empty-icon" style="background:#FEE2E2;color:#DC2626;"><svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg></div>
            <h3>Impossible de charger vos candidatures</h3>
            <p>${escapeHtml(msg || 'Veuillez réessayer.')}</p>
        </div>`;
    }

    // ── Filtres ───────────────────────────────────────────────
    document.querySelectorAll('.filter-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('.filter-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            filtreActif = btn.dataset.filter;
            afficher();
        });
    });

    // ── Utilitaires ───────────────────────────────────────────
    function escapeHtml(str) {
        if (!str) return '';
        return String(str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;')
            .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    }

    function formaterDate(iso) {
        try {
            const d = new Date(iso);
            return d.toLocaleDateString('fr-FR', { day: '2-digit', month: 'long', year: 'numeric' });
        } catch { return ''; }
    }

    function formatContrat(t) {
        return { STAGE: 'Stage', CDI: 'CDI', CDD: 'CDD', ALTERNANCE: 'Alternance', FREELANCE: 'Freelance' }[t] || t;
    }

    // ── Init ──────────────────────────────────────────────────
    charger();
});
