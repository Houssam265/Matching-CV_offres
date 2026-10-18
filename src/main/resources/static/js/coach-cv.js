/**
 * coach-cv.js — Module frontend du Coach CV
 * Matching CV-Offres | GI3 ENSA Tétouan
 */

'use strict';

window.CoachCV = (() => {

    function esc(s) {
        return String(s || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * Charge le résumé Coach d'un étudiant via l'API
     */
    async function loadOverview(studentId) {
        try {
            const res = await fetch(`/api/etudiants/${studentId}/coach`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (e) {
            console.error('[CoachCV] Erreur lors du chargement du résumé :', e);
            return null;
        }
    }

    /**
     * Charge les conseils spécifiques pour une offre
     */
    async function loadOfferAdvice(studentId, offerId) {
        try {
            const res = await fetch(`/api/etudiants/${studentId}/coach/offres/${offerId}`);
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            return await res.json();
        } catch (e) {
            console.error('[CoachCV] Erreur lors du chargement des conseils pour l\'offre :', e);
            return [];
        }
    }

    /**
     * Génère le HTML pour une carte de conseil individuelle
     */
    function renderTipCard(tip) {
        let typeBadgeClass = 'offre';
        let typeLabel = 'Offre ciblée';
        if (tip.type === 'VALORISATION') {
            typeBadgeClass = 'valorisation';
            typeLabel = 'Valorisation';
        } else if (tip.type === 'COMPETENCE_DEMANDEE') {
            typeBadgeClass = 'demande';
            typeLabel = 'Marché';
        } else if (tip.type === 'HYGIENE_PROFIL') {
            typeBadgeClass = 'hygiene';
            typeLabel = 'Hygiène';
        }

        const gainDisplay = (tip.gainPoints && tip.gainPoints > 0)
            ? `+${tip.gainPoints.toFixed(1)} pts`
            : 'Recommandé';

        let targetPill = '';
        if (tip.offreTitre) {
            targetPill = `<span class="coach-tip-target"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/></svg> ${esc(tip.offreTitre)}</span>`;
        }

        return `
            <div class="coach-tip-card">
                <div>
                    <div class="coach-tip-top">
                        <span class="coach-tip-type-badge ${typeBadgeClass}">${typeLabel}</span>
                        <span class="coach-gain-badge">${gainDisplay}</span>
                    </div>
                    <div class="coach-tip-content" style="margin-top:.75rem;">
                        <h4 class="coach-tip-title">${esc(tip.titre)}</h4>
                        <p class="coach-tip-desc">${esc(tip.description)}</p>
                        ${targetPill}
                    </div>
                </div>
                <div>
                    <a href="${esc(tip.actionUrl)}" class="coach-tip-action">
                        <span>${esc(tip.actionLabel || 'Agir maintenant')}</span>
                        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                    </a>
                </div>
            </div>
        `;
    }

    /**
     * Rend la carte Coach CV complète dans un conteneur du Dashboard
     */
    function renderDashboardCard(containerEl, resume) {
        if (!containerEl || !resume) return;

        const completion = resume.scoreCompletionProfil || 0;
        const maxGain = (resume.gainPotentielMax && resume.gainPotentielMax > 0)
            ? `+${resume.gainPotentielMax.toFixed(1)} pts`
            : 'Optimisé';

        const topTips = resume.topConseils || [];
        const allTips = resume.tousConseils || [];
        const hasExtraTips = allTips.length > topTips.length;

        containerEl.innerHTML = `
            <div class="coach-summary-card" id="coach-cv-card">
                <div class="coach-card-header">
                    <div class="coach-header-left">
                        <div class="coach-avatar-icon">
                            <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                                <circle cx="12" cy="12" r="10"></circle>
                                <polygon points="12 6 15 11 20 11 16 14 17 19 12 16 7 19 8 14 4 11 9 11 12 6"></polygon>
                            </svg>
                        </div>
                        <div class="coach-title-area">
                            <span class="coach-badge-pill">Coach CV &bull; Règles d'optimisation</span>
                            <h3 class="coach-title">Recommandations pour maximiser votre score</h3>
                            <p class="coach-subtitle">Actions concrètes et chiffrées pour booster vos correspondances avec les recruteurs.</p>
                        </div>
                    </div>
                    <div class="coach-metrics">
                        <div class="coach-metric-box">
                            <div class="coach-completion-circle" style="--completion: ${completion};">
                                <span>${completion}%</span>
                            </div>
                            <div class="coach-metric-info">
                                <span class="coach-metric-label">Complétion profil</span>
                                <span class="coach-metric-val">${completion >= 80 ? 'Très solide' : (completion >= 50 ? 'En cours' : 'À compléter')}</span>
                            </div>
                        </div>
                        <div class="coach-metric-box">
                            <div class="coach-metric-info">
                                <span class="coach-metric-label">Gain potentiel max</span>
                                <span class="coach-metric-val gain">${maxGain}</span>
                            </div>
                        </div>
                    </div>
                </div>

                <!-- Grille des 3 meilleurs conseils -->
                <div class="coach-tips-grid" id="coach-top-tips">
                    ${topTips.length > 0 ? topTips.map(renderTipCard).join('') : '<div style="color:var(--text-mid);padding:1rem;">Votre profil est parfaitement optimisé ! Aucun conseil critique pour le moment.</div>'}
                </div>

                <!-- Section dépliable pour tous les conseils -->
                <div class="coach-tips-grid" id="coach-all-tips" style="display:none;margin-top:1.25rem;">
                    ${allTips.map(renderTipCard).join('')}
                </div>

                <div class="coach-card-footer">
                    <div class="coach-footer-note">
                        <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="16" x2="12" y2="12"></line><line x1="12" y1="8" x2="12.01" y2="8"></line></svg>
                        <span>Chaque point de gain est calculé en temps réel selon les exigences des offres actives.</span>
                    </div>
                    <div style="display:flex;align-items:center;gap:.6rem;flex-wrap:wrap;">
                        <a href="/coach-cv.html" class="coach-btn-view-all" style="background:transparent;border-color:rgba(31,56,100,0.18);color:var(--navy-deep);">
                            <span>Ouvrir la page Coach</span>
                            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="7" y1="17" x2="17" y2="7"></line><polyline points="7 7 17 7 17 17"></polyline></svg>
                        </a>
                        ${hasExtraTips ? `
                            <button type="button" class="coach-btn-view-all" id="btn-toggle-all-coach">
                                <span id="btn-toggle-label">Voir tous les conseils (${allTips.length})</span>
                                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="6 9 12 15 18 9"></polyline></svg>
                            </button>
                        ` : ''}
                    </div>
                </div>
            </div>
        `;

        if (hasExtraTips) {
            const toggleBtn = containerEl.querySelector('#btn-toggle-all-coach');
            const topGrid = containerEl.querySelector('#coach-top-tips');
            const allGrid = containerEl.querySelector('#coach-all-tips');
            const labelSpan = containerEl.querySelector('#btn-toggle-label');

            let expanded = false;
            toggleBtn?.addEventListener('click', () => {
                expanded = !expanded;
                if (expanded) {
                    topGrid.style.display = 'none';
                    allGrid.style.display = 'grid';
                    labelSpan.textContent = 'Réduire aux 3 meilleurs';
                } else {
                    topGrid.style.display = 'grid';
                    allGrid.style.display = 'none';
                    labelSpan.textContent = `Voir tous les conseils (${allTips.length})`;
                }
            });
        }
    }

    /**
     * Rend le bloc "Comment améliorer ce score" dans le modal d'offre
     */
    async function renderOfferModalBlock(containerEl, studentId, offerId) {
        if (!containerEl || !studentId || !offerId) return;

        containerEl.innerHTML = `
            <div style="color:var(--text-mid);font-size:.85rem;padding:1rem;display:flex;align-items:center;gap:.5rem;">
                <span class="spinner-small" style="width:14px;height:14px;border:2px solid var(--border);border-top-color:var(--teal);border-radius:50%;display:inline-block;animation:spin 1s linear infinite;"></span>
                Calcul des gains potentiels par le Coach CV...
            </div>
        `;

        const conseils = await loadOfferAdvice(studentId, offerId);
        if (!conseils || conseils.length === 0) {
            containerEl.innerHTML = `
                <div style="background:#FFFFFF;border-radius:12px;padding:1rem;border:1px solid #E2E8F0;color:var(--text-mid);font-size:.84rem;">
                    <strong>Félicitations !</strong> Vous maîtrisez déjà toutes les compétences clés de ce poste au niveau maximal.
                </div>
            `;
            return;
        }

        const gainTotal = conseils.reduce((acc, c) => acc + (c.gainPoints || 0), 0);

        containerEl.innerHTML = `
            <div style="display:flex;flex-direction:column;gap:.75rem;">
                <div style="font-size:.84rem;color:var(--text-mid);margin-bottom:.25rem;">
                    En appliquant ces suggestions, vous pouvez gagner jusqu'à <strong style="color:#059669;">+${gainTotal.toFixed(1)} points</strong> sur cette offre :
                </div>
                ${conseils.map(c => `
                    <div class="coach-modal-item">
                        <div class="coach-modal-item-left">
                            <div class="coach-modal-item-title">${esc(c.titre)}</div>
                            <div class="coach-modal-item-desc">${esc(c.description)}</div>
                        </div>
                        <div class="coach-modal-item-right">
                            <span class="coach-gain-badge">+${(c.gainPoints || 0).toFixed(1)} pts</span>
                            <a href="${esc(c.actionUrl)}" class="coach-modal-btn">
                                <span>${esc(c.actionLabel || 'Agir')}</span>
                                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="5" y1="12" x2="19" y2="12"></line><polyline points="12 5 19 12 12 19"></polyline></svg>
                            </a>
                        </div>
                    </div>
                `).join('')}
            </div>
        `;
    }

    return {
        loadOverview,
        loadOfferAdvice,
        renderDashboardCard,
        renderOfferModalBlock
    };
})();
