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

        return `
        <article class="recruit-card" data-card-id="${o.id}" data-statut="${o.statut}" data-title="${esc(o.titre).toLowerCase()}" data-domain="${esc(o.domaine).toLowerCase()}" data-loc="${esc(o.localisation).toLowerCase()}">
            <div class="recruit-card-header">
                <div class="recruit-card-tags">
                    <span class="recruit-badge ${isAct ? 'active' : ''}">
                        ${isAct ? '<span class="pulse-dot"></span> Active' : 'Clôturée'}
                    </span>
                    <span class="contract-badge ${esc(o.typeContrat)}">${esc(o.typeContrat)}</span>
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
                <a class="recruit-btn primary" href="/candidats-offre.html?offreId=${o.id}">
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
                ${isAct ? `
                <button type="button" class="recruit-btn" data-action="close" data-id="${o.id}">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                    Clôturer
                </button>` : ''}
                <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}" data-has-cands="${(o.nbCandidatures || 0) > 0}">
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
            try {
                const dash = await api(`/api/recruteurs/${user.id}/dashboard`);
                if (dash) {
                    const totalCandEl = document.getElementById('dash-cand-total');
                    if (totalCandEl) totalCandEl.textContent = dash.candidaturesRecues;
                    const aTraiterEl = document.getElementById('dash-cand-atraiter');
                    if (aTraiterEl) aTraiterEl.textContent = dash.candidaturesATraiter;
                    const accEl = document.getElementById('dash-cand-acceptees');
                    if (accEl) accEl.textContent = (dash.repartitionStatuts && dash.repartitionStatuts.ACCEPTEE) || 0;
                    const refEl = document.getElementById('dash-cand-refusees');
                    if (refEl) refEl.textContent = (dash.repartitionStatuts && dash.repartitionStatuts.REFUSEE) || 0;

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
                                        ${dash.dernieresCandidatures.map(c => `
                                            <tr>
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
                                                    <a class="recruit-btn" style="padding:0.35rem 0.75rem; font-size:0.8rem;" href="/candidats-offre.html?offreId=${c.offreId}&candidatureId=${c.candidatureId}">
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
            if (action !== 'close' && action !== 'delete') return;

            const id = button.dataset.id;
            const close = action === 'close';

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
                    ${isAct ? `
                    <button type="button" class="recruit-btn" data-action="close" data-id="${o.id}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                        Clôturer l’offre
                    </button>` : ''}
                    <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}" data-has-cands="${(o.nbCandidatures || 0) > 0}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                        Supprimer l’offre
                    </button>
                    <a class="recruit-btn" href="/mes-offres.html">Retour à mes offres</a>
                </div>
            </article>`;

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
                                        <a href="/candidats-offre.html?offreId=${id}&candidatureId=${c.candidatureId}" class="recruit-btn" style="padding:0.4rem 0.8rem; font-size:0.82rem;">Consulter</a>
                                    </div>
                                </div>
                            `).join('')}
                        </div>
                        ${totalCands > 3 ? `<p style="text-align:center; margin-top:1rem; font-size:0.88rem;" class="recruit-muted">Et ${totalCands - 3} autre(s) candidat(s)... <a href="/candidats-offre.html?offreId=${id}" style="color:var(--rec-blue);font-weight:700;">Voir la liste complète ordonnée par score</a></p>` : ''}
                        `;
                    }
                } catch (_) {}
            }
        }

        if (page === 'candidats') await initCandidatsPage();
        if (page === 'form') await initForm();
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

        if (id) {
            if (!/^\d+$/.test(id)) throw new Error('Identifiant d’offre invalide.');
            const o = await api(`/api/offres/${id}?${owned}`);
            ['titre', 'description', 'domaine', 'localisation', 'typeContrat'].forEach(key => {
                form.elements[key].value = o[key];
            });
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
        const offreId = urlParams.get('offreId');
        const targetCandId = urlParams.get('candidatureId');

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
        }

        function renderCard(c) {
            const initials = esc(((c.prenom?.[0] || '') + (c.nom?.[0] || '')).toUpperCase());
            const isTerminal = c.statut === 'ACCEPTEE' || c.statut === 'REFUSEE';

            return `
            <article class="candidat-card" data-cand-id="${c.candidatureId}">
                <div class="candidat-card-top">
                    <div class="candidat-info-group">
                        <div class="candidat-avatar">${initials}</div>
                        <div>
                            <h3 class="candidat-name" style="margin:0 0 0.25rem;">${esc(c.prenom)} ${esc(c.nom)}</h3>
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
                <div style="background:#F0FDF4; border:1px solid #BBF7D0; border-radius:var(--rec-radius-sm); padding:0.65rem 0.9rem; display:flex; align-items:center; gap:0.5rem; font-size:0.88rem; color:#166534;">
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"></path><polyline points="22,6 12,13 2,6"></polyline></svg>
                    <strong>Contact candidat débloqué :</strong>
                    <a href="mailto:${esc(c.email)}" style="color:#15803D; font-weight:700; text-decoration:underline;">${esc(c.email)}</a>
                </div>` : `
                <div style="font-size:0.82rem; color:#64748B; display:flex; align-items:center; gap:0.4rem;">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                    <span>Email protégé : disponible immédiatement dès acceptation de la candidature.</span>
                </div>`}

                <div class="recruit-actions" style="margin-top:0.5rem; border-top:1px solid var(--rec-border); padding-top:0.9rem;">
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

            let filtered = allCandidats;
            if (activeFilter !== 'ALL') {
                filtered = filtered.filter(c => c.statut === activeFilter);
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

        // Modal interactions
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
            if (e.key === 'Escape' && backdrop && backdrop.classList.contains('active')) {
                closeCandidateModal();
            }
        });

        async function handleStatutChange(candId, newStatut, candName) {
            const verb = newStatut === 'ACCEPTEE' ? 'accepter' : 'refuser';
            const confirmMsg = `Confirmez-vous vouloir ${verb} la candidature de ${candName || 'ce candidat'} ?\n\nCette action est irréversible.`;
            if (!window.confirm(confirmMsg)) return;

            clearError();
            try {
                const updated = await api(`/api/candidatures/${candId}/statut?${owned}`, 'PATCH', { statut: newStatut });
                const idx = allCandidats.findIndex(c => String(c.candidatureId) === String(candId));
                if (idx !== -1) {
                    allCandidats[idx].statut = updated.statut || newStatut;
                    if (newStatut === 'ACCEPTEE') {
                        try {
                            const d = await api(`/api/candidatures/${candId}?${owned}`);
                            allCandidats[idx].email = d.email;
                        } catch (_) {}
                    }
                }
                updateCounts();
                renderCandidates();
                closeCandidateModal();
            } catch (err) {
                showError("Impossible de mettre à jour le statut : " + err.message);
            }
        }

        async function openCandidateModal(candId) {
            if (!backdrop || !modalBody) return;
            backdrop.classList.add('active');
            backdrop.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
            modalBody.innerHTML = '<div style="padding:2.5rem;text-align:center;" class="recruit-muted">Chargement du profil et de l\'explication du score…</div>';
            if (modalFooter) modalFooter.innerHTML = '';

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
                        document.getElementById('modal-btn-accepter')?.addEventListener('click', async () => {
                            await handleStatutChange(detail.candidatureId, 'ACCEPTEE', `${detail.prenom} ${detail.nom}`);
                        });
                        document.getElementById('modal-btn-refuser')?.addEventListener('click', async () => {
                            await handleStatutChange(detail.candidatureId, 'REFUSEE', `${detail.prenom} ${detail.nom}`);
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
                const openBtn = e.target.closest('button[data-open-detail]');
                if (openBtn) {
                    await openCandidateModal(openBtn.dataset.openDetail);
                    return;
                }

                const actionBtn = e.target.closest('button[data-quick-action]');
                if (actionBtn) {
                    const newStatut = actionBtn.dataset.quickAction;
                    const candId = actionBtn.dataset.candId;
                    const candName = actionBtn.dataset.candName;
                    await handleStatutChange(candId, newStatut, candName);
                }
            });
        }

        // Affichage initial
        updateCounts();
        renderCandidates();

        // Ouvrir automatiquement la modal si demandée en paramètre d'URL
        if (targetCandId && /^\d+$/.test(targetCandId)) {
            await openCandidateModal(targetCandId);
        }
    }
});
