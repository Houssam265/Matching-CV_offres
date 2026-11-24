'use strict';

// « Mes entretiens » (candidat) : prochain rendez-vous, entretiens à venir, en attente de date et passés.
document.addEventListener('DOMContentLoaded', () => {
    const user = MatchingCVSession.require('ETUDIANT');
    if (!user) return;

    const $ = id => document.getElementById(id);
    const status = $('iv-status');
    const refresh = $('iv-refresh');
    const TZ = Intl.DateTimeFormat().resolvedOptions().timeZone;

    const el = (tag, cls, text) => { const e = document.createElement(tag); if (cls) e.className = cls; if (text != null && text !== '') e.textContent = text; return e; };
    const link = (label, cls, href, external) => {
        const a = el('a', cls, label);
        a.href = href;
        if (external) { a.target = '_blank'; a.rel = 'noopener noreferrer'; }
        return a;
    };
    const button = (label, cls, onClick) => { const b = el('button', cls, label); b.type = 'button'; b.addEventListener('click', onClick); return b; };
    const icon = path => { const s = el('span', 'iv-ico'); s.innerHTML = `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`; return s; };
    const ICON = {
        clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
        video: '<rect x="3" y="6" width="13" height="12" rx="2"/><path d="m16 10 5-3v10l-5-3"/>',
        pin: '<path d="M12 21s-7-6.2-7-11a7 7 0 0 1 14 0c0 4.8-7 11-7 11z"/><circle cx="12" cy="10" r="2.5"/>',
        note: '<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5M9 13h6M9 17h4"/>'
    };

    async function get(url) {
        const r = await fetch(url);
        if (!r.ok) throw new Error('Chargement indisponible. Vérifiez votre connexion puis réessayez.');
        const text = await r.text();
        return text ? JSON.parse(text) : null;
    }

    function remoteUrl(location) {
        try { const u = new URL(location); return ['https:', 'http:'].includes(u.protocol) ? u.href : null; } catch (_) { return null; }
    }

    function relative(date) {
        const diff = date - Date.now();
        const minutes = Math.round(diff / 60000);
        const sameDay = date.toDateString() === new Date().toDateString();
        const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate() + 1);
        if (diff < 0) return minutes > -90 ? 'En cours' : 'Terminé';
        if (minutes < 60) return `Dans ${Math.max(1, minutes)} min`;
        if (sameDay) return `Aujourd’hui · dans ${Math.round(minutes / 60)} h`;
        if (date.toDateString() === tomorrow.toDateString()) return 'Demain';
        // Jours de calendrier (et non tranches de 24 h) : le 26 vu le 24 = « Dans 2 jours ».
        const midnight = d => new Date(d.getFullYear(), d.getMonth(), d.getDate());
        const days = Math.round((midnight(date) - midnight(new Date())) / 86400000);
        return `Dans ${days} jours`;
    }

    // ------------------------------------------------------------ Agenda (.ics)
    function icsText(s) { return String(s || '').replace(/\\/g, '\\\\').replace(/;/g, '\\;').replace(/,/g, '\\,').replace(/\r?\n/g, '\\n'); }
    function icsDate(d) { return d.toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, ''); }
    function downloadIcs(item) {
        const start = new Date(item.interview.startsAt);
        const end = new Date(start.getTime() + 60 * 60000);
        const url = remoteUrl(item.interview.location);
        const lines = [
            'BEGIN:VCALENDAR', 'VERSION:2.0', 'PRODID:-//MatchingCV//Entretiens//FR', 'CALSCALE:GREGORIAN', 'METHOD:PUBLISH',
            'BEGIN:VEVENT',
            `UID:entretien-${item.c.id}@matchingcv`,
            `DTSTAMP:${icsDate(new Date())}`,
            `DTSTART:${icsDate(start)}`,
            `DTEND:${icsDate(end)}`,
            `SUMMARY:${icsText(`Entretien — ${item.c.offreTitre || 'Candidature'}${item.c.entreprise ? ` (${item.c.entreprise})` : ''}`)}`,
            `LOCATION:${icsText(item.interview.location)}`,
            `DESCRIPTION:${icsText((item.interview.instructions || 'Entretien planifié via MatchingCV.') + (url ? `\nLien : ${url}` : ''))}`,
            ...(url ? [`URL:${url}`] : []),
            'BEGIN:VALARM', 'TRIGGER:-PT30M', 'ACTION:DISPLAY', 'DESCRIPTION:Entretien dans 30 minutes', 'END:VALARM',
            'END:VEVENT', 'END:VCALENDAR'
        ];
        const blob = new Blob([lines.join('\r\n') + '\r\n'], { type: 'text/calendar;charset=utf-8' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `entretien-${(item.c.offreTitre || 'matchingcv').normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '').toLowerCase()}.ics`;
        document.body.append(a); a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(a.href), 10000);
    }

    // ------------------------------------------------------------ Cartes
    function actions(item, { past = false } = {}) {
        const box = el('div', 'iv-actions');
        const i = item.interview;
        if (i && !past) {
            const url = remoteUrl(i.location);
            if (url) box.append(link('Rejoindre la visio', 'iv-btn iv-btn-primary', url, true));
            else box.append(link('Itinéraire', 'iv-btn iv-btn-primary', `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(i.location)}`, true));
            box.append(button('Ajouter à mon agenda', 'iv-btn', () => downloadIcs(item)));
        }
        box.append(link('Écrire au recruteur', 'iv-btn', `/messages.html?c=${item.c.id}`));
        box.append(link('Voir la candidature', 'iv-btn iv-btn-ghost', '/mes-candidatures.html'));
        return box;
    }

    function details(i) {
        const box = el('ul', 'iv-details');
        const start = new Date(i.startsAt);
        const when = el('li');
        when.append(icon(ICON.clock), el('span', null, `${start.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })} à ${start.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}`));
        const tz = el('small', 'iv-tz', `heure locale (${TZ})`);
        when.append(tz);
        box.append(when);
        const url = remoteUrl(i.location);
        const where = el('li');
        where.append(icon(url ? ICON.video : ICON.pin));
        if (url) where.append(el('span', null, 'Entretien en visioconférence'));
        else where.append(el('span', null, i.location));
        box.append(where);
        if (i.instructions) {
            const note = el('li', 'iv-instructions');
            note.append(icon(ICON.note), el('span', null, i.instructions));
            box.append(note);
        }
        return box;
    }

    function dateBlock(d) {
        const b = el('div', 'iv-date');
        b.append(el('span', 'iv-date-month', d.toLocaleDateString('fr-FR', { month: 'short' }).replace('.', '')),
            el('strong', 'iv-date-day', String(d.getDate())),
            el('span', 'iv-date-time', d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })));
        return b;
    }

    function card(item, kind) {
        const art = el('article', `iv-card iv-${kind}`);
        art.id = `candidature-${item.c.id}`;
        const head = el('div', 'iv-card-head');
        const who = el('div', 'iv-who');
        who.append(el('h3', null, item.c.offreTitre || 'Votre candidature'), el('p', null, item.c.entreprise || ''));
        if (item.interview) {
            const d = new Date(item.interview.startsAt);
            head.append(dateBlock(d), who, el('span', `iv-badge ${kind === 'past' ? 'iv-badge-muted' : ''}`, kind === 'past' ? 'Passé' : relative(d)));
            art.append(head, details(item.interview));
        } else {
            head.append(who, el('span', 'iv-badge iv-badge-wait', item.c.statut === 'ACCEPTEE' ? 'Candidature acceptée' : 'Entretien demandé'));
            art.append(head);
        }
        art.append(actions(item, { past: kind === 'past' }));
        return art;
    }

    function renderNext(item) {
        const box = $('iv-next');
        box.replaceChildren();
        if (!item) { box.hidden = true; return; }
        const d = new Date(item.interview.startsAt);
        const top = el('div', 'iv-next-top');
        top.append(el('span', 'iv-next-label', 'Prochain entretien'), el('span', 'iv-badge iv-badge-strong', relative(d)));
        const body = el('div', 'iv-next-body');
        const info = el('div', 'iv-who');
        const title = el('h2', null, item.c.offreTitre || 'Votre candidature');
        title.id = 'iv-next-title';
        info.append(title, el('p', null, item.c.entreprise || ''), details(item.interview));
        body.append(dateBlock(d), info);
        box.id = 'iv-next';
        const anchor = el('span'); anchor.id = `candidature-${item.c.id}`;
        box.append(anchor, top, body, actions(item));
        box.hidden = false;
    }

    function fill(listId, sectionId, items, kind) {
        const list = $(listId);
        list.replaceChildren(...items.map(i => card(i, kind)));
        $(sectionId).hidden = items.length === 0;
    }

    async function load() {
        refresh.disabled = true;
        status.textContent = 'Chargement de vos entretiens…';
        try {
            const applications = await get(`/api/etudiants/${user.id}/candidatures`) || [];
            const concerned = applications.filter(c => c.statut === 'ENTRETIEN' || c.statut === 'ACCEPTEE');
            // Tous les rendez-vous sont chargés en parallèle.
            const items = await Promise.all(concerned.map(async c => {
                try { return { c, interview: await get(`/api/candidatures/${c.id}/entretien`) }; }
                catch (_) { return { c, interview: null, error: true }; }
            }));
            const now = Date.now();
            const withDate = items.filter(i => i.interview?.startsAt).sort((a, b) => new Date(a.interview.startsAt) - new Date(b.interview.startsAt));
            const upcoming = withDate.filter(i => new Date(i.interview.startsAt).getTime() > now - 90 * 60000);
            const past = withDate.filter(i => !upcoming.includes(i)).reverse();
            const pending = items.filter(i => !i.interview?.startsAt && i.c.statut === 'ENTRETIEN');

            renderNext(upcoming[0]);
            fill('iv-upcoming', 'iv-upcoming-section', upcoming.slice(1), 'upcoming');
            fill('iv-pending', 'iv-pending-section', pending, 'pending');
            fill('iv-past', 'iv-past-section', past, 'past');
            $('iv-count-upcoming').textContent = upcoming.length;
            $('iv-count-pending').textContent = pending.length;
            $('iv-count-past').textContent = past.length;
            const any = upcoming.length + pending.length + past.length > 0;
            $('iv-stats').hidden = !any;
            $('iv-empty').hidden = any;
            status.textContent = items.some(i => i.error) ? 'Certains rendez-vous n’ont pas pu être chargés. Actualisez la page.' : '';

            const target = location.hash.slice(1);
            if (target) {
                const node = document.getElementById(target);
                if (node) {
                    const holder = node.closest('details');
                    if (holder) holder.open = true;
                    (node.closest('.iv-next') || node).classList.add('iv-target');
                    node.scrollIntoView({ block: 'center' });
                }
            }
        } catch (e) {
            status.textContent = e.message;
        } finally {
            refresh.disabled = false;
        }
    }

    refresh.addEventListener('click', load);
    load();
    // Les badges « dans X min » restent à jour si la page reste ouverte.
    setInterval(() => { if (!document.hidden) load(); }, 5 * 60000);
});
