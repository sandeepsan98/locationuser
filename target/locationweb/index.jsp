<%@ page language="java" contentType="text/html; charset=UTF-8"
    pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Automatic Location Retrieval</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            background-color: #f0f0f0;
            padding: 20px;
        }
        button {
            background-color: #4CAF50;
            color: white;
            padding: 10px 20px;
            border: none;
            border-radius: 4px;
            cursor: pointer;
        }
        button:hover {
            background-color: #45a049;
        }
        
        
        html,
      body,
.main {
  width: 100%;
  height: 100%;
  margin: 0;
}

.main {
  display: flex;
  flex-direction: row;
}

.results {
    padding: 10px;
    display: flex;
    flex-direction: column;
    min-width: 400px;
    max-width: 400px;
}

.status {
    padding: 10px;
    max-width: 400px;
}

#my-map {
  flex: 1;
height: 580px;
}
    </style>
    
    
 <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
     integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY="
     crossorigin=""/>
</head>
<body>
    <h1>Automatic Location Retrieval</h1>
    <button id="getLocationBtn">Get My Location</button>
<div class="main">
  <div class="controls">
    <div class="results">
          <label for="name">Name:</label>          
          <input id="name" type="text" readonly />
          <label for="house-number">House number:</label>          
          <input id="house-number" type="text" readonly />
          <label for="street">Street:</label>          
          <input id="street" type="text" readonly />
          <label for="postcode">Postcode:</label>          
          <input id="postcode" type="text" readonly />
          <label for="city">City:</label>          
          <input id="city" type="text" readonly />
          <label for="state">State:</label>          
          <input id="state" type="text" readonly />
          <label for="country">Country:</label>          
          <input id="country" type="text" readonly />
    </div>
    <div class="status">
      <span id="status">Click on the map to query an address by coordinates</span>
    </div>
  </div>
  <div id="my-map"></div>
</div>

    
  
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js" integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""></script>
  
  	<script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.min.js"></script>
    <script src="dot.js"></script>
</body>
</html>
