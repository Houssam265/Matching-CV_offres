'use strict';
document.addEventListener('DOMContentLoaded',()=>{
 const roots=[...document.querySelectorAll('.modal-overlay,.candidat-modal-backdrop')];
 const previous=new WeakMap();
 const visible=el=>!el.hidden&&el.getAttribute('aria-hidden')!=='true'&&getComputedStyle(el).display!=='none'&&(el.classList.contains('open')||el.classList.contains('active')||getComputedStyle(el).opacity==='1');
 const controls=el=>[...el.querySelectorAll('a[href],button,input,select,textarea,[tabindex="0"]')].filter(e=>!e.disabled&&e.getClientRects().length);
 for(const root of roots){const dialog=root.querySelector('[role="dialog"]')||root;dialog.setAttribute('role','dialog');dialog.setAttribute('aria-modal','true');if(!dialog.hasAttribute('aria-label')&&!dialog.hasAttribute('aria-labelledby'))dialog.setAttribute('aria-label',root.querySelector('h2,h3')?.textContent||'Détails');
  let opened=false;
  new MutationObserver(()=>{const now=visible(root);if(now&&!opened){previous.set(root,document.activeElement);opened=true;const first=controls(dialog)[0];if(first)first.focus();}else if(!now&&opened){opened=false;previous.get(root)?.focus();}}).observe(root,{attributes:true,attributeFilter:['class','style','aria-hidden','hidden']});
  root.addEventListener('keydown',e=>{if(e.key!=='Tab'||!visible(root))return;const items=controls(dialog);if(!items.length)return;const first=items[0],last=items[items.length-1];if(e.shiftKey&&(document.activeElement===first||!dialog.contains(document.activeElement))){e.preventDefault();last.focus();}else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}});
 }
});
