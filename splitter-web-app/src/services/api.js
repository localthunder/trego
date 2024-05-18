import axios from 'axios';

// Function to fetch JWT token from the backend
const fetchJwtToken = async () => {
    try {
        const response = await axios.get('http://localhost:3000/api/auth/token');
        const token = response.data.token;
        if (token) {
            const decodedToken = JSON.parse(atob(token.split('.')[1])); // Decode the JWT token to get its payload
            const expiryTime = decodedToken.exp * 1000; // Convert expiry time to milliseconds
            localStorage.setItem('authToken', token);
            localStorage.setItem('authTokenExpiry', expiryTime.toString());
            return token;
        }
    } catch (error) {
        console.error('Error fetching JWT token', error);
        return null;
    }
};

// Function to check if the JWT token has expired
const isTokenExpired = () => {
    const expiryTime = localStorage.getItem('authTokenExpiry');
    if (!expiryTime) {
        return true;
    }
    return Date.now() > parseInt(expiryTime, 10);
};

// Axios instance
const api = axios.create({
    baseURL: 'http://localhost:3000/api', // Replace with your backend URL
});

api.interceptors.request.use(
    async config => {
        let token = localStorage.getItem('authToken');
        if (!token || isTokenExpired()) {
            token = await fetchJwtToken();
        }
        if (token) {
            config.headers['JWT-Authorization'] = `Bearer ${token}`;
            config.headers['Content-Type'] = 'application/json';
        }
        return config;
    },
    error => {
        return Promise.reject(error);
    }
);

export default api;
