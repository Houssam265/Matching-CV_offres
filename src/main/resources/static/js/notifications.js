/**
 * notifications.js — Composant partagé de notifications en temps réel
 * Matching CV-Offres | GI3, ENSA Tétouan
 *
 * Expose window.NotificationBell avec :
 * - NotificationBell.init({ userId })
 * - NotificationBell.onNotification(callback)
 */

'use strict';

window.NotificationBell = (() => {
    let currentUserId = null;
    let stompClient = null;
    let isConnected = false;
    let reconnectAttempts = 0;
    let reconnectTimer = null;
    let fallbackPollTimer = null;
    const callbacks = [];
    let unreadCount = 0;
    let notificationsCache = [];

    // Sélecteurs et éléments DOM
    let wrapperEl = null;
    let bellBtnEl = null;
    let badgeEl = null;
    let dropdownEl = null;
    let listEl = null;
    let unreadCountPillEl = null;
    let toastContainerEl = null;

    /**
     * Échappe les caractères HTML
     */
    function esc(s) {
        return String(s || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    /**
     * Formate une date ISO en relatif français
     */
    function formatRelativeTime(isoString) {
        if (!isoString) return '';
        try {
            const date = new Date(isoString);
            const now = new Date();
            const diffSeconds = Math.floor((now - date) / 1000);

            if (diffSeconds < 45) return "À l'instant";
            if (diffSeconds < 3600) {
                const mins = Math.max(1, Math.floor(diffSeconds / 60));
                return `Il y a ${mins} min`;
            }
            if (diffSeconds < 86400) {
                const hours = Math.floor(diffSeconds / 3600);
                return `Il y a ${hours} h`;
            }
            if (diffSeconds < 172800) {
                return 'Hier';
            }
            return date.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' });
        } catch (_) {
            return '';
        }
    }

    /**
     * Retourne le SVG d'icône selon le TypeNotification
     */
    function getNotificationIcon(type) {
        switch (type) {
            case 'OFFRE_CORRESPONDANTE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"></polygon>
                </svg>`;
            case 'CANDIDATURE_ACCEPTEE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
                    <polyline points="22 4 12 14.01 9 11.01"></polyline>
                </svg>`;
            case 'CANDIDATURE_VUE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                    <circle cx="12" cy="12" r="3"></circle>
                </svg>`;
            case 'CANDIDATURE_REFUSEE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="15" y1="9" x2="9" y2="15"></line>
                    <line x1="9" y1="9" x2="15" y2="15"></line>
                </svg>`;
            case 'COMPETENCE_VALIDEE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"></path>
                </svg>`;
            case 'COMPETENCE_REJETEE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <circle cx="12" cy="12" r="10"></circle>
                    <line x1="12" y1="8" x2="12" y2="12"></line>
                    <line x1="12" y1="16" x2="12.01" y2="16"></line>
                </svg>`;
            case 'NOUVELLE_CANDIDATURE':
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
                    <circle cx="8.5" cy="7" r="4"></circle>
                    <line x1="20" y1="8" x2="20" y2="14"></line>
                    <line x1="23" y1="11" x2="17" y2="11"></line>
                </svg>`;
            default:
                return `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>`;
        }
    }

    /**
     * Crée le DOM pour le bouton cloche et le panneau
     */
    function renderBellDOM(container) {
        if (wrapperEl) return;

        wrapperEl = document.createElement('div');
        wrapperEl.className = 'notif-bell-wrapper';
        wrapperEl.setAttribute('role', 'region');
        wrapperEl.setAttribute('aria-label', 'Notifications');

        wrapperEl.innerHTML = `
            <button type="button" class="notif-bell-btn" id="notif-bell-button" aria-haspopup="true" aria-expanded="false" aria-label="Notifications" title="Notifications">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                    <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path>
                    <path d="M13.73 21a2 2 0 0 1-3.46 0"></path>
                </svg>
                <span class="notif-badge hidden" id="notif-badge" aria-live="polite">0</span>
            </button>
            <div class="notif-dropdown" id="notif-dropdown" role="menu">
                <div class="notif-dropdown-header">
                    <div class="notif-header-title">
                        <span>Notifications</span>
                        <span class="notif-header-count" id="notif-header-count">0 non lue</span>
                    </div>
                    <button type="button" class="notif-btn-readall" id="notif-btn-readall" title="Tout marquer comme lu">
                        Tout marquer lu
                    </button>
                </div>
                <ul class="notif-list" id="notif-list">
                    <li class="notif-empty">
                        <div class="notif-empty-icon">
                            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
                        </div>
                        <div class="notif-empty-text">Aucune notification pour l'instant</div>
                        <div class="notif-empty-sub">Vous serez alerté dès qu'une activité vous concerne.</div>
                    </li>
                </ul>
                <div class="notif-dropdown-footer">
                    <a href="/notifications.html" class="notif-link-all">
                        Voir tout l'historique &rarr;
                    </a>
                </div>
            </div>
        `;

        // Insertion dans le conteneur cible (avant les éléments existants si possible)
        if (container.firstChild) {
            container.insertBefore(wrapperEl, container.firstChild);
        } else {
            container.appendChild(wrapperEl);
        }

        bellBtnEl = document.getElementById('notif-bell-button');
        badgeEl = document.getElementById('notif-badge');
        dropdownEl = document.getElementById('notif-dropdown');
        listEl = document.getElementById('notif-list');
        unreadCountPillEl = document.getElementById('notif-header-count');

        // Créer conteneur pour toasts légers
        if (!toastContainerEl) {
            toastContainerEl = document.createElement('div');
            toastContainerEl.className = 'notif-toast-container';
            document.body.appendChild(toastContainerEl);
        }

        // Événements
        bellBtnEl.addEventListener('click', (e) => {
            e.stopPropagation();
            toggleDropdown();
        });

        document.getElementById('notif-btn-readall')?.addEventListener('click', async (e) => {
            e.stopPropagation();
            await markAllAsRead();
        });

        document.addEventListener('click', (e) => {
            if (dropdownEl && dropdownEl.classList.contains('open')) {
                if (!wrapperEl.contains(e.target)) {
                    closeDropdown();
                }
            }
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && dropdownEl && dropdownEl.classList.contains('open')) {
                closeDropdown();
                bellBtnEl.focus();
            }
        });
    }

    function toggleDropdown() {
        if (!dropdownEl) return;
        const isOpen = dropdownEl.classList.toggle('open');
        bellBtnEl.classList.toggle('active', isOpen);
        bellBtnEl.setAttribute('aria-expanded', String(isOpen));
        if (isOpen) {
            loadRecentNotifications();
        }
    }

    function closeDropdown() {
        if (!dropdownEl) return;
        dropdownEl.classList.remove('open');
        bellBtnEl.classList.remove('active');
        bellBtnEl.setAttribute('aria-expanded', 'false');
    }

    /**
     * Met à jour l'affichage du badge de compteur
     */
    function updateBadge(count) {
        unreadCount = Math.max(0, count);

        if (!badgeEl) return;

        if (unreadCount <= 0) {
            badgeEl.textContent = '0';
            badgeEl.classList.add('hidden');
        } else {
            badgeEl.textContent = unreadCount > 9 ? '9+' : String(unreadCount);
            badgeEl.classList.remove('hidden');
            badgeEl.classList.remove('pop');
            void badgeEl.offsetWidth; // force reflow
            badgeEl.classList.add('pop');
        }

        if (unreadCountPillEl) {
            unreadCountPillEl.textContent = unreadCount === 0
                ? '0 non lue'
                : (unreadCount === 1 ? '1 non lue' : `${unreadCount} non lues`);
        }
    }

    /**
     * Joue une animation de vibration discrète sur la cloche
     */
    function shakeBell() {
        if (!bellBtnEl) return;
        bellBtnEl.classList.remove('bell-shake');
        void bellBtnEl.offsetWidth;
        bellBtnEl.classList.add('bell-shake');
    }

    /**
     * Affiche un toast discret en bas à droite
     */
    function showToast(notification) {
        if (!toastContainerEl) return;

        const toast = document.createElement('div');
        toast.className = 'notif-toast';
        toast.setAttribute('role', 'status');

        const iconHtml = getNotificationIcon(notification.type);
        const iconClass = `notif-type-${notification.type || 'OFFRE_CORRESPONDANTE'}`;

        toast.innerHTML = `
            <div class="notif-toast-icon ${iconClass}">
                ${iconHtml}
            </div>
            <div class="notif-toast-body">
                <div class="notif-toast-title">${esc(notification.titre)}</div>
                <div class="notif-toast-msg">${esc(notification.message)}</div>
            </div>
            <button type="button" class="notif-toast-close" aria-label="Fermer">&times;</button>
        `;

        toast.querySelector('.notif-toast-close').addEventListener('click', (e) => {
            e.stopPropagation();
            dismissToast(toast);
        });

        toast.addEventListener('click', async () => {
            await handleNotificationClick(notification);
            dismissToast(toast);
        });

        toastContainerEl.appendChild(toast);

        // Disparition automatique au bout de 5.5s
        setTimeout(() => {
            dismissToast(toast);
        }, 5500);
    }

    function dismissToast(toast) {
        if (!toast || toast.classList.contains('hiding')) return;
        toast.classList.add('hiding');
        setTimeout(() => {
            if (toast.parentNode) {
                toast.parentNode.removeChild(toast);
            }
        }, 300);
    }

    /**
     * Charge le compteur de non lues depuis l'API REST
     */
    async function fetchUnreadCount() {
        if (!currentUserId) return;
        try {
            const res = await fetch(`/api/utilisateurs/${currentUserId}/notifications/non-lues/count`, {
                headers: { 'Accept': 'application/json' }
            });
            if (res.ok) {
                const data = await res.json();
                if (data && typeof data.count === 'number') {
                    updateBadge(data.count);
                }
            }
        } catch (_) {}
    }

    /**
     * Charge les dernières notifications pour le dropdown
     */
    async function loadRecentNotifications() {
        if (!currentUserId || !listEl) return;
        try {
            const res = await fetch(`/api/utilisateurs/${currentUserId}/notifications?page=0&size=7`, {
                headers: { 'Accept': 'application/json' }
            });
            if (res.ok) {
                const page = await res.json();
                const items = page.content || [];
                notificationsCache = items;
                renderList(items);
            }
        } catch (_) {}
    }

    /**
     * Rendu de la liste des notifications dans le dropdown
     */
    function renderList(items) {
        if (!listEl) return;

        if (!items || items.length === 0) {
            listEl.innerHTML = `
                <li class="notif-empty">
                    <div class="notif-empty-icon">
                        <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"></path><path d="M13.73 21a2 2 0 0 1-3.46 0"></path></svg>
                    </div>
                    <div class="notif-empty-text">Aucune notification pour l'instant</div>
                    <div class="notif-empty-sub">Vous serez alerté dès qu'une activité vous concerne.</div>
                </li>
            `;
            return;
        }

        listEl.innerHTML = items.map(n => {
            const iconHtml = getNotificationIcon(n.type);
            const iconClass = `notif-type-${n.type}`;
            const unreadClass = n.lue ? '' : 'unread';
            const unreadDot = n.lue ? '' : '<span class="notif-unread-dot" aria-hidden="true"></span>';
            const timeStr = formatRelativeTime(n.dateCreation);

            return `
                <li class="notif-item ${unreadClass}" data-id="${n.id}">
                    <div class="notif-type-icon ${iconClass}">
                        ${iconHtml}
                    </div>
                    <div class="notif-content">
                        <div class="notif-title-row">
                            <span class="notif-title" title="${esc(n.titre)}">${esc(n.titre)}</span>
                            <span class="notif-time">${esc(timeStr)}</span>
                        </div>
                        <div class="notif-message">${esc(n.message)}</div>
                    </div>
                    ${unreadDot}
                </li>
            `;
        }).join('');

        // Attacher clics sur les items
        listEl.querySelectorAll('.notif-item').forEach(itemEl => {
            itemEl.addEventListener('click', async () => {
                const id = Number(itemEl.dataset.id);
                const notif = notificationsCache.find(n => n.id === id);
                if (notif) {
                    await handleNotificationClick(notif);
                }
            });
        });
    }

    /**
     * Traite le clic sur une notification : marque comme lue et redirige vers le lien
     */
    async function handleNotificationClick(notification) {
        if (!notification) return;

        // Si non lue, marquer comme lue
        if (!notification.lue) {
            try {
                await fetch(`/api/notifications/${notification.id}/lue`, {
                    method: 'PATCH',
                    headers: { 'Accept': 'application/json' }
                });
                notification.lue = true;
                updateBadge(unreadCount - 1);
                // Mise à jour de l'élément dans la liste
                if (listEl) {
                    const row = listEl.querySelector(`[data-id="${notification.id}"]`);
                    if (row) {
                        row.classList.remove('unread');
                        const dot = row.querySelector('.notif-unread-dot');
                        if (dot) dot.remove();
                    }
                }
            } catch (_) {}
        }

        closeDropdown();

        // Redirection vers le lien
        if (notification.lien) {
            window.location.href = notification.lien;
        }
    }

    /**
     * Tout marquer comme lu
     */
    async function markAllAsRead() {
        if (!currentUserId) return;
        try {
            await fetch(`/api/utilisateurs/${currentUserId}/notifications/tout-lire`, {
                method: 'PATCH',
                headers: { 'Accept': 'application/json' }
            });
            updateBadge(0);
            notificationsCache.forEach(n => n.lue = true);
            if (listEl) {
                listEl.querySelectorAll('.notif-item').forEach(el => {
                    el.classList.remove('unread');
                    const dot = el.querySelector('.notif-unread-dot');
                    if (dot) dot.remove();
                });
            }
        } catch (_) {}
    }

    /**
     * Connexion WebSocket + STOMP (avec repli SockJS)
     */
    function connectWebSocket() {
        if (!currentUserId) return;

        // Vérification de la disponibilité de SockJS et Stomp
        if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
            console.warn('[NotificationBell] SockJS ou Stomp introuvable. Activation du repli de scrutation (polling).');
            startFallbackPolling();
            return;
        }

        try {
            // URL avec query parameter pour attribuer le Principal utilisateur
            const socketUrl = `/ws?userId=${currentUserId}`;
            const socket = new SockJS(socketUrl);
            stompClient = Stomp.over(socket);

            // Désactiver les logs verbeux de Stomp en production
            stompClient.debug = null;

            const headers = {
                'userId': String(currentUserId)
            };

            stompClient.connect(headers, () => {
                isConnected = true;
                reconnectAttempts = 0;
                stopFallbackPolling();

                // Abonnement à la file personnelle /user/queue/notifications
                stompClient.subscribe('/user/queue/notifications', (message) => {
                    try {
                        const payload = JSON.parse(message.body);
                        onReceiveNotification(payload);
                    } catch (e) {
                        console.error('[NotificationBell] Erreur de parsing du message:', e);
                    }
                });
            }, (error) => {
                isConnected = false;
                scheduleReconnect();
            });
        } catch (e) {
            isConnected = false;
            scheduleReconnect();
        }
    }

    /**
     * Traitement à la réception d'une notification en temps réel
     */
    function onReceiveNotification(notification) {
        // Incrémentation du badge
        updateBadge(unreadCount + 1);

        // Vibration discrète de la cloche
        shakeBell();

        // Affichage d'un toast
        showToast(notification);

        // Ajout au début du cache local
        notificationsCache.unshift(notification);
        if (notificationsCache.length > 8) notificationsCache.pop();

        // Mise à jour de la liste si le panneau est ouvert
        if (dropdownEl && dropdownEl.classList.contains('open')) {
            renderList(notificationsCache);
        }

        // Appel des callbacks abonnés
        callbacks.forEach(cb => {
            try { cb(notification); } catch (_) {}
        });
    }

    /**
     * Reconnexion automatique avec backoff exponentiel
     */
    function scheduleReconnect() {
        if (reconnectTimer) clearTimeout(reconnectTimer);

        // Active le polling immédiat en attendant la reconnexion
        startFallbackPolling();

        const delay = Math.min(30000, 1500 * Math.pow(1.5, reconnectAttempts));
        reconnectAttempts++;

        reconnectTimer = setTimeout(() => {
            connectWebSocket();
        }, delay);
    }

    /**
     * Repli propre : polling toutes les 35 secondes si WebSocket indisponible
     */
    function startFallbackPolling() {
        if (fallbackPollTimer) return;
        fetchUnreadCount();
        fallbackPollTimer = setInterval(() => {
            fetchUnreadCount();
        }, 35000);
    }

    function stopFallbackPolling() {
        if (fallbackPollTimer) {
            clearInterval(fallbackPollTimer);
            fallbackPollTimer = null;
        }
    }

    /**
     * Initialisation du composant
     */
    function init(options = {}) {
        let userId = options.userId;

        // Auto-détection de l'utilisateur connecté via MatchingCVSession si non fourni
        if (!userId && window.MatchingCVSession) {
            const user = window.MatchingCVSession.read();
            if (user && user.id) {
                userId = user.id;
            }
        }

        if (!userId) {
            return;
        }

        currentUserId = Number(userId);

        // Recherche du conteneur dans la navbar
        let container = null;
        if (options.containerSelector) {
            container = document.querySelector(options.containerSelector);
        }
        if (!container) {
            container = document.querySelector('.nav-cta') || document.querySelector('.navbar .nav-inner');
        }

        if (container) {
            renderBellDOM(container);
            fetchUnreadCount();
            connectWebSocket();
        } else {
            // Tentative ultérieure si le DOM n'est pas encore prêt
            window.addEventListener('DOMContentLoaded', () => {
                init(options);
            });
        }
    }

    /**
     * Enregistre un écouteur de notification
     */
    function onNotification(callback) {
        if (typeof callback === 'function') {
            callbacks.push(callback);
        }
    }

    return {
        init,
        onNotification,
        markAllAsRead,
        markAsRead: handleNotificationClick,
        getUnreadCount: () => unreadCount
    };
})();

// Auto-démarrage si l'utilisateur est déjà présent dans la session
document.addEventListener('DOMContentLoaded', () => {
    if (window.MatchingCVSession) {
        const user = window.MatchingCVSession.read();
        // N'initialiser automatiquement que pour l'espace étudiant dans ce lot
        if (user && user.role === 'ETUDIANT') {
            window.NotificationBell.init({ userId: user.id });
        }
    }
});
