import React, { useState, useContext } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../services/api';
import { AuthContext } from '../contexts/AuthContexts';
import GoogleLoginButton from '../components/GoogleLoginButton';

const LoginPage = () => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const { login } = useContext(AuthContext);
    const navigate = useNavigate();

    const handleLogin = async (e) => {
        e.preventDefault();
        try {
            const response = await api.post('/auth/login', { email, password });
            login(response.data.token);
            navigate('/'); // Redirect to homepage after successful login
        } catch (error) {
            console.error('Error logging in', error);
        }
    };

    const goToSignUp = () => {
        navigate('/signup');
    };

    return (
        <div>
            <h2>Login</h2>
            <form onSubmit={handleLogin}>
                <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                />
                <button type="submit">Login</button>
            </form>
            <GoogleLoginButton />
            <p>
                Don't have an account? <button onClick={goToSignUp}>Sign Up</button>
            </p>
        </div>
    );
};

export default LoginPage;
