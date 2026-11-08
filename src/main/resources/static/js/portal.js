'use strict';

// Keep portal labels and registration links aligned with the selected role.
// auth.js checks the API role before saving a login session.
document.addEventListener('DOMContentLoaded', () => {
    const requested = new URLSearchParams(location.search).get('espace');
    const portal = requested === 'recruteur' ? 'recruteur' : 'utilisateur';
    const label = document.getElementById('portal-label');
    const registerRole = document.getElementById('register-role');
    function updateContext(value) {
        if (label) label.textContent = value === 'recruteur' ? 'Espace recruteur' : 'Espace utilisateur';
        document.querySelectorAll('a[href^="/register.html"], a[href^="/login.html"]').forEach(link => {
            const url = new URL(link.href);
            url.searchParams.set('espace', value);
            link.href = url.pathname + url.search;
        });
    }
    updateContext(portal);
    if (registerRole) {
        // auth.js installs the role handlers at DOMContentLoaded before this one.
        document.getElementById(portal === 'recruteur' ? 'role-recruteur' : 'role-etudiant')?.click();
        const buttons = document.querySelectorAll('.auth-role-btn');
        function syncSelection() {
            buttons.forEach(button => button.setAttribute('aria-pressed', String(button.dataset.role === registerRole.value)));
            updateContext(registerRole.value === 'RECRUTEUR' ? 'recruteur' : 'utilisateur');
        }
        buttons.forEach(button => button.addEventListener('click', syncSelection));
        syncSelection();
    }
});
