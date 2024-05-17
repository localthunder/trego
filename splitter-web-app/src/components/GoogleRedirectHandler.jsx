import React, { useEffect, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContexts';

const GoogleRedirectHandler = () => {
    const { login, isAuthenticated } = useContext(AuthContext);
    const navigate = useNavigate();

    useEffect(() => {
        const handleAuth = async () => {
            const urlParams = new URLSearchParams(window.location.search);
            const token = urlParams.get('token');
            const refreshToken = urlParams.get('refreshToken');

            if (token) {
                console.log('Token received:', token); // Debug log
                localStorage.setItem('authToken', token);
                localStorage.setItem('refreshToken', refreshToken);
                login(token, () => {
                    console.log('Navigating to home page'); // Debug log
                    navigate('/');
                });
            } else {
                navigate('/login');
            }
        };

        handleAuth();
    }, [login, navigate]);

    useEffect(() => {
        console.log('isAuthenticated:', isAuthenticated); // Debug log
    }, [isAuthenticated]);

    return <div>Loading...</div>;
};

export default GoogleRedirectHandler;
