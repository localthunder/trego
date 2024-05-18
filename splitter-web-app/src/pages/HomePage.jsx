import React, { useContext } from 'react';
import { Link } from 'react-router-dom';
import { AuthContext } from '../contexts/AuthContexts'; // Import AuthContext

const HomePage = () => {
    console.log('Rendering HomePage');
    const { logout } = useContext(AuthContext); // Use AuthContext

    const handleLogout = () => {
        logout();
    };

    return (
        <div>
            <h1>Home Page</h1>
            <nav>
                <ul>
                    <li><Link to="/institutions">Institutions</Link></li>
                    <li><Link to="/requisitions">Create Requisition</Link></li>
                    <li><Link to="/accounts">Accounts</Link></li>
                    <li><Link to="/transactions">Transactions</Link></li>
                </ul>
            </nav>
            <button onClick={handleLogout}>Logout</button>
        </div>
    );
};

export default HomePage;
