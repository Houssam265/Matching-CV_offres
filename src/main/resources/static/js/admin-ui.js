'use strict';
window.adminEscapeHtml = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
window.adminReadJson=async response=>{const body=await response.json();if(!response.ok)throw new Error(body?.message||'Opération indisponible. Réessayez.');return body;};
document.addEventListener('DOMContentLoaded',()=>{
 document.body.classList.add('admin-portal');
 document.querySelectorAll('table').forEach(table=>{if(table.parentElement.classList.contains('admin-table-scroll'))return;const wrap=document.createElement('div');wrap.className='admin-table-scroll';wrap.tabIndex=0;wrap.setAttribute('role','region');wrap.setAttribute('aria-label','Tableau défilant');table.before(wrap);wrap.append(table);});
 document.querySelectorAll('.sidebar-link').forEach(link=>{const active=new URL(link.href,location.href).pathname===location.pathname;link.classList.toggle('active',active);if(active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
 const actions=document.getElementById('topbar-nav-cta')||document.querySelector('.topbar-right');const logout=document.getElementById('btn-topbar-logout');if(actions&&logout)actions.append(logout);
 // Barre du haut identique aux autres portails : bouton de déconnexion avec icône + pastille utilisateur.
 if(logout){logout.removeAttribute('style');logout.className='topbar-logout';logout.innerHTML='<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 5H4v14h5M13 8l4 4-4 4M8 12h9"/></svg><span>Déconnexion</span>';}
 document.querySelector('.admin-topbar-badge')?.remove();
 const name=document.getElementById('nav-admin-name');
 if(name&&!name.closest('.topbar-user-pill')){
  const user=window.MatchingCVSession?.read?.()||{};
  const initials=((user.prenom||'A').charAt(0)+(user.nom||'').charAt(0)).toUpperCase();
  const pill=document.createElement('span');pill.className='topbar-user-pill';
  pill.innerHTML=`<span class="topbar-user-pill-avatar" aria-hidden="true">${adminEscapeHtml(initials)}</span>`;
  name.className='topbar-user-pill-name';name.before(pill);pill.append(name);
 }
 document.querySelectorAll('.modal-overlay').forEach(modal=>{modal.setAttribute('role','dialog');modal.setAttribute('aria-modal','true');modal.setAttribute('aria-label',modal.querySelector('h2,h3')?.textContent||'Confirmation');let prior,open=false;
  const fields=()=>[...modal.querySelectorAll('button,input,select,textarea,a[href]')].filter(e=>!e.disabled&&e.getClientRects().length);
  new MutationObserver(()=>{const visible=modal.classList.contains('open');if(visible&&!open){prior=document.activeElement;fields()[0]?.focus();}if(!visible&&open)prior?.focus();open=visible;}).observe(modal,{attributes:true,attributeFilter:['class']});
  modal.addEventListener('keydown',e=>{if(e.key==='Escape'){modal.classList.remove('open');return;}if(e.key==='Tab'){const f=fields();if(!f.length)return;if(e.shiftKey&&document.activeElement===f[0]){e.preventDefault();f.at(-1).focus();}else if(!e.shiftKey&&document.activeElement===f.at(-1)){e.preventDefault();f[0].focus();}}});
 });
});
