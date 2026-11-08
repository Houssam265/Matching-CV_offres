'use strict';
document.addEventListener('DOMContentLoaded',async()=>{
 const user=MatchingCVSession.require('ETUDIANT');if(!user)return;
 const list=document.getElementById('applications-list'),status=document.getElementById('workflow-status');
 const el=(tag,value,cls)=>{const e=document.createElement(tag);e.textContent=value;if(cls)e.className=cls;return e;};
 const get=async url=>{const r=await fetch(url);if(!r.ok)throw new Error('Chargement indisponible. Réessayez.');return r.json().catch(()=>null);};
 const reload=el('button','Actualiser');reload.type='button';list.before(reload);
 async function load(){reload.disabled=true;status.textContent='Chargement…';list.replaceChildren();
  try{const applications=await get(`/api/etudiants/${user.id}/candidatures`);let shown=0;
   for(const c of applications){
    if(!['ACCEPTEE','ENTRETIEN'].includes(c.statut))continue;
    const row=el('article','','interview-card');row.id='candidature-'+c.id;row.append(el('h3',c.offreTitre||'Votre candidature'),el('p',c.entreprise||''));list.append(row);shown++;
    try{const i=await get(`/api/candidatures/${c.id}/entretien`);
     if(!i){row.append(el('p','Le recruteur n’a pas encore fixé le rendez-vous. Une notification vous informera de sa planification si vos notifications sont activées.'));continue;}
     const past=new Date(i.startsAt)<new Date();row.append(el('span',past?'Rendez-vous passé':'Entretien à venir','interview-badge'));
     const box=el('div','','interview-info');box.append(el('strong',new Date(i.startsAt).toLocaleString('fr-FR',{dateStyle:'full',timeStyle:'short'})),el('p','Heure locale : '+Intl.DateTimeFormat().resolvedOptions().timeZone),el('p',i.location),el('p',i.instructions||'Aucune préparation particulière indiquée.','interview-instructions'));row.append(box);
     let remote=false;try{const url=new URL(i.location);if(['https:','http:'].includes(url.protocol)){const link=el('a',past?'Consulter le lien de visioconférence':'Rejoindre la visioconférence');link.href=url.href;link.target='_blank';link.rel='noopener noreferrer';row.append(link);remote=true;}}catch(_){}
     if(!past)row.append(el('p',remote?'À l’heure prévue, ouvrez ce lien et autorisez votre caméra et votre microphone dans le service de visioconférence.':'Présentez-vous à cette adresse à l’heure indiquée. Consultez les instructions du recruteur avant votre départ.'));
    }catch(e){row.append(el('p',e.message,'interview-error'));}
   }
   if(!shown)list.append(el('p','Aucun entretien pour le moment. Vos prochains rendez-vous apparaîtront ici.','interview-empty'));
   status.textContent='';const id=location.hash.slice(1);if(id)document.getElementById(id)?.scrollIntoView({block:'center'});
  }catch(e){status.textContent=e.message;}finally{reload.disabled=false;}
 }
 reload.onclick=load;await load();
});
