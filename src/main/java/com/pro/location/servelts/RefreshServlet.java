package com.pro.location.servelts;

import com.pro.location.util.ConnectionUtil;
import com.pro.location.util.JwtUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.logging.Logger;

@WebServlet("/refresh")
public class RefreshServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RefreshServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String refreshToken = req.getParameter("refreshToken");
        resp.setContentType("application/json");

        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\": false, \"message\": \"Refresh token required\"}");
            return;
        }

        try (Connection conn = ConnectionUtil.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT user_id, expiry_date FROM refresh_tokens WHERE token = ?");
            stmt.setString(1, refreshToken);
            ResultSet rs = stmt.executeQuery();

            if (rs.next() && rs.getTimestamp("expiry_date").after(new Timestamp(System.currentTimeMillis()))) {
                int userId = rs.getInt("user_id");
                String newJwt = JwtUtil.generateJwt(userId);

                String cookieValue = "jwt=" + newJwt + "; Path=/; HttpOnly; Max-Age=900";
                resp.setHeader("Set-Cookie", cookieValue);
                LOGGER.info("Manually set cookie header (refresh): " + cookieValue);

                LOGGER.info("Token refreshed for user: " + userId);
                resp.getWriter().write("{\"success\": true, \"message\": \"Token refreshed\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                resp.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired refresh token\"}");
            }
        } catch (Exception e) {
            LOGGER.severe("Refresh error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }
}