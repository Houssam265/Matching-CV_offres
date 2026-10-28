'use strict';

/**
 * sidebar.js — Gestion interactive de la navigation latérale ultra-premium
 * - Mode dynamique : rétractable par défaut, s'étire au survol du curseur
 * - Bouton "Épingler / Rétracter" avec sauvegarde dans localStorage
 * - Synchronisation du profil connecté (nom, avatar, rôle)
 * - Gestion du tiroir mobile & déconnexion
 */
window.MatchingCVSidebar = (() => {
    const PIN_STORAGE_KEY = 'matchingcv_sidebar_pinned';

    function init() {
        const sidebar = document.getElementById('app-sidebar');
        const backdrop = document.getElementById('sidebar-backdrop');
        const toggleBtn = document.getElementById('topbar-sidebar-toggle');
        const closeBtn = document.getElementById('sidebar-close-btn');
        const pinBtn = document.getElementById('sidebar-pin-btn');

        // 1. Restauration de l'état "épinglé"
        const isPinned = localStorage.getItem(PIN_STORAGE_KEY) === 'true';
        if (isPinned) {
            document.body.classList.add('sidebar-pinned');
            updatePinIcon(true);
        }

        // 2. Bouton Épingler / Rétracter
        if (pinBtn) {
            pinBtn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const nowPinned = document.body.classList.toggle('sidebar-pinned');
                localStorage.setItem(PIN_STORAGE_KEY, String(nowPinned));
                updatePinIcon(nowPinned);
            });
        }

        function updatePinIcon(pinned) {
            if (!pinBtn) return;
            pinBtn.setAttribute('title', pinned ? 'Rétracter la barre' : 'Épingler la barre');
            pinBtn.innerHTML = pinned
                ? `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><line x1="2" y1="2" x2="22" y2="22"></line><path d="M12 17v5"></path><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h11a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 14 10.76V7a2 2 0 0 1 2-2 2 2 0 0 0 2-2V2H6v1a2 2 0 0 0 2 2 2 2 0 0 1 2 2v3.76z"></path></svg>`
                : `<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="M12 17v5"></path><path d="M9 10.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24V16a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V7a2 2 0 0 1 2-2 2 2 0 0 0 2-2V2H5v1a2 2 0 0 0 2 2 2 2 0 0 1 2 2v3.76z"></path></svg>`;
        }

        // 3. Contrôle ouverture/fermeture sur mobile
        function openSidebar() {
            if (sidebar) sidebar.classList.add('sidebar-open');
            if (backdrop) backdrop.classList.add('active');
            document.body.style.overflow = 'hidden';
        }

        function closeSidebar() {
            if (sidebar) sidebar.classList.remove('sidebar-open');
            if (backdrop) backdrop.classList.remove('active');
            document.body.style.overflow = '';
        }

        if (toggleBtn) toggleBtn.addEventListener('click', openSidebar);
        if (closeBtn) closeBtn.addEventListener('click', closeSidebar);
        if (backdrop) backdrop.addEventListener('click', closeSidebar);

        if (sidebar) {
            sidebar.querySelectorAll('.sidebar-item').forEach(item => {
                const link = item.querySelector('.sidebar-link');
                if (!link) return;

                // Fermer sur clic mobile
                link.addEventListener('click', () => {
                    if (window.innerWidth < 1024) closeSidebar();
                });
            });
        }

        // 4. Synchronisation de la session utilisateur
        if (window.MatchingCVSession) {
            const user = MatchingCVSession.read();
            if (user) {
                updateUserInfo(user);
            }
        }

        // 5. Liaison de la déconnexion
        const doLogout = () => {
            if (window.MatchingCVSession) {
                MatchingCVSession.logout();
            } else {
                localStorage.removeItem('matchingcv_user');
                window.location.replace('/login.html');
            }
        };

        document.getElementById('sidebar-logout')?.addEventListener('click', doLogout);
        document.getElementById('nav-logout')?.addEventListener('click', doLogout);
    }

    /**
     * Met à jour les éléments d'affichage utilisateur
     */
    function updateUserInfo(user) {
        if (!user) return;
        const prenom = user.prenom || '';
        const nom = user.nom || '';
        const fullName = `${prenom} ${nom}`.trim() || 'Étudiant';
        const initials = ((prenom.charAt(0) || '') + (nom.charAt(0) || '')).toUpperCase() || 'E';

        const sbName = document.getElementById('sidebar-user-name');
        if (sbName) sbName.textContent = fullName;

        const sbAvatar = document.getElementById('sidebar-user-avatar');
        if (sbAvatar) sbAvatar.textContent = initials;

        const tbName = document.getElementById('topbar-user-name');
        if (tbName) tbName.textContent = fullName;

        const tbAvatar = document.getElementById('topbar-user-avatar');
        if (tbAvatar) tbAvatar.textContent = initials;

        const legacyNavName = document.getElementById('nav-user-name');
        if (legacyNavName) legacyNavName.textContent = fullName;
    }

    /**
     * Met à jour le badge de notifications dans la sidebar
     */
    function updateNotificationCount(count) {
        const pill = document.getElementById('sidebar-notif-pill');
        const notifLink = document.querySelector('a[href="/notifications.html"].sidebar-link');
        if (pill) {
            pill.textContent = count > 9 ? '9+' : String(count);
            pill.style.removeProperty('display');
        }
        if (notifLink) {
            if (count > 0) {
                notifLink.setAttribute('data-has-notif', 'true');
            } else {
                notifLink.removeAttribute('data-has-notif');
            }
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

    return {
        updateUserInfo,
        updateNotificationCount
    };
})();
