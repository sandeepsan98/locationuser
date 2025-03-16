package com.pro.location.servelts;

import com.pro.location.util.ConnectionUtil;
import io.jsonwebtoken.Claims;

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
import java.util.UUID;
import java.util.logging.Logger;

@WebServlet(urlPatterns = {"/locations/share", "/locations/view-shared/*"})
public class LocationShareServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(LocationShareServlet.class.getName());
    private static final long SHARE_EXPIRY_MS = 24 * 60 * 60 * 1000; // 24 hours

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Claims claims = (Claims) req.getAttribute("claims");
        if (claims == null) {
            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            resp.getWriter().write("{\"success\": false, \"message\": \"Unauthorized\"}");
            return;
        }
        int userId = Integer.parseInt(claims.getSubject());

        String locationIdStr = req.getParameter("locationId");
        if (locationIdStr == null) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\": false, \"message\": \"Location ID required\"}");
            return;
        }

        int locationId = Integer.parseInt(locationIdStr);

        try (Connection conn = ConnectionUtil.getConnection()) {
            PreparedStatement checkStmt = conn.prepareStatement("SELECT user_id FROM locations WHERE id = ?");
            checkStmt.setInt(1, locationId);
            ResultSet rs = checkStmt.executeQuery();
            if (!rs.next() || rs.getInt("user_id") != userId) {
                resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
                resp.getWriter().write("{\"success\": false, \"message\": \"Location not found or not owned by user\"}");
                return;
            }

            String shareToken = UUID.randomUUID().toString();
            Timestamp expiryDate = new Timestamp(System.currentTimeMillis() + SHARE_EXPIRY_MS);

            PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO shared_locations (location_id, share_token, expiry_date) VALUES (?, ?, ?)");
            insertStmt.setInt(1, locationId);
            insertStmt.setString(2, shareToken);
            insertStmt.setTimestamp(3, expiryDate);
            insertStmt.executeUpdate();

            String shareUrl = req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort() +
                    req.getContextPath() + "/shared.html?token=" + shareToken;

            LOGGER.info("Location shared: " + locationId + ", Share URL: " + shareUrl);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"success\": true, \"message\": \"Location shared\", \"shareUrl\": \"" + shareUrl + "\"}");
        } catch (Exception e) {
            LOGGER.severe("Share error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\": false, \"message\": \"Share token required\"}");
            return;
        }

        String shareToken = pathInfo.substring(1);

        try (Connection conn = ConnectionUtil.getConnection()) {
            PreparedStatement stmt = conn.prepareStatement(
                    "SELECT l.* FROM shared_locations sl JOIN locations l ON sl.location_id = l.id WHERE sl.share_token = ? AND sl.expiry_date > NOW()");
            stmt.setString(1, shareToken);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String jsonResponse = String.format(
                        "{\"success\": true, \"data\": {" +
                                "\"id\": %d, \"latitude\": %.6f, \"longitude\": %.6f, \"temperature\": %.1f, " +
                                "\"weather_condition\": \"%s\", \"street\": \"%s\", \"city\": \"%s\", \"country\": \"%s\", " +
                                "\"name\": \"%s\", \"forecast\": %s}}",
                        rs.getInt("id"), rs.getDouble("latitude"), rs.getDouble("longitude"),
                        rs.getDouble("temperature"), rs.getString("weather_condition"),
                        rs.getString("street"), rs.getString("city"), rs.getString("country"),
                        rs.getString("name") != null ? rs.getString("name") : "",
                        rs.getString("forecast_data") != null ? rs.getString("forecast_data") : "null"
                );
                resp.setContentType("application/json");
                resp.getWriter().write(jsonResponse);
            } else {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.getWriter().write("{\"success\": false, \"message\": \"Invalid or expired share token\"}");
            }
        } catch (Exception e) {
            LOGGER.severe("View shared error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }
}