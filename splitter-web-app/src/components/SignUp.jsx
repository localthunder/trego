import React, { useState } from 'react';
import axios from '../services/api';

const SignUpPage = () => {
    const [username, setUsername] = useState('');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [message, setMessage] = useState('');

    const handleSignUp = async () => {
        try {
            const response = await axios.post('/auth/register', { // Use the correct endpoint
                username,
                email,
                password
            });
            setMessage('Sign up successful! Please log in.');
        } catch (error) {
            setMessage('Sign up failed.');
            console.error('Error signing up', error);
        }
    };

    return (
        <div>
            <h2>Sign Up</h2>
            <input
                type="text"
                placeholder="Username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
            />
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
            <button onClick={handleSignUp}>Sign Up</button>
            {message && <p>{message}</p>}
        </div>
    );
};

export default SignUpPage;
