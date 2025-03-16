package com.pro.location.servelts;


import com.pro.location.util.ConnectionUtil;
import com.pro.location.util.JwtUtil;
import org.mindrot.jbcrypt.BCrypt;

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
import java.util.Date;
import java.util.logging.Logger;

@WebServlet("/login")
public class LoginServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LoginServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        resp.setContentType("application/json");

        // Ensure no output is written before setting cookies
        if (email == null || password == null || !isValidEmail(email) || password.isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\": false, \"message\": \"Valid email and password are required\"}");
            return;
        }

        try (Connection conn = ConnectionUtil.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement("SELECT id, password FROM users WHERE email = ?");
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && BCrypt.checkpw(password, rs.getString("password"))) {
                int userId = rs.getInt("id");
                String jwt = JwtUtil.generateJwt(userId);
                String refreshToken = JwtUtil.generateRefreshToken();

                PreparedStatement tokenStmt = conn.prepareStatement(
                        "INSERT INTO refresh_tokens (user_id, token, expiry_date) VALUES (?, ?, ?)");
                tokenStmt.setInt(1, userId);
                tokenStmt.setString(2, refreshToken);
                tokenStmt.setTimestamp(3, new java.sql.Timestamp(new Date().getTime() + JwtUtil.getRefreshExpiryMs()));
                tokenStmt.executeUpdate();

                // Set cookie manually via header for full control
                String cookieValue = "jwt=" + jwt + "; Path=/; HttpOnly; Max-Age=900";
                resp.setHeader("Set-Cookie", cookieValue);
                LOGGER.info("Manually set cookie header: " + cookieValue);

                LOGGER.info("User logged in: " + email + ", JWT: " + jwt);
                resp.getWriter().write("{\"success\": true, \"message\": \"Login successful\", \"refreshToken\": \"" + refreshToken + "\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().write("{\"success\": false, \"message\": \"Invalid credentials\"}");
            }
        } catch (Exception e) {
            LOGGER.severe("Login error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}