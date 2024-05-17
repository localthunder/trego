import React, { useContext } from 'react';
import { Navigate } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContexts';

const ProtectedRoute = ({ element }) => {
    const { isAuthenticated } = useContext(AuthContext);
    console.log('ProtectedRoute isAuthenticated:', isAuthenticated); // Debug log
    return isAuthenticated ? element : <Navigate to="/login" />;
};

export default ProtectedRoute;
