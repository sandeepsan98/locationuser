package com.pro.location.servelts;




import com.pro.location.util.ConnectionUtil;
import org.mindrot.jbcrypt.BCrypt;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Logger;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {
    private static final Logger LOGGER = Logger.getLogger(RegisterServlet.class.getName());

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String email = req.getParameter("email");
        String password = req.getParameter("password");
        resp.setContentType("application/json");

        if (email == null || password == null || !isValidEmail(email) || password.length() < 6) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.getWriter().write("{\"success\": false, \"message\": \"Valid email and password (min 6 chars) are required\"}");
            return;
        }

        try (Connection conn = ConnectionUtil.getConnection()) {
            PreparedStatement checkStmt = conn.prepareStatement("SELECT id FROM users WHERE email = ?");
            checkStmt.setString(1, email);
            ResultSet rs = checkStmt.executeQuery();
            if (rs.next()) {
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
                resp.getWriter().write("{\"success\": false, \"message\": \"Email already exists\"}");
                return;
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO users (email, password) VALUES (?, ?)");
            insertStmt.setString(1, email);
            insertStmt.setString(2, hashedPassword);
            insertStmt.executeUpdate();

            LOGGER.info("User registered: " + email);
            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.getWriter().write("{\"success\": true, \"message\": \"Registration successful\"}");
        } catch (Exception e) {
            LOGGER.severe("Registration error: " + e.getMessage());
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
        }
    }

    private boolean isValidEmail(String email) {
        return email != null && email.matches("^[A-Za-z0-9+_.-]+@(.+)$");
    }
}