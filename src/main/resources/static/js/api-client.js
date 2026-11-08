'use strict';
(() => {
    const nativeFetch=window.fetch.bind(window);
    let csrfPromise;
    const csrf=()=>csrfPromise ||= nativeFetch('/api/auth/csrf',{credentials:'same-origin'})
        .then(r=>{if(!r.ok)throw new Error('Impossible de vérifier la session.');return r.json();})
        .catch(e=>{csrfPromise=null;throw e;});
    window.fetch=async (input,options={})=>{
        const url=new URL(input instanceof Request?input.url:input,location.href);
        if(url.origin!==location.origin || !url.pathname.startsWith('/api/'))return nativeFetch(input,options);
        const method=(options.method || (input instanceof Request?input.method:'GET')).toUpperCase();
        const headers=new Headers(options.headers || (input instanceof Request?input.headers:undefined));
        if(!['GET','HEAD','OPTIONS'].includes(method)) {const t=await csrf();headers.set(t.headerName,t.token);}
        const response=await nativeFetch(input,{...options,headers,credentials:'same-origin'});
        if(url.pathname==='/api/auth/connexion' || url.pathname==='/api/auth/logout')csrfPromise=null;
        if(response.status===401 && !url.pathname.startsWith('/api/auth/')) {
            localStorage.removeItem('matchingcv_user');location.replace('/login.html');
        }
        return response;
    };
})();
