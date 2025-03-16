package com.pro.location.filter;

import com.pro.location.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

@WebFilter(urlPatterns = {"/locations", "/locations/list", "/protected","/locations/share","/locations/view-shared","/alerts","/locations/route","/locations/search"})
public class JwtFilter implements Filter {
    private static final Logger LOGGER = Logger.getLogger(JwtFilter.class.getName());

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String authHeader = req.getHeader("Authorization");
        String jwt = null;
        System.out.println("paappsaosospospspsspspspsps");
        // Check Authorization header first
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            jwt = authHeader.substring(7).trim();
            LOGGER.info("Found JWT in Authorization header: " + jwt);
        } else {
            // Fallback to jwt cookie
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                jwt = Arrays.stream(cookies)
                        .filter(cookie -> "jwt".equals(cookie.getName()))
                        .map(Cookie::getValue)
                        .findFirst()
                        .orElse(null);
                LOGGER.info("Found JWT in cookie: " + jwt);
            }
            if (jwt == null) {
                LOGGER.warning("No JWT found in Authorization header or cookies");
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().write("{\"success\": false, \"message\": \"Missing or invalid Authorization header or cookie\"}");
                return;
            }
        }

        try {
            Claims claims = JwtUtil.validateJwt(jwt);
            req.setAttribute("claims", claims);
            chain.doFilter(request, response);
        } catch (JwtException e) {
            LOGGER.warning("JWT validation failed: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired JWT\"}");
        } catch (Exception e) {
            LOGGER.severe("Filter error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }
}