'use strict';

/* ─── ÉTAT GLOBAL ─────────────────────────────────────────── */
const S = {
    id: null,
    etudiant: null,
    competences: [],
    projets: [],
    experiences: [],
    allComps: [],
    fpChips: [],
    feChips: []
};

/* ─── UTILITAIRES ─────────────────────────────────────────── */
const esc = s => String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const fmt = iso => {
    if (!iso) return 'En cours';
    try {
        const d = new Date(iso);
        return d.toLocaleDateString('fr-FR', { month: 'short', year: 'numeric' });
    } catch (_) {
        return iso;
    }
};

function alert$(id, msg, type = 'err') {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = msg;
    el.className = 'alert ' + type;
    setTimeout(() => {
        el.className = 'alert';
        el.textContent = '';
    }, 4500);
}

async function api(url, opts = {}) {
    const r = await fetch(url, {
        headers: { 'Content-Type': 'application/json', ...(opts.headers || {}) },
        ...opts
    });
    if (!r.ok) {
        let m = 'Erreur ' + r.status;
        try {
            const b = await r.json();
            m = b.message || m;
        } catch (_) {}
        throw new Error(m);
    }
    if (r.status === 204) return null;
    return r.json();
}

/* ─── GESTION DU DRAWER ───────────────────────────────────── */
function openDrawer() {
    const d = document.getElementById('drover');
    if (d) d.classList.add('open');
}

function closeDrawer() {
    const d = document.getElementById('drover');
    if (d) d.classList.remove('open');
}

window.toggleSec = function(id) {
    const el = document.getElementById(id);
    if (el) el.classList.toggle('open');
};

/* ─── CALCUL DES RELATIONS CROISÉES ───────────────────────── */
function getCompetenceRelations(compId) {
    const projs = (S.projets || []).filter(p => (p.competences || []).some(c => c.id === compId));
    const exps = (S.experiences || []).filter(e => (e.competences || []).some(c => c.id === compId));
    return { projs, exps, total: projs.length + exps.length };
}

/* ─── RENDU DU RÉSUMÉ SUR LE DASHBOARD ────────────────────── */
function renderDashboardSummary() {
    // 0. Recalculer les compétences depuis projets + expériences
    computeCompetencesFromContext();

    // 1. Stats KPI
    document.getElementById('sc').textContent = S.competences.length;
    document.getElementById('sp').textContent = S.projets.length;
    document.getElementById('se').textContent = S.experiences.length;

    document.getElementById('sum-cnt-c').textContent = S.competences.length;
    document.getElementById('sum-cnt-p').textContent = S.projets.length;
    document.getElementById('sum-cnt-e').textContent = S.experiences.length;

    // 2. Colonne Compétences (agrégées depuis projets + expériences)
    const cList = document.getElementById('sum-list-c');
    if (!S.competences.length) {
        cList.innerHTML = '<span class="r-chip-empty" style="padding:.5rem 0;">Aucune compétence liée pour le moment. Ajoutez un projet ou une expérience et liez-y des compétences.</span>';
    } else {
        cList.innerHTML = S.competences.map(c => {
            const rel = getCompetenceRelations(c.id);
            let relDesc = '';
            if (rel.total > 0) {
                const parts = [];
                if (rel.projs.length) parts.push(rel.projs.length + ' projet' + (rel.projs.length > 1 ? 's' : ''));
                if (rel.exps.length) parts.push(rel.exps.length + ' exp.' + (rel.exps.length > 1 ? 's' : ''));
                relDesc = '<span style="color:var(--teal);font-weight:600;"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align:-1px;margin-right:2px;"><polyline points="20 6 9 17 4 12"/></svg>Mobilisée dans : ' + parts.join(' &amp; ') + '</span>';
            } else {
                relDesc = '<span style="color:#94A3B8;font-style:italic;">Compétence autonome (non liée à un projet)</span>';
            }

            return `
            <div class="skill-item-row">
                <div class="skill-item-header">
                    <span class="skill-name">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" style="color:var(--teal);"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                        ${esc(c.nom)}
                    </span>
                    <span class="skill-badge-status ${c.statut === 'VALIDEE' ? 'val' : 'att'}">${c.statut === 'VALIDEE' ? 'Validée' : 'En attente'}</span>
                </div>
                ${c.categorie ? `<div style="font-size:.72rem;color:var(--text-light);">${esc(c.categorie)}</div>` : ''}
                <div class="skill-usage-line">${relDesc}</div>
            </div>`;
        }).join('');
    }

    // 3. Colonne Projets réalisés
    const pList = document.getElementById('sum-list-p');
    if (!S.projets.length) {
        pList.innerHTML = '<span class="r-chip-empty" style="padding:.5rem 0;">Aucun projet renseigné. Cliquez sur "Gérer" pour ajouter vos réalisations.</span>';
    } else {
        pList.innerHTML = S.projets.map(p => {
            const hasComps = p.competences && p.competences.length;
            const chipsHtml = hasComps
                ? p.competences.map(c => `
                    <span class="r-chip" title="${esc(c.categorie || '')}">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                        ${esc(c.nom)}
                    </span>
                `).join('')
                : '<span class="r-chip-empty">Aucune compétence liée à ce projet</span>';

            return `
            <div class="sum-card-item">
                <div class="sci-t">${esc(p.titre)}</div>
                ${p.description ? `<div class="sci-desc">${esc(p.description)}</div>` : ''}
                <div class="relation-box">
                    <div class="relation-label">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                        Compétences mobilisées :
                    </div>
                    <div class="relation-chips">${chipsHtml}</div>
                </div>
            </div>`;
        }).join('');
    }

    // 4. Colonne Expériences professionnelles
    const eList = document.getElementById('sum-list-e');
    if (!S.experiences.length) {
        eList.innerHTML = '<span class="r-chip-empty" style="padding:.5rem 0;">Aucune expérience renseignée. Cliquez sur "Gérer" pour ajouter vos stages/emplois.</span>';
    } else {
        eList.innerHTML = S.experiences.map(exp => {
            const hasComps = exp.competences && exp.competences.length;
            const chipsHtml = hasComps
                ? exp.competences.map(c => `
                    <span class="r-chip" title="${esc(c.categorie || '')}">
                        <svg width="10" height="10" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                        ${esc(c.nom)}
                    </span>
                `).join('')
                : '<span class="r-chip-empty">Aucune compétence liée à cette expérience</span>';

            let dDeb = exp.dateDebut;
            let dFin = exp.dateFin;
            if (dDeb && dFin && dFin < dDeb) {
                const tmp = dDeb; dDeb = dFin; dFin = tmp;
            }
            const dateStr = dFin ? `${fmt(dDeb)} &rarr; ${fmt(dFin)}` : `Depuis ${fmt(dDeb)} <span class="badge-ec">En cours</span>`;

            return `
            <div class="sum-card-item">
                <div class="sci-t">
                    <span>${esc(exp.poste)}</span>
                    ${!exp.dateFin ? '<span class="badge-ec">En cours</span>' : ''}
                </div>
                <div class="sci-meta">
                    <strong>${esc(exp.entreprise)}</strong> &bull; ${dateStr}
                </div>
                <div class="relation-box">
                    <div class="relation-label">
                        <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
                        Compétences appliquées :
                    </div>
                    <div class="relation-chips">${chipsHtml}</div>
                </div>
            </div>`;
        }).join('');
    }
}

/* ─── CALCUL DES COMPÉTENCES DEPUIS PROJETS + EXPÉRIENCES ─── */
function computeCompetencesFromContext() {
    const map = new Map();
    (S.projets || []).forEach(p =>
        (p.competences || []).forEach(c => { if (!map.has(c.id)) map.set(c.id, c); })
    );
    (S.experiences || []).forEach(e =>
        (e.competences || []).forEach(c => { if (!map.has(c.id)) map.set(c.id, c); })
    );
    S.competences = Array.from(map.values());
}

/* ─── RENDU DU DRAWER ─────────────────────────────────────── */
function renderDrawer() {
    // 1. Compétences (agrégées automatiquement)
    computeCompetencesFromContext();
    document.getElementById('cnt-c').textContent = S.competences.length;
    const dc = document.getElementById('dr-c');
    if (!S.competences.length) {
        dc.innerHTML = '<span class="din" style="font-style:italic;color:var(--text-light);">Aucune compétence liée pour le moment — ajoutez-en via vos projets ou expériences ci-dessous.</span>';
    } else {
        dc.innerHTML = S.competences.map(c => `
            <span class="ctag ${c.statut === 'EN_ATTENTE' ? 'pend' : ''}" title="${c.statut === 'EN_ATTENTE' ? 'En attente de validation' : 'Validée'}">
                ${c.statut === 'EN_ATTENTE' ? '&#9203;' : '&#10003;'} ${esc(c.nom)}
            </span>
        `).join('');
    }

    // 2. Projets
    document.getElementById('cnt-p').textContent = S.projets.length;
    const dp = document.getElementById('dr-p');
    if (!S.projets.length) {
        dp.innerHTML = '<div class="din" style="margin-bottom:.6rem;">Aucun projet pour le moment.</div>';
    } else {
        dp.innerHTML = S.projets.map(p => {
            const hasComps = p.competences && p.competences.length;
            const compChips = hasComps
                ? p.competences.map(c => `
                    <span class="dic">
                        ${esc(c.nom)}
                        <button class="dicd" onclick="unlinkProjComp(${p.id}, ${c.id})" title="Dissocier">&times;</button>
                    </span>
                `).join('')
                : '<span class="din">Aucune compétence liée à ce projet</span>';

            return `
            <div class="di" data-pid="${p.id}">
                <div class="dit">${esc(p.titre)}</div>
                ${p.description ? `<div class="dim">${esc(p.description)}</div>` : ''}
                <div class="dicl">Compétences mobilisées :</div>
                <div class="dics">${compChips}</div>
                
                <!-- Bloc pour associer une compétence directement à ce projet existant -->
                <div class="di-link-box">
                    <button class="btn-inline-link" onclick="toggleInlineLink('plink-${p.id}')">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        + Lier une compétence à ce projet
                    </button>
                    <div class="di-ac" id="plink-${p.id}" style="display:none;margin-top:.45rem;">
                        <input type="text" class="di-ai" placeholder="Rechercher ou proposer une compétence..." autocomplete="off" oninput="onInlineProjInput(${p.id}, this)" />
                        <div class="di-ad" id="pdrop-${p.id}"></div>
                    </div>
                </div>

                <button class="dide" onclick="delP(${p.id})" title="Supprimer ce projet">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg>
                </button>
            </div>`;
        }).join('');
    }

    // 3. Expériences
    document.getElementById('cnt-e').textContent = S.experiences.length;
    const de = document.getElementById('dr-e');
    if (!S.experiences.length) {
        de.innerHTML = '<div class="din" style="margin-bottom:.6rem;">Aucune expérience pour le moment.</div>';
    } else {
        de.innerHTML = S.experiences.map(exp => {
            const hasComps = exp.competences && exp.competences.length;
            const compChips = hasComps
                ? exp.competences.map(c => `
                    <span class="dic">
                        ${esc(c.nom)}
                        <button class="dicd" onclick="unlinkExpComp(${exp.id}, ${c.id})" title="Dissocier">&times;</button>
                    </span>
                `).join('')
                : '<span class="din">Aucune compétence liée à cette expérience</span>';

            let dDeb = exp.dateDebut;
            let dFin = exp.dateFin;
            if (dDeb && dFin && dFin < dDeb) {
                const tmp = dDeb; dDeb = dFin; dFin = tmp;
            }
            const dateStr = dFin ? `Du ${fmt(dDeb)} au ${fmt(dFin)}` : `Depuis ${fmt(dDeb)} <span class="badge-ec">En cours</span>`;

            return `
            <div class="di" data-eid="${exp.id}">
                <div class="dit">${esc(exp.poste)} &mdash; <span style="font-weight:600;color:var(--text-mid);">${esc(exp.entreprise)}</span></div>
                <div class="dim">${dateStr}</div>
                <div class="dicl">Compétences appliquées :</div>
                <div class="dics">${compChips}</div>

                <!-- Bloc pour associer une compétence directement à cette expérience existante -->
                <div class="di-link-box">
                    <button class="btn-inline-link" onclick="toggleInlineLink('elink-${exp.id}')">
                        <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                        + Lier une compétence à cette expérience
                    </button>
                    <div class="di-ac" id="elink-${exp.id}" style="display:none;margin-top:.45rem;">
                        <input type="text" class="di-ai" placeholder="Rechercher ou proposer une compétence..." autocomplete="off" oninput="onInlineExpInput(${exp.id}, this)" />
                        <div class="di-ad" id="edrop-${exp.id}"></div>
                    </div>
                </div>

                <button class="dide" onclick="delE(${exp.id})" title="Supprimer cette expérience">
                    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg>
                </button>
            </div>`;
        }).join('');
    }
}

/* ─── CHIPS FORMULAIRE PROJET / EXPÉRIENCE ────────────────── */
function renderFpChips() {
    document.getElementById('fp-chips').innerHTML = S.fpChips.map((c, i) => `
        <span class="schip">${esc(c.nom)}<button onclick="S.fpChips.splice(${i},1);renderFpChips()">&times;</button></span>
    `).join('');
}

function renderFeChips() {
    document.getElementById('fe-chips').innerHTML = S.feChips.map((c, i) => `
        <span class="schip">${esc(c.nom)}<button onclick="S.feChips.splice(${i},1);renderFeChips()">&times;</button></span>
    `).join('');
}

/* ─── TOGGLE INLINE LINK INPUTS ───────────────────────────── */
window.toggleInlineLink = function(id) {
    const el = document.getElementById(id);
    if (!el) return;
    const isVis = el.style.display !== 'none';
    el.style.display = isVis ? 'none' : 'block';
    if (!isVis) {
        const inp = el.querySelector('input');
        if (inp) inp.focus();
    }
};

/* ─── AUTOCOMPLETE INLINE PROJET ──────────────────────────── */
window.onInlineProjInput = function(projId, inputEl) {
    const drop = document.getElementById('pdrop-' + projId);
    if (!drop) return;
    const q = inputEl.value.trim().toLowerCase();
    if (!q) {
        drop.innerHTML = '';
        drop.classList.remove('open');
        return;
    }
    const proj = S.projets.find(p => p.id === projId);
    const existingCompIds = new Set((proj && proj.competences ? proj.competences : []).map(c => c.id));
    const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !existingCompIds.has(c.id)).slice(0, 7);

    let html = matches.map(c => `
        <div class="aitem" onclick="linkCompToProj(${projId}, {competenceId:${c.id}})">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            <strong>${esc(c.nom)}</strong>
            ${c.categorie ? `<span style="margin-left:auto;font-size:.7rem;color:var(--text-light);">${esc(c.categorie)}</span>` : ''}
        </div>
    `).join('');

    const raw = inputEl.value.trim();
    const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
    if (!exact && raw) {
        html += `
        <div class="aitem prop" onclick="linkCompToProj(${projId}, {nouvelleCompetence:'${esc(raw)}'})">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            + Proposer et lier &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;
        </div>`;
    }

    drop.innerHTML = html;
    drop.classList.add('open');
};

window.linkCompToProj = async function(projId, payload) {
    if (!S.id) return;
    try {
        const updatedProj = await api(`/api/etudiants/${S.id}/projets/${projId}/competences`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        const idx = S.projets.findIndex(p => p.id === projId);
        if (idx !== -1) S.projets[idx] = updatedProj;
        // Recomputer les compétences depuis les projets + expériences
        computeCompetencesFromContext();
        renderDrawer();
        renderDashboardSummary();
        alert$('al-p', 'Compétence liée au projet avec succès !', 'ok');
    } catch (e) {
        alert$('al-p', e.message);
    }
};

window.unlinkProjComp = async function(projId, compId) {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/projets/${projId}/competences/${compId}`, { method: 'DELETE' });
        const proj = S.projets.find(p => p.id === projId);
        if (proj && proj.competences) {
            proj.competences = proj.competences.filter(c => c.id !== compId);
        }
        renderDrawer();
        renderDashboardSummary();
        alert$('al-p', 'Compétence dissociée du projet.', 'ok');
    } catch (e) {
        alert$('al-p', e.message);
    }
};

/* ─── AUTOCOMPLETE INLINE EXPÉRIENCE ──────────────────────── */
window.onInlineExpInput = function(expId, inputEl) {
    const drop = document.getElementById('edrop-' + expId);
    if (!drop) return;
    const q = inputEl.value.trim().toLowerCase();
    if (!q) {
        drop.innerHTML = '';
        drop.classList.remove('open');
        return;
    }
    const exp = S.experiences.find(e => e.id === expId);
    const existingCompIds = new Set((exp && exp.competences ? exp.competences : []).map(c => c.id));
    const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !existingCompIds.has(c.id)).slice(0, 7);

    let html = matches.map(c => `
        <div class="aitem" onclick="linkCompToExp(${expId}, {competenceId:${c.id}})">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
            <strong>${esc(c.nom)}</strong>
            ${c.categorie ? `<span style="margin-left:auto;font-size:.7rem;color:var(--text-light);">${esc(c.categorie)}</span>` : ''}
        </div>
    `).join('');

    const raw = inputEl.value.trim();
    const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
    if (!exact && raw) {
        html += `
        <div class="aitem prop" onclick="linkCompToExp(${expId}, {nouvelleCompetence:'${esc(raw)}'})">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
            + Proposer et lier &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;
        </div>`;
    }

    drop.innerHTML = html;
    drop.classList.add('open');
};

window.linkCompToExp = async function(expId, payload) {
    if (!S.id) return;
    try {
        const updatedExp = await api(`/api/etudiants/${S.id}/experiences/${expId}/competences`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        const idx = S.experiences.findIndex(e => e.id === expId);
        if (idx !== -1) S.experiences[idx] = updatedExp;
        computeCompetencesFromContext();
        renderDrawer();
        renderDashboardSummary();
        alert$('al-e', 'Compétence liée à l\'expérience avec succès !', 'ok');
    } catch (e) {
        alert$('al-e', e.message);
    }
};

window.unlinkExpComp = async function(expId, compId) {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/experiences/${expId}/competences/${compId}`, { method: 'DELETE' });
        const exp = S.experiences.find(e => e.id === expId);
        if (exp && exp.competences) {
            exp.competences = exp.competences.filter(c => c.id !== compId);
        }
        renderDrawer();
        renderDashboardSummary();
        alert$('al-e', 'Compétence dissociée de l\'expérience.', 'ok');
    } catch (e) {
        alert$('al-e', e.message);
    }
};

/* ─── SUPPRESSIONS ────────────────────────────────────────── */

window.delP = async id => {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/projets/${id}`, { method: 'DELETE' });
        S.projets = S.projets.filter(p => p.id !== id);
        renderDrawer();
        renderDashboardSummary();
        alert$('al-p', 'Projet supprimé.', 'ok');
    } catch (e) {
        alert$('al-p', e.message);
    }
};

window.delE = async id => {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/experiences/${id}`, { method: 'DELETE' });
        S.experiences = S.experiences.filter(e => e.id !== id);
        renderDrawer();
        renderDashboardSummary();
        alert$('al-e', 'Expérience supprimée.', 'ok');
    } catch (e) {
        alert$('al-e', e.message);
    }
};

/* ─── AUTOCOMPLETE COMPOSANT GÉNÉRIQUE ────────────────────── */
function setupAutocomplete(inputId, dropId, usedIdsFn, onSelect) {
    const inp = document.getElementById(inputId);
    const drop = document.getElementById(dropId);
    if (!inp || !drop) return;

    const close = () => {
        drop.innerHTML = '';
        drop.classList.remove('open');
    };

    inp.addEventListener('input', () => {
        const q = inp.value.trim().toLowerCase();
        if (!q) {
            close();
            return;
        }
        const used = usedIdsFn();
        const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !used.has(c.id)).slice(0, 7);

        let html = matches.map(c => `
            <div class="aitem" data-id="${c.id}" data-nom="${esc(c.nom)}">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                <strong>${esc(c.nom)}</strong>
                ${c.categorie ? `<span style="margin-left:auto;font-size:.7rem;color:var(--text-light);">${esc(c.categorie)}</span>` : ''}
            </div>
        `).join('');

        const raw = inp.value.trim();
        const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
        if (!exact && raw) {
            html += `
            <div class="aitem prop" data-propose="${esc(raw)}">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
                + Ajouter &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;
            </div>`;
        }

        drop.innerHTML = html;
        drop.classList.add('open');
    });

    drop.addEventListener('click', e => {
        const item = e.target.closest('.aitem');
        if (!item) return;
        onSelect(item);
        inp.value = '';
        close();
    });

    document.addEventListener('click', e => {
        if (!inp.contains(e.target) && !drop.contains(e.target)) close();
    });
}

/* ─── CHARGEMENT DE TOUTES LES DONNÉES ────────────────────── */
async function loadAllData(id) {
    try {
        S.allComps = await api('/api/competences').catch(() => []);
    } catch (_) {}

    try {
        const e = await api('/api/etudiants/' + id);
        S.etudiant = e;
        const prenom = e.prenom || '';
        const nom = e.nom || '';
        const init = ((prenom.charAt(0) || '') + (nom.charAt(0) || '')).toUpperCase() || '?';

        // Hero & Navbar
        document.getElementById('user-prenom').textContent = prenom || 'Étudiant';
        const navU = document.getElementById('nav-user-name');
        if (navU) navU.textContent = prenom + ' ' + nom;

        // Profil Card
        document.getElementById('pc-av').textContent = init;
        document.getElementById('pc-nm').textContent = prenom + ' ' + nom;
        document.getElementById('pc-em').textContent = e.email || '';
        const roleLabel = e.role === 'ETUDIANT' ? 'Étudiant' : (e.role || 'Étudiant');
        
        let tagsHtml = `<span class="pct"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align:-1px;margin-right:2px;"><path d="M22 10v6M2 10l10-5 10 5-10 5z"/></svg>${esc(roleLabel)}</span>`;
        if (e.filiere) {
            tagsHtml += `<span class="pct"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align:-1px;margin-right:2px;"><path d="M12 2l10 5-10 5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/></svg>${esc(e.filiere)}</span>`;
        }
        if (e.etablissement) {
            tagsHtml += `<span class="pct"><svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" style="vertical-align:-1px;margin-right:2px;"><path d="M3 21h18M3 10h18M5 6l7-3 7 3M4 10v11M20 10v11M8 14v3M12 14v3M16 14v3"/></svg>${esc(e.etablissement)}</span>`;
        }
        tagsHtml += `<span class="pct" style="color:var(--green);border-color:rgba(22,163,74,.25);background:var(--green-pale);">Compte Actif</span>`;
        document.getElementById('pc-tags').innerHTML = tagsHtml;

        // Drawer Header & Inputs
        document.getElementById('dr-av').textContent = init;
        document.getElementById('dr-nm').textContent = prenom + ' ' + nom;
        document.getElementById('dr-em').textContent = e.email || '';
        if (document.getElementById('dr-prenom')) document.getElementById('dr-prenom').value = prenom;
        if (document.getElementById('dr-nom')) document.getElementById('dr-nom').value = nom;
        if (document.getElementById('dr-filiere')) document.getElementById('dr-filiere').value = e.filiere || '';
        if (document.getElementById('dr-etablissement')) document.getElementById('dr-etablissement').value = e.etablissement || '';
    } catch (_) {}

    try {
        const [projs, exps] = await Promise.all([
            api(`/api/etudiants/${id}/projets`).catch(() => []),
            api(`/api/etudiants/${id}/experiences`).catch(() => [])
        ]);
        S.projets = projs;
        S.experiences = exps;
        // Les compétences sont déduites automatiquement depuis projets + expériences
        computeCompetencesFromContext();
    } catch (_) {}

    renderDashboardSummary();
    renderDrawer();
}

/* ─── INITIALISATION AU CHARGEMENT DE LA PAGE ─────────────── */
document.addEventListener('DOMContentLoaded', async () => {
    const user = MatchingCVSession.require('ETUDIANT');
    if (!user) return;
    // 1. Navbar mobile burger & déconnexion
    const nb = document.getElementById('navbar'), bg = document.getElementById('nav-burger');
    if (nb) window.addEventListener('scroll', () => nb.classList.toggle('scrolled', window.scrollY > 20), { passive: true });
    if (bg) bg.addEventListener('click', () => {
        const o = nb.classList.toggle('menu-open');
        bg.classList.toggle('open', o);
        bg.setAttribute('aria-expanded', String(o));
    });

    const doLogout = () => {
        MatchingCVSession.logout();
    };
    document.getElementById('nav-logout')?.addEventListener('click', doLogout);
    document.getElementById('logout-hero')?.addEventListener('click', doLogout);

    // 2. Déterminer l'étudiant connecté
    const studentId = user.id;
    S.id = studentId;

    // 3. Charger les données
    await loadAllData(studentId);

    // 4. Drawer boutons d'ouverture / fermeture
    document.getElementById('btn-dr')?.addEventListener('click', openDrawer);
    document.getElementById('btn-summary-edit')?.addEventListener('click', openDrawer);
    document.getElementById('nav-link-profil')?.addEventListener('click', e => {
        e.preventDefault();
        openDrawer();
    });
    document.getElementById('nav-user-name')?.addEventListener('click', () => {
        openDrawer();
    });
    document.getElementById('pc-av')?.addEventListener('click', () => {
        openDrawer();
    });
    document.getElementById('dr-close')?.addEventListener('click', closeDrawer);
    document.getElementById('drover')?.addEventListener('click', e => {
        if (e.target === document.getElementById('drover')) closeDrawer();
    });
    document.addEventListener('keydown', e => {
        if (e.key === 'Escape') closeDrawer();
    });

    // 4a. Ouvrir le volet si demandé dans l'URL (?open=profil ou ?open=drawer ou #profil)
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('open') === 'profil' || urlParams.get('open') === 'drawer' || window.location.hash === '#profil' || window.location.hash === '#dash-summary') {
        openDrawer();
    }

    // 4b. Mise à jour des informations personnelles & formation
    document.getElementById('dr-save-info')?.addEventListener('click', async () => {
        if (!S.id) return;
        const prenom = document.getElementById('dr-prenom')?.value.trim();
        const nom = document.getElementById('dr-nom')?.value.trim();
        const filiere = document.getElementById('dr-filiere')?.value.trim() || null;
        const etablissement = document.getElementById('dr-etablissement')?.value.trim() || null;

        if (!prenom || !nom) {
            alert$('al-info', 'Prénom et nom sont obligatoires.');
            return;
        }

        try {
            const updated = await api(`/api/etudiants/${S.id}`, {
                method: 'PUT',
                body: JSON.stringify({ prenom, nom, filiere, etablissement })
            });
            S.etudiant = updated;
            await loadAllData(S.id);
            alert$('al-info', 'Informations mises à jour avec succès !', 'ok');
        } catch (e) {
            alert$('al-info', e.message);
        }
    });

    // 5. (supprimé) — la déclaration manuelle de compétences est remplacée par la liaison via projets/expériences

    // 6. Formulaire d'ajout de projet dans le drawer
    const btnAp = document.getElementById('btn-ap');
    const fp = document.getElementById('fp');
    btnAp?.addEventListener('click', () => {
        fp.classList.toggle('show');
        btnAp.style.display = fp.classList.contains('show') ? 'none' : 'flex';
    });
    document.getElementById('fp-ca')?.addEventListener('click', () => {
        fp.classList.remove('show');
        if (btnAp) btnAp.style.display = 'flex';
        S.fpChips.length = 0;
        renderFpChips();
        document.getElementById('fp-t').value = '';
        document.getElementById('fp-d').value = '';
    });

    setupAutocomplete('fp-ci', 'fp-cd', () => new Set(S.fpChips.map(c => c.competenceId).filter(Boolean)), item => {
        if (item.dataset.id) {
            S.fpChips.push({ competenceId: parseInt(item.dataset.id, 10), nom: item.dataset.nom });
        } else if (item.dataset.propose) {
            S.fpChips.push({ nouvelleCompetence: item.dataset.propose, nom: item.dataset.propose });
        }
        renderFpChips();
    });

    document.getElementById('fp-sv')?.addEventListener('click', async () => {
        if (!S.id) return;
        const titre = document.getElementById('fp-t').value.trim();
        if (!titre) {
            alert$('al-p', 'Le titre du projet est obligatoire.');
            return;
        }
        const desc = document.getElementById('fp-d').value.trim();
        try {
            const created = await api(`/api/etudiants/${S.id}/projets`, {
                method: 'POST',
                body: JSON.stringify({ titre, description: desc, competences: S.fpChips })
            });
            S.projets.push(created);
            // Recalculer compétences depuis projets + expériences
            computeCompetencesFromContext();
            fp.classList.remove('show');
            if (btnAp) btnAp.style.display = 'flex';
            S.fpChips.length = 0;
            renderFpChips();
            document.getElementById('fp-t').value = '';
            document.getElementById('fp-d').value = '';
            renderDrawer();
            renderDashboardSummary();
            alert$('al-p', 'Projet ajouté avec succès !', 'ok');
        } catch (e) {
            alert$('al-p', e.message);
        }
    });

    // 7. Formulaire d'ajout d'expérience dans le drawer
    const btnAe = document.getElementById('btn-ae');
    const fe = document.getElementById('fe');
    btnAe?.addEventListener('click', () => {
        fe.classList.toggle('show');
        btnAe.style.display = fe.classList.contains('show') ? 'none' : 'flex';
    });
    const feDb = document.getElementById('fe-db');
    const feFi = document.getElementById('fe-fi');
    const feDateErr = document.getElementById('fe-date-err');

    function resetFeDates() {
        if (feDb) { feDb.value = ''; feDb.removeAttribute('max'); feDb.style.borderColor = ''; }
        if (feFi) { feFi.value = ''; feFi.removeAttribute('min'); feFi.style.borderColor = ''; }
        if (feDateErr) { feDateErr.textContent = ''; feDateErr.style.display = 'none'; }
    }

    function validateFeDates() {
        if (!feDb || !feFi) return true;
        const deb = feDb.value;
        const fin = feFi.value;

        if (deb) feFi.min = deb;
        else feFi.removeAttribute('min');

        if (fin) feDb.max = fin;
        else feDb.removeAttribute('max');

        if (deb && fin && fin < deb) {
            if (feDateErr) {
                feDateErr.textContent = 'La date de fin ne peut pas être antérieure à la date de début.';
                feDateErr.style.display = 'block';
            }
            feFi.style.borderColor = '#ef4444';
            feDb.style.borderColor = '#ef4444';
            return false;
        } else {
            if (feDateErr) {
                feDateErr.textContent = '';
                feDateErr.style.display = 'none';
            }
            feFi.style.borderColor = '';
            feDb.style.borderColor = '';
            return true;
        }
    }

    feDb?.addEventListener('change', validateFeDates);
    feDb?.addEventListener('input', validateFeDates);
    feFi?.addEventListener('change', validateFeDates);
    feFi?.addEventListener('input', validateFeDates);

    document.getElementById('fe-ca')?.addEventListener('click', () => {
        fe.classList.remove('show');
        if (btnAe) btnAe.style.display = 'flex';
        S.feChips.length = 0;
        renderFeChips();
        document.getElementById('fe-po').value = '';
        document.getElementById('fe-en').value = '';
        resetFeDates();
    });

    setupAutocomplete('fe-ci', 'fe-cd', () => new Set(S.feChips.map(c => c.competenceId).filter(Boolean)), item => {
        if (item.dataset.id) {
            S.feChips.push({ competenceId: parseInt(item.dataset.id, 10), nom: item.dataset.nom });
        } else if (item.dataset.propose) {
            S.feChips.push({ nouvelleCompetence: item.dataset.propose, nom: item.dataset.propose });
        }
        renderFeChips();
    });

    document.getElementById('fe-sv')?.addEventListener('click', async () => {
        if (!S.id) return;
        const poste = document.getElementById('fe-po').value.trim();
        const entr = document.getElementById('fe-en').value.trim();
        const deb = document.getElementById('fe-db').value;
        const fin = document.getElementById('fe-fi').value || null;

        if (!poste) { alert$('al-e', 'Le poste est obligatoire.'); return; }
        if (!entr) { alert$('al-e', 'L\'entreprise est obligatoire.'); return; }
        if (!deb) { alert$('al-e', 'La date de début est obligatoire.'); return; }
        if (fin && fin < deb) {
            alert$('al-e', 'La date de fin ne peut pas être antérieure à la date de début.');
            if (feDateErr) {
                feDateErr.textContent = 'La date de fin ne peut pas être antérieure à la date de début.';
                feDateErr.style.display = 'block';
            }
            if (feFi) feFi.focus();
            return;
        }

        try {
            const created = await api(`/api/etudiants/${S.id}/experiences`, {
                method: 'POST',
                body: JSON.stringify({ poste, entreprise: entr, dateDebut: deb, dateFin: fin, competences: S.feChips })
            });
            S.experiences.push(created);
            computeCompetencesFromContext();
            fe.classList.remove('show');
            if (btnAe) btnAe.style.display = 'flex';
            S.feChips.length = 0;
            renderFeChips();
            document.getElementById('fe-po').value = '';
            document.getElementById('fe-en').value = '';
            resetFeDates();
            renderDrawer();
            renderDashboardSummary();
            alert$('al-e', 'Expérience ajoutée avec succès !', 'ok');
        } catch (e) {
            alert$('al-e', e.message);
        }
    });
});
