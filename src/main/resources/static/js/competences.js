/**
 * competences.js — Gestion du référentiel des compétences
 * Matching CV-Offres (GI3, ENSA Tétouan)
 *
 * Fonctionnalités :
 * 1. Chargement initial de la liste via GET /api/competences
 * 2. Création d'une compétence via POST /api/competences avec validation
 * 3. Rafraîchissement automatique de la vue et compteurs
 * 4. Recherche et filtrage en temps réel
 * 5. Gestion de la navbar mobile
 */

'use strict';

document.addEventListener('DOMContentLoaded', () => {
    // Éléments du DOM
    const form = document.getElementById('competence-form');
    const nomInput = document.getElementById('competence-nom');
    const catInput = document.getElementById('competence-categorie');
    const submitBtn = document.getElementById('btn-submit');
    const formAlert = document.getElementById('form-alert');

    const table = document.getElementById('competences-table');
    const tbody = document.getElementById('competences-tbody');
    const emptyState = document.getElementById('empty-state');
    const emptyStateTitle = document.getElementById('empty-state-title');
    const emptyStateText = document.getElementById('empty-state-text');

    const competencesCount = document.getElementById('competences-count');
    const statTotalCount = document.getElementById('stat-total-count');
    const statCatCount = document.getElementById('stat-cat-count');

    const searchInput = document.getElementById('search-input');
    const refreshBtn = document.getElementById('btn-refresh');

    // État local des compétences chargées
    let competencesList = [];

    /* ═══════════════════════════════════════════════════════════
       1. CHARGEMENT DES COMPÉTENCES (GET /api/competences)
    ═══════════════════════════════════════════════════════════ */
    async function loadCompetences() {
        if (refreshBtn) refreshBtn.classList.add('spinning');
        showLoadingState();

        try {
            const response = await fetch('/api/competences', {
                headers: { 'Accept': 'application/json' },
                cache: 'no-cache'
            });

            if (!response.ok) {
                throw new Error(`Erreur HTTP: ${response.status}`);
            }

            competencesList = await response.json();
            updateStatistics(competencesList);
            applyFilter();
        } catch (error) {
            console.error('[Competences] Erreur de chargement:', error);
            showErrorAlert('Impossible de charger les compétences. Vérifiez que le serveur est démarré.');
            renderTable([]);
        } finally {
            if (refreshBtn) {
                setTimeout(() => refreshBtn.classList.remove('spinning'), 400);
            }
        }
    }

    /* ═══════════════════════════════════════════════════════════
       2. CRÉATION D'UNE COMPÉTENCE (POST /api/competences)
    ═══════════════════════════════════════════════════════════ */
    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        hideAlert();

        const nom = nomInput.value.trim();
        const categorie = catInput.value.trim();

        // Validation côté client
        if (!nom) {
            showAlert('Le nom de la compétence est obligatoire.', 'danger');
            nomInput.focus();
            return;
        }

        // Préparation du payload JSON
        const payload = {
            nom: nom,
            categorie: categorie ? categorie : null
        };

        setSubmitting(true);

        try {
            const response = await fetch('/api/competences', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(payload)
            });

            const data = await response.json().catch(() => null);

            if (response.status === 201 && data) {
                showAlert(`La comp\u00e9tence \u00ab ${escapeHtml(data.nom)} \u00bb a \u00e9t\u00e9 ajout\u00e9e avec succ\u00e8s !`, 'success');
                form.reset();
                nomInput.focus();

                // Rafra\u00eechissement imm\u00e9diat de la liste sans recharger la page
                await loadCompetences();
            } else if (response.status === 409) {
                const message = (data && data.message)
                    ? data.message
                    : 'Cette comp\u00e9tence existe d\u00e9j\u00e0.';
                showAlert(message, 'danger');
                nomInput.focus();
            } else {
                const message = (data && data.message)
                    ? data.message
                    : `Erreur lors de la cr\u00e9ation (Code HTTP: ${response.status})`;
                showAlert(message, 'danger');
            }
        } catch (error) {
            console.error('[Competences] Erreur d\'envoi:', error);
            showAlert('Erreur réseau lors de la communication avec le serveur.', 'danger');
        } finally {
            setSubmitting(false);
        }
    });

    /* ═══════════════════════════════════════════════════════════
       3. AFFICHAGE ET RENDU DU TABLEAU
    ═══════════════════════════════════════════════════════════ */
    function renderTable(items) {
        tbody.innerHTML = '';

        if (!items || items.length === 0) {
            table.closest('.table-responsive').style.display = 'none';
            emptyState.style.display = 'flex';

            if (competencesList.length > 0 && searchInput.value.trim() !== '') {
                emptyStateTitle.textContent = 'Aucun résultat trouvé';
                emptyStateText.textContent = `Aucune compétence ne correspond à votre recherche « ${escapeHtml(searchInput.value.trim())} ».`;
            } else {
                emptyStateTitle.textContent = 'Aucune compétence pour le moment';
                emptyStateText.textContent = 'Le référentiel est vide. Renseignez le formulaire à gauche pour ajouter votre première compétence.';
            }
            return;
        }

        table.closest('.table-responsive').style.display = 'block';
        emptyState.style.display = 'none';

        const fragment = document.createDocumentFragment();

        items.forEach((comp) => {
            const tr = document.createElement('tr');

            const categorieBadge = comp.categorie
                ? `<span class="badge-cat"><span class="badge-cat-dot"></span>${escapeHtml(comp.categorie)}</span>`
                : `<span style="color:var(--text-light);font-size:.84rem;">&mdash;</span>`;

            const statutLabel = comp.statut ? comp.statut : 'VALIDEE';
            const statutBadge = `<span class="badge-status badge-status-validee">
                <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>
                ${escapeHtml(statutLabel)}
            </span>`;

            tr.innerHTML = `
                <td class="competence-id">#${comp.id}</td>
                <td>
                    <div class="competence-name">
                        <span class="competence-name-icon" aria-hidden="true">&#9670;</span>
                        <strong>${escapeHtml(comp.nom)}</strong>
                    </div>
                </td>
                <td>${categorieBadge}</td>
                <td>${statutBadge}</td>
                <td style="text-align:center;">
                    <button type="button" class="btn-action-delete" data-id="${comp.id}" data-name="${escapeHtml(comp.nom)}" title="Supprimer cette comp\u00e9tence" aria-label="Supprimer ${escapeHtml(comp.nom)}">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                            <polyline points="3 6 5 6 21 6"></polyline>
                            <path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"></path>
                            <line x1="10" y1="11" x2="10" y2="17"></line>
                            <line x1="14" y1="11" x2="14" y2="17"></line>
                        </svg>
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
                <td><div class="skeleton-bar" style="width:30px;"></div></td>
                <td><div class="skeleton-bar" style="width:140px;"></div></td>
                <td><div class="skeleton-bar" style="width:100px;"></div></td>
                <td><div class="skeleton-bar" style="width:70px;"></div></td>
                <td><div class="skeleton-bar" style="width:32px;margin:0 auto;"></div></td>
            </tr>
            <tr class="skeleton-row">
                <td><div class="skeleton-bar" style="width:30px;"></div></td>
                <td><div class="skeleton-bar" style="width:180px;"></div></td>
                <td><div class="skeleton-bar" style="width:110px;"></div></td>
                <td><div class="skeleton-bar" style="width:70px;"></div></td>
                <td><div class="skeleton-bar" style="width:32px;margin:0 auto;"></div></td>
            </tr>
        `;
    }

    /* ═══════════════════════════════════════════════════════════
       4. RECHERCHE ET FILTRAGE EN TEMPS RÉEL
    ═══════════════════════════════════════════════════════════ */
    function applyFilter() {
        const query = (searchInput ? searchInput.value : '').toLowerCase().trim();

        if (!query) {
            renderTable(competencesList);
            return;
        }

        const filtered = competencesList.filter((c) => {
            const nomMatch = c.nom && c.nom.toLowerCase().includes(query);
            const catMatch = c.categorie && c.categorie.toLowerCase().includes(query);
            return nomMatch || catMatch;
        });

        renderTable(filtered);
    }

    if (searchInput) {
        searchInput.addEventListener('input', applyFilter);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadCompetences);
    }

    /* ═══════════════════════════════════════════════════════════
       4b. SUPPRESSION D'UNE COMPÉTENCE (DELETE /api/competences/:id)
    ═══════════════════════════════════════════════════════════ */
    if (tbody) {
        tbody.addEventListener('click', async (event) => {
            const deleteBtn = event.target.closest('.btn-action-delete');
            if (!deleteBtn) return;

            const id = deleteBtn.getAttribute('data-id');
            const nom = deleteBtn.getAttribute('data-name') || 'cette comp\u00e9tence';

            if (!confirm(`Voulez-vous vraiment supprimer la comp\u00e9tence « ${nom} » ?`)) {
                return;
            }

            deleteBtn.disabled = true;
            deleteBtn.style.opacity = '0.5';

            try {
                const response = await fetch(`/api/competences/${id}`, {
                    method: 'DELETE',
                    headers: { 'Accept': 'application/json' }
                });

                if (response.status === 204 || response.ok) {
                    showAlert(`La comp\u00e9tence « ${escapeHtml(nom)} » a \u00e9t\u00e9 supprim\u00e9e avec succ\u00e8s !`, 'success');
                    // Retirer de la liste locale
                    competencesList = competencesList.filter(c => String(c.id) !== String(id));
                    updateStatistics(competencesList);
                    applyFilter();
                } else {
                    const data = await response.json().catch(() => null);
                    const message = (data && data.message) ? data.message : `Impossible de supprimer la comp\u00e9tence (Code HTTP: ${response.status})`;
                    showAlert(message, 'danger');
                    deleteBtn.disabled = false;
                    deleteBtn.style.opacity = '1';
                }
            } catch (error) {
                console.error('[Competences] Erreur suppression:', error);
                showAlert('Erreur r\u00e9seau lors de la suppression de la comp\u00e9tence.', 'danger');
                deleteBtn.disabled = false;
                deleteBtn.style.opacity = '1';
            }
        });
    }

    /* ═══════════════════════════════════════════════════════════
       5. TAGS RAPIDES DE CATÉGORIE
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
       6. COMPTEURS ET STATISTIQUES
    ═══════════════════════════════════════════════════════════ */
    function updateStatistics(list) {
        const total = list.length;
        if (competencesCount) competencesCount.textContent = `${total}`;
        if (statTotalCount) statTotalCount.textContent = `${total}`;

        if (statCatCount) {
            const categories = new Set(
                list.map(c => c.categorie).filter(cat => cat && cat.trim() !== '')
            );
            statCatCount.textContent = `${categories.size}`;
        }
    }

    /* ═══════════════════════════════════════════════════════════
       7. FEEDBACK ET ALERTES
    ═══════════════════════════════════════════════════════════ */
    function showAlert(message, type = 'info') {
        if (!formAlert) return;
        const iconSvg = type === 'success'
            ? `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"/><polyline points="22 4 12 14.01 9 11.01"/></svg>`
            : `<svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`;

        formAlert.className = `alert-box alert-box-${type}`;
        formAlert.innerHTML = `${iconSvg} <span>${message}</span>`;
        formAlert.style.display = 'flex';

        // Auto-masquer les succès après 5 secondes
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

    function setSubmitting(isSubmitting) {
        if (!submitBtn) return;
        submitBtn.disabled = isSubmitting;
        const span = submitBtn.querySelector('span');
        if (span) {
            span.textContent = isSubmitting ? 'Enregistrement en cours...' : 'Enregistrer la compétence';
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

    /* ═══════════════════════════════════════════════════════════
       8. NAVBAR MOBILE (BURGER MENU)
    ═══════════════════════════════════════════════════════════ */
    const navbar = document.getElementById('navbar');
    const burger = document.getElementById('nav-burger');
    if (burger && navbar) {
        burger.addEventListener('click', () => {
            const isOpen = navbar.classList.toggle('menu-open');
            burger.classList.toggle('open', isOpen);
            burger.setAttribute('aria-expanded', String(isOpen));
        });
    }

    // Lancement du chargement initial
    loadCompetences();
});
