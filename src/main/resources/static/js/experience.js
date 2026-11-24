'use strict';

// Shared, role-aware navigation aids. All destination pages retain their guards.
document.addEventListener('DOMContentLoaded', () => {
    const user = window.MatchingCVSession?.read();
    const sidebar = document.querySelector('.app-sidebar, #app-sidebar');
    if (!user || !sidebar) return;
    const recruiter = user.role === 'RECRUTEUR';
    const file = location.pathname.split('/').pop();
    const dashboard = file === 'dashboard-recruteur.html' || file === 'dashboard-etudiant.html';
    const icon = (path) => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${path}</svg>`;
    const arrow = icon('<path d="M5 12h14m-5-5 5 5-5 5"/>');
    const paths = {
        profile: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
        search: '<circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/>',
        briefcase: '<rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V3h8v4M3 12h18M12 10v4"/>',
        chart: '<path d="M4 3v17h17M8 15v-4m5 4V7m5 8V4"/>',
    };
    const links = recruiter ? [
        ['briefcase','Publier une offre','Décrivez le poste et les compétences recherchées.','/publier-offre.html'],
        ['search','Gérer les candidatures','Retrouvez les candidats depuis vos offres.','/mes-offres.html'],
        ['profile','Présenter mon entreprise','Donnez envie aux talents de vous rejoindre.','/mon-entreprise.html']
    ] : [
        ['profile','Construire mon profil','Mettez en valeur vos projets et vos expériences.','/mon-compte.html'],
        ['search','Trouver une opportunité','Explorez les offres adaptées à vos compétences.','/offres-recommandees.html'],
        ['chart','Améliorer mon CV','Identifiez vos points forts avec le coach.','/coach-cv.html']
    ];
    sidebar.querySelectorAll('a[href="/compte.html"], a[href="/suivi.html"]').forEach(link => link.closest('li')?.remove());
    if (recruiter) {
        sidebar.querySelectorAll('a[href="/entretiens.html"]').forEach(link => link.closest('li')?.remove());
        const interviewItem = document.createElement('li'); interviewItem.className = 'sidebar-item';
        interviewItem.innerHTML = `<a class="sidebar-link" href="/entretiens.html" title="Entretiens"><span class="sidebar-link-icon">${icon('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18M8 15h3"/>')}</span><span class="sidebar-link-label">Entretiens</span></a>`;
        sidebar.querySelector('a[href="/mes-offres.html"]')?.closest('li')?.after(interviewItem);
        if (!interviewItem.isConnected) sidebar.querySelector('.sidebar-list')?.append(interviewItem);
    }
    if (!recruiter) {
        sidebar.querySelectorAll('a[href="/mes-entretiens.html"]').forEach(link=>link.closest('li')?.remove());
        const item=document.createElement('li');item.className='sidebar-item';
        item.innerHTML=`<a class="sidebar-link" href="/mes-entretiens.html"><span class="sidebar-link-icon">${icon('<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18"/>')}</span><span class="sidebar-link-label">Mes entretiens</span></a>`;
        sidebar.querySelector('.sidebar-list')?.append(item);
        // « Mon compte » (profil + paramètres) et « Mon CV » (aperçu + export PDF) sont deux entrées distinctes.
        const profileLink = sidebar.querySelector('#nav-link-profil');
        const profileLabel = profileLink?.querySelector('.sidebar-link-label');
        if (profileLabel) profileLabel.textContent = 'Mon compte';
        if (profileLink) profileLink.title = 'Mon compte : profil, projets, expériences et paramètres';
        sidebar.querySelectorAll('a[href="/cv.html"]').forEach(link => link.closest('li')?.remove());
        const cvItem = document.createElement('li'); cvItem.className = 'sidebar-item';
        cvItem.innerHTML = `<a class="sidebar-link" href="/cv.html" title="Mon CV : aperçu et export PDF"><span class="sidebar-link-icon">${icon('<path d="M14 3H6a1 1 0 0 0-1 1v16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V8z"/><path d="M14 3v5h5M9 13h6M9 17h6M9 9h2"/>')}</span><span class="sidebar-link-label">Mon CV</span></a>`;
        if (profileLink) profileLink.closest('li')?.after(cvItem); else sidebar.querySelector('.sidebar-list')?.append(cvItem);
    }
    // Messagerie : lien dans le menu (juste avant Notifications) avec compteur de non-lus.
    sidebar.querySelectorAll('a[href="/messages.html"]').forEach(link => link.closest('li')?.remove());
    const messagesItem = document.createElement('li'); messagesItem.className = 'sidebar-item';
    messagesItem.innerHTML = `<a class="sidebar-link" href="/messages.html" title="Messages"><span class="sidebar-link-icon">${icon('<path d="M4 5h16a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H9l-5 4V6a1 1 0 0 1 1-1z"/>')}</span><span class="sidebar-link-label">Messages</span><span class="sidebar-badge-counter" id="sidebar-msg-pill" hidden>0</span></a>`;
    const notifItem = sidebar.querySelector('a[href="/notifications.html"]')?.closest('li');
    if (notifItem) notifItem.before(messagesItem); else sidebar.querySelector('.sidebar-list')?.append(messagesItem);
    const messagesLink = messagesItem.querySelector('a');
    const refreshUnread = async () => {
        try {
            const r = await fetch('/api/messages/non-lus');
            if (!r.ok) return;
            const count = Number((await r.json()).count) || 0;
            const pill = document.getElementById('sidebar-msg-pill');
            pill.textContent = count > 9 ? '9+' : String(count);
            pill.hidden = count === 0;
            if (count > 0) messagesLink.setAttribute('data-has-notif', 'true'); else messagesLink.removeAttribute('data-has-notif');
            messagesLink.title = count > 0 ? `Messages (${count} non lu${count > 1 ? 's' : ''})` : 'Messages';
        } catch (_) { /* compteur indicatif uniquement */ }
    };
    window.MatchingCVMessages = { refreshUnread };
    refreshUnread();
    setInterval(() => { if (!document.hidden) refreshUnread(); }, 45000);
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refreshUnread(); });
    const logout = document.getElementById('sidebar-logout');
    const actions = document.getElementById('topbar-nav-cta');
    if (logout && actions) {
        actions.append(logout);
        logout.className = 'topbar-logout';
        logout.innerHTML = `${icon('<path d="M9 5H4v14h5M13 8l4 4-4 4M8 12h9"/>')}<span>Déconnexion</span>`;
    }
    sidebar.querySelector('.sidebar-footer')?.remove();
    sidebar.querySelectorAll('.sidebar-link').forEach(link=>{
        const target=new URL(link.href,location.href);
        const active=target.pathname===location.pathname && target.search===location.search;
        link.classList.toggle('active',active);
        if(active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');
    });
    try {sidebar.scrollTop=Number(sessionStorage.getItem('matchingcv-sidebar-scroll')||0);}catch(e){}
    sidebar.addEventListener('click',event=>{if(event.target.closest('a[href]')){try{sessionStorage.setItem('matchingcv-sidebar-scroll',String(sidebar.scrollTop));}catch(e){}}});
    requestAnimationFrame(()=>requestAnimationFrame(()=>document.body.classList.remove('navigation-loading')));
    const currentLink = sidebar.querySelector('.sidebar-link.active');
    currentLink?.setAttribute('aria-current','page');
    if (dashboard) {
        const main = document.querySelector('main');
        const launch = document.createElement('section');
        launch.className = 'workspace-launch';
        launch.setAttribute('aria-label','Actions recommandées');
        launch.innerHTML = `<div class="section-heading"><div><span class="section-eyebrow">À vous de jouer</span><h2>${recruiter ? 'Votre prochain recrutement commence ici.' : 'Un pas de plus vers votre prochain poste.'}</h2></div><span class="section-note">Vos essentiels, à portée de main</span></div><div class="launch-grid">${links.map(([key,title,description,url],i)=>`<a class="launch-card" href="${url}"><span class="launch-icon">${icon(paths[key])}</span><span class="launch-number">0${i+1}</span><h3>${title}</h3><p>${description}</p><span class="launch-link">Commencer ${arrow}</span></a>`).join('')}</div>`;
        main?.prepend(launch);
        const greeting = document.getElementById('user-prenom');
        if (greeting && user.prenom) greeting.textContent = user.prenom;
    }
});
