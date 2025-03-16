package com.pro.location.servelts;


import io.jsonwebtoken.Claims;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

@WebServlet("/protected")
public class ProtectedServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(ProtectedServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Claims claims = (Claims) req.getAttribute("claims");
        resp.setContentType("application/json");
        String userId = claims.getSubject();
        System.out.println("pooapaoa");
        LOGGER.info("Protected resource accessed by user: " + userId);
        resp.getWriter().write("{\"success\": true, \"message\": \"Welcome, user " + userId + "! This is your protected dashboard.\"}");
    }
}