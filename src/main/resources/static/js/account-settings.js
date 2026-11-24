'use strict';

/*
 * Paramètres du compte (tous les rôles), rendus dans #account-settings :
 * notifications, email, mot de passe et suppression du compte.
 */
document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('account-settings');
    const user = window.MatchingCVSession?.read();
    if (!root || !user) return;

    const el = (tag, attrs = {}, ...children) => {
        const e = document.createElement(tag);
        for (const [k, v] of Object.entries(attrs)) {
            if (k === 'class') e.className = v; else if (k === 'text') e.textContent = v; else e.setAttribute(k, v);
        }
        e.append(...children);
        return e;
    };
    const status = () => el('p', { class: 'set-status', role: 'status', 'aria-live': 'polite' });
    const say = (node, msg, kind = '') => { node.textContent = msg; node.className = 'set-status' + (kind ? ' ' + kind : ''); };
    async function api(url, options = {}) {
        const r = await fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers || {}) } });
        const body = await r.json().catch(() => null);
        if (!r.ok) throw new Error(body?.message || 'Opération impossible pour le moment. Réessayez.');
        return body;
    }

    let account = null;

    // ------------------------------------------------------------ Notifications
    const notifToggle = el('input', { type: 'checkbox', id: 'set-notif' });
    const notifStatus = status();
    const notifRow = el('div', { class: 'set-row' },
        el('div', {}, el('h3', { text: 'Notifications' }),
            el('p', { text: 'Recevoir les alertes de la plateforme : nouvelles offres, réponses des recruteurs, messages…' }), notifStatus),
        el('label', { class: 'switch', for: 'set-notif' }, notifToggle, el('span', { text: 'Activées' })));
    notifToggle.addEventListener('change', async () => {
        if (!account) return;
        notifToggle.disabled = true;
        const p = account.profile || {};
        try {
            account = await api('/api/account', { method: 'PUT', body: JSON.stringify({
                prenom: account.prenom, nom: account.nom, headline: p.headline || '', biography: p.biography || '',
                availability: p.availability || '', professionalUrl: p.professionalUrl || '', portfolioUrl: p.portfolioUrl || '',
                notificationsEnabled: notifToggle.checked }) });
            say(notifStatus, notifToggle.checked ? 'Notifications activées.' : 'Notifications désactivées.', 'ok');
        } catch (e) {
            notifToggle.checked = !notifToggle.checked;
            say(notifStatus, e.message, 'err');
        } finally { notifToggle.disabled = false; }
    });

    // ------------------------------------------------------------ Email
    const emailText = el('p', { text: '…' });
    const emailStatus = status();
    const verifyBtn = el('button', { type: 'button', class: 'btn-plain', text: 'Vérifier mon email', hidden: '' });
    verifyBtn.addEventListener('click', async () => {
        verifyBtn.disabled = true;
        try { const r = await api('/api/account/verify-email', { method: 'POST' }); say(emailStatus, r.message, 'ok'); }
        catch (e) { say(emailStatus, e.message, 'err'); }
        finally { verifyBtn.disabled = false; }
    });
    const emailRow = el('div', { class: 'set-row' },
        el('div', {}, el('h3', { text: 'Adresse email' }), emailText, emailStatus), verifyBtn);

    // ------------------------------------------------------------ Mot de passe
    const pwdStatus = status();
    const pwdForm = el('form', { hidden: '', novalidate: '' },
        el('label', {}, 'Mot de passe actuel', el('input', { type: 'password', name: 'current', autocomplete: 'current-password', required: '' })),
        el('label', {}, 'Nouveau mot de passe (10 caractères minimum)', el('input', { type: 'password', name: 'next', autocomplete: 'new-password', minlength: '10', required: '' })),
        el('label', {}, 'Confirmer le nouveau mot de passe', el('input', { type: 'password', name: 'confirm', autocomplete: 'new-password', required: '' })),
        el('div', { class: 'set-actions' },
            el('button', { type: 'submit', class: 'set-btn-primary', text: 'Changer le mot de passe' }),
            el('button', { type: 'button', class: 'btn-plain', 'data-cancel': '', text: 'Annuler' })));
    const pwdOpen = el('button', { type: 'button', class: 'btn-plain', text: 'Modifier' });
    pwdOpen.addEventListener('click', () => { pwdForm.hidden = false; pwdOpen.hidden = true; pwdForm.current.focus(); });
    pwdForm.querySelector('[data-cancel]').addEventListener('click', () => { pwdForm.reset(); pwdForm.hidden = true; pwdOpen.hidden = false; say(pwdStatus, ''); });
    pwdForm.addEventListener('submit', async e => {
        e.preventDefault();
        const f = pwdForm;
        if (!f.current.value) return say(pwdStatus, 'Saisissez votre mot de passe actuel.', 'err');
        if (f.next.value.length < 10) return say(pwdStatus, 'Le nouveau mot de passe doit contenir au moins 10 caractères.', 'err');
        if (f.next.value !== f.confirm.value) return say(pwdStatus, 'Les deux nouveaux mots de passe ne correspondent pas.', 'err');
        f.querySelectorAll('button').forEach(b => { b.disabled = true; });
        try {
            const r = await api('/api/account/password', { method: 'POST', body: JSON.stringify({ currentPassword: f.current.value, newPassword: f.next.value }) });
            say(pwdStatus, (r.message || 'Mot de passe modifié.') + ' Redirection vers la connexion…', 'ok');
            localStorage.removeItem('matchingcv_user');
            setTimeout(() => location.replace('/login.html'), 1800);
        } catch (err) {
            say(pwdStatus, err.message, 'err');
            f.querySelectorAll('button').forEach(b => { b.disabled = false; });
        }
    });
    const pwdRow = el('div', { class: 'set-row' },
        el('div', {}, el('h3', { text: 'Mot de passe' }), el('p', { text: 'Après modification, vous devrez vous reconnecter sur tous vos appareils.' }), pwdForm, pwdStatus),
        pwdOpen);

    // ------------------------------------------------------------ Suppression (pas pour l'admin)
    let deleteRow = null;
    if (user.role !== 'ADMIN') {
        const delStatus = status();
        const consequence = user.role === 'RECRUTEUR'
            ? 'Vos offres actives seront clôturées et les candidats prévenus. Vos coordonnées sont anonymisées.'
            : 'Vos candidatures en cours seront retirées. Vos coordonnées, votre photo et votre CV sont effacés.';
        const delForm = el('form', { hidden: '', novalidate: '' },
            el('p', { text: consequence + ' Cette action est définitive.' }),
            el('label', {}, 'Mot de passe', el('input', { type: 'password', name: 'pwd', autocomplete: 'current-password', required: '' })),
            el('label', {}, 'Tapez SUPPRIMER pour confirmer', el('input', { type: 'text', name: 'confirm', autocomplete: 'off', required: '' })),
            el('div', { class: 'set-actions' },
                el('button', { type: 'submit', class: 'btn-danger-outline', text: 'Supprimer définitivement' }),
                el('button', { type: 'button', class: 'btn-plain', 'data-cancel': '', text: 'Annuler' })));
        const delOpen = el('button', { type: 'button', class: 'btn-danger-outline', text: 'Supprimer mon compte' });
        delOpen.addEventListener('click', () => { delForm.hidden = false; delOpen.hidden = true; delForm.pwd.focus(); });
        delForm.querySelector('[data-cancel]').addEventListener('click', () => { delForm.reset(); delForm.hidden = true; delOpen.hidden = false; say(delStatus, ''); });
        delForm.addEventListener('submit', async e => {
            e.preventDefault();
            if (delForm.confirm.value.trim() !== 'SUPPRIMER') return say(delStatus, 'Tapez exactement SUPPRIMER pour confirmer.', 'err');
            delForm.querySelectorAll('button').forEach(b => { b.disabled = true; });
            try {
                const r = await api('/api/account', { method: 'DELETE', body: JSON.stringify({ motDePasse: delForm.pwd.value, confirmation: 'SUPPRIMER' }) });
                say(delStatus, r.message || 'Compte supprimé.', 'ok');
                localStorage.removeItem('matchingcv_user');
                setTimeout(() => location.replace('/index.html'), 1800);
            } catch (err) {
                say(delStatus, err.message, 'err');
                delForm.querySelectorAll('button').forEach(b => { b.disabled = false; });
            }
        });
        deleteRow = el('div', { class: 'set-row' },
            el('div', {}, el('h3', { text: 'Supprimer mon compte' }), el('p', { text: 'Fermez définitivement votre compte MatchingCV.' }), delForm, delStatus),
            delOpen);
    }

    root.classList.add('account-settings');
    root.append(notifRow, emailRow, pwdRow, ...(deleteRow ? [deleteRow] : []));

    api('/api/account').then(a => {
        account = a;
        notifToggle.checked = a.profile?.notificationsEnabled !== false;
        emailText.textContent = a.email + (a.emailVerified ? ' · vérifiée' : ' · non vérifiée');
        verifyBtn.hidden = !!a.emailVerified;
    }).catch(e => say(notifStatus, e.message, 'err'));
});
