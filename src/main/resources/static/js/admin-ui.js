'use strict';
window.adminEscapeHtml = value => String(value).replace(/[&<>"']/g, char => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[char]));
window.adminReadJson=async response=>{const body=await response.json();if(!response.ok)throw new Error(body?.message||'Opération indisponible. Réessayez.');return body;};
document.addEventListener('DOMContentLoaded',()=>{
 document.body.classList.add('admin-portal');
 document.querySelectorAll('table').forEach(table=>{if(table.parentElement.classList.contains('admin-table-scroll'))return;const wrap=document.createElement('div');wrap.className='admin-table-scroll';wrap.tabIndex=0;wrap.setAttribute('role','region');wrap.setAttribute('aria-label','Tableau défilant');table.before(wrap);wrap.append(table);});
 document.querySelectorAll('.sidebar-link').forEach(link=>{const active=new URL(link.href,location.href).pathname===location.pathname;link.classList.toggle('active',active);if(active)link.setAttribute('aria-current','page');else link.removeAttribute('aria-current');});
 const actions=document.getElementById('topbar-nav-cta')||document.querySelector('.topbar-right');const logout=document.getElementById('btn-topbar-logout');if(actions&&logout)actions.append(logout);
 document.querySelectorAll('.modal-overlay').forEach(modal=>{modal.setAttribute('role','dialog');modal.setAttribute('aria-modal','true');modal.setAttribute('aria-label',modal.querySelector('h2,h3')?.textContent||'Confirmation');let prior,open=false;
  const fields=()=>[...modal.querySelectorAll('button,input,select,textarea,a[href]')].filter(e=>!e.disabled&&e.getClientRects().length);
  new MutationObserver(()=>{const visible=modal.classList.contains('open');if(visible&&!open){prior=document.activeElement;fields()[0]?.focus();}if(!visible&&open)prior?.focus();open=visible;}).observe(modal,{attributes:true,attributeFilter:['class']});
  modal.addEventListener('keydown',e=>{if(e.key==='Escape'){modal.classList.remove('open');return;}if(e.key==='Tab'){const f=fields();if(!f.length)return;if(e.shiftKey&&document.activeElement===f[0]){e.preventDefault();f.at(-1).focus();}else if(!e.shiftKey&&document.activeElement===f.at(-1)){e.preventDefault();f[0].focus();}}});
 });
});
