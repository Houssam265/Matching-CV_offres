'use strict';

const S = {
    id: null,
    competences: [],
    allComps: [],
    projets: [],
    experiences: [],
    mpSel: [],
    meSel: []
};

const esc = s => String(s || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const fmt = iso => iso ? new Date(iso).toLocaleDateString('fr-FR', { year: 'numeric', month: 'short' }) : 'En cours';

function alert$(id, msg, type = 'err') {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = msg;
    el.className = 'm-alert ' + type;
    setTimeout(() => {
        el.className = 'm-alert';
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

function openM(id) {
    const m = document.getElementById(id);
    if (m) {
        m.classList.add('open');
        m.removeAttribute('aria-hidden');
    }
}

function closeM(id) {
    const m = document.getElementById(id);
    if (m) {
        m.classList.remove('open');
        m.setAttribute('aria-hidden', 'true');
    }
}

document.querySelectorAll('.modal-overlay').forEach(m => {
    m.addEventListener('click', e => { if (e.target === m) closeM(m.id); });
});
document.querySelectorAll('[data-close]').forEach(btn => {
    btn.addEventListener('click', () => closeM(btn.dataset.close));
});
document.addEventListener('keydown', e => {
    if (e.key === 'Escape') document.querySelectorAll('.modal-overlay.open').forEach(m => closeM(m.id));
});

function makeAc(inputId, dropId, usedFn, onSelect) {
    const inp = document.getElementById(inputId), drop = document.getElementById(dropId);
    if (!inp || !drop) return;
    const close = () => { drop.innerHTML = ''; drop.classList.remove('open'); };

    inp.addEventListener('input', () => {
        const q = inp.value.trim().toLowerCase();
        if (!q) { close(); return; }
        const used = usedFn();
        const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !used.has(c.id)).slice(0, 7);
        let html = matches.map(c => `
            <div class="ac-item" data-id="${c.id}" data-nom="${esc(c.nom)}">
                <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>
                ${esc(c.nom)}
                ${c.categorie ? '<span style="margin-left:auto;font-size:.7rem;color:#94A3B8">' + esc(c.categorie) + '</span>' : ''}
            </div>
        `).join('');

        const raw = inp.value.trim();
        const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
        if (!exact && raw) {
            html += `<div class="ac-item prop" data-propose="${esc(raw)}"><svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>+ Ajouter &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;</div>`;
        }
        drop.innerHTML = html;
        drop.classList.add('open');
    });

    drop.addEventListener('click', e => {
        const item = e.target.closest('.ac-item');
        if (!item) return;
        onSelect(item);
        inp.value = '';
        close();
    });

    document.addEventListener('click', e => {
        if (!inp.contains(e.target) && !drop.contains(e.target)) close();
    });
}

function getCompUsage(compId) {
    const projs = (S.projets || []).filter(p => (p.competences || []).some(c => c.id === compId));
    const exps = (S.experiences || []).filter(e => (e.competences || []).some(c => c.id === compId));
    return { projs, exps, total: projs.length + exps.length };
}

function renderC() {
    document.getElementById('cnt-c').textContent = S.competences.length;
    const el = document.getElementById('disp-c');
    if (!S.competences.length) {
        el.innerHTML = '<span class="empty-hint">Aucune compétence déclarée pour le moment.</span>';
        return;
    }
    el.innerHTML = S.competences.map(c => {
        const u = getCompUsage(c.id);
        const useText = u.total > 0
            ? ` (liée à ${u.projs.length} projet${u.projs.length > 1 ? 's' : ''}, ${u.exps.length} exp.)`
            : '';
        return `<span class="t-chip${c.statut === 'EN_ATTENTE' ? ' pending' : ''}" title="${c.statut === 'EN_ATTENTE' ? 'En attente' : 'Validée'}${useText}">
            ${c.statut === 'EN_ATTENTE' ? '&#9203;' : '&#10003;'} ${esc(c.nom)}
            ${u.total > 0 ? `<small style="opacity:.8;font-size:.7rem;margin-left:3px;">(${u.total})</small>` : ''}
        </span>`;
    }).join('');
}

function renderP() {
    document.getElementById('cnt-p').textContent = S.projets.length;
    const el = document.getElementById('disp-p');
    if (!S.projets.length) {
        el.innerHTML = '<span class="empty-hint">Aucun projet renseigné pour le moment.</span>';
        return;
    }
    el.innerHTML = S.projets.map(p => `
        <div class="item-card">
            <div class="ic-title">${esc(p.titre)}</div>
            ${p.description ? '<div class="ic-desc">' + esc(p.description) + '</div>' : ''}
            <div style="font-size:.72rem;font-weight:700;color:var(--text-light);text-transform:uppercase;margin-top:.4rem;margin-bottom:.25rem;">Compétences mobilisées :</div>
            ${p.competences && p.competences.length
                ? '<div class="ic-tags">' + p.competences.map(c => '<span class="ic-tag">' + esc(c.nom) + '</span>').join('') + '</div>'
                : '<span class="empty-hint" style="font-size:.75rem;">Aucune compétence liée à ce projet</span>'
            }
        </div>
    `).join('');
}

function renderE() {
    document.getElementById('cnt-e').textContent = S.experiences.length;
    const el = document.getElementById('disp-e');
    if (!S.experiences.length) {
        el.innerHTML = '<span class="empty-hint">Aucune expérience renseignée pour le moment.</span>';
        return;
    }
    el.innerHTML = S.experiences.map(exp => `
        <div class="item-card">
            <div class="ic-title">${esc(exp.poste)}</div>
            <div class="ic-meta">
                <strong>${esc(exp.entreprise)}</strong>
                <span style="color:#CBD5E1">|</span>
                ${esc(fmt(exp.dateDebut))} &rarr; ${esc(fmt(exp.dateFin))}
                ${!exp.dateFin ? '<span class="badge-ec">En cours</span>' : ''}
            </div>
            <div style="font-size:.72rem;font-weight:700;color:var(--text-light);text-transform:uppercase;margin-top:.4rem;margin-bottom:.25rem;">Compétences appliquées :</div>
            ${exp.competences && exp.competences.length
                ? '<div class="ic-tags">' + exp.competences.map(c => '<span class="ic-tag">' + esc(c.nom) + '</span>').join('') + '</div>'
                : '<span class="empty-hint" style="font-size:.75rem;">Aucune compétence liée à cette expérience</span>'
            }
        </div>
    `).join('');
}

function renderMC() {
    const el = document.getElementById('ml-comp');
    if (!S.competences.length) {
        el.innerHTML = '<div class="empty-hint" style="padding:.75rem 0">Aucune compétence.</div>';
        return;
    }
    el.innerHTML = S.competences.map(c => {
        const u = getCompUsage(c.id);
        const relText = u.total > 0
            ? `Mobilisée dans : ${u.projs.length} projet(s), ${u.exps.length} exp.`
            : 'Non rattachée à un projet ou expérience';

        return `
        <div class="edit-row">
            <div class="er-info">
                <div class="er-title">${esc(c.nom)}</div>
                <div class="er-sub" style="color:${c.statut === 'EN_ATTENTE' ? '#B45309' : '#16A34A'}">
                    ${c.statut === 'EN_ATTENTE' ? 'En attente' : 'Validée'}${c.categorie ? ' - ' + esc(c.categorie) : ''}
                </div>
                <div style="font-size:.72rem;color:#64748B;margin-top:.2rem;">${relText}</div>
            </div>
            <button class="btn-del" onclick="delC(${c.id})" title="Supprimer"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg></button>
        </div>`;
    }).join('');
}

function renderMP() {
    const el = document.getElementById('ml-proj');
    if (!S.projets.length) {
        el.innerHTML = '<div class="empty-hint" style="padding:.75rem 0">Aucun projet.</div>';
        return;
    }
    el.innerHTML = S.projets.map(p => `
        <div class="edit-row" style="flex-direction:column;align-items:stretch;">
            <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:.5rem;">
                <div class="er-info">
                    <div class="er-title">${esc(p.titre)}</div>
                    ${p.description ? '<div class="er-sub">' + esc(p.description) + '</div>' : ''}
                </div>
                <button class="btn-del" onclick="delP(${p.id})" title="Supprimer ce projet"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg></button>
            </div>
            <div style="margin-top:.5rem;padding-top:.4rem;border-top:1px dashed #E2E8F0;">
                <div style="font-size:.7rem;font-weight:700;color:#64748B;text-transform:uppercase;">Compétences mobilisées :</div>
                <div class="er-tags" style="margin-top:.3rem;">
                    ${p.competences && p.competences.length
                        ? p.competences.map(c => `
                            <span class="er-tag" style="display:inline-flex;align-items:center;gap:.25rem;">
                                ${esc(c.nom)}
                                <button onclick="unlinkProjCompModal(${p.id}, ${c.id})" style="background:none;border:none;cursor:pointer;color:inherit;font-size:.85rem;line-height:1;padding:0;">&times;</button>
                            </span>
                        `).join('')
                        : '<span style="font-size:.75rem;color:#94A3B8;font-style:italic;">Aucune compétence liée</span>'
                    }
                </div>
                <div style="margin-top:.4rem;">
                    <div class="ac-wrap">
                        <input type="text" class="fi" style="padding:.35rem .65rem;font-size:.78rem;" placeholder="+ Lier une compétence à ce projet..." oninput="onModalProjAcInput(${p.id}, this)" autocomplete="off" />
                        <div class="ac-drop" id="m-pdrop-${p.id}"></div>
                    </div>
                </div>
            </div>
        </div>
    `).join('');
}

function renderME() {
    const el = document.getElementById('ml-exp');
    if (!S.experiences.length) {
        el.innerHTML = '<div class="empty-hint" style="padding:.75rem 0">Aucune expérience.</div>';
        return;
    }
    el.innerHTML = S.experiences.map(exp => `
        <div class="edit-row" style="flex-direction:column;align-items:stretch;">
            <div style="display:flex;align-items:flex-start;justify-content:space-between;gap:.5rem;">
                <div class="er-info">
                    <div class="er-title">${esc(exp.poste)} - <span style="color:#475569;font-weight:500">${esc(exp.entreprise)}</span></div>
                    <div class="er-sub">${esc(fmt(exp.dateDebut))} &rarr; ${esc(fmt(exp.dateFin))}${!exp.dateFin ? ' (En cours)' : ''}</div>
                </div>
                <button class="btn-del" onclick="delE(${exp.id})" title="Supprimer cette expérience"><svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14H6L5 6"/><path d="M10 11v6"/><path d="M14 11v6"/><path d="M9 6V4h6v2"/></svg></button>
            </div>
            <div style="margin-top:.5rem;padding-top:.4rem;border-top:1px dashed #E2E8F0;">
                <div style="font-size:.7rem;font-weight:700;color:#64748B;text-transform:uppercase;">Compétences appliquées :</div>
                <div class="er-tags" style="margin-top:.3rem;">
                    ${exp.competences && exp.competences.length
                        ? exp.competences.map(c => `
                            <span class="er-tag" style="display:inline-flex;align-items:center;gap:.25rem;">
                                ${esc(c.nom)}
                                <button onclick="unlinkExpCompModal(${exp.id}, ${c.id})" style="background:none;border:none;cursor:pointer;color:inherit;font-size:.85rem;line-height:1;padding:0;">&times;</button>
                            </span>
                        `).join('')
                        : '<span style="font-size:.75rem;color:#94A3B8;font-style:italic;">Aucune compétence liée</span>'
                    }
                </div>
                <div style="margin-top:.4rem;">
                    <div class="ac-wrap">
                        <input type="text" class="fi" style="padding:.35rem .65rem;font-size:.78rem;" placeholder="+ Lier une compétence à cette expérience..." oninput="onModalExpAcInput(${exp.id}, this)" autocomplete="off" />
                        <div class="ac-drop" id="m-edrop-${exp.id}"></div>
                    </div>
                </div>
            </div>
        </div>
    `).join('');
}

/* Modal Inlines for Project and Experience */
window.onModalProjAcInput = function(projId, inputEl) {
    const drop = document.getElementById('m-pdrop-' + projId);
    if (!drop) return;
    const q = inputEl.value.trim().toLowerCase();
    if (!q) { drop.innerHTML = ''; drop.classList.remove('open'); return; }
    const proj = S.projets.find(p => p.id === projId);
    const existing = new Set((proj && proj.competences ? proj.competences : []).map(c => c.id));
    const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !existing.has(c.id)).slice(0, 6);

    let html = matches.map(c => `
        <div class="ac-item" onclick="linkProjCompModal(${projId}, {competenceId:${c.id}})">
            ${esc(c.nom)} ${c.categorie ? '<span style="margin-left:auto;font-size:.7rem;color:#94A3B8">' + esc(c.categorie) + '</span>' : ''}
        </div>
    `).join('');

    const raw = inputEl.value.trim();
    const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
    if (!exact && raw) {
        html += `<div class="ac-item prop" onclick="linkProjCompModal(${projId}, {nouvelleCompetence:'${esc(raw)}'})">+ Lier &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;</div>`;
    }
    drop.innerHTML = html;
    drop.classList.add('open');
};

window.linkProjCompModal = async function(projId, payload) {
    if (!S.id) return;
    try {
        const updated = await api(`/api/etudiants/${S.id}/projets/${projId}/competences`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        const idx = S.projets.findIndex(p => p.id === projId);
        if (idx !== -1) S.projets[idx] = updated;
        S.competences = await api(`/api/etudiants/${S.id}/competences`).catch(() => S.competences);
        renderP(); renderMP(); renderC(); renderMC();
        alert$('a-proj', 'Compétence liée au projet.', 'ok');
    } catch (e) {
        alert$('a-proj', e.message);
    }
};

window.unlinkProjCompModal = async function(projId, compId) {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/projets/${projId}/competences/${compId}`, { method: 'DELETE' });
        const proj = S.projets.find(p => p.id === projId);
        if (proj && proj.competences) {
            proj.competences = proj.competences.filter(c => c.id !== compId);
        }
        renderP(); renderMP(); renderC(); renderMC();
        alert$('a-proj', 'Compétence dissociée.', 'ok');
    } catch (e) {
        alert$('a-proj', e.message);
    }
};

window.onModalExpAcInput = function(expId, inputEl) {
    const drop = document.getElementById('m-edrop-' + expId);
    if (!drop) return;
    const q = inputEl.value.trim().toLowerCase();
    if (!q) { drop.innerHTML = ''; drop.classList.remove('open'); return; }
    const exp = S.experiences.find(e => e.id === expId);
    const existing = new Set((exp && exp.competences ? exp.competences : []).map(c => c.id));
    const matches = S.allComps.filter(c => c.nom.toLowerCase().includes(q) && !existing.has(c.id)).slice(0, 6);

    let html = matches.map(c => `
        <div class="ac-item" onclick="linkExpCompModal(${expId}, {competenceId:${c.id}})">
            ${esc(c.nom)} ${c.categorie ? '<span style="margin-left:auto;font-size:.7rem;color:#94A3B8">' + esc(c.categorie) + '</span>' : ''}
        </div>
    `).join('');

    const raw = inputEl.value.trim();
    const exact = S.allComps.find(c => c.nom.toLowerCase() === raw.toLowerCase());
    if (!exact && raw) {
        html += `<div class="ac-item prop" onclick="linkExpCompModal(${expId}, {nouvelleCompetence:'${esc(raw)}'})">+ Lier &laquo;&nbsp;${esc(raw)}&nbsp;&raquo;</div>`;
    }
    drop.innerHTML = html;
    drop.classList.add('open');
};

window.linkExpCompModal = async function(expId, payload) {
    if (!S.id) return;
    try {
        const updated = await api(`/api/etudiants/${S.id}/experiences/${expId}/competences`, {
            method: 'POST',
            body: JSON.stringify(payload)
        });
        const idx = S.experiences.findIndex(e => e.id === expId);
        if (idx !== -1) S.experiences[idx] = updated;
        S.competences = await api(`/api/etudiants/${S.id}/competences`).catch(() => S.competences);
        renderE(); renderME(); renderC(); renderMC();
        alert$('a-exp', 'Compétence liée à l\'expérience.', 'ok');
    } catch (e) {
        alert$('a-exp', e.message);
    }
};

window.unlinkExpCompModal = async function(expId, compId) {
    if (!S.id) return;
    try {
        await api(`/api/etudiants/${S.id}/experiences/${expId}/competences/${compId}`, { method: 'DELETE' });
        const exp = S.experiences.find(e => e.id === expId);
        if (exp && exp.competences) {
            exp.competences = exp.competences.filter(c => c.id !== compId);
        }
        renderE(); renderME(); renderC(); renderMC();
        alert$('a-exp', 'Compétence dissociée.', 'ok');
    } catch (e) {
        alert$('a-exp', e.message);
    }
};

function renderMpChips() {
    document.getElementById('mp-chips').innerHTML = S.mpSel.map((c, i) => `
        <span class="s-chip">${esc(c.nom)}<button onclick="S.mpSel.splice(${i},1);renderMpChips()">&times;</button></span>
    `).join('');
}

function renderMeChips() {
    document.getElementById('me-chips').innerHTML = S.meSel.map((c, i) => `
        <span class="s-chip">${esc(c.nom)}<button onclick="S.meSel.splice(${i},1);renderMeChips()">&times;</button></span>
    `).join('');
}

window.delC = async id => {
    if (!S.id) return;
    try {
        await api('/api/etudiants/' + S.id + '/competences/' + id, { method: 'DELETE' });
        S.competences = S.competences.filter(c => c.id !== id);
        renderC(); renderMC();
    } catch (e) {
        alert$('a-comp', e.message);
    }
};

window.delP = async id => {
    if (!S.id) return;
    try {
        await api('/api/etudiants/' + S.id + '/projets/' + id, { method: 'DELETE' });
        S.projets = S.projets.filter(p => p.id !== id);
        renderP(); renderMP(); renderC();
    } catch (e) {
        alert$('a-proj', e.message);
    }
};

window.delE = async id => {
    if (!S.id) return;
    try {
        await api('/api/etudiants/' + S.id + '/experiences/' + id, { method: 'DELETE' });
        S.experiences = S.experiences.filter(e => e.id !== id);
        renderE(); renderME(); renderC();
    } catch (e) {
        alert$('a-exp', e.message);
    }
};

async function loadAll(id) {
    try {
        S.allComps = await api('/api/competences');
    } catch (_) {}

    try {
        const e = await api('/api/etudiants/' + id);
        const init = ((e.prenom || '').charAt(0) + (e.nom || '').charAt(0)).toUpperCase() || '?';
        document.getElementById('profil-avatar').textContent = init;
        document.getElementById('profil-name').textContent = (e.prenom || '') + ' ' + (e.nom || '');
        document.getElementById('profil-email').textContent = e.email || '';
        const navU = document.getElementById('nav-user-name');
        if (navU) navU.textContent = (e.prenom || '') + ' ' + (e.nom || '');
    } catch (_) {}

    try {
        [S.competences, S.projets, S.experiences] = await Promise.all([
            api('/api/etudiants/' + id + '/competences').catch(() => []),
            api('/api/etudiants/' + id + '/projets').catch(() => []),
            api('/api/etudiants/' + id + '/experiences').catch(() => [])
        ]);
    } catch (_) {}

    renderC(); renderP(); renderE();
}

async function loadEtudiants() {
    try {
        const list = await api('/api/etudiants');
        const sel = document.getElementById('etudiant-select');
        sel.innerHTML = '<option value="">Choisir un étudiant</option>';
        list.forEach(e => {
            const o = document.createElement('option');
            o.value = e.id;
            o.textContent = (e.prenom || '') + ' ' + (e.nom || '') + ' <' + (e.email || '') + '>';
            sel.appendChild(o);
        });
    } catch (_) {}
}

function resetPForm() {
    ['mp-titre', 'mp-desc', 'mp-csearch'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    S.mpSel.length = 0;
    renderMpChips();
}

function resetEForm() {
    ['me-poste', 'me-entr', 'me-deb', 'me-fin', 'me-csearch'].forEach(id => {
        const el = document.getElementById(id);
        if (el) el.value = '';
    });
    S.meSel.length = 0;
    renderMeChips();
}

document.addEventListener('DOMContentLoaded', () => {
    if (!MatchingCVSession.require('ETUDIANT')) return;
    const nb = document.getElementById('navbar'), bg = document.getElementById('nav-burger');
    if (nb) window.addEventListener('scroll', () => nb.classList.toggle('scrolled', window.scrollY > 20), { passive: true });
    if (bg) bg.addEventListener('click', () => {
        const o = nb.classList.toggle('menu-open');
        bg.classList.toggle('open', o);
        bg.setAttribute('aria-expanded', String(o));
    });

    document.getElementById('nav-logout')?.addEventListener('click', () => {
        MatchingCVSession.logout();
    });

    document.getElementById('etudiant-select').addEventListener('change', function() {
        const id = parseInt(this.value, 10);
        if (!id) return;
        S.id = id;
        loadAll(id);
    });

    const urlId = parseInt(new URLSearchParams(window.location.search).get('id'), 10);
    if (urlId) {
        S.id = urlId;
        loadAll(urlId);
        loadEtudiants().then(() => {
            const s = document.getElementById('etudiant-select');
            if (s) s.value = String(urlId);
        });
    } else {
        try {
            const u = JSON.parse(localStorage.getItem('matchingcv_user') || 'null');
            if (u && u.id) {
                S.id = u.id;
                loadEtudiants().then(() => {
                    const s = document.getElementById('etudiant-select');
                    if (s) s.value = String(u.id);
                    loadAll(u.id);
                });
            } else {
                loadEtudiants();
            }
        } catch (_) {
            loadEtudiants();
        }
    }

    document.getElementById('btn-ec').addEventListener('click', () => {
        renderMC();
        openM('m-comp');
        setTimeout(() => document.getElementById('mc-search').focus(), 300);
    });
    document.getElementById('btn-ep').addEventListener('click', () => {
        renderMP();
        resetPForm();
        openM('m-proj');
    });
    document.getElementById('btn-ee').addEventListener('click', () => {
        renderME();
        resetEForm();
        openM('m-exp');
    });

    makeAc('mc-search', 'mc-drop', () => new Set(S.competences.map(c => c.id)), async item => {
        if (!S.id) return;
        let payload;
        if (item.dataset.id) payload = { competenceId: parseInt(item.dataset.id, 10) };
        else if (item.dataset.propose) payload = { nouvelleCompetence: item.dataset.propose };
        else return;

        try {
            const saved = await api('/api/etudiants/' + S.id + '/competences', {
                method: 'POST',
                body: JSON.stringify(payload)
            });
            if (!S.competences.find(c => c.id === saved.id)) S.competences.push(saved);
            renderC(); renderMC();
            alert$('a-comp', 'Compétence ajoutée.', 'ok');
        } catch (e) {
            alert$('a-comp', e.message);
        }
    });

    makeAc('mp-csearch', 'mp-cdrop', () => new Set(S.mpSel.map(c => c.competenceId).filter(Boolean)), item => {
        if (item.dataset.id) S.mpSel.push({ competenceId: parseInt(item.dataset.id, 10), nom: item.dataset.nom });
        else if (item.dataset.propose) S.mpSel.push({ nouvelleCompetence: item.dataset.propose, nom: item.dataset.propose });
        renderMpChips();
    });

    makeAc('me-csearch', 'me-cdrop', () => new Set(S.meSel.map(c => c.competenceId).filter(Boolean)), item => {
        if (item.dataset.id) S.meSel.push({ competenceId: parseInt(item.dataset.id, 10), nom: item.dataset.nom });
        else if (item.dataset.propose) S.meSel.push({ nouvelleCompetence: item.dataset.propose, nom: item.dataset.propose });
        renderMeChips();
    });

    document.getElementById('mp-save').addEventListener('click', async () => {
        if (!S.id) { alert$('a-proj', 'Sélectionnez d\'abord un étudiant.', 'info'); return; }
        const titre = document.getElementById('mp-titre').value.trim();
        if (!titre) { alert$('a-proj', 'Le titre est obligatoire.'); return; }
        const desc = document.getElementById('mp-desc').value.trim();
        try {
            const p = await api('/api/etudiants/' + S.id + '/projets', {
                method: 'POST',
                body: JSON.stringify({ titre, description: desc, competences: S.mpSel })
            });
            S.projets.push(p);
            S.competences = await api('/api/etudiants/' + S.id + '/competences').catch(() => S.competences);
            renderP(); renderMP(); renderC();
            resetPForm();
            alert$('a-proj', 'Projet ajouté.', 'ok');
        } catch (e) {
            alert$('a-proj', e.message);
        }
    });

    document.getElementById('me-save').addEventListener('click', async () => {
        if (!S.id) { alert$('a-exp', 'Sélectionnez d\'abord un étudiant.', 'info'); return; }
        const poste = document.getElementById('me-poste').value.trim();
        const entr = document.getElementById('me-entr').value.trim();
        const deb = document.getElementById('me-deb').value;
        const fin = document.getElementById('me-fin').value || null;
        if (!poste) { alert$('a-exp', 'Le poste est obligatoire.'); return; }
        if (!entr) { alert$('a-exp', 'Entreprise obligatoire.'); return; }
        if (!deb) { alert$('a-exp', 'Date début obligatoire.'); return; }

        try {
            const e = await api('/api/etudiants/' + S.id + '/experiences', {
                method: 'POST',
                body: JSON.stringify({ poste, entreprise: entr, dateDebut: deb, dateFin: fin, competences: S.meSel })
            });
            S.experiences.push(e);
            S.competences = await api('/api/etudiants/' + S.id + '/competences').catch(() => S.competences);
            renderE(); renderME(); renderC();
            resetEForm();
            alert$('a-exp', 'Expérience ajoutée.', 'ok');
        } catch (e) {
            alert$('a-exp', e.message);
        }
    });
});
