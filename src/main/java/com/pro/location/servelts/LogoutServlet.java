package com.pro.location.servelts;



import com.pro.location.util.ConnectionUtil;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.logging.Logger;

@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LogoutServlet.class.getName());

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
            PreparedStatement stmt = conn.prepareStatement("DELETE FROM refresh_tokens WHERE token = ?");
            stmt.setString(1, refreshToken);
            int rows = stmt.executeUpdate();

            String cookieValue = "jwt=; Path=/; HttpOnly; Max-Age=0";
            resp.setHeader("Set-Cookie", cookieValue);
            LOGGER.info("Manually set cookie header (logout): " + cookieValue);

            if (rows > 0) {
                LOGGER.info("User logged out, token invalidated: " + refreshToken);
                resp.getWriter().write("{\"success\": true, \"message\": \"Logged out successfully\"}");
            } else {
                resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                resp.getWriter().write("{\"success\": false, \"message\": \"Invalid refresh token\"}");
            }
        } catch (Exception e) {
            LOGGER.severe("Logout error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }
}