const BASE_URL = '/locationweb';
let refreshToken = localStorage.getItem('refreshToken');
let currentPage = 1;
const pageSize = 10;
let map;
let routeLayer = null;

function showToast(message, type = 'success') {
    Toastify({
        text: message,
        duration: 3000,
        gravity: 'top',
        position: 'right',
        style: { background: type === 'success' ? '#10B981' : '#EF4444' },
    }).showToast();
}

function decodePolyline(encoded) {
    let points = [];
    let index = 0, len = encoded.length;
    let lat = 0, lng = 0;

    while (index < len) {
        let b, shift = 0, result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlat = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lat += dlat;

        shift = 0;
        result = 0;
        do {
            b = encoded.charCodeAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        let dlng = ((result & 1) ? ~(result >> 1) : (result >> 1));
        lng += dlng;

        points.push([lat / 1e5, lng / 1e5]);
    }
    return points;
}

async function checkAuth() {
    if (!refreshToken) {
        location.href = 'login.html';
        return false;
    }
    return true;
}

async function refreshJwt() {
    try {
        const response = await fetch(`${BASE_URL}/refresh`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `refreshToken=${encodeURIComponent(refreshToken)}`,
            credentials: 'include'
        });
        const result = await response.json();
        if (result.success) {
            showToast('Token refreshed');
            return true;
        }
        throw new Error('Refresh failed');
    } catch (error) {
        localStorage.removeItem('refreshToken');
        location.href = 'login.html';
        return false;
    }
}

function initializeMap() {
    if (!map) {
        map = L.map('my-map', { zIndex: 1 }).setView([13.0827, 80.2707], 15);
        L.tileLayer('https://maps.geoapify.com/v1/tile/osm-carto/{z}/{x}/{y}.png?apiKey=01369d8d7c974b61b45ad10e0b1be91c', {
            attribution: 'Powered by Geoapify | © OpenStreetMap contributors',
            maxZoom: 20
        }).addTo(map);
    }
}

async function fetchRealTimeWeather(latitude, longitude) {
    try {
        const response = await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}&current_weather=true`);
        const data = await response.json();
        if (data.current_weather) {
            return {
                temperature: data.current_weather.temperature,
                weather_condition: wmoCodeToDescription(data.current_weather.weathercode)
            };
        }
        return null;
    } catch (error) {
        console.error('Error fetching real-time weather:', error);
        return null;
    }
}

async function fetchLocations(page = 1) {
    if (!await checkAuth()) return;
    try {
        const response = await axios.get(`${BASE_URL}/locations/list?page=${page}&size=${pageSize}`, { withCredentials: true });
        if (response.data.success) {
            const locations = response.data.data;
            currentPage = page;
            document.getElementById('locationList').innerHTML = locations.length > 0 ?
                locations.map(loc => `
                    <div class="p-2 bg-gray-700 rounded-lg mb-2 flex justify-between items-center ${loc.isFavorite ? 'border-2 border-yellow-500' : ''}" id="location-${loc.id}">
                        <span class="cursor-pointer flex items-center">
                            <input type="checkbox" value="${loc.id}" class="mr-2" onchange="updateRouteButton()">
                            <span onclick="centerMap(${loc.latitude}, ${loc.longitude}, ${loc.id}); showForecast('${encodeURIComponent(JSON.stringify(loc.forecast))}')">
                                ${loc.isFavorite ? '<span class="text-yellow-400">★</span> ' : ''}${loc.name ? loc.name + ' - ' : ''}${loc.street}, ${loc.city}
                                (<span id="weather-${loc.id}">${loc.weather_condition}, ${loc.temperature !== null ? loc.temperature : 'N/A'}°C</span>)
                                ${loc.tags ? '<span class="text-gray-400 text-sm ml-2">[' + loc.tags + ']</span>' : ''}
                                ${loc.lastVisitedAt ? '<span class="text-gray-500 text-sm ml-2">Last visited: ' + new Date(loc.lastVisitedAt).toLocaleString() + '</span>' : ''}
                            </span>
                        </span>
                        <div>
                            <button onclick="shareLocation(${loc.id})" class="bg-blue-500 hover:bg-blue-600 text-white px-2 py-1 rounded text-sm mr-2">Share</button>
                            <button onclick="editLocation(${loc.id}, '${loc.name || ''}', '${loc.street}', '${loc.city}', '${loc.country}', '${loc.tags || ''}', ${loc.isFavorite})" class="bg-yellow-500 hover:bg-yellow-600 text-white px-2 py-1 rounded text-sm mr-2">Edit</button>
                            <button onclick="setWeatherAlert(${loc.id})" class="bg-purple-500 hover:bg-purple-600 text-white px-2 py-1 rounded text-sm mr-2">Set Alert</button>
                            <button onclick="deleteLocation(${loc.id})" class="bg-red-500 hover:bg-red-600 text-white px-2 py-1 rounded text-sm">Delete</button>
                        </div>
                    </div>
                `).join('') :
                '<div class="p-2 text-gray-400">No locations found</div>';
            map.eachLayer(layer => {
                if (layer instanceof L.Marker) map.removeLayer(layer);
            });
            locations.forEach(loc => L.marker([loc.latitude, loc.longitude]).addTo(map));
            document.getElementById('prevPage').disabled = page <= 1;
            document.getElementById('nextPage').disabled = locations.length < pageSize;
            updateRouteButton();

            locations.forEach(async loc => {
                const weather = await fetchRealTimeWeather(loc.latitude, loc.longitude);
                if (weather) {
                    const weatherSpan = document.getElementById(`weather-${loc.id}`);
                    if (weatherSpan) {
                        weatherSpan.textContent = `${weather.weather_condition}, ${weather.temperature.toFixed(1)}°C`;
                    }
                }
            });
        } else {
            showToast(response.data.message || 'Failed to fetch locations', 'error');
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
        else showToast('Error fetching locations: ' + (error.response?.data?.message || error.message), 'error');
    }
}

async function centerMap(lat, lon, locationId) {
    map.setView([lat, lon], 18);
    if (locationId) {
        try {
            const response = await axios.post(`${BASE_URL}/locations/${locationId}/visit`, {}, { withCredentials: true });
            if (response.data.success) {
                showToast('Visit recorded');
                fetchLocations(currentPage);
            } else {
                showToast(response.data.message || 'Failed to record visit', 'error');
            }
        } catch (error) {
            console.error('Error recording visit:', error);
            showToast('Error recording visit: ' + (error.response?.data?.message || error.message), 'error');
        }
    }
}

function showForecast(forecastJson) {
    const forecastContainer = document.getElementById('forecastContainer');
    const forecastList = document.getElementById('forecastList');
    const forecast = forecastJson ? JSON.parse(decodeURIComponent(forecastJson)) : null;

    if (!forecast || !forecast.daily) {
        forecastContainer.style.display = 'none';
        return;
    }

    forecastContainer.style.display = 'block';
    const daily = forecast.daily;
    forecastList.innerHTML = daily.time.map((date, index) => `
        <div class="forecast-day">
            <p><strong>${new Date(date).toLocaleDateString()}</strong></p>
            <p>Max: ${daily.temperature_2m_max[index]}°C | Min: ${daily.temperature_2m_min[index]}°C</p>
            <p>${wmoCodeToDescription(daily.weathercode[index])}</p>
        </div>
    `).join('');
}

function wmoCodeToDescription(code) {
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

async function shareLocation(locationId) {
    if (!await checkAuth()) return;
    try {
        const response = await axios.post(`${BASE_URL}/locations/share`, `locationId=${locationId}`, {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            withCredentials: true
        });
        if (response.data.success) {
            navigator.clipboard.writeText(response.data.shareUrl);
            showToast('Share URL copied to clipboard: ' + response.data.shareUrl);
        } else {
            showToast(response.data.message || 'Failed to share location', 'error');
        }
    } catch (error) {
        showToast('Error sharing location: ' + (error.response?.data?.message || error.message), 'error');
    }
}

async function deleteLocation(locationId) {
    if (!await checkAuth()) return;
    if (!confirm('Are you sure you want to delete this location?')) return;
    try {
        const response = await axios.delete(`${BASE_URL}/locations/${locationId}`, { withCredentials: true });
        if (response.data.success) {
            showToast('Location deleted');
            fetchLocations(currentPage);
        } else {
            showToast(response.data.message || 'Failed to delete location', 'error');
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
        else showToast('Error deleting location: ' + (error.response?.data?.message || error.message), 'error');
    }
}

function editLocation(id, name, street, city, country, tags, isFavorite) {
    document.getElementById('editId').value = id;
    document.getElementById('editName').value = name;
    document.getElementById('editStreet').value = street;
    document.getElementById('editCity').value = city;
    document.getElementById('editCountry').value = country;
    document.getElementById('editTags').value = tags;
    document.getElementById('editIsFavorite').checked = isFavorite;
    document.getElementById('editFormContainer').style.display = 'block';
}

document.getElementById('editForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!await checkAuth()) return;
    const id = document.getElementById('editId').value;
    const name = document.getElementById('editName').value;
    const street = document.getElementById('editStreet').value;
    const city = document.getElementById('editCity').value;
    const country = document.getElementById('editCountry').value;
    const tags = document.getElementById('editTags').value;
    const isFavorite = document.getElementById('editIsFavorite').checked;

    const data = { name, street, city, country, tags, isFavorite };

    try {
        const response = await axios.put(`${BASE_URL}/locations/${id}`, data, {
            headers: { 'Content-Type': 'application/json' },
            withCredentials: true
        });
        if (response.data.success) {
            showToast('Location updated');
            document.getElementById('editFormContainer').style.display = 'none';
            fetchLocations(currentPage);
        } else {
            showToast(response.data.message || 'Failed to update location', 'error');
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
        else showToast('Error updating location: ' + (error.response?.data?.message || error.message), 'error');
    }
});

document.getElementById('updateCoords').addEventListener('click', async () => {
    if (!await checkAuth()) return;
    const id = document.getElementById('editId').value;

    navigator.geolocation.getCurrentPosition(async (position) => {
        const { latitude, longitude } = position.coords;
        const data = { latitude, longitude };

        try {
            const response = await axios.put(`${BASE_URL}/locations/${id}`, data, {
                headers: { 'Content-Type': 'application/json' },
                withCredentials: true
            });
            if (response.data.success) {
                showToast('Location coordinates updated');
                document.getElementById('editFormContainer').style.display = 'none';
                fetchLocations(currentPage);
            } else {
                showToast(response.data.message || 'Failed to update coordinates', 'error');
            }
        } catch (error) {
            if (error.response?.status === 401) await refreshJwt();
            else showToast('Error updating coordinates: ' + (error.response?.data?.message || error.message), 'error');
        }
    }, (error) => showToast('Error getting location: ' + error.message, 'error'));
});

function setWeatherAlert(locationId) {
    document.getElementById('alertLocationId').value = locationId;
    document.getElementById('minTemp').value = '';
    document.getElementById('maxTemp').value = '';
    document.getElementById('weatherCondition').value = '';
    document.getElementById('alertFormContainer').style.display = 'block';
}

document.getElementById('alertForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!await checkAuth()) return;
    const locationId = document.getElementById('alertLocationId').value;
    const minTemp = document.getElementById('minTemp').value;
    const maxTemp = document.getElementById('maxTemp').value;
    const weatherCondition = document.getElementById('weatherCondition').value;

    const data = { locationId };
    if (minTemp) data.minTemp = parseFloat(minTemp);
    if (maxTemp) data.maxTemp = parseFloat(maxTemp);
    if (weatherCondition) data.weatherCondition = weatherCondition;

    try {
        const response = await axios.post(`${BASE_URL}/alerts`, data, {
            headers: { 'Content-Type': 'application/json' },
            withCredentials: true
        });
        if (response.data.success) {
            showToast('Weather alert set');
            document.getElementById('alertFormContainer').style.display = 'none';
        } else {
            showToast(response.data.message || 'Failed to set weather alert', 'error');
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
        else showToast('Error setting weather alert: ' + (error.response?.data?.message || error.message), 'error');
    }
});

async function fetchNotifications() {
    if (!await checkAuth()) return;
    try {
        const response = await axios.get(`${BASE_URL}/alerts`, { withCredentials: true });
        if (response.data.success) {
            const notifications = response.data.data;
            document.getElementById('notificationList').innerHTML = notifications.length > 0 ?
                notifications.map(notif => `
                    <div class="notification ${notif.isRead ? 'opacity-50' : ''}">
                        <p>${notif.location}: ${notif.message}</p>
                        <p class="text-sm text-gray-400">${new Date(notif.createdAt).toLocaleString()}</p>
                    </div>
                `).join('') :
                '<div class="p-2 text-gray-400">No notifications</div>';
            document.getElementById('notificationsContainer').style.display = 'block';
        } else {
            showToast(response.data.message || 'Failed to fetch notifications', 'error');
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
        else showToast('Error fetching notifications: ' + (error.response?.data?.message || error.message), 'error');
    }
}

document.getElementById('getLocationBtn').addEventListener('click', async () => {
    if (!await checkAuth()) return;
    navigator.geolocation.getCurrentPosition(async (position) => {
        const { latitude, longitude } = position.coords;
        const tags = document.getElementById('tags').value;
        const isFavorite = document.getElementById('isFavorite').checked;
        const params = new URLSearchParams({ latitude, longitude, tags, isFavorite });

        try {
            const response = await axios.post(`${BASE_URL}/locations`, params.toString(), {
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                withCredentials: true
            });
            if (response.data.success) {
                const { data } = response.data;
                document.getElementById('street').value = data.street;
                document.getElementById('city').value = data.city;
                document.getElementById('weather').value = `${data.weather} (${data.temperature}°C)`;
                document.getElementById('tags').value = '';
                document.getElementById('isFavorite').checked = false;
                centerMap(data.latitude, data.longitude);
                fetchLocations(currentPage);
                showToast('Location saved');
            } else {
                showToast(response.data.message || 'Failed to save location', 'error');
            }
        } catch (error) {
            if (error.response?.status === 401) await refreshJwt();
            else showToast('Error saving location: ' + (error.response?.data?.message || error.message), 'error');
        }
    }, (error) => showToast('Error getting location: ' + error.message, 'error'));
});

document.getElementById('logoutBtn').addEventListener('click', () => {
    localStorage.removeItem('refreshToken');
    location.href = 'login.html';
});

document.getElementById('prevPage').addEventListener('click', () => {
    if (currentPage > 1) fetchLocations(--currentPage);
});

document.getElementById('nextPage').addEventListener('click', () => {
    fetchLocations(++currentPage);
});

document.getElementById('cancelEdit').addEventListener('click', () => {
    document.getElementById('editFormContainer').style.display = 'none';
});

document.getElementById('cancelAlert').addEventListener('click', () => {
    document.getElementById('alertFormContainer').style.display = 'none';
});

document.getElementById('closeForecast').addEventListener('click', () => {
    document.getElementById('forecastContainer').style.display = 'none';
});

document.getElementById('closeNotifications').addEventListener('click', () => {
    document.getElementById('notificationsContainer').style.display = 'none';
});

document.getElementById('showNotifications').addEventListener('click', () => {
    fetchNotifications();
});

// New Features: Search and Route Suggestion

async function searchLocation() {
    const query = document.getElementById('searchQuery').value.trim();
    if (!query) {
        showToast('Please enter a location name', 'error');
        return;
    }

    try {
        const response = await axios.get(`${BASE_URL}/locations/search?query=${encodeURIComponent(query)}`, { withCredentials: true });
        if (response.data.success) {
            const { latitude, longitude, street, city, country, formatted } = response.data.data;
            document.getElementById('searchResult').innerHTML = `
                <div class="p-2 bg-gray-700 rounded-lg mb-2">
                    <span>${formatted}</span>
                    <button onclick="saveSearchedLocation(${latitude}, ${longitude}, '${street}', '${city}', '${country}')" class="bg-green-500 hover:bg-green-600 text-white px-2 py-1 rounded text-sm ml-2">Save</button>
                </div>
            `;
        } else {
            showToast(response.data.message || 'No results found', 'error');
            document.getElementById('searchResult').innerHTML = '';
        }
    } catch (error) {
        showToast('Error searching location: ' + (error.response?.data?.message || error.message), 'error');
    }
}

async function saveSearchedLocation(latitude, longitude, street, city, country) {
    if (!await checkAuth()) return;
    const tags = document.getElementById('tags').value;
    const isFavorite = document.getElementById('isFavorite').checked;
    const params = new URLSearchParams({ latitude, longitude, tags, isFavorite });

    try {
        const response = await axios.post(`${BASE_URL}/locations`, params.toString(), {
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            withCredentials: true
        });
        if (response.data.success) {
            const { data } = response.data;
            document.getElementById('street').value = data.street;
            document.getElementById('city').value = data.city;
            document.getElementById('weather').value = `${data.weather} (${data.temperature}°C)`;
            document.getElementById('tags').value = '';
            document.getElementById('isFavorite').checked = false;
            document.getElementById('searchResult').innerHTML = '';
            centerMap(data.latitude, data.longitude);
            fetchLocations(currentPage);
            showToast('Location saved');
        } else {
            showToast(response.data.message || 'Failed to save location', 'error');
        }
    } catch (error) {
        showToast('Error saving location: ' + (error.response?.data?.message || error.message), 'error');
    }
}

async function suggestRoute() {
    const selectedLocations = Array.from(document.querySelectorAll('#locationList input[type="checkbox"]:checked'))
        .map(checkbox => checkbox.value);
    if (selectedLocations.length < 2) {
        showToast('Select at least two locations for a route', 'error');
        return;
    }

    const profile = document.getElementById('transportMode').value; // Get transport mode
    const optimize = document.getElementById('optimizeRoute').checked; // Get optimization toggle

    try {
        const response = await axios.get(`${BASE_URL}/locations/route?locationIds=${selectedLocations.join(',')}&profile=${profile}&optimize=${optimize}`, { withCredentials: true });
        if (response.data.success) {
            const routeData = response.data.data;
            console.log('Route data received:', routeData);

            if (!routeData.routes || !routeData.routes[0]) {
                showToast('No route data available', 'error');
                return;
            }

            const route = routeData.routes[0];
            const coordinates = decodePolyline(route.geometry);
            const distance = route.summary.distance / 1000; // km
            const duration = route.summary.duration / 60; // minutes

            if (routeLayer) map.removeLayer(routeLayer);
            routeLayer = L.polyline(coordinates, { color: 'blue' }).addTo(map);
            map.fitBounds(routeLayer.getBounds());
            showToast(`Route: ${distance.toFixed(1)} km, ${duration.toFixed(1)} minutes`);
        } else {
            showToast(response.data.message || 'Failed to fetch route', 'error');
        }
    } catch (error) {
        console.error('Route fetch error:', error);
        showToast('Error fetching route: ' + (error.response?.data?.message || error.message), 'error');
    }
}

function updateRouteButton() {
    const selectedCount = document.querySelectorAll('#locationList input[type="checkbox"]:checked').length;
    document.getElementById('suggestRouteBtn').disabled = selectedCount < 2;
}

document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('searchBtn').addEventListener('click', searchLocation);
    document.getElementById('suggestRouteBtn').addEventListener('click', suggestRoute);
    initializeMap();
    fetchLocations(currentPage);
});