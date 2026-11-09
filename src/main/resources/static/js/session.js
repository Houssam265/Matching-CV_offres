'use strict';

/* Contrôle commun des sessions et des pages privées du navigateur. */
window.MatchingCVSession = (() => {
    const storageKey = 'matchingcv_user';
    const dashboards = {
        ETUDIANT: '/dashboard-etudiant.html',
        RECRUTEUR: '/dashboard-recruteur.html',
        ADMIN: '/admin-dashboard.html'
    };

    function read() {
        try {
            const user = JSON.parse(localStorage.getItem(storageKey) || 'null');
            if (!user || !Object.hasOwn(dashboards, user.role)) return null;
            const id = Number(user.id);
            if (!Number.isSafeInteger(id) || id <= 0) return null;
            return { ...user, id };
        } catch (_) {
            return null;
        }
    }

    function redirect(url) {
        document.documentElement.style.visibility = 'hidden';
        window.location.replace(url);
    }

    function requireSession(role) {
        const user = read();
        if (!user) {
            redirect(role === 'RECRUTEUR' ? '/login.html?espace=recruteur' : role === 'ETUDIANT' ? '/login.html?espace=utilisateur' : role === 'ADMIN' ? '/admin-login.html' : '/login.html');
            return null;
        }
        if (role && user.role !== role) {
            redirect(dashboards[user.role]);
            return null;
        }
        return user;
    }

    function protect(role) {
        const initialUser = requireSession(role);
        if (!initialUser) return null;
        fetch('/api/auth/me').then(async response => {
            if (response.status === 401 || response.status === 403) {
                localStorage.removeItem(storageKey);
                redirect(role === 'ADMIN' ? '/admin-login.html' : (role === 'RECRUTEUR' ? '/login.html?espace=recruteur' : '/login.html'));
                return;
            }
            if (!response.ok) return;
            const actual = await response.json();
            localStorage.setItem(storageKey, JSON.stringify(actual));
            if (actual.id !== initialUser.id || actual.role !== initialUser.role) redirect(dashboards[actual.role] || '/login.html');
        }).catch(() => { /* Individual API requests display their network errors. */ });
        const recheck = () => {
            const user = requireSession(role);
            if (user && (user.id !== initialUser.id || user.role !== initialUser.role)) {
                document.documentElement.style.visibility = 'hidden';
                window.location.reload();
            }
        };
        // Le retour arrière peut restaurer une page sans recharger ses scripts.
        window.addEventListener('pageshow', recheck);
        window.addEventListener('storage', event => {
            if (event.key === storageKey || event.key === null) recheck();
        });
        return initialUser;
    }

    async function logout() {
        const role = read()?.role;
        try {
            const response=await fetch('/api/auth/logout',{method:'POST'});
            if(!response.ok) throw new Error('Déconnexion indisponible. Réessayez.');
        } catch (error) { window.alert(error.message); return; }
        localStorage.removeItem(storageKey);
        redirect(role === 'RECRUTEUR' ? '/login.html?espace=recruteur' : role === 'ADMIN' ? '/admin-login.html' : '/login.html?espace=utilisateur');
    }

    return { read, getUser: read, require: requireSession, protect, logout };
})();
