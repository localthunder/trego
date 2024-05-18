import React, { useState } from 'react';

const Authentication = ({ onAuthenticated }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');

    const handleLogin = () => {
        // Simulate authentication and store a dummy token
        const dummyToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6MSwidXNlcm5hbWUiOiJ0ZXN0dXNlciIsImVtYWlsIjoidGVzdHVzZXJAZXhhbXBsZS5jb20iLCJpYXQiOjE3MTU2OTI0NDEsImV4cCI6MTcxNTY5NjA0MX0.73v-96ca4CR3wzAXiSzVZHzRaqbP0xyoovV2D4rFqe4";
        localStorage.setItem('authToken', dummyToken);
        onAuthenticated(dummyToken);
    };

    return (
        <div>
            <h2>Login</h2>
            <input
                type="text"
                placeholder="Username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
            />
            <input
                type="password"
                placeholder="Password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
            />
            <button onClick={handleLogin}>Login</button>
        </div>
    );
};

export default Authentication;
