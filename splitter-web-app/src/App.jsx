import React, { useContext, useEffect } from 'react';
import { BrowserRouter as Router, Route, Routes, Navigate, useLocation } from 'react-router-dom';
import HomePage from './pages/HomePage';
import InstitutionsPage from './pages/InstitutionsPage';
import RequisitionsPage from './pages/RequisitionsPage';
import AccountsPage from './pages/AccountsPage';
import TransactionsPage from './pages/TransactionsPage';
import LoginPage from './pages/LoginPage';
import SignUpPage from './pages/SignUpPage';
import GoogleRedirectHandler from './components/GoogleRedirectHandler';
import AuthProvider, { AuthContext } from './contexts/AuthContexts';

const NotFound = () => <h2>Page Not Found</h2>;

const ProtectedRoute = ({ element }) => {
    const { isAuthenticated } = useContext(AuthContext);
    console.log('ProtectedRoute isAuthenticated:', isAuthenticated); // Debug log
    const location = useLocation();

    useEffect(() => {
        if (!isAuthenticated) {
            console.log('User not authenticated, redirecting to login');
            navigate('/login');
        }
    }, [isAuthenticated]);

    return isAuthenticated ? element : <Navigate to="/login" state={{ from: location }} />;
};

const App = () => {
    const { isAuthenticated } = useContext(AuthContext);

    useEffect(() => {
        console.log('App isAuthenticated:', isAuthenticated); // Debug log
    }, [isAuthenticated]);

    return (
        <AuthProvider>
            <Router>
                <Routes>
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/signup" element={<SignUpPage />} />
                    <Route path="/auth/google/callback" element={<GoogleRedirectHandler />} />
                    <Route path="/" element={<ProtectedRoute element={<HomePage />} />} />
                    <Route path="/institutions" element={<ProtectedRoute element={<InstitutionsPage />} />} />
                    <Route path="/requisitions" element={<ProtectedRoute element={<RequisitionsPage />} />} />
                    <Route path="/accounts" element={<ProtectedRoute element={<AccountsPage />} />} />
                    <Route path="/transactions" element={<ProtectedRoute element={<TransactionsPage />} />} />
                    <Route path="*" element={<NotFound />} />
                </Routes>
            </Router>
        </AuthProvider>
    );
};

export default App;
