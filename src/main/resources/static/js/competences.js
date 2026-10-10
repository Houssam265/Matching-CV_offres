/**
 * competences.js — Page "Mes compétences" (Espace Étudiant)
 * Matching CV-Offres (GI3, ENSA Tétouan)
 *
 * Fonctionnalités :
 * 1. Chargement de la liste personnelle de compétences via GET /api/etudiants/{id}/competences (DTO sans boucle)
 * 2. Autocomplétion (/suggestions, debounce 250 ms, navigation clavier)
 * 3. Rattachement direct ou proposition d'une nouvelle compétence (POST /api/etudiants/{id}/competences)
 * 4. Retrait d'une compétence avec confirmation détaillée (DELETE /api/etudiants/{id}/competences/{id})
 * 5. Recherche et filtrage en temps réel dans le tableau
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
    const user = MatchingCVSession.require('ETUDIANT');
    if (!user) return;
    const studentId = user.id;

    // Éléments du DOM
    const searchInput = document.getElementById('competence-search-input');
    const dropdown = document.getElementById('autocomplete-dropdown');
    const formAlert = document.getElementById('form-alert');

    // Bloc proposition de nouvelle compétence
    const categorieGroup = document.getElementById('categorie-group');
    const lblNewSkillName = document.getElementById('lbl-new-skill-name');
    const catInput = document.getElementById('competence-categorie');
    const btnSubmitProposal = document.getElementById('btn-submit-proposal');
    const btnCancelProposal = document.getElementById('btn-cancel-proposal');

    // Tableau et statistiques
    const table = document.getElementById('competences-table');
    const tbody = document.getElementById('competences-tbody');
    const emptyState = document.getElementById('empty-state');
    const emptyStateTitle = document.getElementById('empty-state-title');
    const emptyStateText = document.getElementById('empty-state-text');

    const competencesCount = document.getElementById('competences-count');
    const statTotalCount = document.getElementById('stat-total-count');
    const statUnusedCount = document.getElementById('stat-unused-count');

    const filterInput = document.getElementById('search-input');
    const refreshBtn = document.getElementById('btn-refresh');

    // État local
    let studentCompetences = [];
    let debounceTimer = null;
    let pendingProposalName = '';
    let currentSelectedIndex = -1;

    /* ═══════════════════════════════════════════════════════════
       1. CHARGEMENT DE MES COMPÉTENCES (GET /api/etudiants/:id/competences)
    ═══════════════════════════════════════════════════════════ */
    async function loadStudentCompetences() {
        if (refreshBtn) refreshBtn.classList.add('spinning');
        showLoadingState();

        try {
            const response = await fetch(`/api/etudiants/${studentId}/competences`, {
                headers: { 'Accept': 'application/json' },
                cache: 'no-cache'
            });

            if (!response.ok) {
                throw new Error(`Erreur HTTP: ${response.status}`);
            }

            studentCompetences = await response.json();
            updateStatistics(studentCompetences);
            applyFilter();
        } catch (error) {
            console.error('[Mes Competences] Erreur de chargement:', error);
            showErrorAlert('Impossible de charger vos compétences. Vérifiez votre connexion au serveur.');
            renderTable([]);
        } finally {
            if (refreshBtn) {
                setTimeout(() => refreshBtn.classList.remove('spinning'), 400);
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════
       2. AUTOCOMPLÉTION (/suggestions, DEBOUNCE ~250ms, CLAVIER)
    ═══════════════════════════════════════════════════════════ */
    if (searchInput) {
        searchInput.addEventListener('input', () => {
            clearTimeout(debounceTimer);
            const query = searchInput.value.trim();

            if (!query) {
                closeDropdown();
                return;
            }

            debounceTimer = setTimeout(() => {
                fetchSuggestions(query);
            }, 250);
        });

        searchInput.addEventListener('keydown', (e) => {
            if (!dropdown.classList.contains('open')) return;

            const items = dropdown.querySelectorAll('.autocomplete-item');
            if (!items.length) return;

            if (e.key === 'ArrowDown') {
                e.preventDefault();
                currentSelectedIndex = (currentSelectedIndex + 1) % items.length;
                updateActiveItem(items);
            } else if (e.key === 'ArrowUp') {
                e.preventDefault();
                currentSelectedIndex = (currentSelectedIndex - 1 + items.length) % items.length;
                updateActiveItem(items);
            } else if (e.key === 'Enter') {
                e.preventDefault();
                if (currentSelectedIndex >= 0 && currentSelectedIndex < items.length) {
                    items[currentSelectedIndex].click();
                } else if (items.length > 0) {
                    items[0].click();
                }
            } else if (e.key === 'Escape') {
                closeDropdown();
            }
        });
    }

    function updateActiveItem(items) {
        items.forEach((item, index) => {
            item.classList.toggle('active', index === currentSelectedIndex);
            if (index === currentSelectedIndex) {
                item.scrollIntoView({ block: 'nearest' });
            }
        });
    }

    async function fetchSuggestions(query) {
        try {
            const url = `/api/etudiants/${studentId}/competences/suggestions?q=${encodeURIComponent(query)}`;
            const response = await fetch(url, { headers: { 'Accept': 'application/json' } });
            if (!response.ok) return;

            const suggestions = await response.json();
            renderDropdown(query, suggestions);
        } catch (error) {
            console.error('[Autocomplete] Erreur:', error);
        }
    }

    function normaliserNom(str) {
        if (!str) return '';
        return str
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .toLowerCase()
            .replace(/[\s_\-.]+/g, '');
    }

    function renderDropdown(query, suggestions) {
        dropdown.innerHTML = '';
        currentSelectedIndex = -1;

        const qNorm = normaliserNom(query);
        const dejaDansMaListe = studentCompetences.find(c => normaliserNom(c.nom) === qNorm);
        const matchExactDansSuggestions = suggestions.some(s => normaliserNom(s.nom) === qNorm);

        // Si la compétence (ou une variante orthographique ex: spring.boot) est déjà dans la liste de l'étudiant
        if (dejaDansMaListe) {
            const alreadyItem = document.createElement('div');
            alreadyItem.className = 'autocomplete-item already-present';
            alreadyItem.style.cssText = 'cursor: default; background: rgba(245, 158, 11, 0.08); border-left: 3px solid #f59e0b;';
            alreadyItem.innerHTML = `
                <div class="autocomplete-item-left" style="color: #b45309; font-weight: 500;">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                    <span>&laquo; ${escapeHtml(dejaDansMaListe.nom)} &raquo; est d&eacute;j&agrave; dans votre liste</span>
                </div>
                <span style="font-size: .72rem; color: #b45309; font-weight: 600;">D&eacute;j&agrave; pr&eacute;sente</span>
            `;
            dropdown.appendChild(alreadyItem);
        }

        // Suggestions existantes correspondantes
        suggestions.forEach(comp => {
            const item = document.createElement('div');
            item.className = 'autocomplete-item';
            item.setAttribute('role', 'option');
            item.dataset.id = comp.id;
            item.dataset.nom = comp.nom;

            const catLabel = comp.categorie
                ? `<span class="badge-cat" style="font-size:.7rem;padding:.15rem .5rem;"><span class="badge-cat-dot"></span>${escapeHtml(comp.categorie)}</span>`
                : '';

            item.innerHTML = `
                <div class="autocomplete-item-left">
                    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                    <span>${escapeHtml(comp.nom)}</span>
                </div>
                ${catLabel}
            `;

            item.addEventListener('click', () => {
                addExistingCompetence(comp.id, comp.nom);
                closeDropdown();
                searchInput.value = '';
            });

            dropdown.appendChild(item);
        });

        // Option "+ Proposer « X » comme nouvelle compétence" :
        // Uniquement si pas déjà dans la liste personnelle ET pas déjà dans les suggestions
        if (!dejaDansMaListe && !matchExactDansSuggestions && query.length > 0) {
            const proposeItem = document.createElement('div');
            proposeItem.className = 'autocomplete-item propose';
            proposeItem.setAttribute('role', 'option');
            proposeItem.innerHTML = `
                <div class="autocomplete-item-left">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                    <span>+ Proposer &laquo;&nbsp;${escapeHtml(query)}&nbsp;&raquo; comme nouvelle comp&eacute;tence</span>
                </div>
                <span style="font-size:.74rem;color:var(--text-light);font-style:italic;">En attente de validation</span>
            `;

            proposeItem.addEventListener('click', () => {
                openProposalForm(query);
                closeDropdown();
            });

            dropdown.appendChild(proposeItem);
        }

        if (dropdown.children.length > 0) {
            dropdown.classList.add('open');
        } else {
            closeDropdown();
        }
    }

    function closeDropdown() {
        dropdown.classList.remove('open');
        dropdown.innerHTML = '';
        currentSelectedIndex = -1;
    }

    document.addEventListener('click', (e) => {
        if (!searchInput.contains(e.target) && !dropdown.contains(e.target)) {
            closeDropdown();
        }
    });

    /* ═══════════════════════════════════════════════════════════
       3. AJOUT D'UNE COMPÉTENCE EXISTANTE (POST {competenceId})
    ═══════════════════════════════════════════════════════════ */
    async function addExistingCompetence(competenceId, competenceNom) {
        hideAlert();
        try {
            const response = await fetch(`/api/etudiants/${studentId}/competences`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ competenceId: competenceId })
            });

            const data = await response.json().catch(() => null);

            if (response.ok && data) {
                showAlert(`La compétence « ${escapeHtml(data.nom || competenceNom)} » a été ajoutée à votre liste !`, 'success');
                await loadStudentCompetences();
            } else if (response.status === 409) {
                const message = (data && data.message) ? data.message : 'Cette compétence est déjà dans votre liste.';
                showAlert(message, 'danger');
            } else {
                const message = (data && data.message) ? data.message : `Erreur (Code ${response.status})`;
                showAlert(message, 'danger');
            }
        } catch (error) {
            console.error('[Ajout Competence] Erreur:', error);
            showAlert('Erreur réseau lors de l\'ajout de la compétence.', 'danger');
        }
    }

    /* ═══════════════════════════════════════════════════════════
       4. PROPOSITION D'UNE NOUVELLE COMPÉTENCE (POST {nouvelleCompetence, categorie})
    ═══════════════════════════════════════════════════════════ */
    function openProposalForm(nom) {
        pendingProposalName = nom;
        lblNewSkillName.textContent = nom;
        categorieGroup.style.display = 'block';
        catInput.value = '';
        catInput.focus();
    }

    function closeProposalForm() {
        pendingProposalName = '';
        categorieGroup.style.display = 'none';
        searchInput.value = '';
        searchInput.focus();
    }

    btnCancelProposal?.addEventListener('click', closeProposalForm);

    btnSubmitProposal?.addEventListener('click', async () => {
        const nom = pendingProposalName.trim();
        if (!nom) {
            showAlert('Veuillez saisir un nom de compétence.', 'danger');
            return;
        }

        const qNorm = normaliserNom(nom);
        const dejaDansMaListe = studentCompetences.find(c => normaliserNom(c.nom) === qNorm);
        if (dejaDansMaListe) {
            showAlert(`La compétence « ${escapeHtml(dejaDansMaListe.nom)} » est déjà dans votre liste.`, 'danger');
            closeProposalForm();
            return;
        }

        const categorie = catInput.value.trim();
        btnSubmitProposal.disabled = true;

        try {
            const response = await fetch(`/api/etudiants/${studentId}/competences`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({
                    nouvelleCompetence: nom,
                    categorie: categorie ? categorie : null
                })
            });

            const data = await response.json().catch(() => null);

            if (response.ok && data) {
                if (data.statut === 'EN_ATTENTE') {
                    showAlert(`La compétence « ${escapeHtml(data.nom)} » a été proposée, en attente de validation.`, 'success');
                } else {
                    showAlert(`La compétence « ${escapeHtml(data.nom)} » a été ajoutée à votre liste !`, 'success');
                }
                closeProposalForm();
                await loadStudentCompetences();
            } else if (response.status === 409) {
                const message = (data && data.message) ? data.message : 'Cette compétence est déjà dans votre liste.';
                showAlert(message, 'danger');
            } else {
                const message = (data && data.message) ? data.message : `Erreur (Code ${response.status})`;
                showAlert(message, 'danger');
            }
        } catch (error) {
            console.error('[Proposer Competence] Erreur:', error);
            showAlert('Erreur réseau lors de la proposition.', 'danger');
        } finally {
            btnSubmitProposal.disabled = false;
        }
    });

    /* ═══════════════════════════════════════════════════════════
       5. RETRAIT D'UNE COMPÉTENCE (DELETE /api/etudiants/:id/competences/:compId)
    ═══════════════════════════════════════════════════════════ */
    if (tbody) {
        tbody.addEventListener('click', async (event) => {
            const btn = event.target.closest('.btn-action-retirer');
            if (!btn) return;

            const compId = btn.getAttribute('data-id');
            const compNom = btn.getAttribute('data-name') || 'cette compétence';
            const nbProjets = parseInt(btn.getAttribute('data-projets') || '0', 10);
            const nbExperiences = parseInt(btn.getAttribute('data-experiences') || '0', 10);

            // Message de confirmation précis mentionnant les détachements
            let confirmMsg = `Voulez-vous vraiment retirer la compétence « ${compNom} » de votre liste ?`;
            const parts = [];
            if (nbProjets > 0) parts.push(`${nbProjets} projet${nbProjets > 1 ? 's' : ''}`);
            if (nbExperiences > 0) parts.push(`${nbExperiences} expérience${nbExperiences > 1 ? 's' : ''}`);

            if (parts.length > 0) {
                confirmMsg = `Cette compétence sera retirée de votre liste personnelle et détachée de vos ${parts.join(' et ')}. Confirmer le retrait ?`;
            }

            if (!confirm(confirmMsg)) return;

            btn.disabled = true;

            try {
                const response = await fetch(`/api/etudiants/${studentId}/competences/${compId}`, {
                    method: 'DELETE',
                    headers: { 'Accept': 'application/json' }
                });

                if (response.status === 204 || response.ok) {
                    showAlert(`La compétence « ${escapeHtml(compNom)} » a été retirée de votre liste.`, 'success');
                    studentCompetences = studentCompetences.filter(c => String(c.id) !== String(compId));
                    updateStatistics(studentCompetences);
                    applyFilter();
                } else {
                    const data = await response.json().catch(() => null);
                    const message = (data && data.message) ? data.message : `Erreur lors du retrait (Code ${response.status})`;
                    showAlert(message, 'danger');
                    btn.disabled = false;
                }
            } catch (error) {
                console.error('[Retirer Competence] Erreur:', error);
                showAlert('Erreur réseau lors du retrait de la compétence.', 'danger');
                btn.disabled = false;
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       6. RENDU DU TABLEAU
    ═══════════════════════════════════════════════════════════ */
    function renderTable(items) {
        tbody.innerHTML = '';

        if (!items || items.length === 0) {
            table.closest('.table-responsive').style.display = 'none';
            emptyState.style.display = 'flex';

            if (studentCompetences.length > 0 && filterInput.value.trim() !== '') {
                emptyStateTitle.textContent = 'Aucun résultat trouvé';
                emptyStateText.textContent = `Aucune compétence ne correspond à votre recherche « ${escapeHtml(filterInput.value.trim())} ».`;
            } else {
                emptyStateTitle.textContent = 'Aucune compétence dans votre liste';
                emptyStateText.textContent = 'Utilisez le champ à gauche pour rechercher et ajouter vos compétences.';
            }
            return;
        }

        table.closest('.table-responsive').style.display = 'block';
        emptyState.style.display = 'none';

        const fragment = document.createDocumentFragment();

        items.forEach((comp) => {
            const tr = document.createElement('tr');

            // Catégorie
            const categorieBadge = comp.categorie
                ? `<span class="badge-cat"><span class="badge-cat-dot"></span>${escapeHtml(comp.categorie)}</span>`
                : `<span style="color:var(--text-light);font-size:.84rem;">&mdash;</span>`;

            // Statut : Validée (vert), En attente (orange), Rejetée (rouge)
            const statut = comp.statut || 'VALIDEE';
            let statutClass = 'badge-status-validee';
            let statutLabel = 'Validée';
            let statutIcon = '<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';

            if (statut === 'EN_ATTENTE') {
                statutClass = 'badge-status-enattente';
                statutLabel = 'En attente';
                statutIcon = '<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>';
            } else if (statut === 'REJETEE') {
                statutClass = 'badge-status-rejetee';
                statutLabel = 'Rejetée';
                statutIcon = '<svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';
            }

            const statutBadge = `<span class="badge-status ${statutClass}">${statutIcon} ${escapeHtml(statutLabel)}</span>`;

            // Utilisation ("2 projets · 1 expérience" ou "Non utilisée")
            const nbP = comp.nbProjets || 0;
            const nbE = comp.nbExperiences || 0;
            let utilisationHtml = '';

            if (nbP === 0 && nbE === 0) {
                utilisationHtml = `<span class="badge-usage-none">Non utilisée</span>`;
            } else {
                const parts = [];
                if (nbP > 0) parts.push(`${nbP} projet${nbP > 1 ? 's' : ''}`);
                if (nbE > 0) parts.push(`${nbE} exp&eacute;rience${nbE > 1 ? 's' : ''}`);
                utilisationHtml = `<span class="usage-text">
                    <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align:-1px;"><polyline points="20 6 9 17 4 12"/></svg>
                    ${parts.join(' &middot; ')}
                </span>`;
            }

            tr.innerHTML = `
                <td>
                    <div class="competence-name">
                        <span class="competence-name-icon" aria-hidden="true">&#9670;</span>
                        <strong>${escapeHtml(comp.nom)}</strong>
                    </div>
                </td>
                <td>${categorieBadge}</td>
                <td>${statutBadge}</td>
                <td>${utilisationHtml}</td>
                <td style="text-align:center;">
                    <button type="button" class="btn-action-retirer" data-id="${comp.id}" data-name="${escapeHtml(comp.nom)}" data-projets="${nbP}" data-experiences="${nbE}" title="Retirer cette compétence" aria-label="Retirer ${escapeHtml(comp.nom)}">
                        <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/>
                        </svg>
                        <span>Retirer</span>
                    </button>
                </td>
            `;

            fragment.appendChild(tr);
        });

        tbody.appendChild(fragment);
    }

    function showLoadingState() {
        table.closest('.table-responsive').style.display = 'block';
        emptyState.style.display = 'none';
        tbody.innerHTML = `
            <tr class="skeleton-row">
                <td><div class="skeleton-bar" style="width:140px;"></div></td>
                <td><div class="skeleton-bar" style="width:100px;"></div></td>
                <td><div class="skeleton-bar" style="width:70px;"></div></td>
                <td><div class="skeleton-bar" style="width:110px;"></div></td>
                <td><div class="skeleton-bar" style="width:60px;margin:0 auto;"></div></td>
            </tr>
            <tr class="skeleton-row">
                <td><div class="skeleton-bar" style="width:180px;"></div></td>
                <td><div class="skeleton-bar" style="width:110px;"></div></td>
                <td><div class="skeleton-bar" style="width:70px;"></div></td>
                <td><div class="skeleton-bar" style="width:90px;"></div></td>
                <td><div class="skeleton-bar" style="width:60px;margin:0 auto;"></div></td>
            </tr>
        `;
    }

    /* ═══════════════════════════════════════════════════════════
       7. RECHERCHE ET FILTRAGE DU TABLEAU
    ═══════════════════════════════════════════════════════════ */
    function applyFilter() {
        const query = (filterInput ? filterInput.value : '').toLowerCase().trim();

        if (!query) {
            renderTable(studentCompetences);
            return;
        }

        const filtered = studentCompetences.filter((c) => {
            const nomMatch = c.nom && c.nom.toLowerCase().includes(query);
            const catMatch = c.categorie && c.categorie.toLowerCase().includes(query);
            return nomMatch || catMatch;
        });

        renderTable(filtered);
    }

    if (filterInput) {
        filterInput.addEventListener('input', applyFilter);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadStudentCompetences);
    }

    /* ═══════════════════════════════════════════════════════════
       8. STATISTIQUES HERO ET COMPTEURS
    ═══════════════════════════════════════════════════════════ */
    function updateStatistics(list) {
        const total = list.length;
        const nonUtilisees = list.filter(c => (c.nbProjets || 0) === 0 && (c.nbExperiences || 0) === 0).length;

        if (competencesCount) competencesCount.textContent = `${total}`;
        if (statTotalCount) statTotalCount.textContent = `${total}`;
        if (statUnusedCount) statUnusedCount.textContent = `${nonUtilisees}`;
    }

    /* ═══════════════════════════════════════════════════════════
       9. QUICK TAGS DE CATÉGORIE
    ═══════════════════════════════════════════════════════════ */
    document.querySelectorAll('.quick-tag-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const cat = btn.getAttribute('data-cat');
            if (cat && catInput) {
                catInput.value = cat;
                catInput.focus();
            }
        });
    });

    /* ═══════════════════════════════════════════════════════════
       10. FEEDBACK ET ALERTES (TOASTS)
    ═══════════════════════════════════════════════════════════ */
    function showAlert(message, type = 'info') {
        if (!formAlert) return;
        const iconSvg = type === 'success'
            ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`
            : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;

        formAlert.className = `alert-box alert-box-${type}`;
        formAlert.innerHTML = `${iconSvg} <span>${message}</span>`;
        formAlert.style.display = 'flex';

        if (type === 'success') {
            setTimeout(() => {
                if (formAlert.classList.contains('alert-box-success')) {
                    hideAlert();
                }
            }, 5000);
        }
    }

    function showErrorAlert(message) {
        showAlert(message, 'danger');
    }

    function hideAlert() {
        if (formAlert) {
            formAlert.style.display = 'none';
            formAlert.innerHTML = '';
        }
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    // Burger mobile navbar
    const navbar = document.getElementById('navbar');
    const burger = document.getElementById('nav-burger');
    if (burger && navbar) {
        burger.addEventListener('click', () => {
            const isOpen = navbar.classList.toggle('menu-open');
            burger.classList.toggle('open', isOpen);
            burger.setAttribute('aria-expanded', String(isOpen));
        });
    }

    // Chargement initial
    loadStudentCompetences();
});
