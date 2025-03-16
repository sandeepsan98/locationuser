//package com.pro.location.servelts;
//
//
//
//import com.pro.location.util.ConnectionUtil;
//import io.jsonwebtoken.Claims;
//
//import javax.servlet.ServletException;
//import javax.servlet.annotation.WebServlet;
//import javax.servlet.http.HttpServlet;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//import java.sql.Connection;
//import java.sql.PreparedStatement;
//import java.sql.ResultSet;
//import java.util.logging.Logger;
//
//@WebServlet("/locations/list")
//public class LocationListServlet extends HttpServlet {
//    private static final Logger LOGGER = Logger.getLogger(LocationListServlet.class.getName());
//
//    @Override
//    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
//        Claims claims = (Claims) req.getAttribute("claims");
//        if (claims == null) {
//            resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
//            resp.getWriter().write("{\"success\": false, \"message\": \"Unauthorized\"}");
//            return;
//        }
//        int userId = Integer.parseInt(claims.getSubject());
//
//        String pageStr = req.getParameter("page");
//        String sizeStr = req.getParameter("size");
//        int page = pageStr != null ? Integer.parseInt(pageStr) : 1;
//        int size = sizeStr != null ? Integer.parseInt(sizeStr) : 10;
//        int offset = (page - 1) * size;
//
//        try (Connection conn = ConnectionUtil.getConnection()) {
//            String sql = "SELECT * FROM locations WHERE user_id = ? LIMIT ? OFFSET ?";
//            PreparedStatement stmt = conn.prepareStatement(sql);
//            stmt.setInt(1, userId);
//            stmt.setInt(2, size);
//            stmt.setInt(3, offset);
//            ResultSet rs = stmt.executeQuery();
//
//            StringBuilder json = new StringBuilder("{\"success\": true, \"data\": [");
//            boolean first = true;
//            while (rs.next()) {
//                if (!first) json.append(",");
//                json.append("{")
//                        .append("\"id\": ").append(rs.getInt("id")).append(",")
//                        .append("\"latitude\": ").append(rs.getDouble("latitude")).append(",")
//                        .append("\"longitude\": ").append(rs.getDouble("longitude")).append(",")
//                        .append("\"temperature\": ").append(rs.getDouble("temperature")).append(",")
//                        .append("\"weather_condition\": \"").append(rs.getString("weather_condition")).append("\",")
//                        .append("\"street\": \"").append(rs.getString("street")).append("\",")
//                        .append("\"city\": \"").append(rs.getString("city")).append("\",")
//                        .append("\"country\": \"").append(rs.getString("country")).append("\",")
//                        .append("\"created_at\": \"").append(rs.getTimestamp("created_at")).append("\"")
//                        .append("}");
//                first = false;
//            }
//            json.append("]}");
//
//            resp.setContentType("application/json");
//            resp.getWriter().write(json.toString());
//        } catch (Exception e) {
//            LOGGER.severe("Error fetching locations: " + e.getMessage());
//            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//            resp.getWriter().write("{\"success\": false, \"message\": \"Server error\"}");
//        }
//    }
//}
