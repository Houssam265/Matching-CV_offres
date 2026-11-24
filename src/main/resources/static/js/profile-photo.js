'use strict';

/*
 * Photos de profil (tous les rôles).
 * - /api/account/photo/{id} renvoie toujours une image : la photo envoyée,
 *   sinon un avatar aux initiales. Aucune image cassée possible.
 * - Les avatars de l'utilisateur connecté sont peints automatiquement ;
 *   tout élément [data-photo-user="id"] reçoit la photo de cet utilisateur.
 * - Les avatars [data-photo-edit] (et la pastille admin) ouvrent l'éditeur de photo.
 * - Choix de la photo → cadrage portrait 4:5 (format CV) → envoi d'un JPEG déjà recadré :
 *   la photo est identique sur le site et dans le CV téléchargé.
 */
window.MatchingCVPhoto = (() => {
    const VERSION_KEY = 'matchingcv_photo_v';
    const ACCEPT = ['image/jpeg', 'image/png', 'image/webp'];
    const MAX = 2 * 1024 * 1024;            // photo envoyée (déjà recadrée)
    const MAX_SOURCE = 15 * 1024 * 1024;    // photo d'origine choisie par l'utilisateur
    const OUT_W = 480, OUT_H = 600;         // portrait 4:5
    const me = () => window.MatchingCVSession?.read?.();

    function version() {
        try { return localStorage.getItem(VERSION_KEY) || '0'; } catch (_) { return '0'; }
    }

    function url(userId) {
        if (!userId) return null;
        const own = me()?.id === Number(userId);
        return `/api/account/photo/${encodeURIComponent(userId)}${own ? `?v=${version()}` : ''}`;
    }

    function paint(el, userId) {
        const src = url(userId);
        if (!el || !src) return;
        el.style.backgroundImage = `url("${src}")`;
        el.classList.add('has-photo');
    }

    const OWN_SELECTORS = ['#topbar-user-avatar', '.topbar-user-pill-avatar', '#sidebar-user-avatar', '#dr-av', '#profil-avatar', '[data-photo-self]'];

    function paintAll(root = document) {
        const user = me();
        if (user) root.querySelectorAll(OWN_SELECTORS.join(',')).forEach(el => paint(el, user.id));
        root.querySelectorAll('[data-photo-user]').forEach(el => paint(el, el.dataset.photoUser));
    }

    // ------------------------------------------------------------------ Éditeur (choix → cadrage → envoi)
    let dialog;
    function buildDialog() {
        dialog = document.createElement('dialog');
        dialog.className = 'photo-dialog';
        dialog.setAttribute('aria-labelledby', 'photo-dialog-title');
        dialog.innerHTML = `
            <form method="dialog" class="photo-dialog-body">
                <h2 id="photo-dialog-title">Photo de profil</h2>
                <div class="photo-step" data-step="view">
                    <p class="photo-dialog-hint">Une photo nette, de face, sur fond neutre. Vous choisirez ensuite le cadrage.</p>
                    <div class="photo-dialog-preview" data-photo-self></div>
                    <div class="photo-dialog-actions">
                        <button type="button" class="photo-btn photo-btn-primary" data-act="choose">Choisir une photo</button>
                        <button type="button" class="photo-btn photo-btn-danger" data-act="remove" hidden>Supprimer</button>
                        <button type="submit" class="photo-btn" value="close">Fermer</button>
                    </div>
                </div>
                <div class="photo-step" data-step="crop" hidden>
                    <p class="photo-dialog-hint">Faites glisser la photo pour la placer, puis ajustez le zoom. Le cadre correspond exactement à votre CV.</p>
                    <div class="photo-crop-stage" tabindex="0" role="application" aria-label="Cadrage de la photo : flèches pour déplacer, plus et moins pour zoomer">
                        <img alt="" draggable="false">
                        <span class="photo-crop-grid" aria-hidden="true"></span>
                    </div>
                    <label class="photo-zoom">
                        <span>Zoom</span>
                        <input type="range" min="1" max="4" step="0.01" value="1" data-act="zoom">
                    </label>
                    <div class="photo-dialog-actions">
                        <button type="button" class="photo-btn photo-btn-primary" data-act="validate">Valider le cadrage</button>
                        <button type="button" class="photo-btn" data-act="back">Annuler</button>
                    </div>
                </div>
                <p class="photo-dialog-status" role="status" aria-live="polite"></p>
                <input type="file" accept="${ACCEPT.join(',')}" hidden>
            </form>`;
        document.body.append(dialog);
        const input = dialog.querySelector('input[type=file]');
        const status = dialog.querySelector('.photo-dialog-status');
        const remove = dialog.querySelector('[data-act=remove]');
        const stage = dialog.querySelector('.photo-crop-stage');
        const img = stage.querySelector('img');
        const zoom = dialog.querySelector('[data-act=zoom]');
        const setBusy = busy => dialog.querySelectorAll('button, input').forEach(b => { b.disabled = busy; });
        const step = name => dialog.querySelectorAll('.photo-step').forEach(s => { s.hidden = s.dataset.step !== name; });

        // État du cadrage : échelle et position (px) de l'image dans le cadre
        const crop = { scale: 1, min: 1, x: 0, y: 0, url: null };
        const dims = () => ({ w: stage.clientWidth, h: stage.clientHeight });
        function apply() {
            const { w, h } = dims();
            const iw = img.naturalWidth * crop.scale, ih = img.naturalHeight * crop.scale;
            crop.x = Math.min(0, Math.max(w - iw, crop.x)); // l'image couvre toujours tout le cadre
            crop.y = Math.min(0, Math.max(h - ih, crop.y));
            img.style.width = `${iw}px`;
            img.style.height = `${ih}px`;
            img.style.transform = `translate(${crop.x}px, ${crop.y}px)`;
        }
        function setScale(next, cx, cy) {
            const { w, h } = dims();
            const s = Math.min(crop.min * 4, Math.max(crop.min, next));
            const px = cx ?? w / 2, py = cy ?? h / 2;
            crop.x = px - (px - crop.x) * (s / crop.scale); // zoom centré sur le point visé
            crop.y = py - (py - crop.y) * (s / crop.scale);
            crop.scale = s;
            zoom.value = String(s / crop.min);
            apply();
        }

        let drag = null;
        stage.addEventListener('pointerdown', e => {
            drag = { x: e.clientX, y: e.clientY, ox: crop.x, oy: crop.y };
            stage.setPointerCapture(e.pointerId);
            stage.classList.add('dragging');
        });
        stage.addEventListener('pointermove', e => {
            if (!drag) return;
            crop.x = drag.ox + e.clientX - drag.x;
            crop.y = drag.oy + e.clientY - drag.y;
            apply();
        });
        const endDrag = () => { drag = null; stage.classList.remove('dragging'); };
        stage.addEventListener('pointerup', endDrag);
        stage.addEventListener('pointercancel', endDrag);
        stage.addEventListener('wheel', e => {
            e.preventDefault();
            const r = stage.getBoundingClientRect();
            setScale(crop.scale * (e.deltaY < 0 ? 1.08 : 1 / 1.08), e.clientX - r.left, e.clientY - r.top);
        }, { passive: false });
        stage.addEventListener('keydown', e => {
            const moves = { ArrowLeft: [10, 0], ArrowRight: [-10, 0], ArrowUp: [0, 10], ArrowDown: [0, -10] };
            if (moves[e.key]) { e.preventDefault(); crop.x += moves[e.key][0]; crop.y += moves[e.key][1]; apply(); }
            else if (e.key === '+' || e.key === '=') { e.preventDefault(); setScale(crop.scale * 1.1); }
            else if (e.key === '-') { e.preventDefault(); setScale(crop.scale / 1.1); }
        });
        zoom.addEventListener('input', () => setScale(crop.min * Number(zoom.value)));

        async function startCrop(file) {
            if (crop.url) URL.revokeObjectURL(crop.url);
            crop.url = URL.createObjectURL(file);
            img.src = crop.url;
            try { await img.decode(); }
            catch (_) { status.textContent = 'Cette image ne peut pas être lue. Essayez une photo JPEG ou PNG.'; return; }
            step('crop');
            status.textContent = '';
            const { w, h } = dims();
            crop.min = Math.max(w / img.naturalWidth, h / img.naturalHeight);
            crop.scale = crop.min;
            crop.x = (w - img.naturalWidth * crop.scale) / 2;
            crop.y = (h - img.naturalHeight * crop.scale) / 3; // le visage est souvent dans le tiers haut
            zoom.value = '1';
            apply();
            stage.focus({ preventScroll: true });
        }

        function exportCrop() {
            const ratio = OUT_W / dims().w;
            const canvas = document.createElement('canvas');
            canvas.width = OUT_W;
            canvas.height = OUT_H;
            const ctx = canvas.getContext('2d');
            ctx.fillStyle = '#ffffff'; // fond blanc pour les PNG transparents
            ctx.fillRect(0, 0, OUT_W, OUT_H);
            ctx.imageSmoothingQuality = 'high';
            ctx.drawImage(img, crop.x * ratio, crop.y * ratio, img.naturalWidth * crop.scale * ratio, img.naturalHeight * crop.scale * ratio);
            return new Promise(res => canvas.toBlob(res, 'image/jpeg', 0.9));
        }

        dialog.querySelector('[data-act=choose]').addEventListener('click', () => input.click());
        input.addEventListener('change', () => {
            const file = input.files?.[0];
            input.value = '';
            if (!file) return;
            if (!ACCEPT.includes(file.type) || file.size > MAX_SOURCE) {
                status.textContent = 'Choisissez une image JPEG, PNG ou WebP de 15 Mo maximum.';
                return;
            }
            startCrop(file);
        });
        dialog.querySelector('[data-act=back]').addEventListener('click', () => { step('view'); status.textContent = ''; });
        dialog.querySelector('[data-act=validate]').addEventListener('click', async () => {
            setBusy(true);
            status.textContent = 'Envoi en cours…';
            try {
                const blob = await exportCrop();
                if (!blob || blob.size > MAX) throw new Error('La photo recadrée est trop lourde. Essayez une autre image.');
                const body = new FormData();
                body.append('file', blob, 'photo.jpg');
                const r = await fetch('/api/account/photo', { method: 'POST', body });
                const d = await r.json().catch(() => ({}));
                if (!r.ok) throw new Error(d.message || 'La photo n’a pas pu être enregistrée.');
                changed();
                step('view');
                status.textContent = 'Photo mise à jour.';
                remove.hidden = false;
            } catch (e) { status.textContent = e.message; }
            finally { setBusy(false); }
        });
        remove.addEventListener('click', async () => {
            setBusy(true);
            status.textContent = 'Suppression…';
            try {
                const r = await fetch('/api/account/photo', { method: 'DELETE' });
                if (!r.ok) throw new Error('La photo n’a pas pu être supprimée.');
                changed();
                status.textContent = 'Photo supprimée : vos initiales sont affichées.';
                remove.hidden = true;
            } catch (e) { status.textContent = e.message; }
            finally { setBusy(false); }
        });
        dialog.addEventListener('close', () => {
            step('view');
            if (crop.url) { URL.revokeObjectURL(crop.url); crop.url = null; }
        });
        return dialog;
    }

    function changed() {
        try { localStorage.setItem(VERSION_KEY, String(Date.now())); } catch (_) { /* sans stockage : repeint quand même */ }
        paintAll();
        document.dispatchEvent(new CustomEvent('matchingcv:photo-changed'));
    }

    async function open() {
        const d = dialog || buildDialog();
        d.querySelector('.photo-dialog-status').textContent = '';
        paint(d.querySelector('.photo-dialog-preview'), me()?.id);
        const remove = d.querySelector('[data-act=remove]');
        remove.hidden = true;
        if (typeof d.showModal === 'function') d.showModal(); else d.setAttribute('open', '');
        try {
            const r = await fetch('/api/account');
            if (r.ok) remove.hidden = !(await r.json()).hasPhoto;
        } catch (_) { /* le bouton reste masqué */ }
    }

    function makeEditable(el) {
        if (!el || el.dataset.photoEditBound) return;
        el.dataset.photoEditBound = '1';
        el.classList.add('photo-editable');
        el.setAttribute('role', 'button');
        el.setAttribute('tabindex', '0');
        el.setAttribute('title', 'Changer la photo de profil');
        el.setAttribute('aria-label', 'Changer la photo de profil');
        const go = e => { e.preventDefault(); e.stopPropagation(); open(); };
        el.addEventListener('click', go);
        el.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') go(e); });
    }

    function setup() {
        paintAll();
        document.querySelectorAll('[data-photo-edit], #dr-av, #profil-avatar').forEach(makeEditable);
        // Espace admin : la pastille de la barre du haut ouvre aussi l'éditeur.
        if (me()?.role === 'ADMIN') document.querySelectorAll('.topbar-user-pill').forEach(pill => {
            pill.style.cursor = 'pointer';
            makeEditable(pill);
        });
        // Les listes chargées dynamiquement (candidats, messages…) sont repeintes à la volée.
        new MutationObserver(records => {
            for (const r of records) for (const n of r.addedNodes) if (n.nodeType === 1) {
                if (n.matches?.('[data-photo-user]')) paint(n, n.dataset.photoUser);
                if (n.querySelectorAll) {
                    n.querySelectorAll('[data-photo-user]').forEach(el => paint(el, el.dataset.photoUser));
                    if (n.matches?.('.topbar-user-pill-avatar, #topbar-user-avatar') || n.querySelector?.('.topbar-user-pill-avatar')) paintAll(n.parentNode || document);
                }
            }
        }).observe(document.body, { childList: true, subtree: true });
    }

    // Après les autres scripts DOMContentLoaded (barre du haut admin construite par admin-ui.js).
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => setTimeout(setup, 0));
    else setTimeout(setup, 0);

    return { url, paint, paintAll, open, makeEditable };
})();
