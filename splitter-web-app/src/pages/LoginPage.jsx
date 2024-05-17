import React from 'react';
import Login from '../components/Login.jsx';

const LoginPage = ({ onAuthenticated }) => {
    return (
        <div>
            <Login onAuthenticated={onAuthenticated} />
        </div>
    );
};

export default LoginPage;
