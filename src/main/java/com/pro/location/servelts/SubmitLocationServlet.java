package com.pro.location.servlets;

import com.pro.location.util.ConnectionUtil;
import com.pro.location.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@WebServlet(urlPatterns = {"/locations", "/locations/list", "/locations/*", "/alerts/*", "/locations/route", "/locations/search"})
public class SubmitLocationServlet extends HttpServlet {
	private static final Logger LOGGER = Logger.getLogger(SubmitLocationServlet.class.getName());
	private static final String GEOAPIFY_API_KEY = "01369d8d7c974b61b45ad10e0b1be91c"; // Your Geoapify key
	private static final String OPENROUTE_API_KEY = "5b3ce3597851110001cf62486e6fdbf5d05f45a9b37f72dd1e3c343a"; // Your OpenRouteService key
	private static final int MAX_RETRIES = 3;
	private static final int RETRY_DELAY_MS = 2000;
	private static final long FORECAST_CACHE_DURATION_MS = 24 * 60 * 60 * 1000;
	private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

	@Override
	public void init() throws ServletException {
		super.init();
		scheduler.scheduleAtFixedRate(this::checkWeatherAlerts, 0, 1, TimeUnit.HOURS);
	}

	@Override
	protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String jwt = extractJwtFromCookie(req);
		try {
			Claims claims = JwtUtil.validateJwt(jwt);
			req.setAttribute("claims", claims);
			super.service(req, resp);
		} catch (Exception e) {
			resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": false, \"message\": \"Unauthorized: Invalid or expired JWT\"}");
		}
	}

	private String extractJwtFromCookie(HttpServletRequest req) {
		Cookie[] cookies = req.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if ("jwt".equals(cookie.getName())) {
					return cookie.getValue();
				}
			}
		}
		return null;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getServletPath();
		String pathInfo = req.getPathInfo();

		if ("/alerts".equals(path)) {
			handleAlertCreation(req, resp);
		} else if ("/locations".equals(path) && pathInfo != null && pathInfo.contains("/visit")) {
			handleLocationVisit(req, resp);
		} else {
			handleLocationCreation(req, resp);
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		String path = req.getServletPath();
		if ("/locations/list".equals(path)) {
			handleLocationList(req, resp);
		} else if ("/alerts".equals(path)) {
			handleNotificationList(req, resp);
		} else if ("/locations/route".equals(path)) {
			handleRouteSuggestion(req, resp);
		} else if ("/locations/search".equals(path)) {
			handleLocationSearch(req, resp);
		} else {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			resp.getWriter().write("{\"success\": false, \"message\": \"Not found\"}");
		}
	}

	@Override
	protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() <= 1) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"Location or Alert ID required\"}");
			return;
		}

		int id = Integer.parseInt(pathInfo.substring(1));
		String path = req.getServletPath();

		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql;
			if ("/locations".equals(path)) {
				sql = "DELETE FROM locations WHERE id = ? AND user_id = ?";
			} else if ("/alerts".equals(path)) {
				sql = "DELETE FROM weather_alerts WHERE id = ? AND user_id = ?";
			} else {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				resp.getWriter().write("{\"success\": false, \"message\": \"Invalid path\"}");
				return;
			}

			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, id);
			stmt.setInt(2, userId);
			int rowsAffected = stmt.executeUpdate();

			if (rowsAffected > 0) {
				resp.setContentType("application/json");
				resp.getWriter().write("{\"success\": true, \"message\": \"" + ("/locations".equals(path) ? "Location" : "Alert") + " deleted\"}");
			} else {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				resp.getWriter().write("{\"success\": false, \"message\": \"" + ("/locations".equals(path) ? "Location" : "Alert") + " not found or not owned by user\"}");
			}
		} catch (Exception e) {
			LOGGER.severe("Error deleting resource: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	@Override
	protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());

		String pathInfo = req.getPathInfo();
		if (pathInfo == null || pathInfo.length() <= 1) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"Location ID required\"}");
			return;
		}

		int locationId = Integer.parseInt(pathInfo.substring(1));

		StringBuilder jsonBuffer = new StringBuilder();
		try (BufferedReader reader = req.getReader()) {
			String line;
			while ((line = reader.readLine()) != null) {
				jsonBuffer.append(line);
			}
		}
		JSONObject jsonBody = jsonBuffer.length() > 0 ? new JSONObject(jsonBuffer.toString()) : new JSONObject();

		String name = jsonBody.optString("name", null);
		String street = jsonBody.optString("street", null);
		String city = jsonBody.optString("city", null);
		String country = jsonBody.optString("country", null);
		Double latitude = jsonBody.has("latitude") && !jsonBody.isNull("latitude") ? jsonBody.getDouble("latitude") : null;
		Double longitude = jsonBody.has("longitude") && !jsonBody.isNull("longitude") ? jsonBody.getDouble("longitude") : null;
		String tags = jsonBody.optString("tags", null);
		Boolean isFavorite = jsonBody.has("isFavorite") ? jsonBody.getBoolean("isFavorite") : null;

		try (Connection conn = ConnectionUtil.getConnection()) {
			StringBuilder sql = new StringBuilder("UPDATE locations SET ");
			boolean hasFields = false;

			if (name != null) {
				sql.append("name = ?, ");
				hasFields = true;
			}
			if (street != null) {
				sql.append("street = ?, ");
				hasFields = true;
			}
			if (city != null) {
				sql.append("city = ?, ");
				hasFields = true;
			}
			if (country != null) {
				sql.append("country = ?, ");
				hasFields = true;
			}
			if (latitude != null && longitude != null) {
				sql.append("latitude = ?, longitude = ?, ");
				JSONObject weatherData = fetchWeatherData(latitude, longitude);
				Double temp = weatherData != null && weatherData.has("current_weather") ? weatherData.getJSONObject("current_weather").getDouble("temperature") : null;
				String condition = weatherData != null && weatherData.has("current_weather") ? wmoCodeToDescription(weatherData.getJSONObject("current_weather").getInt("weathercode")) : null;
				if (temp != null) sql.append("temperature = ?, ");
				if (condition != null) sql.append("weather_condition = ?, ");
				JSONObject forecastData = fetchWeatherForecast(latitude, longitude);
				String forecastJson = forecastData != null ? forecastData.toString() : null;
				if (forecastJson != null) sql.append("forecast_data = ?, forecast_updated_at = NOW(), ");
				hasFields = true;
			}
			if (tags != null) {
				sql.append("tags = ?, ");
				hasFields = true;
			}
			if (isFavorite != null) {
				sql.append("is_favorite = ?, ");
				hasFields = true;
			}

			if (!hasFields) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().write("{\"success\": false, \"message\": \"At least one field must be provided for update\"}");
				return;
			}

			sql.setLength(sql.length() - 2);
			sql.append(" WHERE id = ? AND user_id = ?");

			PreparedStatement stmt = conn.prepareStatement(sql.toString());
			int paramIndex = 1;

			if (name != null) stmt.setString(paramIndex++, name);
			if (street != null) stmt.setString(paramIndex++, street);
			if (city != null) stmt.setString(paramIndex++, city);
			if (country != null) stmt.setString(paramIndex++, country);
			if (latitude != null && longitude != null) {
				stmt.setDouble(paramIndex++, latitude);
				stmt.setDouble(paramIndex++, longitude);
				JSONObject weatherData = fetchWeatherData(latitude, longitude);
				Double temp = weatherData != null && weatherData.has("current_weather") ? weatherData.getJSONObject("current_weather").getDouble("temperature") : null;
				String condition = weatherData != null && weatherData.has("current_weather") ? wmoCodeToDescription(weatherData.getJSONObject("current_weather").getInt("weathercode")) : null;
				if (temp != null) stmt.setDouble(paramIndex++, temp);
				if (condition != null) stmt.setString(paramIndex++, condition);
				JSONObject forecastData = fetchWeatherForecast(latitude, longitude);
				String forecastJson = forecastData != null ? forecastData.toString() : null;
				if (forecastJson != null) stmt.setString(paramIndex++, forecastJson);
			}
			if (tags != null) stmt.setString(paramIndex++, tags);
			if (isFavorite != null) stmt.setBoolean(paramIndex++, isFavorite);
			stmt.setInt(paramIndex++, locationId);
			stmt.setInt(paramIndex, userId);

			int rowsAffected = stmt.executeUpdate();

			if (rowsAffected > 0) {
				resp.setContentType("application/json");
				resp.getWriter().write("{\"success\": true, \"message\": \"Location updated\"}");
			} else {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				resp.getWriter().write("{\"success\": false, \"message\": \"Location not found or not owned by user\"}");
			}
		} catch (Exception e) {
			LOGGER.severe("Error updating location: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleLocationCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());
		String latitudeStr = req.getParameter("latitude");
		String longitudeStr = req.getParameter("longitude");
		String tags = req.getParameter("tags");
		boolean isFavorite = Boolean.parseBoolean(req.getParameter("isFavorite"));

		if (latitudeStr == null || longitudeStr == null) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"Latitude and longitude required\"}");
			return;
		}

		double latitude = Double.parseDouble(latitudeStr);
		double longitude = Double.parseDouble(longitudeStr);

		try (Connection conn = ConnectionUtil.getConnection()) {
			String checkSql = "SELECT id FROM locations WHERE user_id = ? AND ABS(latitude - ?) < 0.0001 AND ABS(longitude - ?) < 0.0001";
			PreparedStatement checkStmt = conn.prepareStatement(checkSql);
			checkStmt.setInt(1, userId);
			checkStmt.setDouble(2, latitude);
			checkStmt.setDouble(3, longitude);
			ResultSet rs = checkStmt.executeQuery();
			if (rs.next()) {
				resp.setStatus(HttpServletResponse.SC_CONFLICT);
				resp.getWriter().write("{\"success\": false, \"message\": \"Location with these coordinates already exists\"}");
				return;
			}

			JSONObject addressData = fetchAddressData(latitude, longitude);
			String street = addressData.optString("street", addressData.optString("housenumber", ""));
			if (street.isEmpty()) street = addressData.optString("formatted", "Unknown Street").split(",")[0];
			String city = addressData.optString("city", "Unknown City");
			String country = addressData.optString("country", "Unknown Country");

			JSONObject weatherData = fetchWeatherData(latitude, longitude);
			Double temp = weatherData != null && weatherData.has("current_weather") ? weatherData.getJSONObject("current_weather").getDouble("temperature") : 25.0;
			String condition = weatherData != null && weatherData.has("current_weather") ? wmoCodeToDescription(weatherData.getJSONObject("current_weather").getInt("weathercode")) : "Unknown";

			JSONObject forecastData = fetchWeatherForecast(latitude, longitude);
			String forecastJson = forecastData != null ? forecastData.toString() : null;

			String sql = "INSERT INTO locations (user_id, latitude, longitude, temperature, weather_condition, street, city, country, forecast_data, forecast_updated_at, tags, is_favorite, last_visited_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, ?, NULL)";
			PreparedStatement stmt = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
			stmt.setInt(1, userId);
			stmt.setDouble(2, latitude);
			stmt.setDouble(3, longitude);
			stmt.setDouble(4, temp);
			stmt.setString(5, condition);
			stmt.setString(6, street);
			stmt.setString(7, city);
			stmt.setString(8, country);
			stmt.setString(9, forecastJson);
			stmt.setString(10, tags);
			stmt.setBoolean(11, isFavorite);
			stmt.executeUpdate();

			ResultSet generatedKeys = stmt.getGeneratedKeys();
			int generatedId = generatedKeys.next() ? generatedKeys.getInt(1) : -1;

			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": true, \"message\": \"Location saved\", \"data\": {\"id\": " + generatedId + ", \"latitude\": " + latitude + ", \"longitude\": " + longitude + ", \"temperature\": " + temp + ", \"weather\": \"" + condition + "\", \"street\": \"" + street + "\", \"city\": \"" + city + "\", \"country\": \"" + country + "\", \"forecast\": " + (forecastJson != null ? forecastJson : "null") + ", \"tags\": \"" + (tags != null ? tags : "") + "\", \"isFavorite\": " + isFavorite + ", \"lastVisitedAt\": null}}");
		} catch (Exception e) {
			LOGGER.severe("Error saving location: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleAlertCreation(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());

		StringBuilder jsonBuffer = new StringBuilder();
		try (BufferedReader reader = req.getReader()) {
			String line;
			while ((line = reader.readLine()) != null) {
				jsonBuffer.append(line);
			}
		}
		JSONObject jsonBody = new JSONObject(jsonBuffer.toString());

		int locationId = jsonBody.getInt("locationId");
		Double minTemp = jsonBody.has("minTemp") && !jsonBody.isNull("minTemp") ? jsonBody.getDouble("minTemp") : null;
		Double maxTemp = jsonBody.has("maxTemp") && !jsonBody.isNull("maxTemp") ? jsonBody.getDouble("maxTemp") : null;
		String weatherCondition = jsonBody.optString("weatherCondition", null);

		if (minTemp == null && maxTemp == null && weatherCondition == null) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"At least one alert condition must be provided\"}");
			return;
		}

		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "INSERT INTO weather_alerts (location_id, user_id, min_temp, max_temp, weather_condition) VALUES (?, ?, ?, ?, ?)";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, locationId);
			stmt.setInt(2, userId);
			if (minTemp != null) stmt.setDouble(3, minTemp); else stmt.setNull(3, Types.DOUBLE);
			if (maxTemp != null) stmt.setDouble(4, maxTemp); else stmt.setNull(4, Types.DOUBLE);
			stmt.setString(5, weatherCondition);
			stmt.executeUpdate();

			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": true, \"message\": \"Weather alert created\"}");
		} catch (Exception e) {
			LOGGER.severe("Error creating weather alert: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleLocationList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());

		int page = Integer.parseInt(req.getParameter("page") != null ? req.getParameter("page") : "1");
		int size = Integer.parseInt(req.getParameter("size") != null ? req.getParameter("size") : "10");
		int offset = (page - 1) * size;

		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "SELECT id, latitude, longitude, temperature, weather_condition, street, city, country, name, forecast_data, forecast_updated_at, tags, is_favorite, last_visited_at FROM locations WHERE user_id = ? ORDER BY is_favorite DESC, ISNULL(last_visited_at), last_visited_at DESC, id DESC LIMIT ? OFFSET ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, userId);
			stmt.setInt(2, size);
			stmt.setInt(3, offset);
			ResultSet rs = stmt.executeQuery();

			JSONArray locations = new JSONArray();
			while (rs.next()) {
				JSONObject loc = new JSONObject();
				loc.put("id", rs.getInt("id"));
				loc.put("latitude", rs.getDouble("latitude"));
				loc.put("longitude", rs.getDouble("longitude"));
				loc.put("temperature", rs.getDouble("temperature"));
				loc.put("weather_condition", rs.getString("weather_condition"));
				loc.put("street", rs.getString("street"));
				loc.put("city", rs.getString("city"));
				loc.put("country", rs.getString("country"));
				loc.put("name", rs.getString("name") != null ? rs.getString("name") : "");
				loc.put("tags", rs.getString("tags") != null ? rs.getString("tags") : "");
				loc.put("isFavorite", rs.getBoolean("is_favorite"));
				Timestamp lastVisitedAt = rs.getTimestamp("last_visited_at");
				loc.put("lastVisitedAt", lastVisitedAt != null ? lastVisitedAt.toString() : null);

				String forecastJson = rs.getString("forecast_data");
				Timestamp forecastUpdatedAt = rs.getTimestamp("forecast_updated_at");
				if (forecastJson != null && forecastUpdatedAt != null && (System.currentTimeMillis() - forecastUpdatedAt.getTime()) < FORECAST_CACHE_DURATION_MS) {
					loc.put("forecast", new JSONObject(forecastJson));
				} else {
					JSONObject forecastData = fetchWeatherForecast(rs.getDouble("latitude"), rs.getDouble("longitude"));
					if (forecastData != null) {
						String newForecastJson = forecastData.toString();
						loc.put("forecast", forecastData);
						PreparedStatement updateStmt = conn.prepareStatement(
								"UPDATE locations SET forecast_data = ?, forecast_updated_at = NOW() WHERE id = ?");
						updateStmt.setString(1, newForecastJson);
						updateStmt.setInt(2, rs.getInt("id"));
						updateStmt.executeUpdate();
					} else {
						loc.put("forecast", JSONObject.NULL);
					}
				}
				locations.put(loc);
			}

			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": true, \"data\": " + locations.toString() + "}");
		} catch (Exception e) {
			LOGGER.severe("Error fetching locations: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleNotificationList(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());

		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "SELECT n.id, n.location_id, n.message, n.created_at, n.is_read, l.street, l.city FROM notifications n JOIN locations l ON n.location_id = l.id WHERE n.user_id = ? ORDER BY n.created_at DESC";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, userId);
			ResultSet rs = stmt.executeQuery();

			JSONArray notifications = new JSONArray();
			while (rs.next()) {
				JSONObject notification = new JSONObject();
				notification.put("id", rs.getInt("id"));
				notification.put("locationId", rs.getInt("location_id"));
				notification.put("message", rs.getString("message"));
				notification.put("createdAt", rs.getTimestamp("created_at").toString());
				notification.put("isRead", rs.getBoolean("is_read"));
				notification.put("location", rs.getString("street") + ", " + rs.getString("city"));
				notifications.put(notification);
			}

			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": true, \"data\": " + notifications.toString() + "}");
		} catch (Exception e) {
			LOGGER.severe("Error fetching notifications: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleLocationVisit(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());
		String pathInfo = req.getPathInfo();
		int locationId = Integer.parseInt(pathInfo.split("/")[1]);

		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "UPDATE locations SET last_visited_at = NOW() WHERE id = ? AND user_id = ?";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, locationId);
			stmt.setInt(2, userId);
			int rowsAffected = stmt.executeUpdate();

			if (rowsAffected > 0) {
				resp.setContentType("application/json");
				resp.getWriter().write("{\"success\": true, \"message\": \"Visit recorded\"}");
			} else {
				resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
				resp.getWriter().write("{\"success\": false, \"message\": \"Location not found\"}");
			}
		} catch (Exception e) {
			LOGGER.severe("Error recording visit: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private void handleRouteSuggestion(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Claims claims = (Claims) req.getAttribute("claims");
		int userId = Integer.parseInt(claims.getSubject());
		String locationIdsStr = req.getParameter("locationIds");
		String profile = req.getParameter("profile"); // e.g., "driving-car", "cycling-regular"
		String optimize = req.getParameter("optimize"); // "true" or "false"

		if (locationIdsStr == null || locationIdsStr.isEmpty() || profile == null) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"Location IDs and transport mode required\"}");
			return;
		}

		String[] locationIds = locationIdsStr.split(",");
		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "SELECT id, latitude, longitude, street, city FROM locations WHERE user_id = ? AND id IN (" + String.join(",", locationIds) + ")";
			PreparedStatement stmt = conn.prepareStatement(sql);
			stmt.setInt(1, userId);
			ResultSet rs = stmt.executeQuery();

			JSONArray coordinates = new JSONArray();
			while (rs.next()) {
				JSONObject coord = new JSONObject();
				coord.put("id", rs.getInt("id"));
				coord.put("latitude", rs.getDouble("latitude"));
				coord.put("longitude", rs.getDouble("longitude"));
				coord.put("name", rs.getString("street") + ", " + rs.getString("city"));
				coordinates.put(coord);
			}

			if (coordinates.length() < 2) {
				resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
				resp.getWriter().write("{\"success\": false, \"message\": \"At least two locations required for route\"}");
				return;
			}

			JSONObject routeData = fetchRouteData(coordinates, profile, "true".equals(optimize));
			if (routeData != null) {
				resp.setContentType("application/json");
				resp.getWriter().write("{\"success\": true, \"data\": " + routeData.toString() + "}");
			} else {
				resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				resp.getWriter().write("{\"success\": false, \"message\": \"Failed to fetch route data\"}");
			}
		} catch (Exception e) {
			LOGGER.severe("Error fetching route: " + e.getMessage());
			resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			resp.getWriter().write("{\"success\": false, \"message\": \"Server error: " + e.getMessage() + "\"}");
		}
	}

	private JSONObject fetchRouteData(JSONArray coordinates, String profile, boolean optimize) throws IOException {
		String urlStr = "https://api.openrouteservice.org/v2/directions/" + profile;
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Authorization", OPENROUTE_API_KEY);
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setRequestProperty("Accept", "application/json");
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);
		conn.setDoOutput(true);

		// Construct JSON body
		JSONObject requestBody = new JSONObject();
		JSONArray coordsArray = new JSONArray();
		for (int i = 0; i < coordinates.length(); i++) {
			JSONObject coord = coordinates.getJSONObject(i);
			JSONArray point = new JSONArray();
			point.put(coord.getDouble("longitude")); // [lon, lat] order
			point.put(coord.getDouble("latitude"));
			coordsArray.put(point);
		}
		requestBody.put("coordinates", coordsArray);
		if (optimize) {
			requestBody.put("optimize_waypoints", true); // Enable optimization
		}

		LOGGER.info("Fetching route from URL: " + urlStr + " with body: " + requestBody.toString());

		try (OutputStream os = conn.getOutputStream()) {
			byte[] input = requestBody.toString().getBytes("utf-8");
			os.write(input, 0, input.length);
		}

		int responseCode = conn.getResponseCode();
		LOGGER.info("OpenRouteService response code: " + responseCode);

		if (responseCode == 200) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) response.append(line);
			reader.close();
			LOGGER.info("Route data received: " + response.toString());
			return new JSONObject(response.toString());
		} else {
			BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
			StringBuilder errorResponse = new StringBuilder();
			String errorLine;
			while ((errorLine = errorReader.readLine()) != null) errorResponse.append(errorLine);
			errorReader.close();
			LOGGER.severe("Failed to fetch route data. Response code: " + responseCode + ", Error: " + errorResponse.toString());
			return null;
		}
	}

	private void handleLocationSearch(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String query = req.getParameter("query");
		if (query == null || query.trim().isEmpty()) {
			resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			resp.getWriter().write("{\"success\": false, \"message\": \"Search query required\"}");
			return;
		}

		JSONObject searchResult = fetchSearchData(query);
		if (searchResult != null && searchResult.has("features") && searchResult.getJSONArray("features").length() > 0) {
			JSONArray results = searchResult.getJSONArray("features");
			JSONObject location = results.getJSONObject(0).getJSONObject("properties");
			JSONObject geometry = results.getJSONObject(0).getJSONObject("geometry");

			JSONObject responseData = new JSONObject();
			responseData.put("latitude", geometry.getJSONArray("coordinates").getDouble(1));
			responseData.put("longitude", geometry.getJSONArray("coordinates").getDouble(0));
			responseData.put("street", location.optString("street", location.optString("housenumber", "")));
			responseData.put("city", location.optString("city", "Unknown City"));
			responseData.put("country", location.optString("country", "Unknown Country"));
			responseData.put("formatted", location.optString("formatted", "Unknown Location"));

			resp.setContentType("application/json");
			resp.getWriter().write("{\"success\": true, \"data\": " + responseData.toString() + "}");
		} else {
			resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
			resp.getWriter().write("{\"success\": false, \"message\": \"No results found for query\"}");
		}
	}

	private JSONObject fetchSearchData(String query) throws IOException {
		String urlStr = "https://api.geoapify.com/v1/geocode/search?text=" + URLEncoder.encode(query, "UTF-8") + "&apiKey=" + GEOAPIFY_API_KEY;
		URL url = new URL(urlStr);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setConnectTimeout(10000);
		conn.setReadTimeout(10000);

		if (conn.getResponseCode() == 200) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) response.append(line);
			reader.close();
			return new JSONObject(response.toString());
		}
		return null;
	}

	private void checkWeatherAlerts() {
		try (Connection conn = ConnectionUtil.getConnection()) {
			String sql = "SELECT wa.id, wa.location_id, wa.user_id, wa.min_temp, wa.max_temp, wa.weather_condition, l.latitude, l.longitude, l.temperature, l.weather_condition AS current_condition FROM weather_alerts wa JOIN locations l ON wa.location_id = l.id";
			PreparedStatement stmt = conn.prepareStatement(sql);
			ResultSet rs = stmt.executeQuery();

			while (rs.next()) {
				int locationId = rs.getInt("location_id");
				int userId = rs.getInt("user_id");
				Double minTemp = rs.getObject("min_temp") != null ? rs.getDouble("min_temp") : null;
				Double maxTemp = rs.getObject("max_temp") != null ? rs.getDouble("max_temp") : null;
				String weatherCondition = rs.getString("weather_condition");
				double currentTemp = rs.getDouble("temperature");
				String currentCondition = rs.getString("current_condition");

				StringBuilder message = new StringBuilder();
				if (minTemp != null && currentTemp < minTemp) {
					message.append("Temperature dropped below ").append(minTemp).append("°C to ").append(currentTemp).append("°C. ");
				}
				if (maxTemp != null && currentTemp > maxTemp) {
					message.append("Temperature rose above ").append(maxTemp).append("°C to ").append(currentTemp).append("°C. ");
				}
				if (weatherCondition != null && currentCondition.equalsIgnoreCase(weatherCondition)) {
					message.append("Weather condition is ").append(currentCondition).append(". ");
				}

				if (message.length() > 0) {
					String insertSql = "INSERT INTO notifications (user_id, location_id, message) VALUES (?, ?, ?)";
					PreparedStatement insertStmt = conn.prepareStatement(insertSql);
					insertStmt.setInt(1, userId);
					insertStmt.setInt(2, locationId);
					insertStmt.setString(3, message.toString().trim());
					insertStmt.executeUpdate();
				}
			}
		} catch (Exception e) {
			LOGGER.severe("Error checking weather alerts: " + e.getMessage());
		}
	}

	private JSONObject fetchWeatherForecast(double lat, double lon) throws IOException {
		String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&daily=temperature_2m_max,temperature_2m_min,weathercode&timezone=auto";
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				URL url = new URL(urlStr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				if (conn.getResponseCode() == 200) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					StringBuilder response = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) response.append(line);
					reader.close();
					return new JSONObject(response.toString());
				}
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		return null;
	}

	private JSONObject fetchWeatherData(double lat, double lon) throws IOException {
		String urlStr = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current_weather=true";
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				URL url = new URL(urlStr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				if (conn.getResponseCode() == 200) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					StringBuilder response = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) response.append(line);
					reader.close();
					return new JSONObject(response.toString());
				}
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		return null;
	}

	private JSONObject fetchAddressData(double lat, double lon) throws IOException {
		String urlStr = "https://api.geoapify.com/v1/geocode/reverse?lat=" + lat + "&lon=" + lon + "&apiKey=" + GEOAPIFY_API_KEY;
		for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
			try {
				URL url = new URL(urlStr);
				HttpURLConnection conn = (HttpURLConnection) url.openConnection();
				conn.setRequestMethod("GET");
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);
				if (conn.getResponseCode() == 200) {
					BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
					StringBuilder response = new StringBuilder();
					String line;
					while ((line = reader.readLine()) != null) response.append(line);
					reader.close();
					return new JSONObject(response.toString()).getJSONArray("features").getJSONObject(0).getJSONObject("properties");
				}
			} catch (Exception e) {
				if (attempt < MAX_RETRIES) {
					try {
						Thread.sleep(RETRY_DELAY_MS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
					}
				}
			}
		}
		return new JSONObject();
	}

	private String wmoCodeToDescription(int code) {
		switch (code) {
			case 0: return "Clear sky";
			case 1: return "Mainly clear";
			case 2: return "Partly cloudy";
			case 3: return "Overcast";
			case 45: return "Fog";
			case 51: return "Light drizzle";
			case 61: return "Light rain";
			case 63: return "Moderate rain";
			case 80: return "Rain showers";
			case 95: return "Thunderstorm";
			default: return "Unknown (" + code + ")";
		}
	}
}