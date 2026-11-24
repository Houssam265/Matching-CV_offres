'use strict';

// Messagerie recruteur ↔ candidat : une conversation par candidature.
document.addEventListener('DOMContentLoaded', () => {
    const user = window.MatchingCVSession?.read();
    if (!user || user.role === 'ADMIN') return;
    const recruiter = user.role === 'RECRUTEUR';

    const layout = document.getElementById('msg-layout');
    const listEl = document.getElementById('conv-list');
    const thread = document.getElementById('thread');
    const emptyState = document.getElementById('thread-empty');
    const search = document.getElementById('msg-search');
    document.getElementById('messages-sub').textContent = recruiter
        ? 'Échangez avec vos candidats. Les coordonnées restent privées : tout passe par la messagerie.'
        : 'Vos échanges avec les recruteurs. Un recruteur vous écrit en premier, vous pouvez ensuite lui répondre ici.';
    document.getElementById('thread-empty-text').textContent = recruiter
        ? 'Pour contacter un candidat, ouvrez sa candidature puis « Envoyer un message ».'
        : 'Quand un recruteur vous écrit, la conversation apparaît ici.';

    const STATUTS = { ENVOYEE: 'Candidature envoyée', VUE: 'Candidature consultée', ENTRETIEN: 'Entretien prévu', ACCEPTEE: 'Candidature acceptée', REFUSEE: 'Candidature non retenue', RETIREE: 'Candidature retirée' };
    let conversations = [];
    let currentId = null;
    let currentCount = -1;
    let loadingThread = false;

    const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null) e.textContent = text; return e; };
    const avatar = (userId, cls = 'msg-avatar') => { const a = el('span', cls); a.setAttribute('aria-hidden', 'true'); if (userId) a.dataset.photoUser = userId; window.MatchingCVPhoto?.paint(a, userId); return a; };
    const date = v => (v ? new Date(v) : null);
    const sameDay = (a, b) => a.toDateString() === b.toDateString();
    function shortTime(v) {
        const d = date(v); if (!d) return '';
        const now = new Date();
        if (sameDay(d, now)) return d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
        const y = new Date(now); y.setDate(now.getDate() - 1);
        if (sameDay(d, y)) return 'Hier';
        return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
    }
    function dayLabel(d) {
        const now = new Date(); const y = new Date(now); y.setDate(now.getDate() - 1);
        if (sameDay(d, now)) return 'Aujourd’hui';
        if (sameDay(d, y)) return 'Hier';
        return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: d.getFullYear() === now.getFullYear() ? undefined : 'numeric' });
    }
    async function api(url, options) {
        const r = await fetch(url, options);
        const body = await r.json().catch(() => null);
        if (!r.ok) throw new Error(body?.message || body?.error || 'Service momentanément indisponible. Réessayez.');
        return body;
    }

    // ---------------------------------------------------------------- Liste
    function renderList() {
        const q = search.value.trim().toLowerCase();
        listEl.replaceChildren();
        listEl.removeAttribute('aria-busy');
        const shown = conversations.filter(c => !q || `${c.interlocuteurNom} ${c.offreTitre} ${c.entreprise}`.toLowerCase().includes(q));
        if (!shown.length) {
            const li = el('li', 'msg-list-empty', conversations.length ? 'Aucune conversation ne correspond à votre recherche.' : 'Aucune conversation pour le moment.');
            listEl.append(li);
            return;
        }
        for (const c of shown) {
            const li = el('li');
            const btn = el('button', 'msg-conv' + (c.candidatureId === currentId ? ' active' : '') + (c.nonLus ? ' unread' : ''));
            btn.type = 'button';
            btn.dataset.id = c.candidatureId;
            if (c.candidatureId === currentId) btn.setAttribute('aria-current', 'true');
            const body = el('span', 'msg-conv-body');
            const top = el('span', 'msg-conv-top');
            top.append(el('span', 'msg-conv-name', c.interlocuteurNom), el('span', 'msg-conv-time', shortTime(c.dateDernierMessage)));
            const offer = el('span', 'msg-conv-offer', c.offreTitre + (recruiter ? '' : (c.entreprise ? ` · ${c.entreprise}` : '')));
            const preview = el('span', 'msg-conv-preview', (c.dernierEnvoyeParMoi ? 'Vous : ' : '') + (c.dernierMessage || 'Nouvelle conversation'));
            body.append(top, offer, preview);
            btn.append(avatar(c.interlocuteurId), body);
            if (c.nonLus) {
                const badge = el('span', 'msg-conv-badge', String(c.nonLus));
                badge.setAttribute('aria-label', `${c.nonLus} message${c.nonLus > 1 ? 's' : ''} non lu${c.nonLus > 1 ? 's' : ''}`);
                btn.append(badge);
            }
            btn.addEventListener('click', () => openConversation(c.candidatureId, true));
            li.append(btn);
            listEl.append(li);
        }
    }

    async function loadList() {
        try {
            conversations = await api('/api/messages/conversations');
            renderList();
        } catch (e) {
            listEl.replaceChildren(el('li', 'msg-list-empty', e.message));
        }
    }

    // ---------------------------------------------------------------- Fil
    function header(conv) {
        const head = el('header', 'msg-thread-head');
        const back = el('button', 'msg-back');
        back.type = 'button';
        back.setAttribute('aria-label', 'Retour aux conversations');
        back.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 18l-6-6 6-6"/></svg>';
        back.addEventListener('click', () => { layout.classList.remove('show-thread'); currentId = null; history.replaceState(null, '', '/messages.html'); renderList(); });
        const who = el('div', 'msg-thread-who');
        who.append(el('strong', null, conv.interlocuteurNom), el('span', null, conv.interlocuteurDetail || ''));
        const ctx = el('div', 'msg-thread-context');
        const link = el('a', 'msg-thread-offer', conv.offreTitre);
        link.href = recruiter ? `/candidats-offre.html?offre=${conv.offreId}&candidature=${conv.candidatureId}` : '/mes-candidatures.html';
        link.title = recruiter ? 'Voir la candidature' : 'Voir mes candidatures';
        const statut = el('span', `msg-status s-${conv.statut}`, STATUTS[conv.statut] || conv.statut);
        ctx.append(link, statut);
        if (recruiter) {
            const cv = el('a', 'msg-thread-cv', 'Voir le CV');
            cv.href = `/cv.html?candidature=${conv.candidatureId}`;
            ctx.append(cv);
        }
        head.append(back, avatar(conv.interlocuteurId, 'msg-avatar msg-avatar-lg'), who, ctx);
        return head;
    }

    function renderMessages(box, messages, conv) {
        box.replaceChildren();
        if (!messages.length) {
            const intro = el('div', 'msg-intro');
            intro.append(el('strong', null, recruiter ? `Démarrez la conversation avec ${conv.interlocuteurNom}` : 'Aucun message pour le moment'),
                el('p', null, recruiter
                    ? 'Présentez-vous, proposez un échange ou demandez une précision sur sa candidature. Le candidat est notifié de votre message.'
                    : 'Le recruteur n’a pas encore écrit.'));
            box.append(intro);
            return;
        }
        let lastDay = null;
        for (const m of messages) {
            const d = date(m.dateEnvoi);
            if (!lastDay || !sameDay(lastDay, d)) { box.append(el('div', 'msg-day', dayLabel(d))); lastDay = d; }
            const row = el('div', 'msg-row' + (m.moi ? ' mine' : ''));
            const bubble = el('div', 'msg-bubble');
            bubble.append(el('p', null, m.contenu));
            const meta = el('span', 'msg-meta', d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) + (m.moi ? (m.lu ? ' · Lu' : ' · Envoyé') : ''));
            bubble.append(meta);
            row.append(bubble);
            box.append(row);
        }
    }

    function composer(conv, box) {
        if (!conv.peutEcrire) {
            const closed = el('div', 'msg-closed', conv.raisonFermeture || 'Cette conversation est fermée.');
            closed.setAttribute('role', 'note');
            return closed;
        }
        const form = el('form', 'msg-composer');
        const label = el('label', 'visually-hidden', 'Votre message');
        label.htmlFor = 'msg-input';
        const input = el('textarea');
        input.id = 'msg-input';
        input.rows = 1;
        input.maxLength = 2000;
        input.placeholder = 'Écrire un message…';
        const counter = el('span', 'msg-counter', '');
        const send = el('button', 'msg-send', 'Envoyer');
        send.type = 'submit';
        send.disabled = true;
        const error = el('p', 'msg-error');
        error.setAttribute('role', 'alert');
        const autosize = () => { input.style.height = 'auto'; input.style.height = Math.min(input.scrollHeight, 160) + 'px'; };
        input.addEventListener('input', () => {
            send.disabled = !input.value.trim();
            counter.textContent = input.value.length > 1800 ? `${input.value.length}/2000` : '';
            autosize();
        });
        input.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) { e.preventDefault(); form.requestSubmit(); } });
        form.addEventListener('submit', async e => {
            e.preventDefault();
            const contenu = input.value.trim();
            if (!contenu) return;
            send.disabled = true; error.textContent = '';
            try {
                await api(`/api/messages/conversations/${conv.candidatureId}`, {
                    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ contenu })
                });
                input.value = ''; autosize(); counter.textContent = '';
                await openConversation(conv.candidatureId, false, true);
                loadList();
            } catch (err) {
                error.textContent = err.message;
                send.disabled = false;
            }
        });
        const row = el('div', 'msg-composer-row');
        row.append(input, send);
        form.append(label, row, counter, error);
        setTimeout(() => input.focus({ preventScroll: true }), 0);
        return form;
    }

    async function openConversation(id, userAction, keepFocus = false) {
        if (loadingThread && !userAction) return;
        loadingThread = true;
        const switching = id !== currentId;
        currentId = id;
        if (userAction) history.replaceState(null, '', `/messages.html?c=${id}`);
        layout.classList.add('show-thread');
        renderList();
        try {
            const detail = await api(`/api/messages/conversations/${id}`);
            const conv = detail.conversation;
            if (!switching && detail.messages.length === currentCount && !keepFocus) return; // rien de nouveau
            currentCount = detail.messages.length;
            const focused = document.activeElement?.id === 'msg-input' ? document.activeElement.value : null;
            const box = el('div', 'msg-scroll');
            box.setAttribute('aria-live', 'polite');
            renderMessages(box, detail.messages, conv);
            thread.replaceChildren(header(conv), box, composer(conv, box));
            if (focused != null) { const input = document.getElementById('msg-input'); if (input) { input.value = focused; input.dispatchEvent(new Event('input')); } }
            box.scrollTop = box.scrollHeight;
            const item = conversations.find(c => c.candidatureId === id);
            if (item && item.nonLus) { item.nonLus = 0; renderList(); }
            window.MatchingCVMessages?.refreshUnread?.();
        } catch (e) {
            thread.replaceChildren(emptyState);
            emptyState.querySelector('h2').textContent = 'Conversation indisponible';
            document.getElementById('thread-empty-text').textContent = e.message;
        } finally {
            loadingThread = false;
        }
    }

    search.addEventListener('input', renderList);

    (async () => {
        await loadList();
        const requested = Number(new URLSearchParams(location.search).get('c'));
        if (Number.isSafeInteger(requested) && requested > 0) await openConversation(requested, false);
        else if (conversations.length && window.matchMedia('(min-width: 861px)').matches) await openConversation(conversations[0].candidatureId, false);
    })();

    // Rafraîchissement léger : nouveaux messages du fil ouvert et de la liste.
    setInterval(() => {
        if (document.hidden) return;
        if (currentId) openConversation(currentId, false);
        loadList();
    }, 8000);
});
