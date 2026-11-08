'use strict';
window.renderRecruiterInterviews=async function(user){
 const list=document.getElementById('applications-list'),status=document.getElementById('workflow-status');
 document.title='Entretiens — MatchingCV';document.getElementById('workflow-title').textContent='Vos entretiens';
 const card=list.closest('section');card.querySelector('h2').textContent='Candidats et rendez-vous';
 const node=(tag,value,cls)=>{const el=document.createElement(tag);if(value)el.textContent=value;if(cls)el.className=cls;return el;};
 const api=async(url,method='GET',body)=>{const r=await fetch(url,{method,headers:{'Content-Type':'application/json'},body:body?JSON.stringify(body):undefined});const data=await r.json().catch(()=>null);if(!r.ok)throw new Error(data?.message||'Chargement impossible. Réessayez.');return data;};
 const editable=c=>['ENVOYEE','VUE','ENTRETIEN','ACCEPTEE'].includes(c.statut);
 const localDate=value=>{const d=new Date(value);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};
 const labels={ENVOYEE:'Nouvelle candidature',VUE:'Consultée',ENTRETIEN:'En entretien',ACCEPTEE:'Acceptée',REFUSEE:'Refusée',RETIREE:'Retirée'};
 const intro=node('p','Planifiez vos rencontres et retrouvez les informations utiles pour chaque candidat.','interview-intro');
 document.getElementById('workflow-title').after(intro);
 const stats=node('div',null,'interview-stats');card.before(stats);
 const toolbar=node('div',null,'interview-toolbar');
 const search=node('input');search.type='search';search.placeholder='Nom du candidat ou poste';search.setAttribute('aria-label','Rechercher un candidat ou un poste');
 const filter=node('select');filter.setAttribute('aria-label','Filtrer les entretiens');
 for(const [v,label] of [['all','Tous les candidats'],['upcoming','Entretiens à venir'],['pending','À planifier'],['past','Entretiens passés'],['closed','Candidatures terminées']]){const o=node('option',label);o.value=v;filter.append(o);}
 const refresh=node('button','Actualiser');refresh.type='button';toolbar.append(search,filter,refresh);list.before(toolbar);
 const count=node('p');count.setAttribute('aria-live','polite');toolbar.after(count);
 let applications=[];
 const category=c=>!editable(c)?'closed':c.interview?(new Date(c.interview.startsAt)>new Date()?'upcoming':'past'):'pending';
 function render(){
  stats.replaceChildren();
  for(const [key,label] of [['upcoming','À venir'],['pending','À planifier'],['past','Passés']]){const box=node('div',null,'interview-stat');box.append(node('strong',String(applications.filter(c=>!c.loadError&&category(c)===key).length)),node('span',label));stats.append(box);}
  const query=search.value.trim().toLocaleLowerCase('fr');
  const matches=applications.filter(c=>(filter.value==='all'||(!c.loadError&&category(c)===filter.value))&&`${c.prenom} ${c.nom} ${c.titreOffre}`.toLocaleLowerCase('fr').includes(query));
  matches.sort((a,b)=>{const ta=category(a)==='upcoming'?new Date(a.interview.startsAt).getTime():Infinity;const tb=category(b)==='upcoming'?new Date(b.interview.startsAt).getTime():Infinity;return ta-tb||a.nom.localeCompare(b.nom);});
  count.textContent=`${matches.length} résultat${matches.length>1?'s':''}`;list.replaceChildren();
  if(!matches.length){const empty=node('div',null,'interview-empty');empty.append(node('h3',applications.length?'Aucun résultat':'Vos prochains échanges commencent ici'),node('p',applications.length?'Essayez un autre nom ou un autre filtre.':'Les candidats à vos offres apparaîtront ici pour organiser un entretien.'));list.append(empty);return;}
  for(const c of matches){
   const row=node('article',null,'interview-card');const head=node('div',null,'interview-card-head');
   const identity=node('div');identity.append(node('h3',`${c.prenom} ${c.nom}`.trim()||'Candidat'),node('p',c.titreOffre));
   head.append(identity,node('span',labels[c.statut]||c.statut,'interview-badge'));row.append(head);
   if(c.loadError)row.append(node('p','Rendez-vous indisponible. Actualisez avant de le modifier.','interview-error'));
   else if(c.interview){
    const info=node('div',null,'interview-info');const date=new Date(c.interview.startsAt);
    info.append(node('strong',date.toLocaleString('fr-FR',{dateStyle:'full',timeStyle:'short'})),node('p',c.interview.location));
    if(c.interview.instructions)info.append(node('p',c.interview.instructions,'interview-instructions'));
    try{const url=new URL(c.interview.location);if(['https:','http:'].includes(url.protocol)){const link=node('a','Ouvrir le lien de visioconférence');link.href=url.href;link.target='_blank';link.rel='noopener noreferrer';info.append(link);}}catch(_){}
    row.append(info);
   }else row.append(node('p',editable(c)?'Aucun rendez-vous planifié.':'Cette candidature est terminée.'));
   const profile=node('a','Voir la candidature');profile.href=`/candidats-offre.html?offre=${c.offreId}&candidature=${c.id}`;row.append(profile);
   if(editable(c)&&!c.loadError){
    const details=node('details',null,'interview-editor');details.append(node('summary',c.interview?'Modifier le rendez-vous':'Planifier un entretien'));
    const form=node('form');
    form.innerHTML='<div class="account-grid"><label>Date et heure<input name="startsAt" type="datetime-local" required></label><label>Lieu ou lien de visioconférence<input name="location" maxlength="500" required placeholder="Adresse ou https://…"></label></div><label>Informations pour le candidat<textarea name="instructions" rows="3" maxlength="2000" placeholder="Durée prévue, préparation, personne à contacter…"></textarea></label><p class="interview-timezone"></p><p role="alert" class="interview-error"></p><button type="submit">Enregistrer le rendez-vous</button><button type="button" class="interview-cancel">Annuler</button>';
    const modeLabel=node('label','Format de l’entretien');const mode=node('select');mode.name='mode';
    for(const [value,label] of [['onsite','En présentiel'],['video','En visioconférence']]){const option=node('option',label);option.value=value;mode.append(option);}modeLabel.append(mode);form.prepend(modeLabel);
    mode.value=/^https?:\/\//i.test(c.interview?.location||'')?'video':'onsite';
    const updateMode=()=>{form.elements.location.placeholder=mode.value==='video'?'https://meet.google.com/…':'Adresse complète, ville, bâtiment ou salle';form.elements.location.type=mode.value==='video'?'url':'text';};mode.onchange=updateMode;updateMode();
    form.elements.startsAt.min=localDate(Date.now()+60000);
    if(c.interview){form.elements.startsAt.value=localDate(c.interview.startsAt);form.elements.location.value=c.interview.location;form.elements.instructions.value=c.interview.instructions||'';}
    form.querySelector('.interview-timezone').textContent='Heure locale : '+Intl.DateTimeFormat().resolvedOptions().timeZone+'. Le rendez-vous sera visible dans le suivi du candidat.';
    form.querySelector('.interview-cancel').onclick=()=>{form.reset();mode.value=/^https?:\/\//i.test(c.interview?.location||'')?'video':'onsite';form.elements.startsAt.value=c.interview?localDate(c.interview.startsAt):'';form.elements.location.value=c.interview?.location||'';form.elements.instructions.value=c.interview?.instructions||'';updateMode();details.open=false;};
    form.onsubmit=async event=>{event.preventDefault();const error=form.querySelector('[role=alert]'),button=form.querySelector('[type=submit]');error.textContent='';
     const startsAt=new Date(form.elements.startsAt.value),location=form.elements.location.value.trim();
     if(mode.value==='video'){try{const url=new URL(location);if(!['https:','http:'].includes(url.protocol))throw new Error();}catch(_){error.textContent='Indiquez un lien de visioconférence valide commençant par https://.';return;}}
     if(!location||!Number.isFinite(startsAt.getTime())||startsAt<=new Date()){error.textContent='Choisissez une date future et indiquez le lieu ou le lien.';return;}
     button.disabled=true;button.textContent='Enregistrement…';
     try{c.interview=await api(`/api/candidatures/${c.id}/entretien`,'PUT',{startsAt:startsAt.toISOString(),location,instructions:form.elements.instructions.value.trim()});if(c.statut!=='ACCEPTEE')c.statut='ENTRETIEN';render();status.textContent='Entretien enregistré. Le candidat peut le consulter dans son espace.';status.tabIndex=-1;status.focus();}
     catch(e){error.textContent=e.message;}finally{button.disabled=false;button.textContent='Enregistrer le rendez-vous';}
    };details.append(form);row.append(details);
   }
   list.append(row);
  }
 }
 async function load(){refresh.disabled=true;status.textContent='Chargement des entretiens…';list.setAttribute('aria-busy','true');
  try{const offers=await api(`/api/recruteurs/${user.id}/offres`);const loaded=[];let failures=0;
   for(let i=0;i<offers.length;i+=6){await Promise.all(offers.slice(i,i+6).map(async o=>{try{const cs=await api(`/api/offres/${o.id}/candidats?recruteurId=${user.id}`);loaded.push(...cs.map(c=>({...c,id:c.candidatureId,offreId:o.id,titreOffre:o.titre,nom:c.nom||'',prenom:c.prenom||''})));}catch(e){failures++;}}));}
   for(let i=0;i<loaded.length;i+=6){await Promise.all(loaded.slice(i,i+6).map(async c=>{try{c.interview=await api(`/api/candidatures/${c.id}/entretien`);}catch(e){c.loadError=true;failures++;}}));}
   applications=loaded;render();status.textContent=failures?'Certaines données sont indisponibles. Utilisez Actualiser pour réessayer.':'';
  }catch(e){status.textContent=e.message;}finally{refresh.disabled=false;list.removeAttribute('aria-busy');}
 }
 search.addEventListener('input',render);filter.addEventListener('change',render);refresh.addEventListener('click',load);await load();
};
