'use strict';

// Keep portal labels and registration links aligned with the selected role.
// auth.js checks the API role before saving a login session.
document.addEventListener('DOMContentLoaded', () => {
    const requested = new URLSearchParams(location.search).get('espace');
    const label = document.getElementById('portal-label');
    const registerRole = document.getElementById('register-role');

    if (requested === 'admin') {
        if (label) label.textContent = 'Espace Administration';
        const linkToRegister = document.getElementById('link-to-register');
        if (linkToRegister && linkToRegister.parentElement) {
            linkToRegister.parentElement.style.display = 'none';
        }
        return;
    }

    const portal = requested === 'recruteur' ? 'recruteur' : 'utilisateur';

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
