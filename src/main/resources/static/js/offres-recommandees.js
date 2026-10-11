'use strict';

/**
 * Logique de la page des offres recommandées pour les étudiants.
 * Récupère les offres actives et détaille le calcul du score de matching en temps réel.
 */
document.addEventListener('DOMContentLoaded', async () => {
    const user = MatchingCVSession.read();
    if (!user || user.role !== 'ETUDIANT') {
        window.location.replace('/login.html');
        return;
    }

    // Initialiser les éléments utilisateur dans la navbar
    const navUserName = document.getElementById('nav-user-name');
    if (navUserName) {
        navUserName.textContent = `${user.prenom || ''} ${user.nom || ''}`.trim() || 'Mon Compte';
        navUserName.addEventListener('click', () => {
            window.location.href = '/dashboard-etudiant.html#dash-summary';
        });
    }

    const navLogout = document.getElementById('nav-logout');
    if (navLogout) {
        navLogout.addEventListener('click', () => MatchingCVSession.logout());
    }

    // Burger menu responsive
    const burger = document.getElementById('nav-burger');
    const navLinks = document.getElementById('nav-links');
    if (burger && navLinks) {
        burger.addEventListener('click', () => {
            const open = navLinks.classList.toggle('nav-open');
            burger.setAttribute('aria-expanded', String(open));
        });
    }

    // État local
    let offresRecommandees = [];
    let offreSelectionnee = null;
    let candidaturesMap = {};  // offreId → CandidatureDto

    const recoGrid = document.getElementById('reco-grid');
    const countOffres = document.getElementById('count-offres');

    const filtreDomaine = document.getElementById('filtre-domaine');
    const filtreLocalisation = document.getElementById('filtre-localisation');
    const filtreContrat = document.getElementById('filtre-contrat');
    const btnAppliquer = document.getElementById('btn-appliquer-filtres');
    const btnReset = document.getElementById('btn-reinitialiser-filtres');

    // Modal
    const modal = document.getElementById('score-modal');
    const modalClose = document.getElementById('modal-close');

    // Charger les offres
    async function chargerOffres() {
        if (!recoGrid) return;

        recoGrid.innerHTML = `
            <div class="empty-state" style="grid-column: 1 / -1;">
                <div class="empty-icon">
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                        <circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>
                    </svg>
                </div>
                <h3>Calcul des recommandations en direct...</h3>
                <p>Évaluation des correspondances avec votre profil en cours.</p>
            </div>
        `;
        if (countOffres) countOffres.textContent = 'Calcul...';

        const params = new URLSearchParams();
        if (filtreDomaine && filtreDomaine.value.trim()) {
            params.append('domaine', filtreDomaine.value.trim());
        }
        if (filtreLocalisation && filtreLocalisation.value.trim()) {
            params.append('localisation', filtreLocalisation.value.trim());
        }
        if (filtreContrat && filtreContrat.value.trim()) {
            params.append('typeContrat', filtreContrat.value.trim());
        }

        try {
            const url = `/api/etudiants/${user.id}/offres-recommandees?${params.toString()}`;
            const res = await fetch(url);
            if (!res.ok) {
                throw new Error('Erreur lors de la récupération des offres recommandées');
            }
            offresRecommandees = await res.json();
            afficherOffres(offresRecommandees);
        } catch (err) {
            console.error(err);
            recoGrid.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <div class="empty-icon" style="background:#FEE2E2;color:#DC2626;">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                            <circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>
                        </svg>
                    </div>
                    <h3>Impossible de charger les offres recommandées</h3>
                    <p>${escapeHtml(err.message || 'Une erreur est survenue.')}</p>
                </div>
            `;
            if (countOffres) countOffres.textContent = 'Erreur';
        }
    }

    function afficherOffres(offres) {
        if (countOffres) {
            countOffres.textContent = `${offres.length} offre${offres.length > 1 ? 's' : ''}`;
        }

        if (!offres || offres.length === 0) {
            recoGrid.innerHTML = `
                <div class="empty-state" style="grid-column: 1 / -1;">
                    <div class="empty-icon">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5">
                            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
                        </svg>
                    </div>
                    <h3>Aucune offre ne correspond à vos critères</h3>
                    <p>Essayez d'élargir vos filtres de recherche ou consultez toutes les offres actives.</p>
                </div>
            `;
            return;
        }

        recoGrid.innerHTML = offres.map(offre => {
            const scoreDisp = offre.scoreDisponible && offre.score !== null;
            const scoreVal = scoreDisp ? Math.round(offre.score) : null;
            let scoreClass = 'na';
            if (scoreDisp) {
                if (scoreVal >= 70) scoreClass = 'high';
                else if (scoreVal >= 50) scoreClass = 'med';
                else scoreClass = 'low';
            }

            // Aperçu des compétences
            const compList = offre.competences || [];
            const detailComp = (offre.detailScore && offre.detailScore.competences) ? offre.detailScore.competences : [];

            const chipsHtml = compList.slice(0, 6).map(c => {
                const matchInfo = detailComp.find(dc => dc.competenceId === c.competenceId || dc.nom === c.nom);
                let cls = 'missing';
                let icon = '';

                if (c.statut === 'EN_ATTENTE') {
                    cls = 'pending';
                    icon = '&#9203; ';
                } else if (matchInfo && matchInfo.niveau > 0) {
                    cls = c.typeExigence === 'ATOUT' ? 'atout-matched' : 'matched';
                    icon = '&#10003; ';
                }

                const exigLabel = c.typeExigence === 'ATOUT' ? ' (Atout)' : '';
                return `<span class="skill-badge ${cls}" title="${c.typeExigence}">${icon}${escapeHtml(c.nom)}${exigLabel}</span>`;
            }).join('');

            const remaining = compList.length > 6 ? `<span class="skill-badge missing">+${compList.length - 6} autre${compList.length - 6 > 1 ? 's' : ''}</span>` : '';

            const dateStr = offre.datePublication ? formaterDate(offre.datePublication) : 'Récemment';

            return `
                <div class="reco-card" data-id="${offre.id}">
                    <div>
                        <div class="reco-card-top">
                            <div class="reco-card-info">
                                <h3 class="reco-card-title">${escapeHtml(offre.titre)}</h3>
                                <div class="reco-card-company">
                                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/><path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2"/></svg>
                                    ${escapeHtml(offre.entreprise || 'Entreprise')}
                                </div>
                            </div>
                            <div class="score-circle ${scoreClass}" title="${scoreDisp ? 'Score de matching CV' : 'Score indisponible pour cette offre'}">
                                <span class="score-val">${scoreDisp ? `${scoreVal}%` : 'N/D'}</span>
                                <span class="score-lbl">${scoreDisp ? 'Match' : 'Offre'}</span>
                            </div>
                        </div>

                        <div class="reco-card-meta">
                            ${offre.domaine ? `<span class="meta-pill domaine"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/></svg>${escapeHtml(offre.domaine)}</span>` : ''}
                            ${offre.typeContrat ? `<span class="meta-pill contrat"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/></svg>${escapeHtml(offre.typeContrat)}</span>` : ''}
                            ${offre.localisation ? `<span class="meta-pill"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>${escapeHtml(offre.localisation)}</span>` : ''}
                        </div>

                        <div class="reco-card-desc">
                            ${escapeHtml(offre.description || 'Aucune description fournie.')}
                        </div>

                        ${compList.length > 0 ? `
                        <div class="reco-skills-box">
                            <div class="skills-box-title">
                                <span>Compétences recherchées</span>
                                <span>${compList.length} requise(s) / atout(s)</span>
                            </div>
                            <div class="skills-chips">
                                ${chipsHtml}
                                ${remaining}
                            </div>
                        </div>` : ''}
                    </div>

                    <div class="reco-card-foot">
                        <span class="reco-pub-date">Publié le ${dateStr}</span>
                        <div style="display:flex;gap:.5rem;align-items:center;">
                            ${renderPostulerBouton(offre)}
                            <button class="btn-view-detail" data-action="detail" data-id="${offre.id}">
                                <span>Voir l'analyse</span>
                                <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="9 18 15 12 9 6"/></svg>
                            </button>
                        </div>
                    </div>
                </div>
            `;
        }).join('');

        // Attacher les clics sur les boutons d'analyse
        recoGrid.querySelectorAll('[data-action="detail"]').forEach(btn => {
            btn.addEventListener('click', () => {
                const id = Number(btn.getAttribute('data-id'));
                ouvrirDetailScore(id);
            });
        });

        // Attacher les clics sur les boutons Postuler
        recoGrid.querySelectorAll('[data-action="postuler"]').forEach(btn => {
            btn.addEventListener('click', async () => {
                const offreId = Number(btn.getAttribute('data-offre-id'));
                await postuler(offreId, btn);
            });
        });
    }

    // Ouvrir le modal d'explication détaillée du score
    async function ouvrirDetailScore(offreId) {
        const offre = offresRecommandees.find(o => o.id === offreId);
        if (!offre) return;

        offreSelectionnee = offre;

        // Éléments du modal
        document.getElementById('modal-offre-titre').textContent = offre.titre;
        document.getElementById('modal-offre-entreprise').textContent = `${offre.entreprise || 'Entreprise'} • ${offre.localisation || ''} • ${offre.typeContrat || ''}`;
        document.getElementById('modal-offre-description').textContent = offre.description || 'Aucune description disponible.';

        // Vérifier si nous avons déjà le détail ou le charger
        let detail = offre.detailScore;
        if (!detail || !detail.competences) {
            try {
                const res = await fetch(`/api/etudiants/${user.id}/offres/${offreId}/score`);
                if (res.ok) {
                    detail = await res.json();
                }
            } catch (e) {
                console.error(e);
            }
        }

        afficherScoreDetailDansModal(detail, offre);
        modal.classList.add('open');
    }

    function afficherScoreDetailDansModal(detail, offre) {
        const modalScoreVal = document.getElementById('modal-score-val');
        const modalScoreBadge = document.getElementById('modal-score-badge');
        const modalScoreTitre = document.getElementById('modal-score-titre');
        const valPartRequises = document.getElementById('val-part-requises');
        const valPartAtouts = document.getElementById('val-part-atouts');
        const pillPartAtouts = document.getElementById('pill-part-atouts');
        const modalStatusDesc = document.getElementById('modal-score-status-desc');
        const tbody = document.getElementById('modal-comp-tbody');

        modalScoreBadge.className = 'ssb-big-badge';

        if (!detail || !detail.scoreDisponible || detail.score === null) {
            modalScoreVal.textContent = 'N/D';
            modalScoreBadge.classList.add('na');
            modalScoreTitre.textContent = 'Score indisponible';
            valPartRequises.textContent = 'Non calculable';
            valPartAtouts.textContent = 'Non calculable';
            pillPartAtouts.style.display = 'inline-block';
            modalStatusDesc.textContent = detail ? (detail.message || "L'offre ne contient aucune compétence requise validée.") : 'Calcul indisponible';
        } else {
            const scoreArrondi = Math.round(detail.score);
            modalScoreVal.textContent = `${scoreArrondi}%`;

            if (scoreArrondi >= 70) modalScoreBadge.classList.add('high');
            else if (scoreArrondi >= 50) modalScoreBadge.classList.add('med');
            else modalScoreBadge.classList.add('low');

            modalScoreTitre.textContent = scoreArrondi >= 70 ? 'Excellente affinité avec ce poste' :
                                         (scoreArrondi >= 50 ? 'Profil pertinent pour ce poste' : 'Correspondance partielle');

            const reqMax = detail.partRequisesMax || 80;
            valPartRequises.textContent = `${detail.scoreRequises != null ? detail.scoreRequises.toFixed(1) : 0} / ${reqMax}`;

            if (detail.partAtoutsMax && detail.partAtoutsMax > 0) {
                pillPartAtouts.style.display = 'inline-block';
                valPartAtouts.textContent = `${detail.scoreAtouts != null ? detail.scoreAtouts.toFixed(1) : 0} / ${detail.partAtoutsMax}`;
            } else {
                pillPartAtouts.style.display = 'none';
            }

            modalStatusDesc.textContent = detail.message || 'Score calculé à la demande selon les compétences validées.';
        }

        // Lignes du tableau
        const comps = (detail && detail.competences) ? detail.competences : [];
        if (comps.length === 0) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" style="text-align:center;color:var(--text-mid);padding:2rem;">
                        Aucune exigence de compétence spécifiée pour cette offre.
                    </td>
                </tr>
            `;
            return;
        }

        tbody.innerHTML = comps.map(c => {
            const isRequise = c.typeExigence === 'REQUISE';
            const typeBadge = `<span class="type-badge ${isRequise ? 'requise' : 'atout'}">${isRequise ? 'Requise' : 'Atout'}</span>`;

            // Statut validation
            let statutWarn = '';
            if (c.statut === 'EN_ATTENTE') {
                statutWarn = ' <span style="color:#B45309;font-size:.72rem;font-weight:700;">(En attente validation)</span>';
            } else if (c.statut === 'REJETEE') {
                statutWarn = ' <span style="color:#DC2626;font-size:.72rem;font-weight:700;">(Rejetée)</span>';
            }

            // Badge de prise en compte
            let compteeBadge;
            if (c.statut !== 'VALIDEE') {
                compteeBadge = `<span class="comptee-badge warn">&#9888; Ignorée</span>`;
            } else if (c.comptee) {
                compteeBadge = `<span class="comptee-badge yes">&#10003; Comptabilisée</span>`;
            } else {
                compteeBadge = `<span class="comptee-badge no">&#8722; Non comptée</span>`;
            }

            // Niveau N
            const niveauBadge = `<span class="niveau-pill">${(c.niveau != null ? c.niveau.toFixed(2) : '0.00')}</span>`;

            // Provenance
            let provIcon = '';
            if (c.provenance === 'EXPERIENCE') {
                provIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><rect x="2" y="7" width="20" height="14" rx="2"/></svg>';
            } else if (c.provenance === 'PROJET') {
                provIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>';
            } else if (c.provenance === 'DECLARATIF') {
                provIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><circle cx="12" cy="12" r="10"/></svg>';
            } else {
                provIcon = '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>';
            }

            const provTag = `<span class="provenance-tag">${provIcon} ${escapeHtml(c.provenanceLabel || c.provenance || 'Absente')}</span>`;

            return `
                <tr>
                    <td><strong>${escapeHtml(c.nom)}</strong>${statutWarn}</td>
                    <td>${typeBadge}</td>
                    <td>${provTag}</td>
                    <td>${niveauBadge}</td>
                    <td>${compteeBadge}</td>
                    <td style="color:var(--text-mid);line-height:1.45;">${escapeHtml(c.raison || '')}</td>
                </tr>
            `;
        }).join('');
    }

    // Fermeture du modal
    if (modalClose) {
        modalClose.addEventListener('click', () => modal.classList.remove('open'));
    }
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) modal.classList.remove('open');
        });
    }
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape' && modal && modal.classList.contains('open')) {
            modal.classList.remove('open');
        }
    });

    // Actions des filtres
    if (btnAppliquer) {
        btnAppliquer.addEventListener('click', () => chargerOffres());
    }
    [filtreDomaine, filtreLocalisation, filtreContrat].forEach(input => {
        if (input) {
            input.addEventListener('keydown', (e) => {
                if (e.key === 'Enter') chargerOffres();
            });
            if (input.tagName === 'SELECT') {
                input.addEventListener('change', () => chargerOffres());
            }
        }
    });

    if (btnReset) {
        btnReset.addEventListener('click', () => {
            if (filtreDomaine) filtreDomaine.value = '';
            if (filtreLocalisation) filtreLocalisation.value = '';
            if (filtreContrat) filtreContrat.value = '';
            chargerOffres();
        });
    }

    // ── Candidatures : chargement initial et helpers ────────────────

    async function chargerCandidatures() {
        try {
            const res = await fetch(`/api/etudiants/${user.id}/candidatures`);
            if (!res.ok) return;
            const liste = await res.json();
            candidaturesMap = {};
            liste.forEach(c => { candidaturesMap[c.offreId] = c; });
        } catch (_) { /* silencieux */ }
    }

    function renderPostulerBouton(offre) {
        const cand = candidaturesMap[offre.id];
        if (cand) {
            const labels = { ENVOYEE: '&#128338; Envoyée', VUE: '&#128065; Vue', ACCEPTEE: '&#10003; Acceptée', REFUSEE: '&#10007; Refusée' };
            const colors = { ENVOYEE: '#2563EB', VUE: '#2E8B8B', ACCEPTEE: '#15803D', REFUSEE: '#DC2626' };
            const bg     = { ENVOYEE: '#EFF6FF', VUE: '#E0F5F5', ACCEPTEE: '#DCFCE7', REFUSEE: '#FEF2F2' };
            const s = cand.statut;
            return `<span style="display:inline-flex;align-items:center;gap:.35rem;padding:.42rem .9rem;border-radius:100px;font-size:.76rem;font-weight:700;background:${bg[s]};color:${colors[s]};border:1.5px solid ${colors[s]}40;">${labels[s] || s}</span>`;
        }
        // offre ACTIVE uniquement → sinon pas de bouton
        if (offre.statut !== 'ACTIVE') return '';
        return `<button class="btn-view-detail" style="background:linear-gradient(135deg,#1E3A5F,#2E8B8B);color:#fff;border-color:transparent;" data-action="postuler" data-offre-id="${offre.id}">&#9654; Postuler</button>`;
    }

    async function postuler(offreId, btn) {
        btn.disabled = true;
        btn.textContent = 'Envoi...';
        try {
            const res = await fetch(`/api/candidatures?etudiantId=${user.id}&offreId=${offreId}`, { method: 'POST' });
            const data = await res.json();
            if (res.status === 201) {
                candidaturesMap[offreId] = data;
                afficherOffres(offresRecommandees);  // re-render
                showToast('Candidature envoyée avec succès !', 'ok');
            } else {
                showToast(data.message || 'Erreur lors de la candidature.', 'err');
                btn.disabled = false;
                btn.textContent = '&#9654; Postuler';
            }
        } catch (e) {
            showToast('Erreur réseau. Veuillez réessayer.', 'err');
            btn.disabled = false;
        }
    }

    function showToast(msg, type) {
        const t = document.getElementById('reco-toast');
        if (!t) return;
        t.className = `toast ${type} show`;
        t.textContent = msg;
        clearTimeout(t._timer);
        t._timer = setTimeout(() => t.classList.remove('show'), 4000);
    }

    function formaterDate(d) {
        try {
            const dt = new Date(d);
            return dt.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
        } catch (_) {
            return d;
        }
    }

    function escapeHtml(s) {
        if (!s) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // Chargement initial
    await chargerCandidatures();
    chargerOffres();
});
