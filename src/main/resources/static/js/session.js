'use strict';

/* Contrôle commun des sessions et des pages privées du navigateur. */
window.MatchingCVSession = (() => {
    const storageKey = 'matchingcv_user';
    const dashboards = {
        ETUDIANT: '/dashboard-etudiant.html',
        RECRUTEUR: '/dashboard-recruteur.html',
        ADMIN: '/competences.html'
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
            redirect('/login.html');
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

    function logout() {
        localStorage.removeItem(storageKey);
        redirect('/login.html');
    }

    return { read, require: requireSession, protect, logout };
})();
