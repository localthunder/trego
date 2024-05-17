import React, { createContext, useState, useEffect } from 'react';
import axios from '../services/api';

export const AuthContext = createContext();

const AuthProvider = ({ children }) => {
    const [isAuthenticated, setIsAuthenticated] = useState(!!localStorage.getItem('authToken'));

    const login = (token, callback) => {
        console.log('Login function called with token:', token); // Debug log
        localStorage.setItem('authToken', token);
        setIsAuthenticated(true);
        if (callback) {
            console.log('Executing callback after login'); // Debug log
            callback();
        }
    };

    const logout = () => {
        console.log('Logout function called'); // Debug log
        localStorage.removeItem('authToken');
        localStorage.removeItem('refreshToken');
        setIsAuthenticated(false);
    };

    useEffect(() => {
        const checkAuth = async () => {
            try {
                const response = await axios.get('/auth/me', {
                    headers: {
                        'JWT-Authorization': `Bearer ${localStorage.getItem('authToken')}`
                    }
                });
                if (response.status === 200) {
                    console.log('User authenticated'); // Debug log
                    setIsAuthenticated(true);
                } else {
                    setIsAuthenticated(false);
                }
            } catch (error) {
                setIsAuthenticated(false);
            }
        };
        checkAuth();
    }, []);

    return (
        <AuthContext.Provider value={{ isAuthenticated, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export default AuthProvider;
