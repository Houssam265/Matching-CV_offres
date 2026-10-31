'use strict';

document.addEventListener('DOMContentLoaded', async () => {
    const user = MatchingCVSession.require('RECRUTEUR');
    if (!user) return;
    const main = document.querySelector('main');
    const page = document.body.dataset.page;
    const error = document.getElementById('recruit-error');
    const base = `/api/recruteurs/${user.id}`;
    const owned = `recruteurId=${encodeURIComponent(user.id)}`;
    const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;'}[c]));
    const date = value => value ? new Date(value).toLocaleDateString('fr-FR', {day: '2-digit', month: 'short', year: 'numeric'}) : '—';
    const showError = message => { if (error) { error.textContent = message; error.hidden = false; error.scrollIntoView({block: 'nearest'}); } };
    const clearError = () => { if (error) { error.hidden = true; error.textContent = ''; } };

    // Set navbar user info
    const navUserName = document.getElementById('nav-user-name');
    if (navUserName) {
        navUserName.textContent = `${user.prenom || ''} ${user.nom || ''}`.trim() || 'Recruteur';
    }
    const navLogout = document.getElementById('nav-logout');
    if (navLogout) {
        navLogout.addEventListener('click', () => MatchingCVSession.logout());
    }

    // Navbar mobile toggle & scroll
    const navbar = document.getElementById('navbar');
    const burger = document.getElementById('nav-burger');
    if (burger && navbar) {
        burger.addEventListener('click', () => {
            const open = navbar.classList.toggle('menu-open');
            burger.classList.toggle('open', open);
            burger.setAttribute('aria-expanded', String(open));
        });
    }
    if (navbar) {
        window.addEventListener('scroll', () => navbar.classList.toggle('scrolled', window.scrollY > 20), {passive: true});
    }

    // ── Cloche de notifications temps réel (Contrat socle NotificationBell) ──
    if (window.NotificationBell) {
        window.NotificationBell.init({ userId: user.id });

        window.NotificationBell.onNotification(async (notification) => {
            if (!notification) return;

            // 1. Mise à jour instantanée sans rechargement à l'arrivée d'une NOUVELLE_CANDIDATURE
            if (notification.type === 'NOUVELLE_CANDIDATURE') {
                if (page === 'dashboard') {
                    await refreshDashboardLive();
                } else if (page === 'offres') {
                    await loadList();
                } else if (page === 'candidats') {
                    const currentOffreId = new URLSearchParams(location.search).get('offre') || new URLSearchParams(location.search).get('offreId');
                    if (currentOffreId && String(notification.offreId) === String(currentOffreId)) {
                        if (typeof window.__refreshCandidatsLive === 'function') {
                            await window.__refreshCandidatsLive(notification.candidatureId);
                        }
                    }
                }
            } else if (notification.type === 'COMPETENCE_VALIDEE' || notification.type === 'COMPETENCE_REJETEE') {
                if (page === 'detail') {
                    const currentOffreId = new URLSearchParams(location.search).get('id');
                    if (notification.offreId && String(notification.offreId) === String(currentOffreId)) {
                        window.location.reload();
                    }
                }
            }
        });
    }

    function pulseElement(el) {
        if (!el) return;
        el.classList.remove('counter-pop');
        void el.offsetWidth;
        el.classList.add('counter-pop');
    }

    async function findOffreIdForCompetence(competenceId) {
        if (!competenceId) return null;
        try {
            const offres = await api(`${base}/offres`);
            if (offres && offres.length > 0) {
                for (const o of offres) {
                    const comps = o.competences || [];
                    if (comps.some(c => String(c.id || c.competenceId) === String(competenceId))) {
                        return o.id;
                    }
                }
                return offres[0].id;
            }
        } catch (_) {}
        return null;
    }

    async function markNotificationAsReadForCandidature(candId) {
        if (!candId) return;
        try {
            const res = await fetch(`/api/utilisateurs/${user.id}/notifications?nonLues=true&size=50`, {
                headers: { 'Accept': 'application/json' }
            });
            if (!res.ok) return;
            const pageData = await res.json();
            const unreads = pageData.content || [];
            const matches = unreads.filter(n => !n.lue && (
                String(n.candidatureId) === String(candId) ||
                (n.lien && (n.lien.includes(`candidature=${candId}`) || n.lien.includes(`candidatureId=${candId}`)))
            ));
            for (const notif of matches) {
                await fetch(`/api/notifications/${notif.id}/lue`, {
                    method: 'PATCH',
                    headers: { 'Accept': 'application/json' }
                });
                notif.lue = true;
                if (window.NotificationBell && typeof window.NotificationBell.markAsRead === 'function') {
                    window.NotificationBell.markAsRead({ id: notif.id, lue: false, lien: null });
                }
            }
        } catch (e) {
            console.warn("Erreur marquage notification lue :", e);
        }
    }

    // Interception des clics sur les notifications pour les redirections recruteur appropriées
    document.addEventListener('click', async (e) => {
        const item = e.target.closest('.notif-item, .notif-toast');
        if (!item) return;

        if (e.target.closest('.notif-toast-close') || e.target.closest('.notif-btn-readall') || e.target.closest('.notif-dropdown-footer')) {
            return;
        }

        const notifId = item.dataset.id ? Number(item.dataset.id) : null;
        if (!notifId) return;

        e.stopPropagation();
        e.preventDefault();

        try {
            const patchRes = await fetch(`/api/notifications/${notifId}/lue`, {
                method: 'PATCH',
                headers: { 'Accept': 'application/json' }
            });
            const notifDto = patchRes.ok ? await patchRes.json() : null;

            if (window.NotificationBell && typeof window.NotificationBell.markAsRead === 'function') {
                window.NotificationBell.markAsRead({ id: notifId, lue: true, lien: null });
            }

            if (!notifDto) return;

            if (notifDto.type === 'NOUVELLE_CANDIDATURE') {
                const targetOffre = notifDto.offreId;
                const targetCand = notifDto.candidatureId;
                if (targetOffre && targetCand) {
                    window.location.href = `/candidats-offre.html?offre=${targetOffre}&candidature=${targetCand}`;
                    return;
                }
            } else if (notifDto.type === 'COMPETENCE_VALIDEE' || notifDto.type === 'COMPETENCE_REJETEE') {
                let targetOffre = notifDto.offreId;
                if (!targetOffre && notifDto.competenceId) {
                    targetOffre = await findOffreIdForCompetence(notifDto.competenceId);
                }
                if (targetOffre) {
                    window.location.href = `/offre-recruteur.html?id=${targetOffre}`;
                    return;
                } else {
                    window.location.href = '/mes-offres.html';
                    return;
                }
            }

            if (notifDto.lien) {
                window.location.href = notifDto.lien;
            }
        } catch (err) {
            console.error("Erreur traitement clic notification :", err);
        }
    }, true);

    async function api(url, method = 'GET', body) {
        const response = await fetch(url, {
            method,
            headers: {'Accept': 'application/json', ...(body ? {'Content-Type': 'application/json'} : {})},
            ...(body ? {body: JSON.stringify(body)} : {})
        });
        const data = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || `Requête impossible (${response.status}).`);
        return data;
    }

    function scoreUnavailable(competences) {
        return !competences.some(c => c.typeExigence === 'REQUISE' && c.statut === 'VALIDEE');
    }

    function competenceNote(c) {
        if (c.statut === 'EN_ATTENTE') return '<p class="recruit-warning" style="margin:0.4rem 0 0;font-size:0.8rem;">En attente de validation administrative — non comptabilisée tant que non validée.</p>';
        if (c.statut === 'REJETEE') return '<p class="recruit-warning" style="margin:0.4rem 0 0;font-size:0.8rem;">Rejetée — ignorée dans le calcul du score. Vous pouvez la remplacer.</p>';
        return '';
    }

    function card(o) {
        const isAct = o.statut === 'ACTIVE';
        const competences = o.competences || [];
        const requiredComps = competences.filter(c => c.typeExigence === 'REQUISE');
        const bonusComps = competences.filter(c => c.typeExigence === 'ATOUT');

        const skillsHtml = competences.length > 0 ? `
            <div class="recruit-card-skills" aria-label="Compétences de l'offre">
                ${requiredComps.map(c => `<span class="recruit-skill-pill required" title="Compétence requise : ${esc(c.nom)}">${esc(c.nom)}</span>`).join('')}
                ${bonusComps.map(c => `<span class="recruit-skill-pill bonus" title="Atout : ${esc(c.nom)}">+ ${esc(c.nom)}</span>`).join('')}
            </div>` : '';

        const scoreAlert = scoreUnavailable(competences) ? `
            <div class="recruit-warning" style="margin:0.75rem 0;font-size:0.82rem;">
                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;" aria-hidden="true">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="8" x2="12" y2="12"></line>
                    <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>
                <span>Score indisponible : aucune compétence requise n’est encore validée.</span>
            </div>` : '';

        const hasDeadline = !!o.dateLimite;
        const deadlineDate = hasDeadline ? new Date(o.dateLimite) : null;
        const isExpired = deadlineDate && (deadlineDate.getTime() < Date.now());
        const deadlineBadge = hasDeadline ? `
            <span class="recruit-badge ${isExpired ? 'rejected' : ''}" style="font-size:0.75rem; display:inline-flex; align-items:center; gap:0.35rem;" title="${isExpired ? 'Date limite de candidature échue' : 'Date limite de candidature'}">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 14 14"></polyline></svg>
                ${isExpired ? 'Échue le ' : 'Limite : '} ${date(o.dateLimite)}
            </span>` : '';

        const hasCands = (o.nbCandidatures || 0) > 0;
        const deleteTooltip = hasCands
            ? "Cette offre a reçu des candidatures et ne peut pas être supprimée. Proposez de la clôturer."
            : "Supprimer définitivement cette offre";

        return `
        <article class="recruit-card" data-card-id="${o.id}" data-statut="${o.statut}" data-title="${esc(o.titre).toLowerCase()}" data-domain="${esc(o.domaine).toLowerCase()}" data-loc="${esc(o.localisation).toLowerCase()}">
            <div class="recruit-card-header">
                <div class="recruit-card-tags">
                    <span class="recruit-badge ${isAct ? 'active' : ''}">
                        ${isAct ? '<span class="pulse-dot"></span> Active' : 'Clôturée'}
                    </span>
                    <span class="contract-badge ${esc(o.typeContrat)}">${esc(o.typeContrat)}</span>
                    ${deadlineBadge}
                </div>
                <span class="recruit-muted" style="font-size:0.8rem;">Publiée le ${date(o.datePublication)}</span>
            </div>

            <h3><a href="/offre-recruteur.html?id=${o.id}">${esc(o.titre)}</a></h3>

            <div class="recruit-meta-row">
                ${o.entreprise ? `
                <span class="recruit-meta-item">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="7" width="20" height="14" rx="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                    <strong>${esc(o.entreprise)}</strong>
                </span>` : ''}
                <span class="recruit-meta-item">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                    ${esc(o.domaine)}
                </span>
                <span class="recruit-meta-item">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                    ${esc(o.localisation)}
                </span>
                <span class="recruit-meta-item">
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
                    <strong>${o.nbCandidatures || 0}</strong> candidature(s) <span class="recruit-muted" style="margin-left:0.25rem;">(${o.nbATraiter || 0} à traiter)</span>
                </span>
            </div>

            ${skillsHtml}
            ${scoreAlert}

            <div class="recruit-actions">
                <a class="recruit-btn primary" href="/candidats-offre.html?offre=${o.id}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
                    Candidats (${o.nbCandidatures || 0})
                </a>
                <a class="recruit-btn" href="/offre-recruteur.html?id=${o.id}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                    Consulter
                </a>
                <a class="recruit-btn" href="/publier-offre.html?id=${o.id}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                    Modifier
                </a>
                <a class="recruit-btn" href="/publier-offre.html?duplicateFrom=${o.id}" title="Dupliquer cette offre (conserve les compétences sans candidatures)">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                    Dupliquer
                </a>
                ${isAct ? `
                <button type="button" class="recruit-btn" data-action="close" data-id="${o.id}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                    Clôturer
                </button>` : `
                <button type="button" class="recruit-btn" data-action="reopen" data-id="${o.id}" style="color:var(--rec-teal); border-color:var(--rec-teal);" title="Rouvrir cette offre pour accepter à nouveau les candidatures">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 4v6h6"></path><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>
                    Rouvrir
                </button>`}
                <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}" data-has-cands="${hasCands}"
                    ${hasCands ? 'style="opacity:0.55; cursor:not-allowed;"' : ''}
                    title="${esc(deleteTooltip)}" aria-label="${esc(deleteTooltip)}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                    Supprimer
                </button>
            </div>
        </article>`;
    }

    let allLoadedOffres = [];

    function renderFilteredList(offres) {
        const listEl = document.getElementById('offres-list');
        if (!listEl) return;

        if (offres.length === 0) {
            listEl.innerHTML = `
            <div class="recruit-empty">
                <div class="recruit-empty-icon" aria-hidden="true">
                    <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="11" cy="11" r="8"></circle>
                        <line x1="21" y1="21" x2="16.65" y2="16.65"></line>
                    </svg>
                </div>
                <div class="recruit-empty-title">Aucune offre trouvée</div>
                <p>Aucune publication ne correspond à vos filtres ou vous n'avez pas encore publié d'offre.</p>
                <a class="recruit-btn primary" href="/publier-offre.html">Publier une offre</a>
            </div>`;
            return;
        }

        listEl.innerHTML = (page === 'dashboard' ? offres.slice(0, 5) : offres).map(card).join('');
    }

    async function loadList() {
        const offres = await api(`${base}/offres`);
        allLoadedOffres = offres || [];

        const actCount = allLoadedOffres.filter(o => o.statut === 'ACTIVE').length;
        const clsCount = allLoadedOffres.filter(o => o.statut === 'CLOTUREE').length;
        const totalCount = allLoadedOffres.length;

        if (page === 'dashboard') {
            const activeCountEl = document.getElementById('active-count');
            if (activeCountEl) activeCountEl.textContent = actCount;
            const closedCountEl = document.getElementById('closed-count');
            if (closedCountEl) closedCountEl.textContent = clsCount;
            const totalCountEl = document.getElementById('total-count');
            if (totalCountEl) totalCountEl.textContent = totalCount;

            // Load recruiter enterprise info for the dashboard
            try {
                const recruteurInfo = await api(base);
                if (recruteurInfo) {
                    const nameEl = document.getElementById('recruiter-display-name');
                    if (nameEl) {
                        nameEl.textContent = `Bonjour, ${recruteurInfo.prenom || user.prenom || ''} ${recruteurInfo.nom || user.nom || ''}`.trim() || 'Bienvenue';
                    }
                    const entEl = document.getElementById('recruiter-display-enterprise');
                    if (entEl && recruteurInfo.nomEntreprise) {
                        entEl.innerHTML = `
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="7" width="20" height="14" rx="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                            <span>${esc(recruteurInfo.nomEntreprise)} ${recruteurInfo.secteurActivite ? '· ' + esc(recruteurInfo.secteurActivite) : ''}</span>
                        `;
                    }
                    const avatarEl = document.getElementById('recruiter-avatar');
                    if (avatarEl) {
                        const initials = ((recruteurInfo.prenom || user.prenom || 'R')[0] + (recruteurInfo.nom || user.nom || 'E')[0]).toUpperCase();
                        avatarEl.textContent = initials;
                    }
                }
            } catch (_) {}

            // Load recruiter dashboard metrics & recent candidatures (PHASE 2)
            await renderDashboardMetrics(false);
        }

        if (page === 'offres') {
            const countAllEl = document.getElementById('count-all');
            if (countAllEl) countAllEl.textContent = totalCount;
            const countActEl = document.getElementById('count-active');
            if (countActEl) countActEl.textContent = actCount;
            const countClsEl = document.getElementById('count-closed');
            if (countClsEl) countClsEl.textContent = clsCount;
        }

        renderFilteredList(allLoadedOffres);
    }

    // Filter toolbar interactions on mes-offres.html
    if (page === 'offres') {
        let activeFilter = 'ALL';
        let searchQuery = '';

        function applyFilters() {
            let filtered = allLoadedOffres;
            if (activeFilter === 'ACTIVE') {
                filtered = filtered.filter(o => o.statut === 'ACTIVE');
            } else if (activeFilter === 'CLOTUREE') {
                filtered = filtered.filter(o => o.statut === 'CLOTUREE');
            }

            if (searchQuery.trim()) {
                const q = searchQuery.toLowerCase();
                filtered = filtered.filter(o => 
                    (o.titre && o.titre.toLowerCase().includes(q)) ||
                    (o.domaine && o.domaine.toLowerCase().includes(q)) ||
                    (o.localisation && o.localisation.toLowerCase().includes(q)) ||
                    (o.typeContrat && o.typeContrat.toLowerCase().includes(q))
                );
            }

            renderFilteredList(filtered);
        }

        const filterContainer = document.getElementById('status-filters');
        if (filterContainer) {
            filterContainer.addEventListener('click', e => {
                const btn = e.target.closest('button[data-filter]');
                if (!btn) return;
                filterContainer.querySelectorAll('.recruit-filter-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                activeFilter = btn.dataset.filter;
                applyFilters();
            });
        }

        const searchInput = document.getElementById('offres-search');
        if (searchInput) {
            searchInput.addEventListener('input', e => {
                searchQuery = e.target.value;
                applyFilters();
            });
        }
    }

    if (main) {
        main.addEventListener('click', async event => {
            const button = event.target.closest('button[data-action]');
            if (!button) return;
            const action = button.dataset.action;
            if (action !== 'close' && action !== 'delete' && action !== 'reopen') return;

            const id = button.dataset.id;
            const close = action === 'close';

            if (action === 'reopen') {
                const newDeadlineStr = window.prompt(
                    'Rouvrir cette offre ?\n\n' +
                    'Vous pouvez facultativement définir une nouvelle date limite de candidature (format: AAAA-MM-JJ ou laissez vide pour aucune date limite) :'
                );
                if (newDeadlineStr === null) return;
                let payload = null;
                if (newDeadlineStr.trim()) {
                    let parsedDate = new Date(newDeadlineStr.trim());
                    if (isNaN(parsedDate.getTime()) || parsedDate.getTime() <= Date.now()) {
                        showError("La nouvelle date limite doit être valide et située dans le futur.");
                        return;
                    }
                    payload = { dateLimite: parsedDate.toISOString() };
                }
                button.disabled = true;
                clearError();
                try {
                    await api(`/api/offres/${id}/rouvrir?${owned}`, 'PATCH', payload);
                    if (page === 'detail') window.location.reload();
                    else await loadList();
                } catch (e) {
                    showError(e.message);
                } finally {
                    button.disabled = false;
                }
                return;
            }

            if (close) {
                if (!window.confirm('Clôturer cette offre ? Elle disparaîtra des listes et recommandations étudiantes, mais restera accessible pour consulter les candidatures reçues.')) return;
                button.disabled = true;
                clearError();
                try {
                    await api(`/api/offres/${id}/cloturer?${owned}`, 'PATCH');
                    if (page === 'detail') {
                        window.location.reload();
                    } else {
                        await loadList();
                    }
                } catch (e) {
                    showError(e.message);
                } finally {
                    button.disabled = false;
                }
                return;
            }

            if (action === 'delete') {
                const hasCands = button.dataset.hasCands === 'true' || 
                    (allLoadedOffres.find(o => String(o.id) === String(id))?.nbCandidatures > 0);

                if (hasCands) {
                    const wantClose = window.confirm(
                        'Cette offre a déjà reçu des candidatures et ne peut pas être supprimée.\n\n' +
                        'Souhaitez-vous la clôturer à la place pour la retirer des listes publiques tout en conservant l\'accès aux candidatures reçues ?'
                    );
                    if (wantClose) {
                        button.disabled = true;
                        clearError();
                        try {
                            await api(`/api/offres/${id}/cloturer?${owned}`, 'PATCH');
                            if (page === 'detail') {
                                window.location.reload();
                            } else {
                                await loadList();
                            }
                        } catch (err) {
                            showError(err.message);
                        } finally {
                            button.disabled = false;
                        }
                    }
                    return;
                }

                if (!window.confirm('Supprimer définitivement cette offre ? Cette action est irréversible.')) return;
                button.disabled = true;
                clearError();
                try {
                    await api(`/api/offres/${id}?${owned}`, 'DELETE');
                    if (page === 'detail') {
                        window.location.assign('/mes-offres.html');
                    } else {
                        await loadList();
                    }
                } catch (e) {
                    if (e.message && (e.message.includes('candidatures') || e.message.includes('Clôturez-la'))) {
                        const wantClose = window.confirm(
                            e.message + '\n\nSouhaitez-vous clôturer cette offre à la place ?'
                        );
                        if (wantClose) {
                            try {
                                await api(`/api/offres/${id}/cloturer?${owned}`, 'PATCH');
                                if (page === 'detail') window.location.reload();
                                else await loadList();
                            } catch (err) {
                                showError(err.message);
                            }
                        }
                    } else {
                        showError(e.message);
                    }
                } finally {
                    button.disabled = false;
                }
            }
        });
    }

    try {
        if (page === 'dashboard' || page === 'offres') await loadList();

        if (page === 'detail') {
            const id = new URLSearchParams(location.search).get('id');
            if (!id || !/^\d+$/.test(id)) throw new Error('Identifiant d’offre invalide.');
            const o = await api(`/api/offres/${id}?${owned}`);
            const isAct = o.statut === 'ACTIVE';

            const reqComps = (o.competences || []).filter(c => c.typeExigence === 'REQUISE');
            const atoutComps = (o.competences || []).filter(c => c.typeExigence === 'ATOUT');

            document.getElementById('detail-content').innerHTML = `
            <article class="recruit-detail-card">
                <div class="recruit-detail-header">
                    <div style="display:flex;align-items:center;justify-content:space-between;flex-wrap:wrap;gap:0.75rem;margin-bottom:0.75rem;">
                        <div style="display:flex;align-items:center;gap:0.5rem;flex-wrap:wrap;">
                            <span class="recruit-badge ${isAct ? 'active' : ''}">
                                ${isAct ? '<span class="pulse-dot"></span> Active' : 'Clôturée'}
                            </span>
                            <span class="contract-badge ${esc(o.typeContrat)}">${esc(o.typeContrat)}</span>
                        </div>
                        <span class="recruit-muted" style="font-size:0.85rem;">Publiée le ${date(o.datePublication)}</span>
                    </div>

                    <h2>${esc(o.titre)}</h2>

                    <div class="recruit-meta-row" style="margin-bottom:0;">
                        ${o.entreprise ? `
                        <span class="recruit-meta-item">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                            <strong>${esc(o.entreprise)}</strong>
                        </span>` : ''}
                        <span class="recruit-meta-item">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                            ${esc(o.domaine)}
                        </span>
                        <span class="recruit-meta-item">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"></path><circle cx="12" cy="10" r="3"></circle></svg>
                            ${esc(o.localisation)}
                        </span>
                        ${o.dateLimite ? `
                        <span class="recruit-meta-item">
                            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 14 14"></polyline></svg>
                            Date limite : <strong>${date(o.dateLimite)}</strong>
                        </span>` : ''}
                    </div>
                </div>

                <div style="margin: 1.5rem 0;">
                    <h3 style="font-size:1.15rem;font-weight:800;color:var(--rec-navy);margin-bottom:0.6rem;">Missions &amp; Descriptif du poste</h3>
                    <div class="recruit-description">${esc(o.description)}</div>
                </div>

                <div class="recruit-detail-competences">
                    <div class="recruit-comp-box">
                        <h3>
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--rec-teal);"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 14 14"></polyline></svg>
                            Compétences requises (${reqComps.length})
                        </h3>
                        <div class="recruit-tags">
                            ${reqComps.map(c => `
                                <div class="recruit-tag ${c.statut === 'REJETEE' ? 'rejected' : ''}">
                                    <div class="recruit-tag-row">
                                        <strong>${esc(c.nom)}</strong>
                                        <span class="recruit-badge ${c.statut === 'VALIDEE' ? 'active' : ''}">${esc(c.statut)}</span>
                                    </div>
                                    ${competenceNote(c)}
                                </div>
                            `).join('') || '<p class="recruit-muted">Aucune compétence requise spécifiée.</p>'}
                        </div>
                    </div>

                    <div class="recruit-comp-box">
                        <h3>
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--rec-blue);"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                            Atouts appréciés (${atoutComps.length})
                        </h3>
                        <div class="recruit-tags">
                            ${atoutComps.map(c => `
                                <div class="recruit-tag ${c.statut === 'REJETEE' ? 'rejected' : ''}">
                                    <div class="recruit-tag-row">
                                        <strong>${esc(c.nom)}</strong>
                                        <span class="recruit-badge ${c.statut === 'VALIDEE' ? 'active' : ''}">${esc(c.statut)}</span>
                                    </div>
                                    ${competenceNote(c)}
                                </div>
                            `).join('') || '<p class="recruit-muted">Aucun atout demandé pour cette offre.</p>'}
                        </div>
                    </div>
                </div>

                ${scoreUnavailable(o.competences || []) ? `
                <div class="recruit-warning" style="margin-top:1.5rem;">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
                    <div>Score indisponible : aucune compétence requise n’est actuellement validée dans le référentiel.</div>
                </div>` : ''}

                <div class="recruit-actions" style="margin-top:2rem;">
                    <a class="recruit-btn primary" href="/publier-offre.html?id=${o.id}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                        Modifier l’offre
                    </a>
                    <a class="recruit-btn" href="/publier-offre.html?duplicateFrom=${o.id}" title="Dupliquer cette offre">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg>
                        Dupliquer l’offre
                    </a>
                    ${isAct ? `
                    <button type="button" class="recruit-btn" data-action="close" data-id="${o.id}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                        Clôturer l’offre
                    </button>` : `
                    <button type="button" class="recruit-btn" data-action="reopen" data-id="${o.id}" style="color:var(--rec-teal); border-color:var(--rec-teal);">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M1 4v6h6"></path><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"></path></svg>
                        Rouvrir l’offre
                    </button>`}
                    <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}" data-has-cands="${(o.nbCandidatures || 0) > 0}"
                        ${(o.nbCandidatures || 0) > 0 ? 'style="opacity:0.55; cursor:not-allowed;"' : ''}
                        title="${(o.nbCandidatures || 0) > 0 ? 'Cette offre a reçu des candidatures et ne peut pas être supprimée. Proposez de la clôturer.' : 'Supprimer définitivement cette offre'}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                        Supprimer l’offre
                    </button>
                    <a class="recruit-btn" href="/mes-offres.html">Retour à mes offres</a>
                </div>
            </article>`;

            // Charger les statistiques de l'offre (entonnoir, score moyen, tranches, délai moyen)
            try {
                const stats = await api(`/api/offres/${id}/statistiques?${owned}`);
                if (stats) {
                    const statsSection = document.createElement('section');
                    statsSection.className = 'recruit-card';
                    statsSection.style.marginTop = '2rem';
                    statsSection.setAttribute('aria-label', "Statistiques de l'offre");
                    statsSection.innerHTML = `
                        <div class="recruit-pipeline-header">
                            <div>
                                <h2 class="recruit-section-title" style="margin: 0 0 0.25rem;">
                                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                        <line x1="18" y1="20" x2="18" y2="10"></line>
                                        <line x1="12" y1="20" x2="12" y2="4"></line>
                                        <line x1="6" y1="20" x2="6" y2="14"></line>
                                    </svg>
                                    Statistiques &amp; Entonnoir de conversion
                                </h2>
                                <p class="recruit-section-sub">Indicateurs de traitement des candidatures pour cette offre.</p>
                            </div>
                            <span class="recruit-pipeline-badge">Analytics</span>
                        </div>

                        <!-- Entonnoir -->
                        <div class="stat-funnel-container">
                            <div class="stat-funnel-step">
                                <div class="stat-funnel-num">${stats.entonnoir?.envoyees || 0}</div>
                                <div class="stat-funnel-label">Envoyées</div>
                            </div>
                            <div style="color:var(--rec-border); font-size:1.5rem; font-weight:700;">&rarr;</div>
                            <div class="stat-funnel-step">
                                <div class="stat-funnel-num" style="color:var(--rec-blue);">${stats.entonnoir?.vues || 0}</div>
                                <div class="stat-funnel-label">Consultées</div>
                            </div>
                            <div style="color:var(--rec-border); font-size:1.5rem; font-weight:700;">&rarr;</div>
                            <div class="stat-funnel-step success">
                                <div class="stat-funnel-num" style="color:#15803D;">${stats.entonnoir?.acceptees || 0}</div>
                                <div class="stat-funnel-label" style="color:#15803D;">Retenues</div>
                            </div>
                            <div class="stat-funnel-step danger">
                                <div class="stat-funnel-num" style="color:#B91C1C;">${stats.entonnoir?.refusees || 0}</div>
                                <div class="stat-funnel-label" style="color:#B91C1C;">Refusées</div>
                            </div>
                        </div>

                        <!-- KPIs : Score moyen, Délai moyen, Répartition -->
                        <div style="display:grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap:1rem; margin-top:1.25rem;">
                            <div style="background:#F8FAFC; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:1rem;">
                                <div style="font-size:0.78rem; font-weight:700; color:#64748B; text-transform:uppercase;">Score moyen des candidats</div>
                                <div style="font-size:1.6rem; font-weight:800; color:var(--rec-navy); margin-top:0.25rem;">
                                    ${stats.scoreMoyen != null ? (Math.round(stats.scoreMoyen * 10) / 10) + '%' : '—'}
                                </div>
                                <div style="font-size:0.8rem; color:#64748B;">Sur l'ensemble des candidatures</div>
                            </div>

                            <div style="background:#F8FAFC; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:1rem;">
                                <div style="font-size:0.78rem; font-weight:700; color:#64748B; text-transform:uppercase;">Délai moyen de traitement</div>
                                <div style="font-size:1.6rem; font-weight:800; color:var(--rec-blue); margin-top:0.25rem;">
                                    ${stats.delaiMoyenTraitementHeures != null ? (Math.round(stats.delaiMoyenTraitementHeures * 10) / 10) + ' h' : '—'}
                                </div>
                                <div style="font-size:0.8rem; color:#64748B;">Temps jusqu'à décision</div>
                            </div>

                            <div style="background:#F8FAFC; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:1rem;">
                                <div style="font-size:0.78rem; font-weight:700; color:#64748B; text-transform:uppercase; margin-bottom:0.4rem;">Répartition des scores</div>
                                <div style="display:flex; flex-direction:column; gap:0.25rem; font-size:0.8rem;">
                                    <div style="display:flex; justify-content:space-between;">
                                        <span style="color:#059669; font-weight:600;">&ge; 70% (Fort) :</span>
                                        <strong>${stats.repartitionScores?.soixanteDixEtPlus || 0}</strong>
                                    </div>
                                    <div style="display:flex; justify-content:space-between;">
                                        <span style="color:#D97706; font-weight:600;">40% – 69% (Moyen) :</span>
                                        <strong>${stats.repartitionScores?.entre40Et69 || 0}</strong>
                                    </div>
                                    <div style="display:flex; justify-content:space-between;">
                                        <span style="color:#DC2626; font-weight:600;">&lt; 40% (Faible) :</span>
                                        <strong>${stats.repartitionScores?.moinsDe40 || 0}</strong>
                                    </div>
                                </div>
                            </div>
                        </div>
                    `;
                    document.getElementById('detail-content')?.insertAdjacentElement('afterend', statsSection);
                }
            } catch (errStats) {
                console.warn("Impossible de charger les statistiques de l'offre :", errStats);
            }

            const pipelineSection = document.querySelector('.recruit-pipeline-card');
            if (pipelineSection) {
                try {
                    const cands = await api(`/api/offres/${id}/candidats?${owned}`);
                    const totalCands = cands ? cands.length : 0;
                    const aTraiter = cands ? cands.filter(c => c.statut === 'ENVOYEE').length : 0;

                    if (totalCands === 0) {
                        pipelineSection.innerHTML = `
                        <div class="recruit-pipeline-header">
                            <div>
                                <h2 class="recruit-section-title" style="margin: 0 0 0.25rem;">
                                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                                    Candidatures reçues (0)
                                </h2>
                                <p class="recruit-section-sub">Aucun candidat n'a encore postulé à cette opportunité.</p>
                            </div>
                            <span class="recruit-pipeline-badge">0 candidature</span>
                        </div>
                        <div class="recruit-empty" style="margin-top: 1.25rem;">
                            <div class="recruit-empty-icon" aria-hidden="true"><svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg></div>
                            <div class="recruit-empty-title">Aucune candidature reçue pour le moment</div>
                            <p>Dès qu'un étudiant postule, il apparaîtra ici avec son score de pertinence et son profil complet.</p>
                        </div>`;
                    } else {
                        const topCands = cands.slice(0, 3);
                        pipelineSection.innerHTML = `
                        <div class="recruit-pipeline-header">
                            <div>
                                <h2 class="recruit-section-title" style="margin: 0 0 0.25rem;">
                                    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                                    Candidats classés &amp; Scores de matching (${totalCands})
                                </h2>
                                <p class="recruit-section-sub">
                                    <strong>${totalCands}</strong> candidature(s) reçue(s) · <span style="color:var(--rec-blue);font-weight:700;">${aTraiter} à traiter</span>
                                </p>
                            </div>
                            <a href="/candidats-offre.html?offreId=${id}" class="recruit-btn primary" style="gap:0.5rem;">
                                <span>Gérer les candidats (${totalCands})</span>
                                <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"></polyline></svg>
                            </a>
                        </div>
                        <div style="margin-top: 1.5rem; display:flex; flex-direction:column; gap:0.75rem;">
                            ${topCands.map(c => `
                                <div style="display:flex; justify-content:space-between; align-items:center; padding:0.9rem 1.1rem; background:#F8FAFC; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); flex-wrap:wrap; gap:0.75rem;">
                                    <div style="display:flex; align-items:center; gap:0.75rem;">
                                        <div class="candidat-avatar" style="width:38px;height:38px;font-size:0.95rem;">${esc(((c.prenom?.[0] || '') + (c.nom?.[0] || '')).toUpperCase())}</div>
                                        <div>
                                            <div style="font-weight:800; color:var(--rec-navy); font-size:1rem;">${esc(c.prenom)} ${esc(c.nom)}</div>
                                            <div class="recruit-muted" style="font-size:0.82rem;">${esc(c.filiere || 'Filière non renseignée')} · ${esc(c.etablissement || '')}</div>
                                        </div>
                                    </div>
                                    <div style="display:flex; align-items:center; gap:0.75rem;">
                                        <span class="statut-badge ${esc(String(c.statut).toLowerCase())}">${esc(c.statut)}</span>
                                        <span class="candidat-score-pill ${c.score >= 70 ? 'score-high' : (c.score >= 40 ? 'score-mid' : 'score-low')}" style="padding:0.3rem 0.7rem; font-size:0.85rem;">
                                            ${c.score != null ? Math.round(c.score * 10) / 10 + '%' : 'N/A'}
                                        </span>
                                        <a href="/candidats-offre.html?offre=${id}&candidature=${c.candidatureId}" class="recruit-btn" style="padding:0.4rem 0.8rem; font-size:0.82rem;">Consulter</a>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                        ${totalCands > 3 ? `<p style="text-align:center; margin-top:1rem; font-size:0.88rem;" class="recruit-muted">Et ${totalCands - 3} autre(s) candidat(s)... <a href="/candidats-offre.html?offre=${id}" style="color:var(--rec-blue);font-weight:700;">Voir la liste complète ordonnée par score</a></p>` : ''}
                        `;
                    }
                } catch (_) {}
            }
        }

        if (page === 'candidats') await initCandidatsPage();
        if (page === 'form') await initForm();
        if (page === 'mon-entreprise') await initEntreprisePage();
    } catch (e) {
        showError(e.message);
    }

    async function initForm() {
        const form = document.getElementById('offre-form');
        const id = new URLSearchParams(location.search).get('id');
        const fields = document.getElementById('offre-fields');
        let tags = [];
        const search = document.getElementById('competence-search');
        const suggestions = document.getElementById('competence-suggestions');
        const tagsElement = document.getElementById('competence-tags');
        const submit = document.getElementById('offre-submit');
        let searchVersion = 0;
        let timer;

        function renderTags() {
            tagsElement.innerHTML = tags.map(c => `
                <div class="recruit-tag ${c.statut === 'REJETEE' ? 'rejected' : ''}">
                    <div class="recruit-tag-row">
                        <strong>${esc(c.nom)}</strong>
                        <span class="recruit-badge ${c.statut === 'VALIDEE' ? 'active' : ''}">${esc(c.statut || 'VALIDEE')}</span>
                        <select aria-label="Exigence pour ${esc(c.nom)}" data-type-id="${c.competenceId}">
                            <option value="REQUISE" ${c.typeExigence === 'REQUISE' ? 'selected' : ''}>Requise</option>
                            <option value="ATOUT" ${c.typeExigence === 'ATOUT' ? 'selected' : ''}>Atout</option>
                        </select>
                        <button type="button" class="recruit-btn danger" data-remove-id="${c.competenceId}" aria-label="Retirer ${esc(c.nom)}">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                            Retirer
                        </button>
                    </div>
                    ${competenceNote(c)}
                </div>
            `).join('');

            const scoreWarningEl = document.getElementById('score-warning');
            if (scoreWarningEl) {
                scoreWarningEl.hidden = !scoreUnavailable(tags);
            }
        }

        function add(c) {
            if (!tags.some(t => t.competenceId === c.id)) {
                tags.push({competenceId: c.id, nom: c.nom, statut: c.statut, typeExigence: 'REQUISE'});
            }
            search.value = '';
            suggestions.replaceChildren();
            searchVersion++;
            renderTags();
            search.focus();
        }

        tagsElement.addEventListener('change', e => {
            const tag = tags.find(c => c.competenceId === Number(e.target.dataset.typeId));
            if (tag) {
                tag.typeExigence = e.target.value;
                const scoreWarningEl = document.getElementById('score-warning');
                if (scoreWarningEl) scoreWarningEl.hidden = !scoreUnavailable(tags);
            }
        });

        tagsElement.addEventListener('click', e => {
            const button = e.target.closest('[data-remove-id]');
            if (button) {
                tags = tags.filter(c => c.competenceId !== Number(button.dataset.removeId));
                renderTags();
            }
        });

        search.addEventListener('keydown', e => {
            if (e.key === 'Enter') e.preventDefault();
            if (e.key === 'Escape') suggestions.replaceChildren();
        });

        search.addEventListener('input', () => {
            clearTimeout(timer);
            const version = ++searchVersion;
            suggestions.replaceChildren();
            const query = search.value.trim();
            if (!query) return;

            timer = setTimeout(async () => {
                try {
                    const results = await api(`${base}/competences/suggestions?q=${encodeURIComponent(query)}`);
                    if (version !== searchVersion) return;

                    results.filter(c => !tags.some(t => t.competenceId === c.id)).forEach(c => {
                        const button = document.createElement('button');
                        button.type = 'button';
                        button.className = 'recruit-btn';
                        button.innerHTML = `
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="16"></line><line x1="8" y1="12" x2="16" y2="12"></line></svg>
                            <span>${esc(c.nom)}</span>
                            ${c.statut === 'EN_ATTENTE' ? '<span class="recruit-badge" style="margin-left:auto;font-size:0.7rem;">En attente</span>' : ''}
                        `;
                        button.addEventListener('click', () => add(c));
                        suggestions.append(button);
                    });

                    const propose = document.createElement('button');
                    propose.type = 'button';
                    propose.className = 'recruit-btn';
                    propose.style.borderStyle = 'dashed';
                    propose.innerHTML = `
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><line x1="12" y1="5" x2="12" y2="19"></line><line x1="5" y1="12" x2="19" y2="12"></line></svg>
                        <span>Proposer la compétence « <strong>${esc(query)}</strong> »</span>
                    `;
                    propose.addEventListener('click', async () => {
                        propose.disabled = true;
                        clearError();
                        try {
                            const c = await api(`${base}/competences/propositions`, 'POST', {nom: query});
                            if (version === searchVersion) add(c);
                        } catch (e) {
                            showError(e.message);
                        } finally {
                            propose.disabled = false;
                        }
                    });
                    suggestions.append(propose);
                } catch (e) {
                    if (version === searchVersion) showError(e.message);
                }
            }, 250);
        });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            clearError();
            if (!tags.some(c => c.typeExigence === 'REQUISE')) {
                showError('Ajoutez au moins une compétence requise pour publier cette offre.');
                return;
            }
            const payload = {
                recruteurId: user.id,
                competences: tags.map(({competenceId, typeExigence}) => ({competenceId, typeExigence}))
            };
            ['titre', 'description', 'domaine', 'localisation', 'typeContrat'].forEach(key => {
                payload[key] = form.elements[key].value.trim();
            });
            const dateLimiteVal = form.elements['dateLimite']?.value?.trim();
            if (dateLimiteVal) {
                payload.dateLimite = new Date(dateLimiteVal).toISOString();
            } else {
                payload.dateLimite = null;
            }

            fields.disabled = true;
            submit.disabled = true;
            try {
                const offre = await api(id ? `/api/offres/${id}` : '/api/offres', id ? 'PUT' : 'POST', payload);
                location.assign(`/offre-recruteur.html?id=${offre.id}`);
            } catch (e) {
                showError(e.message);
                fields.disabled = false;
                submit.disabled = false;
            }
        });

        const duplicateFrom = new URLSearchParams(location.search).get('duplicateFrom');
        if (duplicateFrom && /^\d+$/.test(duplicateFrom)) {
            try {
                const src = await api(`/api/offres/${duplicateFrom}?${owned}`);
                ['titre', 'description', 'domaine', 'localisation', 'typeContrat'].forEach(key => {
                    if (form.elements[key] && src[key]) form.elements[key].value = src[key];
                });
                tags = (src.competences || []).map(c => ({
                    competenceId: c.id || c.competenceId,
                    nom: c.nom,
                    statut: c.statut,
                    typeExigence: c.typeExigence || 'REQUISE'
                }));
                if (form.elements['dateLimite']) form.elements['dateLimite'].value = '';
                const titleEl = document.getElementById('page-title');
                if (titleEl) titleEl.innerHTML = 'Dupliquer <span>une offre</span>';
                submit.innerHTML = `
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                    Publier l’offre dupliquée
                `;
            } catch (err) {
                showError("Impossible de charger l'offre source : " + err.message);
            }
        }

        if (id) {
            if (!/^\d+$/.test(id)) throw new Error('Identifiant d’offre invalide.');
            const o = await api(`/api/offres/${id}?${owned}`);
            ['titre', 'description', 'domaine', 'localisation', 'typeContrat'].forEach(key => {
                form.elements[key].value = o[key];
            });
            if (o.dateLimite && form.elements['dateLimite']) {
                const d = new Date(o.dateLimite);
                const pad = n => String(n).padStart(2, '0');
                form.elements['dateLimite'].value = `${d.getFullYear()}-${pad(d.getMonth()+1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
            }
            tags = o.competences || [];
            const titleEl = document.getElementById('page-title');
            if (titleEl) titleEl.innerHTML = 'Modifier <span>une offre</span>';
            submit.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z"></path><polyline points="17 21 17 13 7 13 7 21"></polyline></svg>
                Enregistrer les modifications
            `;
        }

        fields.disabled = false;
        submit.disabled = false;
        renderTags();
    }

    async function initCandidatsPage() {
        const urlParams = new URLSearchParams(location.search);
        const offreId = urlParams.get('offre') || urlParams.get('offreId');
        const targetCandId = urlParams.get('candidature') || urlParams.get('candidatureId');

        if (!offreId || !/^\d+$/.test(offreId)) {
            showError("Identifiant d’offre manquant ou invalide. Retournez à vos offres.");
            return;
        }

        const backLink = document.getElementById('back-to-offer-link');
        if (backLink) backLink.href = `/offre-recruteur.html?id=${offreId}`;
        const btnFiche = document.getElementById('btn-fiche-offre');
        if (btnFiche) btnFiche.href = `/offre-recruteur.html?id=${offreId}`;

        // 1. Charger l'offre
        let offre;
        try {
            offre = await api(`/api/offres/${offreId}?${owned}`);
        } catch (err) {
            showError("Impossible de charger les détails de l'offre : " + err.message);
            return;
        }

        const summaryTitle = document.getElementById('summary-title');
        if (summaryTitle) summaryTitle.textContent = offre.titre;
        const summaryBadge = document.getElementById('summary-badge-status');
        if (summaryBadge) {
            summaryBadge.textContent = offre.statut === 'ACTIVE' ? 'Active' : 'Clôturée';
            summaryBadge.className = `recruit-badge ${offre.statut === 'ACTIVE' ? 'active' : ''}`;
        }
        const summaryContract = document.getElementById('summary-contract');
        if (summaryContract) {
            summaryContract.textContent = offre.typeContrat;
            summaryContract.className = `contract-badge ${esc(offre.typeContrat)}`;
        }
        const summaryMeta = document.getElementById('summary-meta');
        if (summaryMeta) {
            summaryMeta.textContent = `${offre.entreprise ? esc(offre.entreprise) + ' · ' : ''}${esc(offre.domaine || '')} · ${esc(offre.localisation || '')} · Publiée le ${date(offre.datePublication)}`;
        }

        // 2. Charger les candidats
        let allCandidats = [];
        try {
            allCandidats = await api(`/api/offres/${offreId}/candidats?${owned}`);
        } catch (err) {
            showError("Impossible de charger les candidats : " + err.message);
            return;
        }

        let activeFilter = 'ALL';
        let searchQuery = '';
        let favorisOnly = false;
        let scoreMin = 0;
        let sortField = 'score';

        function formatScore(val) {
            if (val == null) return 'N/A';
            return (Math.round(val * 10) / 10) + '%';
        }

        function scoreClass(val) {
            if (val == null) return 'score-none';
            if (val >= 70) return 'score-high';
            if (val >= 40) return 'score-mid';
            return 'score-low';
        }

        function updateCounts() {
            const total = allCandidats.length;
            const envoyee = allCandidats.filter(c => c.statut === 'ENVOYEE').length;
            const vue = allCandidats.filter(c => c.statut === 'VUE').length;
            const acceptee = allCandidats.filter(c => c.statut === 'ACCEPTEE').length;
            const refusee = allCandidats.filter(c => c.statut === 'REFUSEE').length;
            const favCount = allCandidats.filter(c => Boolean(c.favori)).length;

            const elTotal = document.getElementById('summary-total-cand');
            if (elTotal) elTotal.textContent = total;
            const elAtraiter = document.getElementById('summary-atraiter-cand');
            if (elAtraiter) elAtraiter.textContent = envoyee;

            const cAll = document.getElementById('count-candidats-all');
            if (cAll) cAll.textContent = total;
            const cEnv = document.getElementById('count-candidats-envoyee');
            if (cEnv) cEnv.textContent = envoyee;
            const cVue = document.getElementById('count-candidats-vue');
            if (cVue) cVue.textContent = vue;
            const cAcc = document.getElementById('count-candidats-acceptee');
            if (cAcc) cAcc.textContent = acceptee;
            const cRef = document.getElementById('count-candidats-refusee');
            if (cRef) cRef.textContent = refusee;

            const btnFav = document.getElementById('btn-toggle-favoris');
            if (btnFav) {
                btnFav.innerHTML = `<span style="color:#F59E0B; font-size:1rem;">★</span> <span>Favoris (${favCount})</span>`;
            }
        }

        function renderCard(c) {
            const initials = esc(((c.prenom?.[0] || '') + (c.nom?.[0] || '')).toUpperCase());
            const isTerminal = c.statut === 'ACCEPTEE' || c.statut === 'REFUSEE';
            const isTarget = targetCandId && String(c.candidatureId) === String(targetCandId);
            const cardClasses = `candidat-card ${isTarget ? 'candidat-card-highlighted' : ''}`;

            return `
            <article class="${cardClasses}" data-cand-id="${c.candidatureId}">
                <div class="candidat-card-top">
                    <div class="candidat-info-group">
                        <div class="candidat-avatar">${initials}</div>
                        <div>
                            <div style="display:flex; align-items:center; gap:0.5rem; flex-wrap:wrap;">
                                <h3 class="candidat-name" style="margin:0 0 0.25rem;">${esc(c.prenom)} ${esc(c.nom)}</h3>
                                <button type="button" class="btn-favori ${c.favori ? 'active' : ''}" data-action="toggle-favori" data-cand-id="${c.candidatureId}" title="${c.favori ? 'Retirer des favoris' : 'Marquer comme favori'}" aria-label="Favori">${c.favori ? '★' : '☆'}</button>
                                ${isTarget ? `
                                <span class="statut-badge" style="background:#ECFDF5; color:#047857; border:1px solid #A7F3D0; font-weight:700; font-size:0.75rem;">
                                    ★ Candidature ciblée
                                </span>` : ''}
                            </div>
                            <div class="candidat-academic">
                                <span>${esc(c.filiere || 'Filière non renseignée')}</span>
                                ${c.etablissement ? `<span>·</span><span>${esc(c.etablissement)}</span>` : ''}
                                <span>·</span>
                                <span class="recruit-muted">Postulé le ${date(c.dateCandidature)}</span>
                            </div>
                        </div>
                    </div>
                    <div style="display:flex; align-items:center; gap:0.6rem; flex-wrap:wrap;">
                        <span class="statut-badge ${esc(String(c.statut).toLowerCase())}">${esc(c.statut)}</span>
                        <span class="candidat-score-pill ${scoreClass(c.score)}">
                            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                            Score : ${formatScore(c.score)}
                        </span>
                    </div>
                </div>

                ${c.statut === 'ACCEPTEE' && c.email ? `
                <div style="background:#F0FDF4; border:1px solid #BBF7D0; border-radius:var(--rec-radius-sm); padding:0.65rem 0.9rem; display:flex; align-items:center; gap:0.5rem; font-size:0.88rem; color:#166534; margin-top:0.6rem;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                    <strong>Contact candidat débloqué :</strong>
                    <a href="mailto:${esc(c.email)}" style="color:#15803D; font-weight:700; text-decoration:underline;">${esc(c.email)}</a>
                </div>` : `
                <div style="font-size:0.82rem; color:#64748B; display:flex; align-items:center; gap:0.4rem; margin-top:0.4rem;">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                    <span>Email protégé : disponible immédiatement dès acceptation de la candidature.</span>
                </div>`}

                ${c.message ? `
                <div style="margin-top:0.6rem; background:#EFF6FF; border:1px solid #BFDBFE; border-radius:var(--rec-radius-sm); padding:0.6rem 0.85rem; font-size:0.84rem; color:#1E40AF;">
                    <strong>Message d'accompagnement transmis :</strong> &laquo; ${esc(c.message)} &raquo;
                </div>` : ''}

                <!-- Note interne privée (JAMAIS visible par l'étudiant) -->
                <div class="note-interne-box" style="margin-top:0.75rem; background:#F8FAFC; border:1px dashed var(--rec-border); border-radius:8px; padding:0.65rem 0.85rem;">
                    <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:0.35rem;">
                        <span style="font-size:0.75rem; font-weight:700; color:#64748B; text-transform:uppercase; display:flex; align-items:center; gap:0.35rem;">
                            <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                            Note interne privée (confidentielle recruteur)
                        </span>
                        <button type="button" class="recruit-btn" style="padding:0.2rem 0.6rem; font-size:0.75rem;" data-action="save-note" data-cand-id="${c.candidatureId}">Enregistrer</button>
                    </div>
                    <textarea class="input-note-interne" data-cand-id="${c.candidatureId}" rows="2" style="width:100%; font-size:0.82rem; border:1px solid var(--rec-border); border-radius:6px; padding:0.4rem; resize:vertical; font-family:inherit;" placeholder="Ajouter une note personnelle ou avis d'entretien (visible uniquement par vous)...">${esc(c.noteInterne || '')}</textarea>
                </div>

                <div class="recruit-actions" style="margin-top:0.75rem; border-top:1px solid var(--rec-border); padding-top:0.9rem;">
                    <button type="button" class="recruit-btn primary" data-open-detail="${c.candidatureId}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"></circle><line x1="21" y1="21" x2="16.65" y2="16.65"></line></svg>
                        Consulter profil &amp; score
                    </button>

                    ${!isTerminal ? `
                    <button type="button" class="recruit-btn" style="color:#15803D; border-color:#86EFAC; margin-left:auto;" data-quick-action="ACCEPTEE" data-cand-id="${c.candidatureId}" data-cand-name="${esc(c.prenom)} ${esc(c.nom)}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                        Accepter
                    </button>
                    <button type="button" class="recruit-btn danger" data-quick-action="REFUSEE" data-cand-id="${c.candidatureId}" data-cand-name="${esc(c.prenom)} ${esc(c.nom)}">
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="18" x2="18" y2="18"></line></svg>
                        Refuser
                    </button>
                    ` : `
                    <span class="recruit-muted" style="margin-left:auto; font-size:0.85rem; font-style:italic;">
                        Décision finale enregistrée (${esc(c.statut)})
                    </span>
                    `}
                </div>
            </article>
            `;
        }

        function renderCandidates() {
            const listEl = document.getElementById('candidats-list');
            if (!listEl) return;

            let filtered = [...allCandidats];
            if (activeFilter !== 'ALL') {
                filtered = filtered.filter(c => c.statut === activeFilter);
            }
            if (favorisOnly) {
                filtered = filtered.filter(c => Boolean(c.favori));
            }
            if (scoreMin > 0) {
                filtered = filtered.filter(c => (c.score || 0) >= scoreMin);
            }

            if (searchQuery.trim()) {
                const q = searchQuery.toLowerCase();
                filtered = filtered.filter(c =>
                    (c.nom && c.nom.toLowerCase().includes(q)) ||
                    (c.prenom && c.prenom.toLowerCase().includes(q)) ||
                    (c.filiere && c.filiere.toLowerCase().includes(q)) ||
                    (c.etablissement && c.etablissement.toLowerCase().includes(q))
                );
            }

            // Tri par score ou date
            if (sortField === 'date') {
                filtered.sort((a, b) => new Date(b.dateCandidature || 0).getTime() - new Date(a.dateCandidature || 0).getTime());
            } else {
                filtered.sort((a, b) => (b.score ?? -1) - (a.score ?? -1));
            }

            if (filtered.length === 0) {
                listEl.innerHTML = `
                <div class="recruit-empty">
                    <div class="recruit-empty-icon" aria-hidden="true">
                        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
                    </div>
                    <div class="recruit-empty-title">Aucun candidat trouvé</div>
                    <p>Aucune candidature ne correspond à vos filtres actuels pour cette offre.</p>
                </div>
                `;
                return;
            }

            listEl.innerHTML = filtered.map(renderCard).join('');
        }

        // Toolbar filter interactions
        const filterContainer = document.getElementById('candidat-filters');
        if (filterContainer) {
            filterContainer.addEventListener('click', e => {
                const btn = e.target.closest('button[data-filter]');
                if (!btn) return;
                filterContainer.querySelectorAll('.recruit-filter-btn').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
                activeFilter = btn.dataset.filter;
                renderCandidates();
            });
        }

        const searchInput = document.getElementById('candidats-search');
        if (searchInput) {
            searchInput.addEventListener('input', e => {
                searchQuery = e.target.value;
                renderCandidates();
            });
        }

        // Toolbar filtres avancés : Favoris uniquement
        const btnToggleFavoris = document.getElementById('btn-toggle-favoris');
        if (btnToggleFavoris) {
            btnToggleFavoris.addEventListener('click', () => {
                favorisOnly = !favorisOnly;
                btnToggleFavoris.classList.toggle('active', favorisOnly);
                btnToggleFavoris.style.background = favorisOnly ? '#FEF3C7' : '';
                btnToggleFavoris.style.borderColor = favorisOnly ? '#F59E0B' : '';
                btnToggleFavoris.style.fontWeight = favorisOnly ? '700' : '500';
                renderCandidates();
            });
        }

        // Toolbar filtres avancés : Score minimum
        const sliderScoreMin = document.getElementById('filter-score-min');
        const valScoreMin = document.getElementById('filter-score-min-val');
        if (sliderScoreMin) {
            sliderScoreMin.addEventListener('input', e => {
                scoreMin = Number(e.target.value) || 0;
                if (valScoreMin) valScoreMin.textContent = scoreMin + '%';
                renderCandidates();
            });
        }

        // Toolbar filtres avancés : Tri
        const selectTri = document.getElementById('filter-tri');
        if (selectTri) {
            selectTri.addEventListener('change', e => {
                sortField = e.target.value;
                renderCandidates();
            });
        }

        // Toolbar export CSV
        const exportBtn = document.getElementById('btn-export-csv');
        if (exportBtn) {
            exportBtn.addEventListener('click', async () => {
                try {
                    exportBtn.disabled = true;
                    const res = await fetch(`/api/offres/${offreId}/candidats/export-csv?${owned}`);
                    if (!res.ok) throw new Error("Erreur lors de l'export CSV (" + res.status + ")");
                    const blob = await res.blob();
                    const downloadUrl = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = downloadUrl;
                    a.download = `candidats-offre-${offreId}.csv`;
                    document.body.appendChild(a);
                    a.click();
                    a.remove();
                    window.URL.revokeObjectURL(downloadUrl);
                } catch (err) {
                    showError("Échec de l'export CSV : " + err.message);
                } finally {
                    exportBtn.disabled = false;
                }
            });
        }

        // Modal de décision statut avec message personnalisé optionnel
        const decisionBackdrop = document.getElementById('decision-modal-backdrop');
        const decisionTitle = document.getElementById('decision-modal-title');
        const decisionSubtitle = document.getElementById('decision-modal-subtitle');
        const decisionPrompt = document.getElementById('decision-modal-prompt');
        const decisionMessage = document.getElementById('decision-message');
        const decisionCancel = document.getElementById('decision-modal-cancel');
        const decisionClose = document.getElementById('decision-modal-close');
        const decisionConfirm = document.getElementById('decision-modal-confirm');

        let pendingCandId = null;
        let pendingStatut = null;

        function openDecisionModal(candId, newStatut, candName) {
            pendingCandId = candId;
            pendingStatut = newStatut;
            if (decisionTitle) {
                decisionTitle.textContent = newStatut === 'ACCEPTEE' ? 'Accepter la candidature' : 'Refuser la candidature';
            }
            if (decisionSubtitle) {
                decisionSubtitle.textContent = `Candidat : ${candName || 'Étudiant'}`;
            }
            if (decisionPrompt) {
                decisionPrompt.innerHTML = newStatut === 'ACCEPTEE'
                    ? `Vous êtes sur le point d'<strong>accepter</strong> cette candidature. Les coordonnées email du candidat vous seront immédiatement débloquées.`
                    : `Vous êtes sur le point de <strong>refuser</strong> cette candidature. Cette décision est définitive.`;
            }
            if (decisionMessage) {
                decisionMessage.value = '';
                decisionMessage.placeholder = newStatut === 'ACCEPTEE'
                    ? 'Ex. Félicitations ! Nous aimerions convenir d\'un entretien téléphonique pour échanger sur vos missions...'
                    : 'Ex. Merci pour votre intérêt. Malgré la qualité de votre profil, nous avons retenu une autre candidature pour ce poste...';
            }
            if (decisionConfirm) {
                decisionConfirm.textContent = newStatut === 'ACCEPTEE' ? 'Confirmer l\'acceptation' : 'Confirmer le refus';
                decisionConfirm.style.background = newStatut === 'ACCEPTEE' ? '#15803D' : '#DC2626';
                decisionConfirm.style.borderColor = newStatut === 'ACCEPTEE' ? '#15803D' : '#DC2626';
            }
            if (decisionBackdrop) {
                decisionBackdrop.classList.add('active');
                decisionBackdrop.setAttribute('aria-hidden', 'false');
            }
        }

        function closeDecisionModal() {
            if (decisionBackdrop) {
                decisionBackdrop.classList.remove('active');
                decisionBackdrop.setAttribute('aria-hidden', 'true');
            }
            pendingCandId = null;
            pendingStatut = null;
        }

        if (decisionCancel) decisionCancel.addEventListener('click', closeDecisionModal);
        if (decisionClose) decisionClose.addEventListener('click', closeDecisionModal);
        if (decisionBackdrop) {
            decisionBackdrop.addEventListener('click', e => {
                if (e.target === decisionBackdrop) closeDecisionModal();
            });
        }

        if (decisionConfirm) {
            decisionConfirm.addEventListener('click', async () => {
                if (!pendingCandId || !pendingStatut) return;
                const candId = pendingCandId;
                const newStatut = pendingStatut;
                const msg = decisionMessage ? decisionMessage.value.trim() : '';

                decisionConfirm.disabled = true;
                clearError();
                try {
                    const body = { statut: newStatut };
                    if (msg) body.message = msg;
                    const updated = await api(`/api/candidatures/${candId}/statut?${owned}`, 'PATCH', body);
                    const idx = allCandidats.findIndex(c => String(c.candidatureId) === String(candId));
                    if (idx !== -1) {
                        allCandidats[idx].statut = updated.statut || newStatut;
                        allCandidats[idx].message = updated.message;
                        if (newStatut === 'ACCEPTEE') {
                            try {
                                const d = await api(`/api/candidatures/${candId}?${owned}`);
                                allCandidats[idx].email = d.email;
                            } catch (_) {}
                        }
                    }
                    closeDecisionModal();
                    updateCounts();
                    renderCandidates();
                    closeCandidateModal();
                } catch (err) {
                    showError("Impossible de mettre à jour le statut : " + err.message);
                } finally {
                    decisionConfirm.disabled = false;
                }
            });
        }

        // Modal detail interactions
        const backdrop = document.getElementById('candidat-modal-backdrop');
        const modalBody = document.getElementById('modal-candidat-body');
        const modalFooter = document.getElementById('modal-candidat-footer');
        const modalCloseBtn = document.getElementById('modal-close-btn');

        function closeCandidateModal() {
            if (backdrop) {
                backdrop.classList.remove('active');
                backdrop.setAttribute('aria-hidden', 'true');
            }
            document.body.style.overflow = '';
        }

        if (modalCloseBtn) modalCloseBtn.addEventListener('click', closeCandidateModal);
        if (backdrop) {
            backdrop.addEventListener('click', e => {
                if (e.target === backdrop) closeCandidateModal();
            });
        }
        window.addEventListener('keydown', e => {
            if (e.key === 'Escape') {
                if (decisionBackdrop && decisionBackdrop.classList.contains('active')) {
                    closeDecisionModal();
                } else if (backdrop && backdrop.classList.contains('active')) {
                    closeCandidateModal();
                }
            }
        });

        async function openCandidateModal(candId) {
            if (!backdrop || !modalBody) return;
            backdrop.classList.add('active');
            backdrop.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            modalBody.innerHTML = '<div style="padding:2.5rem;text-align:center;" class="recruit-muted">Chargement du profil et de l\'explication du score…</div>';
            if (modalFooter) modalFooter.innerHTML = '';

            // Marquer la notification correspondante comme lue (PATCH /api/notifications/{id}/lue)
            await markNotificationAsReadForCandidature(candId);

            try {
                // GET /api/candidatures/{id}?recruteurId=... fait passer automatiquement ENVOYEE à VUE
                const detail = await api(`/api/candidatures/${candId}?${owned}`);

                // Synchroniser statut local si passage auto ENVOYEE -> VUE
                const localCand = allCandidats.find(c => String(c.candidatureId) === String(candId));
                if (localCand && localCand.statut === 'ENVOYEE' && detail.statut === 'VUE') {
                    localCand.statut = 'VUE';
                    updateCounts();
                    renderCandidates();
                }

                // En-tête de la modal
                const modalName = document.getElementById('modal-candidat-name');
                if (modalName) modalName.textContent = `${detail.prenom} ${detail.nom}`;
                const modalAcad = document.getElementById('modal-candidat-academic');
                if (modalAcad) modalAcad.textContent = `${detail.filiere || 'Filière non renseignée'} · ${detail.etablissement || 'Établissement non renseigné'} · Postulé le ${date(detail.dateCandidature)}`;
                const modalStatut = document.getElementById('modal-candidat-statut-badge');
                if (modalStatut) {
                    modalStatut.className = `statut-badge ${esc(String(detail.statut).toLowerCase())}`;
                    modalStatut.textContent = detail.statut;
                }
                const modalScore = document.getElementById('modal-score-badge');
                if (modalScore) {
                    modalScore.className = `candidat-score-pill ${scoreClass(detail.scoreCandidature)}`;
                    modalScore.innerHTML = `<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg> Score : ${formatScore(detail.scoreCandidature)}`;
                }

                // Corps de la modal
                const isAcceptee = detail.statut === 'ACCEPTEE';
                const isTerminal = detail.statut === 'ACCEPTEE' || detail.statut === 'REFUSEE';
                const match = detail.matchingDetail || {};
                const compScores = match.competences || [];

                let html = '';

                // Alerte d'écart de score si le profil étudiant a évolué depuis la candidature
                if (detail.scoreModifie) {
                    html += `
                    <div class="score-diff-banner">
                        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="flex-shrink:0;"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
                        <div>
                            <strong>Évolution du profil détectée depuis la candidature :</strong><br>
                            L'étudiant a mis à jour son CV après avoir postulé. Score initial (snapshot) : <strong>${formatScore(detail.scoreCandidature)}</strong> · Score recalculé en direct : <strong>${formatScore(detail.scoreActuel)}</strong>.
                        </div>
                    </div>`;
                }

                // Coordonnées de contact
                if (isAcceptee && detail.email) {
                    html += `
                    <div class="candidate-detail-block" style="background:#F0FDF4; border-color:#86EFAC;">
                        <div class="candidate-detail-block-title" style="color:#166534;">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                            Coordonnées de contact (Débloquées)
                        </div>
                        <div style="font-size:0.95rem; color:#14532D;">
                            Email du candidat : <a href="mailto:${esc(detail.email)}" style="font-weight:700; color:#15803D; text-decoration:underline;">${esc(detail.email)}</a>
                        </div>
                    </div>`;
                } else {
                    html += `
                    <div class="candidate-detail-block" style="background:#F8FAFC;">
                        <div class="candidate-detail-block-title" style="color:#475569;">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                            Coordonnées de contact protégées
                        </div>
                        <p class="recruit-muted" style="margin:0; font-size:0.88rem;">
                            L’adresse email du candidat est confidentielle à ce stade. Elle sera accessible dès acceptation de sa candidature.
                        </p>
                    </div>`;
                }

                // Message accompagnant la décision si déjà statué
                if (detail.message) {
                    html += `
                    <div class="candidate-detail-block" style="background:#EFF6FF; border-color:#BFDBFE;">
                        <div class="candidate-detail-block-title" style="color:#1E40AF;">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"></path></svg>
                            Message personnalisé transmis au candidat
                        </div>
                        <div style="font-size:0.92rem; color:#1E3A8A; font-style:italic;">
                            &laquo; ${esc(detail.message)} &raquo;
                        </div>
                    </div>`;
                }

                // Note interne privée
                if (detail.noteInterne) {
                    html += `
                    <div class="candidate-detail-block" style="background:#FFFBEB; border-color:#FDE68A;">
                        <div class="candidate-detail-block-title" style="color:#92400E;">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"></path><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"></path></svg>
                            Note interne privée (Réservée au recruteur)
                        </div>
                        <div style="font-size:0.9rem; color:#78350F; white-space:pre-wrap;">${esc(detail.noteInterne)}</div>
                    </div>`;
                }

                // Explication détaillée du score de matching
                html += `
                <div class="candidate-detail-block">
                    <div class="candidate-detail-block-title">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--rec-teal);"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon></svg>
                        Explication du score de matching (${formatScore(match.score != null ? match.score : detail.scoreCandidature)})
                    </div>
                    
                    <div style="display:grid; grid-template-columns:repeat(auto-fit, minmax(200px, 1fr)); gap:1rem; margin-bottom:1.25rem;">
                        <div style="background:#FFFFFF; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:0.85rem 1rem;">
                            <div style="font-size:0.78rem; text-transform:uppercase; font-weight:700; color:#64748B;">Compétences requises</div>
                            <div style="font-size:1.25rem; font-weight:800; color:var(--rec-navy);">
                                ${formatScore(match.scoreRequises)} <span style="font-size:0.85rem; font-weight:500; color:#64748B;">/ ${formatScore(match.partRequisesMax || 80)}</span>
                            </div>
                            <div style="font-size:0.8rem; color:#64748B;">${match.nbRequisesValidees || 0} validée(s)</div>
                        </div>
                        <div style="background:#FFFFFF; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:0.85rem 1rem;">
                            <div style="font-size:0.78rem; text-transform:uppercase; font-weight:700; color:#64748B;">Atouts appréciés</div>
                            <div style="font-size:1.25rem; font-weight:800; color:var(--rec-navy);">
                                ${formatScore(match.scoreAtouts)} <span style="font-size:0.85rem; font-weight:500; color:#64748B;">/ ${formatScore(match.partAtoutsMax || 20)}</span>
                            </div>
                            <div style="font-size:0.8rem; color:#64748B;">${match.nbAtoutsValides || 0} validé(s)</div>
                        </div>
                    </div>

                    ${compScores.length > 0 ? `
                    <div style="overflow-x:auto;">
                        <table class="score-table">
                            <thead>
                                <tr>
                                    <th>Compétence offre</th>
                                    <th>Exigence</th>
                                    <th>Détenue</th>
                                    <th>Provenance</th>
                                    <th>Prise en compte</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${compScores.map(cs => `
                                    <tr>
                                        <td><strong>${esc(cs.nom)}</strong></td>
                                        <td>
                                            <span class="recruit-badge ${cs.typeExigence === 'REQUISE' ? 'active' : ''}">${esc(cs.typeExigence)}</span>
                                        </td>
                                        <td>
                                            ${cs.possedee ? `
                                                <span style="color:#15803D; font-weight:700; display:inline-flex; align-items:center; gap:0.25rem;">
                                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                                    Oui (${esc(cs.statut)})
                                                </span>
                                            ` : `
                                                <span style="color:#B91C1C; font-weight:600; display:inline-flex; align-items:center; gap:0.25rem;">
                                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="18" x2="18" y2="18"></line></svg>
                                                    Non
                                                </span>
                                            `}
                                        </td>
                                        <td style="font-size:0.82rem; color:#4A5B73;">
                                            ${esc(cs.provenanceLabel || cs.provenance || '—')}
                                        </td>
                                        <td>
                                            ${cs.comptee ? `
                                                <span class="badge-comptabilisee">+ comptabilisée</span>
                                            ` : `
                                                <span class="badge-ignoree">${esc(cs.raison || 'Non comptée')}</span>
                                            `}
                                        </td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>` : '<p class="recruit-muted">Aucun détail de compétence disponible pour cette offre.</p>'}
                </div>`;

                // Compétences du profil
                const profilComps = detail.competences || [];
                html += `
                <div class="candidate-detail-block">
                    <div class="candidate-detail-block-title">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" style="color:var(--rec-blue);"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>
                        Compétences renseignées par le candidat (${profilComps.length})
                    </div>
                    ${profilComps.length > 0 ? `
                    <div style="display:flex; flex-wrap:wrap; gap:0.5rem;">
                        ${profilComps.map(cp => `
                            <div class="recruit-tag" style="background:#FFFFFF; border:1px solid var(--rec-border); padding:0.4rem 0.75rem; border-radius:var(--rec-radius-sm);">
                                <strong>${esc(cp.nom)}</strong>
                                <span class="recruit-badge ${cp.statut === 'VALIDEE' ? 'active' : ''}" style="font-size:0.68rem; margin-left:0.35rem;">${esc(cp.statut)}</span>
                                <span class="recruit-muted" style="font-size:0.75rem; display:block; margin-top:0.2rem;">${esc(cp.provenanceLabel || cp.provenance || 'Déclarée')}</span>
                            </div>
                        `).join('')}
                    </div>` : '<p class="recruit-muted">Aucune compétence sur le profil.</p>'}
                </div>`;

                // Expériences professionnelles
                const exps = detail.experiences || [];
                html += `
                <div class="candidate-detail-block">
                    <div class="candidate-detail-block-title">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="7" width="20" height="14" rx="2"></rect><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path></svg>
                        Expériences professionnelles (${exps.length})
                    </div>
                    ${exps.length > 0 ? `
                    <div style="display:flex; flex-direction:column; gap:0.75rem;">
                        ${exps.map(exp => `
                            <div style="background:#FFFFFF; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:0.85rem 1rem;">
                                <div style="font-weight:800; color:var(--rec-navy); font-size:0.98rem;">${esc(exp.poste)} · <span style="color:var(--rec-blue);">${esc(exp.entreprise)}</span></div>
                                <div class="recruit-muted" style="font-size:0.8rem; margin:0.2rem 0 0.4rem;">
                                    ${date(exp.dateDebut)} — ${exp.dateFin ? date(exp.dateFin) : 'En cours'}
                                </div>
                                ${exp.competences && exp.competences.length > 0 ? `
                                <div style="display:flex; flex-wrap:wrap; gap:0.35rem; margin-top:0.4rem;">
                                    ${exp.competences.map(c => `<span class="contract-badge" style="font-size:0.72rem;">${esc(c)}</span>`).join('')}
                                </div>` : ''}
                            </div>
                        `).join('')}
                    </div>` : '<p class="recruit-muted">Aucune expérience professionnelle renseignée.</p>'}
                </div>`;

                // Projets
                const projs = detail.projets || [];
                html += `
                <div class="candidate-detail-block">
                    <div class="candidate-detail-block-title">
                        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 2 7 12 12 22 7 12 2"></polygon><polyline points="2 17 12 22 22 17"></polyline><polyline points="2 12 12 17 22 12"></polyline></svg>
                        Projets académiques &amp; personnels (${projs.length})
                    </div>
                    ${projs.length > 0 ? `
                    <div style="display:flex; flex-direction:column; gap:0.75rem;">
                        ${projs.map(prj => `
                            <div style="background:#FFFFFF; border:1px solid var(--rec-border); border-radius:var(--rec-radius-sm); padding:0.85rem 1rem;">
                                <div style="font-weight:800; color:var(--rec-navy); font-size:0.98rem;">${esc(prj.titre)}</div>
                                ${prj.description ? `<p style="font-size:0.88rem; color:#4A5B73; margin:0.3rem 0 0.5rem;">${esc(prj.description)}</p>` : ''}
                                ${prj.competences && prj.competences.length > 0 ? `
                                <div style="display:flex; flex-wrap:wrap; gap:0.35rem; margin-top:0.4rem;">
                                    ${prj.competences.map(c => `<span class="contract-badge" style="font-size:0.72rem;">${esc(c)}</span>`).join('')}
                                </div>` : ''}
                            </div>
                        `).join('')}
                    </div>` : '<p class="recruit-muted">Aucun projet renseigné.</p>'}
                </div>`;

                modalBody.innerHTML = html;

                // Footer de la modal avec boutons de décision
                if (modalFooter) {
                    if (!isTerminal) {
                        modalFooter.innerHTML = `
                            <button type="button" class="recruit-btn" id="modal-btn-close">Fermer</button>
                            <div style="display:flex; gap:0.75rem;">
                                <button type="button" class="recruit-btn danger" id="modal-btn-refuser">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="18" x2="18" y2="18"></line></svg>
                                    Refuser la candidature
                                </button>
                                <button type="button" class="recruit-btn primary" id="modal-btn-accepter" style="background:#15803D; border-color:#15803D;">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                    Accepter la candidature
                                </button>
                            </div>
                        `;
                        document.getElementById('modal-btn-close')?.addEventListener('click', closeCandidateModal);
                        document.getElementById('modal-btn-accepter')?.addEventListener('click', () => {
                            openDecisionModal(detail.candidatureId, 'ACCEPTEE', `${detail.prenom} ${detail.nom}`);
                        });
                        document.getElementById('modal-btn-refuser')?.addEventListener('click', () => {
                            openDecisionModal(detail.candidatureId, 'REFUSEE', `${detail.prenom} ${detail.nom}`);
                        });
                    } else {
                        modalFooter.innerHTML = `
                            <span class="recruit-muted" style="font-size:0.88rem; font-style:italic;">
                                Décision finale enregistrée : candidature <strong>${esc(detail.statut)}</strong>
                            </span>
                            <button type="button" class="recruit-btn" id="modal-btn-close">Fermer</button>
                        `;
                        document.getElementById('modal-btn-close')?.addEventListener('click', closeCandidateModal);
                    }
                }

            } catch (err) {
                modalBody.innerHTML = `<div class="recruit-error" style="display:block;">${esc(err.message)}</div>`;
            }
        }

        // Clics sur les cartes de la liste des candidats
        const listEl = document.getElementById('candidats-list');
        if (listEl) {
            listEl.addEventListener('click', async e => {
                // 1. Bouton favori (étoile)
                const favBtn = e.target.closest('[data-action="toggle-favori"]');
                if (favBtn) {
                    e.stopPropagation();
                    e.preventDefault();
                    const candId = favBtn.dataset.candId;
                    favBtn.disabled = true;
                    try {
                        const updated = await api(`/api/candidatures/${candId}/favori?${owned}`, 'PATCH');
                        const item = allCandidats.find(c => String(c.candidatureId) === String(candId));
                        if (item) {
                            if (updated && typeof updated.favori === 'boolean') {
                                item.favori = updated.favori;
                            } else {
                                item.favori = !item.favori;
                            }
                        }
                        updateCounts();
                        renderCandidates();
                    } catch (err) {
                        showError("Impossible de modifier le favori : " + err.message);
                    } finally {
                        favBtn.disabled = false;
                    }
                    return;
                }

                // 2. Bouton enregistrer note interne
                const saveNoteBtn = e.target.closest('[data-action="save-note"]');
                if (saveNoteBtn) {
                    const candId = saveNoteBtn.dataset.candId;
                    const card = listEl.querySelector(`article[data-cand-id="${candId}"]`);
                    const textarea = card ? card.querySelector('.input-note-interne') : null;
                    const note = textarea ? textarea.value.trim() : '';

                    saveNoteBtn.disabled = true;
                    saveNoteBtn.textContent = 'Enregistrement…';
                    try {
                        const updated = await api(`/api/candidatures/${candId}/note?${owned}`, 'PATCH', { note });
                        const item = allCandidats.find(c => String(c.candidatureId) === String(candId));
                        if (item) {
                            item.noteInterne = updated.noteInterne;
                        }
                        saveNoteBtn.textContent = 'Enregistré ✓';
                        saveNoteBtn.style.color = '#15803D';
                        setTimeout(() => {
                            saveNoteBtn.textContent = 'Enregistrer';
                            saveNoteBtn.style.color = '';
                            saveNoteBtn.disabled = false;
                        }, 1600);
                    } catch (err) {
                        saveNoteBtn.textContent = 'Erreur';
                        saveNoteBtn.disabled = false;
                        showError("Impossible d'enregistrer la note : " + err.message);
                    }
                    return;
                }

                // 3. Ouvrir la modal de détail
                const openBtn = e.target.closest('button[data-open-detail]');
                if (openBtn) {
                    await openCandidateModal(openBtn.dataset.openDetail);
                    return;
                }

                // 4. Décision rapide (Accepter / Refuser) -> ouvre la modal avec message optionnel
                const actionBtn = e.target.closest('button[data-quick-action]');
                if (actionBtn) {
                    const newStatut = actionBtn.dataset.quickAction;
                    const candId = actionBtn.dataset.candId;
                    const candName = actionBtn.dataset.candName;
                    openDecisionModal(candId, newStatut, candName);
                }
            });
        }

        // Affichage initial
        updateCounts();
        renderCandidates();

        // Ouvrir automatiquement la modal si demandée en paramètre d'URL et défiler vers la carte
        if (targetCandId && /^\d+$/.test(targetCandId)) {
            const cardEl = document.querySelector(`[data-cand-id="${targetCandId}"]`);
            if (cardEl) {
                cardEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
            await openCandidateModal(targetCandId);
        }

        // Enregistrer la fonction d'actualisation en direct si une candidature arrive
        window.__refreshCandidatsLive = async (newCandId) => {
            try {
                allCandidats = await api(`/api/offres/${offreId}/candidats?${owned}`);
                updateCounts();
                renderCandidates();

                if (newCandId) {
                    const newCard = document.querySelector(`[data-cand-id="${newCandId}"]`);
                    if (newCard) {
                        newCard.classList.add('candidat-card-flash');
                        newCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
                    }
                }
            } catch (err) {
                console.error("Erreur actualisation candidats live :", err);
            }
        };
    }

    async function renderDashboardMetrics(isLive = false) {
        try {
            const entLabel = document.getElementById('recruiter-display-enterprise');
            if (entLabel) {
                const entName = user.nomEntreprise || user.entreprise;
                if (entName) {
                    entLabel.innerHTML = `
                        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <rect x="2" y="7" width="20" height="14" rx="2"></rect>
                            <path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"></path>
                        </svg>
                        <span>${esc(entName)}</span>
                    `;
                }
            }

            const dash = await api(`/api/recruteurs/${user.id}/dashboard`);
            if (dash) {
                const totalCandEl = document.getElementById('dash-cand-total');
                if (totalCandEl) {
                    totalCandEl.textContent = dash.candidaturesRecues;
                    if (isLive) pulseElement(totalCandEl);
                }
                const aTraiterEl = document.getElementById('dash-cand-atraiter');
                if (aTraiterEl) {
                    aTraiterEl.textContent = dash.candidaturesATraiter;
                    if (isLive) pulseElement(aTraiterEl);
                }
                const accEl = document.getElementById('dash-cand-acceptees');
                if (accEl) accEl.textContent = (dash.repartitionStatuts && dash.repartitionStatuts.ACCEPTEE) || 0;
                const refEl = document.getElementById('dash-cand-refusees');
                if (refEl) refEl.textContent = (dash.repartitionStatuts && dash.repartitionStatuts.REFUSEE) || 0;

                // 7j et 30j
                const cand7jEl = document.getElementById('dash-cand-7j');
                if (cand7jEl) {
                    cand7jEl.textContent = dash.candidatures7Jours ?? 0;
                    if (isLive) pulseElement(cand7jEl);
                }
                const cand30jEl = document.getElementById('dash-cand-30j');
                if (cand30jEl) {
                    cand30jEl.textContent = dash.candidatures30Jours ?? 0;
                    if (isLive) pulseElement(cand30jEl);
                }

                // Compétences les plus demandées
                const topCompsEl = document.getElementById('dash-top-competences');
                if (topCompsEl) {
                    const comps = dash.competencesLesPlusDemandees || [];
                    if (comps.length === 0) {
                        topCompsEl.innerHTML = `<span class="recruit-muted" style="font-size:0.85rem;">Aucune compétence requise définie sur vos offres actives.</span>`;
                    } else {
                        topCompsEl.innerHTML = comps.map(c => `
                            <span class="recruit-tag" style="background:#F1F5F9; border:1px solid var(--rec-border); padding:0.35rem 0.75rem; border-radius:16px; font-size:0.82rem; display:inline-flex; align-items:center; gap:0.4rem;">
                                <strong>${esc(c.nom)}</strong>
                                <span style="background:var(--rec-blue); color:#FFF; font-size:0.7rem; font-weight:700; padding:0.1rem 0.4rem; border-radius:10px;">${c.nbDemandes} ${c.nbDemandes > 1 ? 'offres' : 'offre'}</span>
                            </span>
                        `).join('');
                    }
                }

                // Offres sans candidature depuis >= 14 jours
                const inactEl = document.getElementById('dash-offres-inactives');
                if (inactEl) {
                    const inactives = dash.offresSansCandidature14Jours || [];
                    if (inactives.length === 0) {
                        inactEl.innerHTML = `
                            <div style="background:#F0FDF4; border:1px solid #BBF7D0; border-radius:8px; padding:0.75rem 1rem; color:#166534; font-size:0.85rem; display:flex; align-items:center; gap:0.5rem;">
                                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
                                <span>Toutes vos offres récentes et actives attirent des candidats. Aucune offre en sommeil (&ge; 14 jours).</span>
                            </div>
                        `;
                    } else {
                        inactEl.innerHTML = `
                            <div style="display:flex; flex-direction:column; gap:0.6rem;">
                                ${inactives.map(o => `
                                    <div style="display:flex; justify-content:space-between; align-items:center; background:#FFFBEB; border:1px solid #FDE68A; border-radius:8px; padding:0.65rem 0.85rem; font-size:0.85rem; flex-wrap:wrap; gap:0.5rem;">
                                        <div>
                                            <a href="/offre-recruteur.html?id=${o.offreId}" style="font-weight:700; color:#92400E; text-decoration:none;">${esc(o.titre)}</a>
                                            <div style="font-size:0.76rem; color:#B45309; margin-top:0.15rem;">Publiée le ${date(o.datePublication)} · Sans candidature depuis <strong>${o.joursSansCandidature} jours</strong></div>
                                        </div>
                                        <a href="/publier-offre.html?id=${o.offreId}" class="recruit-btn" style="padding:0.25rem 0.65rem; font-size:0.75rem;">Optimiser</a>
                                    </div>
                                `).join('')}
                            </div>
                        `;
                    }
                }

                const recentListEl = document.getElementById('recent-candidatures-list');
                if (recentListEl) {
                    if (!dash.dernieresCandidatures || dash.dernieresCandidatures.length === 0) {
                        recentListEl.innerHTML = `
                        <div class="recruit-empty" style="padding:1.75rem 1rem;">
                            <div class="recruit-empty-icon" aria-hidden="true">
                                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="9" cy="7" r="4"></circle></svg>
                            </div>
                            <div class="recruit-empty-title">Aucune candidature reçue pour le moment</div>
                            <p style="font-size:0.88rem;">Vos offres sont en ligne. Dès que des étudiants postuleront, vous retrouverez leurs scores et candidatures ici.</p>
                        </div>`;
                    } else {
                        recentListEl.innerHTML = `
                        <div style="overflow-x:auto;">
                            <table class="score-table" style="background:#FFFFFF; border-radius:12px; overflow:hidden; border:1px solid var(--rec-border);">
                                <thead>
                                    <tr>
                                        <th>Candidat</th>
                                        <th>Offre</th>
                                        <th>Score</th>
                                        <th>Statut</th>
                                        <th>Date</th>
                                        <th style="text-align:right;">Action</th>
                                    </tr>
                                </thead>
                                <tbody>
                                    ${dash.dernieresCandidatures.map((c, idx) => `
                                        <tr ${(isLive && idx === 0) ? 'class="tr-highlight"' : ''}>
                                            <td>
                                                <strong>${esc(c.prenom)} ${esc(c.nom)}</strong>
                                                <div style="font-size:0.78rem; color:#64748B;">${esc(c.filiere || '')} · ${esc(c.etablissement || '')}</div>
                                            </td>
                                            <td>
                                                <a href="/offre-recruteur.html?id=${c.offreId}" style="color:var(--rec-blue); font-weight:600; text-decoration:none;">${esc(c.offreTitre)}</a>
                                            </td>
                                            <td>
                                                <span class="candidat-score-pill ${c.score >= 70 ? 'score-high' : (c.score >= 40 ? 'score-mid' : 'score-low')}" style="padding:0.25rem 0.6rem; font-size:0.82rem;">
                                                    ${c.score != null ? Math.round(c.score) + '%' : '—'}
                                                </span>
                                            </td>
                                            <td>
                                                <span class="statut-badge ${String(c.statut).toLowerCase()}">${esc(c.statut)}</span>
                                            </td>
                                            <td style="color:#64748B; font-size:0.82rem;">${date(c.dateCandidature)}</td>
                                            <td style="text-align:right;">
                                                <a class="recruit-btn" style="padding:0.35rem 0.75rem; font-size:0.8rem;" href="/candidats-offre.html?offre=${c.offreId}&candidature=${c.candidatureId}">
                                                    Consulter
                                                </a>
                                            </td>
                                        </tr>
                                    `).join('')}
                                </tbody>
                            </table>
                        </div>`;
                    }
                }
            }
        } catch (err) {
            console.error("Erreur chargement dashboard recruteur :", err);
        }
    }

    async function refreshDashboardLive() {
        await renderDashboardMetrics(true);
        await loadList();
    }

    async function initEntreprisePage() {
        const form = document.getElementById('entreprise-form');
        const fields = document.getElementById('entreprise-fields');
        const submitBtn = document.getElementById('entreprise-submit');
        const successAlert = document.getElementById('recruit-success');

        const inputPrenom = document.getElementById('prenom');
        const inputNom = document.getElementById('nom');
        const inputNomEntreprise = document.getElementById('nomEntreprise');
        const inputSecteur = document.getElementById('secteurActivite');
        const inputVille = document.getElementById('ville');
        const inputSiteWeb = document.getElementById('siteWeb');
        const inputDesc = document.getElementById('descriptionEntreprise');

        // Éléments d'aperçu dynamique côté étudiant
        const prevAvatar = document.getElementById('preview-avatar');
        const prevNom = document.getElementById('preview-nom');
        const prevSecteurVille = document.getElementById('preview-secteur-ville');
        const prevDesc = document.getElementById('preview-desc');
        const prevWeb = document.getElementById('preview-web');
        const prevLink = document.getElementById('preview-link');

        function updatePreview() {
            const nomEnt = (inputNomEntreprise?.value || '').trim() || 'Nom Entreprise';
            const secteur = (inputSecteur?.value || '').trim();
            const ville = (inputVille?.value || '').trim();
            const site = (inputSiteWeb?.value || '').trim();
            const desc = (inputDesc?.value || '').trim();

            if (prevNom) prevNom.textContent = nomEnt;
            if (prevAvatar) {
                prevAvatar.textContent = (nomEnt[0] || '🏢').toUpperCase();
            }

            const parts = [];
            if (secteur) parts.push(secteur);
            if (ville) parts.push(ville);
            if (prevSecteurVille) {
                prevSecteurVille.textContent = parts.length > 0 ? parts.join(' · ') : 'Secteur d’activité · Ville';
            }

            if (prevDesc) {
                prevDesc.textContent = desc || "Présentation de l'entreprise et opportunités offertes...";
                prevDesc.style.fontStyle = desc ? 'normal' : 'italic';
                prevDesc.style.color = desc ? 'var(--rec-navy)' : '#64748B';
            }

            if (prevWeb && prevLink) {
                if (site) {
                    prevWeb.style.display = 'block';
                    prevLink.textContent = site;
                    prevLink.href = site;
                } else {
                    prevWeb.style.display = 'none';
                }
            }
        }

        [inputNomEntreprise, inputSecteur, inputVille, inputSiteWeb, inputDesc].forEach(el => {
            el?.addEventListener('input', updatePreview);
        });

        // 1. Charger les données du recruteur
        try {
            clearError();
            const data = await api(`/api/recruteurs/${user.id}`);
            if (data) {
                if (inputPrenom) inputPrenom.value = data.prenom || '';
                if (inputNom) inputNom.value = data.nom || '';
                if (inputNomEntreprise) inputNomEntreprise.value = data.nomEntreprise || data.entreprise || '';
                if (inputSecteur) inputSecteur.value = data.secteurActivite || '';
                if (inputVille) inputVille.value = data.ville || '';
                if (inputSiteWeb) inputSiteWeb.value = data.siteWeb || '';
                if (inputDesc) inputDesc.value = data.descriptionEntreprise || '';
                updatePreview();
            }
        } catch (err) {
            showError("Impossible de charger le profil entreprise : " + err.message);
        } finally {
            if (fields) fields.disabled = false;
        }

        // 2. Gestion de la soumission du profil entreprise
        if (form) {
            form.addEventListener('submit', async (e) => {
                e.preventDefault();
                clearError();
                if (successAlert) {
                    successAlert.style.display = 'none';
                    successAlert.textContent = '';
                }

                const nomEnt = (inputNomEntreprise?.value || '').trim();
                if (!nomEnt) {
                    showError("Le nom de l'entreprise est obligatoire.");
                    inputNomEntreprise?.focus();
                    return;
                }

                const site = (inputSiteWeb?.value || '').trim();
                if (site && !/^https?:\/\//i.test(site)) {
                    showError("Le site web doit être une URL valide débutant par http:// ou https://.");
                    inputSiteWeb?.focus();
                    return;
                }

                const payload = {
                    prenom: (inputPrenom?.value || '').trim(),
                    nom: (inputNom?.value || '').trim(),
                    nomEntreprise: nomEnt,
                    secteurActivite: (inputSecteur?.value || '').trim(),
                    ville: (inputVille?.value || '').trim(),
                    siteWeb: site || null,
                    descriptionEntreprise: (inputDesc?.value || '').trim() || null
                };

                if (submitBtn) submitBtn.disabled = true;

                try {
                    const saved = await api(`/api/recruteurs/${user.id}`, 'PUT', payload);

                    // Mettre à jour le cache de session utilisateur
                    const currentUser = MatchingCVSession.read();
                    if (currentUser) {
                        const updatedSession = {
                            ...currentUser,
                            prenom: saved.prenom,
                            nom: saved.nom,
                            nomEntreprise: saved.nomEntreprise,
                            entreprise: saved.nomEntreprise
                        };
                        localStorage.setItem('matchingcv_user', JSON.stringify(updatedSession));
                    }

                    // Mettre à jour la navbar
                    const navUserName = document.getElementById('nav-user-name');
                    if (navUserName) {
                        navUserName.textContent = `${saved.prenom || ''} ${saved.nom || ''}`.trim() || 'Recruteur';
                    }

                    if (successAlert) {
                        successAlert.textContent = "Profil entreprise enregistré avec succès ! Vos informations sont désormais visibles sur vos offres.";
                        successAlert.style.display = 'block';
                        successAlert.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    }
                } catch (err) {
                    showError("Erreur lors de l'enregistrement : " + err.message);
                } finally {
                    if (submitBtn) submitBtn.disabled = false;
                }
            });
        }
    }
});
