let refreshToken = localStorage.getItem('refreshToken');
const BASE_URL = '/codeflowpro';

// Toast notification helper
function showToast(message, type = 'success') {
    Toastify({
        text: message,
        duration: 3000,
        gravity: 'top',
        position: 'right',
        style: { background: type === 'success' ? '#10B981' : '#EF4444' },
        stopOnFocus: true,
    }).showToast();
}

// Refresh JWT
async function refreshJwt() {
    if (!refreshToken) {
        throw new Error('No refresh token available');
    }
    const response = await fetch(`${BASE_URL}/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `refreshToken=${encodeURIComponent(refreshToken)}`,
        credentials: 'include'
    });
    const result = await response.json();
    if (!result.success) {
        throw new Error(result.message);
    }
    showToast('Token refreshed successfully');
    return result;
}

// Fetch user email
async function fetchUserEmail() {
    try {
        const response = await fetch(`${BASE_URL}/user-info`, {
            method: 'GET',
            credentials: 'include'
        });
        const result = await response.json();
        if (result.success) {
            return result.email;
        } else {
            throw new Error(result.message);
        }
    } catch (error) {
        console.error('Error fetching user email:', error);
        showToast(`Error fetching email: ${error.message}`, 'error');
        return null;
    }
}

// Protected resource with email display
document.getElementById('protectedBtn')?.addEventListener('click', async () => {
    try {
        console.log('Attempting to access protected resource...');
        let response = await fetch(`${BASE_URL}/protected`, {
            method: 'GET',
            credentials: 'include'
        });
        let result = await response.json();

        if (!result.success && response.status === 401) {
            console.log('Received 401, attempting token refresh...');
            await refreshJwt();
            response = await fetch(`${BASE_URL}/protected`, {
                method: 'GET',
                credentials: 'include'
            });
            result = await response.json();
        }

        if (result.success) {
            const email = await fetchUserEmail();
            if (email) {
                const message = `${result.message} Email: ${email}`;
                showToast(message);
                document.getElementById('response').innerText = message;
            } else {
                showToast(result.message);
                document.getElementById('response').innerText = result.message;
            }
        } else {
            throw new Error(result.message);
        }
    } catch (error) {
        console.error('Protected resource error:', error);
        showToast(`Error: ${error.message}`, 'error');
        if (error.message.includes('refresh token') || error.message.includes('Invalid or expired')) {
            setTimeout(() => location.href = 'login.html', 1000);
        }
    }
});

// Login
document.getElementById('loginForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const email = formData.get('email');
    const password = formData.get('password');

    if (!email || !password || !email.match(/^[A-Za-z0-9+_.-]+@(.+)$/)) {
        showToast('Valid email and password required', 'error');
        return;
    }

    try {
        const response = await fetch(`${BASE_URL}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ email, password }),
            credentials: 'include'
        });
        const result = await response.json();
        if (result.success) {
            refreshToken = result.refreshToken;
            localStorage.setItem('refreshToken', refreshToken);
            showToast(result.message);
            setTimeout(() => location.href = 'index.html', 1000);
        } else {
            showToast(result.message, 'error');
        }
    } catch (error) {
        console.error('Login error:', error);
        showToast('Network error, please try again', 'error');
    }
});

// Logout
document.getElementById('logoutBtn')?.addEventListener('click', async () => {
    if (!refreshToken) {
        showToast('Not logged in', 'error');
        return;
    }

    try {
        const response = await fetch(`${BASE_URL}/logout`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `refreshToken=${encodeURIComponent(refreshToken)}`,
            credentials: 'include'
        });
        const result = await response.json();
        if (result.success) {
            refreshToken = null;
            localStorage.removeItem('refreshToken');
            showToast(result.message);
            document.getElementById('response').innerText = '';
            setTimeout(() => location.href = 'login.html', 1000);
        } else {
            showToast(result.message, 'error');
        }
    } catch (error) {
        console.error('Logout error:', error);
        showToast('Network error, please try again', 'error');
    }
});

// Register
document.getElementById('registerForm')?.addEventListener('submit', async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const email = formData.get('email');
    const password = formData.get('password');

    if (!email || !email.match(/^[A-Za-z0-9+_.-]+@(.+)$/) || password.length < 6) {
        showToast('Valid email and password (min 6 chars) required', 'error');
        return;
    }

    try {
        const response = await fetch(`${BASE_URL}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: new URLSearchParams({ email, password })
        });
        const result = await response.json();
        if (result.success) {
            showToast(result.message);
            setTimeout(() => location.href = 'login.html', 1000);
        } else {
            showToast(result.message, 'error');
        }
    } catch (error) {
        console.error('Register error:', error);
        showToast('Network error, please try again', 'error');
    }
});