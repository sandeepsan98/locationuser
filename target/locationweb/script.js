// scripts.js
let latitude=0;
let longitude=0;

function getBaseUrlFromCurrentPage() {
	const baseUrl = window.location.protocol + '//' + window.location.host + '/';
	const contextPath = window.location.pathname.split('/')[1]; // Extract the context path

	return baseUrl + contextPath;
}
function getContextName() {

	const contextPath = '/'+window.location.pathname.split('/')[1]; // Extract the context path

	return contextPath;
}


document.addEventListener('DOMContentLoaded', function() {
    const getLocationBtn = document.getElementById('getLocationBtn');

    getLocationBtn.addEventListener('click', function() {
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(function(position) {
                 latitude = position.coords.latitude;
                longitude = position.coords.longitude;
       console.log("HII"+latitude+longitude)
                // Submit location to backend
				
		
				const requestData =
				    "&latitude=" + latitude +
				    "&longitude=" +  longitude;
			
				    
				const url = getBaseUrlFromCurrentPage()+"/SubmitLocationServlet?"; 

				axios.post(url, requestData)
							  .then(function (response) {
							    // handle success
							    console.log(response.data);
						
			
							 
							  })
							  .catch(function (error) {
							    // handle error
							    console.log(error);
							  })

							  
      
            }, function(error) {
                console.error('Error getting location:', error);
                alert('Error getting your location. Please try again.');
            });
        } else {
            alert('Geolocation is not supported by your browser.');
        }
    });

	
	
	
	

});




// Create a Leaflet map
const map = L.map('my-map').setView([48.1500327, 11.5753989], 15);
// Marker to save the position of found address
let marker;

// The API Key provided is restricted to JSFiddle website
// Get your own API Key on https://myprojects.geoapify.com
const myAPIKey = "01369d8d7c974b61b45ad10e0b1be91c";

// Retina displays require different mat tiles quality
const isRetina = L.Browser.retina;
const baseUrl = "https://maps.geoapify.com/v1/tile/osm-bright/{z}/{x}/{y}.png?apiKey={apiKey}";
const retinaUrl = "https://maps.geoapify.com/v1/tile/osm-bright/{z}/{x}/{y}@2x.png?apiKey={apiKey}";

// add Geoapify attribution
map.attributionControl.setPrefix('Powered by <a href="https://www.geoapify.com/" target="_blank">Geoapify</a>')

// Add map tiles layer. Set 20 as the maximal zoom and provide map data attribution.
L.tileLayer(isRetina ? retinaUrl : baseUrl, {
  attribution: '<a href="https://openmaptiles.org/" target="_blank">© OpenMapTiles</a> <a href="https://www.openstreetmap.org/copyright" target="_blank">© OpenStreetMap</a> contributors',
  apiKey: myAPIKey,
  maxZoom: 20,
  id: 'osm-bright',
}).addTo(map);

// move zoom controls to bottom right
map.zoomControl.remove();
L.control.zoom({
  position: 'bottomright'
}).addTo(map);

function onMapClick(e) {

  if (marker) {
    marker.remove();
  }

  const reverseGeocodingUrl = `https://api.geoapify.com/v1/geocode/reverse?lat=${e.latlng.lat}&lon=${e.latlng.lng}&apiKey=${myAPIKey}`;

  // call Reverse Geocoding API - https://www.geoapify.com/reverse-geocoding-api/
  fetch(reverseGeocodingUrl).then(result => result.json())
    .then(featureCollection => {
		console.log(featureCollection);
      if (featureCollection.features.length === 0) {
        document.getElementById("status").textContent = "The address is not found";
        return;
      }

      const foundAddress = featureCollection.features[0];
      document.getElementById("name").value = foundAddress.properties.name || '';
      document.getElementById("house-number").value = foundAddress.properties.housenumber || '';
      document.getElementById("street").value = foundAddress.properties.street || '';
      document.getElementById("postcode").value = foundAddress.properties.postcode || '';
      document.getElementById("city").value = foundAddress.properties.city || '';
      document.getElementById("state").value = foundAddress.properties.state || '';
      document.getElementById("country").value = foundAddress.properties.country || '';

      document.getElementById("status").textContent = `Found address: ${foundAddress.properties.formatted}`;

      marker = L.marker(new L.LatLng(foundAddress.properties.lat, foundAddress.properties.lon)).addTo(map);
    });

}

map.on('click', onMapClick);
