package com.deroyque.activationserviceapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Limiter local: em múltiplas instâncias deve ser substituído por uma implementação distribuída. */
@Component
public class RequestLimitFilter extends OncePerRequestFilter {
    private final long maxBodyBytes; private final int maxRequests; private final ConcurrentHashMap<String, Window> windows=new ConcurrentHashMap<>();
    public RequestLimitFilter(@Value("${security.limits.max-body-bytes:65536}") long maxBodyBytes,
                              @Value("${security.limits.requests-per-minute:120}") int maxRequests) { this.maxBodyBytes=maxBodyBytes;this.maxRequests=maxRequests; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if(request.getContentLengthLong() > maxBodyBytes) { problem(response,413,"Corpo da requisição excede o limite permitido"); return; }
        String key=identity(request); Window window=windows.compute(key,(ignored,current) -> current == null || current.expiresAt().isBefore(Instant.now()) ? new Window(Instant.now().plusSeconds(60),new AtomicInteger(1)) : current);
        if(window.count().getAndIncrement() > maxRequests) { response.setHeader("Retry-After","60"); problem(response,429,"Limite temporário de requisições excedido"); return; }
        chain.doFilter(request,response);
    }
    private String identity(HttpServletRequest request) { Authentication auth=SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.isAuthenticated() ? auth.getName() : request.getRemoteAddr(); }
    private void problem(HttpServletResponse response, int status, String detail) throws IOException { response.setStatus(status); response.setContentType("application/problem+json"); response.getWriter().write("{\"status\":"+status+",\"detail\":\""+detail+"\"}"); }
    private record Window(Instant expiresAt, AtomicInteger count) { }
}
