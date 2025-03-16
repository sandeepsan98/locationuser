const BASE_URL = '/codeflowpro';
let refreshToken = localStorage.getItem('refreshToken');
let currentPage = 1;
const pageSize = 10;

function showToast(message, type = 'success') {
    Toastify({
        text: message,
        duration: 3000,
        gravity: 'top',
        position: 'right',
        style: { background: type === 'success' ? '#10B981' : '#EF4444' },
    }).showToast();
}

async function checkAuth() {
    if (!refreshToken) {
        location.href = 'login.html';
        return false;
    }
    return true;
}

async function refreshJwt() {
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
    localStorage.removeItem('refreshToken');
    location.href = 'login.html';
    return false;
}

const map = L.map('my-map').setView([48.1500327, 11.5753989], 15);
L.tileLayer('https://maps.geoapify.com/v1/tile/osm-carto/{z}/{x}/{y}.png?apiKey=01369d8d7c974b61b45ad10e0b1be91c', {
    attribution: 'Powered by Geoapify | © OpenStreetMap contributors',
    maxZoom: 20
}).addTo(map);

async function fetchLocations(page = 1) {
    if (!await checkAuth()) return;
    try {
        const response = await axios.get(`${BASE_URL}/locations?page=${page}&size=${pageSize}`, { withCredentials: true });
        if (response.data.success) {
            const locations = response.data.data;
            document.getElementById('locationList').innerHTML = locations.map(loc => `
                <div class="p-2 bg-gray-700 rounded-lg mb-2 cursor-pointer" onclick="centerMap(${loc.latitude}, ${loc.longitude})">
                    ${loc.street}, ${loc.city} (${loc.weather_condition}, ${loc.temperature}°C)
                </div>
            `).join('');
            locations.forEach(loc => L.marker([loc.latitude, loc.longitude]).addTo(map));
        }
    } catch (error) {
        if (error.response?.status === 401) await refreshJwt();
    }
}

function centerMap(lat, lon) {
    map.setView([lat, lon], 15);
}

document.getElementById('getLocationBtn')?.addEventListener('click', async () => {
    if (!await checkAuth()) return;
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(async (position) => {
            const { latitude, longitude } = position.coords;
            try {
                const response = await axios.post(`${BASE_URL}/locations`, `latitude=${latitude}&longitude=${longitude}`, {
                    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                    withCredentials: true
                });
                if (response.data.success) {
                    const { data } = response.data;
                    document.getElementById('name').value = data.street || '';
                    document.getElementById('street').value = data.street;
                    document.getElementById('city').value = data.city;
                    document.getElementById('weather').value = `${data.weather} (${data.temperature}°C)`;
                    L.marker([latitude, longitude]).addTo(map);
                    map.setView([latitude, longitude], 15);
                    fetchLocations(currentPage);
                }
            } catch (error) {
                if (error.response?.status === 401) await refreshJwt();
            }
        }, (error) => showToast('Error getting location', 'error'));
    } else {
        showToast('Geolocation not supported', 'error');
    }
});

document.getElementById('logoutBtn')?.addEventListener('click', async () => {
    if (!refreshToken) return;
    const response = await fetch(`${BASE_URL}/logout`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `refreshToken=${encodeURIComponent(refreshToken)}`,
        credentials: 'include'
    });
    const result = await response.json();
    if (result.success) {
        localStorage.removeItem('refreshToken');
        location.href = 'login.html';
    }
});

document.getElementById('prevPage')?.addEventListener('click', () => {
    if (currentPage > 1) {
        currentPage--;
        fetchLocations(currentPage);
    }
});

document.getElementById('nextPage')?.addEventListener('click', () => {
    currentPage++;
    fetchLocations(currentPage);
});

fetchLocations(currentPage);