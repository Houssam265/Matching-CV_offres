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
            </div>

            ${skillsHtml}
            ${scoreAlert}

            <div class="recruit-actions">
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
                <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}">
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
            const close = button.dataset.action === 'close';
            if (!window.confirm(close ? 'Clôturer cette offre ? Elle disparaîtra des listes publiques.' : 'Supprimer définitivement cette offre ? Si elle a reçu des candidatures, vous devrez la clôturer.')) return;
            button.disabled = true;
            clearError();
            try {
                await api(`/api/offres/${button.dataset.id}${close ? '/cloturer' : ''}?${owned}`, close ? 'PATCH' : 'DELETE');
                if (page === 'detail' && !close) {
                    window.location.assign('/mes-offres.html');
                } else if (page === 'detail' && close) {
                    window.location.reload();
                } else {
                    await loadList();
                }
            } catch (e) {
                showError(e.message);
            } finally {
                button.disabled = false;
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
                    <button type="button" class="recruit-btn danger" data-action="delete" data-id="${o.id}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"></polyline><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path></svg>
                        Supprimer l’offre
                    </button>
                    <a class="recruit-btn" href="/mes-offres.html">Retour à mes offres</a>
                </div>
            </article>`;
        }

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
});
