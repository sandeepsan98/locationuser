package com.pro.location.servelts;



import com.pro.location.util.*;

import io.jsonwebtoken.Claims;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.logging.Logger;

@WebServlet("/user-info")
public class UserInfoServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(UserInfoServlet.class.getName());

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("application/json");

        // Extract JWT from cookie
        String jwt = null;
        Cookie[] cookies = req.getCookies();
        if (cookies != null) {
            jwt = Arrays.stream(cookies)
                    .filter(cookie -> "jwt".equals(cookie.getName()))
                    .map(Cookie::getValue)
                    .findFirst()
                    .orElse(null);
            LOGGER.info("JWT from cookie: " + jwt);
        }

        if (jwt == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\": false, \"message\": \"No JWT cookie found\"}");
            return;
        }

        try {
            // Validate JWT and get userId
            Claims claims = JwtUtil.validateJwt(jwt);
            int userId = Integer.parseInt(claims.getSubject());
            LOGGER.info("Validated JWT for userId: " + userId);

            // Fetch email from database
            try (Connection conn = ConnectionUtil.getConnection()) {
                PreparedStatement stmt = conn.prepareStatement("SELECT email FROM users WHERE id = ?");
                stmt.setInt(1, userId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    String email = rs.getString("email");
                    LOGGER.info("Fetched email for userId " + userId + ": " + email);
                    resp.getWriter().write("{\"success\": true, \"email\": \"" + email + "\"}");
                } else {
                    resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    resp.getWriter().write("{\"success\": false, \"message\": \"User not found\"}");
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error retrieving user info: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired JWT: " + e.getMessage() + "\"}");
        }
    }
}
