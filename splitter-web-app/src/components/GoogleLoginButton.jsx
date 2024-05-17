import React from 'react';

const GoogleLoginButton = () => {
    const handleLoginWithGoogle = () => {
        window.location.href = 'http://localhost:3000/api/auth/google';
    };

    return (
        <button onClick={handleLoginWithGoogle}>
            Login with Google
        </button>
    );
};

export default GoogleLoginButton;
