package com.gi3.matchingcv.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded local limiter; production reverse proxy should also enforce global limits. */
public class AccountRateLimitFilter extends OncePerRequestFilter {
    private record Window(long start,int attempts){}
    private final ConcurrentHashMap<String,Window> attempts=new ConcurrentHashMap<>();
    protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain chain)throws ServletException,IOException {
        String path=r.getRequestURI();
        if(!r.getMethod().equals("POST") || !(path.startsWith("/api/auth/") || path.equals("/api/recruteurs"))) {chain.doFilter(r,s);return;}
        long now=System.currentTimeMillis();
        if(attempts.size()>10000) attempts.entrySet().removeIf(e->now-e.getValue().start()>60000);
        if(attempts.size()>20000) {s.setStatus(429);return;}
        var w=attempts.compute(r.getRemoteAddr(),(key,old)->old==null || now-old.start()>60000?new Window(now,1):new Window(old.start(),old.attempts()+1));
        if(w.attempts()>20){s.setStatus(429);s.setHeader("Retry-After","60");s.setContentType("application/json");s.getWriter().write("{\"message\":\"Trop de tentatives. Réessayez dans une minute.\"}");return;}
        chain.doFilter(r,s);
    }
}
