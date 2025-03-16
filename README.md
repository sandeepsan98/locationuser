---

# Location Tracker

![Location Tracker](https://img.shields.io/badge/Status-Active-brightgreen)  
A web-based application for tracking, managing, and sharing locations with real-time weather updates, route planning, and weather alerts.

## Table of Contents
1. [Project Overview](#project-overview)
2. [Features](#features)
3. [Technologies Used](#technologies-used)
4. [APIs Integrated](#apis-integrated)
5. [Prerequisites](#prerequisites)
6. [Setup Instructions](#setup-instructions)
7. [Usage Guide](#usage-guide)
8. [Application Flow](#application-flow)
9. [Database Schema](#database-schema)
10. [Security](#security)


---

## Project Overview
Location Tracker is a full-stack web application designed to help users save, manage, and share geographical locations. It integrates real-time weather data, route planning, and weather alerts, providing a comprehensive tool for location-based activities. The application features a user-friendly interface with map visualization powered by Leaflet and a robust backend built with Java Servlets.

The primary goal of this project is to offer a seamless experience for tracking locations, monitoring weather conditions, and planning routes, all while ensuring secure user authentication and data management.

---

## Features
- **User Authentication**: Secure login, registration, and logout with JWT-based authentication and refresh tokens.
- **Location Management**: Add, edit, delete, and view saved locations with details such as coordinates, address, and tags.
- **Weather Integration**: Real-time weather updates and 7-day forecasts for saved locations.
- **Location Sharing**: Generate temporary shareable links for locations (valid for 24 hours).
- **Route Planning**: Suggest optimized routes between multiple locations using various transport modes (car, bike, walking).
- **Weather Alerts**: Set custom weather alerts based on temperature thresholds or weather conditions.
- **Search Functionality**: Search for locations by name and save them directly.
- **Map Visualization**: Interactive map interface powered by Leaflet for location visualization and route display.
- **Notifications**: Receive notifications for weather alerts and other updates.

---

## Technologies Used
### Backend
- **Java**: Core programming language for the backend.
- **Java Servlets**: Handles HTTP requests and responses.
- **JDBC**: Database connectivity using `ConnectionUtil`.
- **BCrypt**: Password hashing for secure user authentication.
- **JWT (JSON Web Tokens)**: Token-based authentication and authorization.
- **MySQL**: Relational database for storing user and location data.
- **Apache Tomcat**: Servlet container for deploying the application.

### Frontend
- **HTML5**: Structure of the web pages.
- **CSS3**: Styling with Tailwind CSS for responsive design.
- **JavaScript**: Client-side logic and interactivity.
- **Leaflet.js**: Interactive map visualization.
- **Axios**: HTTP client for making API requests.
- **Toastify.js**: Lightweight toast notifications.
- **Tailwind CSS**: Utility-first CSS framework for styling.

### Tools & Libraries
- **Geoapify**: Geocoding and reverse geocoding services.
- **OpenRouteService**: Route planning and optimization.
- **Open-Meteo**: Weather data and forecasts.
- **Maven**: Dependency management and build tool.

---

## APIs Integrated
1. **Geoapify API**
   - **Purpose**: Geocoding (search by name) and reverse geocoding (address from coordinates).
   - **API Key**: `key`
   - **Endpoints**:
     - `/v1/geocode/search`: Search for locations by name.
     - `/v1/geocode/reverse`: Get address details from coordinates.
   - **Usage**: Used for location search and address population.

2. **OpenRouteService API**
   - **Purpose**: Route planning and optimization between multiple locations.
   - **API Key**: `key`
   - **Endpoint**: `/v2/directions/{profile}`
   - **Usage**: Generates routes for selected transport modes (car, bike, walking) with optional optimization.

3. **Open-Meteo API**
   - **Purpose**: Real-time weather data and 7-day weather forecasts.
   - **Endpoint**: `/v1/forecast`
   - **Usage**: Fetches current weather conditions and forecasts for saved locations.

---

## Prerequisites
- **Java 17+**: Required for running the backend.
- **Maven**: For dependency management and building the project.
- **MySQL**: Database server for storing application data.
- **Apache Tomcat 9+**: Servlet container for deployment.
- **Node.js**: Optional, for local development of frontend assets (if modifying).
- **Web Browser**: Modern browser (Chrome, Firefox, Edge) for accessing the application.

---

## Setup Instructions
### Backend Setup
1. **Clone the Repository**
   ```bash
   git clone https://github.com/yourusername/location-tracker.git
   cd location-tracker
   ```

2. **Configure Database**
   - Install MySQL and create a database named `location_tracker`.
   - Execute the following SQL to create required tables:
     ```sql
     CREATE TABLE users (
         id INT AUTO_INCREMENT PRIMARY KEY,
         email VARCHAR(255) UNIQUE NOT NULL,
         password VARCHAR(255) NOT NULL
     );

     CREATE TABLE locations (
         id INT AUTO_INCREMENT PRIMARY KEY,
         user_id INT,
         latitude DOUBLE NOT NULL,
         longitude DOUBLE NOT NULL,
         temperature DOUBLE,
         weather_condition VARCHAR(50),
         street VARCHAR(255),
         city VARCHAR(100),
         country VARCHAR(100),
         name VARCHAR(100),
         forecast_data TEXT,
         forecast_updated_at TIMESTAMP,
         tags VARCHAR(255),
         is_favorite BOOLEAN DEFAULT FALSE,
         last_visited_at TIMESTAMP,
         FOREIGN KEY (user_id) REFERENCES users(id)
     );

     CREATE TABLE refresh_tokens (
         id INT AUTO_INCREMENT PRIMARY KEY,
         user_id INT,
         token VARCHAR(255) NOT NULL,
         expiry_date TIMESTAMP NOT NULL,
         FOREIGN KEY (user_id) REFERENCES users(id)
     );

     CREATE TABLE shared_locations (
         id INT AUTO_INCREMENT PRIMARY KEY,
         location_id INT,
         share_token VARCHAR(36) NOT NULL,
         expiry_date TIMESTAMP NOT NULL,
         FOREIGN KEY (location_id) REFERENCES locations(id)
     );

     CREATE TABLE weather_alerts (
         id INT AUTO_INCREMENT PRIMARY KEY,
         location_id INT,
         user_id INT,
         min_temp DOUBLE,
         max_temp DOUBLE,
         weather_condition VARCHAR(50),
         FOREIGN KEY (location_id) REFERENCES locations(id),
         FOREIGN KEY (user_id) REFERENCES users(id)
     );

     CREATE TABLE notifications (
         id INT AUTO_INCREMENT PRIMARY KEY,
         user_id INT,
         location_id INT,
         message TEXT NOT NULL,
         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
         is_read BOOLEAN DEFAULT FALSE,
         FOREIGN KEY (user_id) REFERENCES users(id),
         FOREIGN KEY (location_id) REFERENCES locations(id)
     );
     ```
   - Update the database connection details in `ConnectionUtil.java`.

3. **Build the Project**
   ```bash
   mvn clean install
   ```

4. **Deploy to Tomcat**
   - Copy the generated `.war` file from the `target` directory to the `webapps` directory of your Tomcat installation.
   - Start Tomcat:
     ```bash
     ./startup.sh  # On Unix/Linux/Mac
     startup.bat   # On Windows
     ```

5. **Access the Application**
   - Open your browser and navigate to: `http://localhost:8080/locationweb`

### Frontend Setup
- The frontend is bundled with the backend in the `war` file. No separate setup is required unless you want to modify the frontend independently:
  1. Install Node.js (optional for development).
  2. Install dependencies (if modifying):
     ```bash
     npm install
     ```
  3. Modify JavaScript/CSS files as needed and rebuild the project.

---

## Usage Guide
1. **Register an Account**
   - Navigate to `/register.html`.
   - Enter a valid email and password (minimum 6 characters).
   - Submit the form to create an account.

2. **Login**
   - Navigate to `/login.html`.
   - Enter your registered email and password.
   - Upon successful login, you’ll be redirected to the main dashboard (`index.html`).

3. **Add a Location**
   - Click "Get My Location" to use your current position or use the search bar to find a location.
   - Add optional tags and mark as favorite if desired.
   - Save the location to your list.

4. **Manage Locations**
   - View your saved locations in the list.
   - Click on a location to center the map on it and record a visit.
   - Edit, delete, share, or set weather alerts for locations using the respective buttons.

5. **Share a Location**
   - Click "Share" on a location to generate a temporary link (valid for 24 hours).
   - Copy the link and share it with others.

6. **Plan a Route**
   - Select at least two locations by checking their checkboxes.
   - Choose a transport mode (car, bike, walking).
   - Toggle "Optimize Route" if desired.
   - Click "Suggest Route" to view the route on the map with distance and duration.

7. **Set Weather Alerts**
   - Click "Set Alert" on a location.
   - Define minimum/maximum temperature thresholds or a specific weather condition.
   - Save the alert to receive notifications when conditions are met.

8. **View Notifications**
   - Click "Notifications" to view weather alerts and other updates.

9. **Logout**
   - Click "Logout" to end your session and return to the login page.

---

## Application Flow
1. **User Authentication**
   - User registers or logs in.
   - Backend generates JWT and refresh token, stored in cookies and local storage, respectively.
   - JWT is validated for all protected routes via `JwtFilter`.

2. **Location Management**
   - User adds a location via geolocation or search.
   - Backend fetches address (Geoapify) and weather data (Open-Meteo), then saves to the database.
   - Locations are listed with real-time weather updates.

3. **Weather Integration**
   - Real-time weather is fetched on page load and updated periodically.
   - 7-day forecasts are cached for 24 hours and updated as needed.

4. **Route Planning**
   - User selects locations and transport mode.
   - Backend uses OpenRouteService to calculate the route, which is displayed on the map.

5. **Weather Alerts**
   - User sets alert conditions.
   - A scheduled task checks weather conditions hourly and generates notifications if conditions are met.

6. **Sharing**
   - User generates a share link.
   - Backend creates a temporary token, valid for 24 hours, linked to the location.

---

## Database Schema
- **users**: Stores user credentials.
- **locations**: Stores user-saved locations with weather and metadata.
- **refresh_tokens**: Stores refresh tokens for session management.
- **shared_locations**: Stores temporary sharing tokens for locations.
- **weather_alerts**: Stores user-defined weather alert conditions.
- **notifications**: Stores notifications for weather alerts and other events.

---

## Security
- **JWT Authentication**: All protected endpoints require a valid JWT, validated by `JwtFilter`.
- **Password Hashing**: User passwords are hashed using BCrypt.
- **Refresh Tokens**: Long-lived refresh tokens are stored securely in the database and local storage.
- **CSRF Protection**: Implicitly handled by JWT and cookie-based authentication.
- **Input Validation**: Frontend and backend validate user inputs to prevent injection attacks.
- **HTTPS**: Recommended for production to secure data in transit.

---




---

