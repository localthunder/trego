import React, { useState, useEffect } from 'react';
import api from '../services/api';

const InstitutionsPage = () => {
    const [institutions, setInstitutions] = useState([]);
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        // Fetch institutions from your backend
        api.get('/gocardless/institutions?country=gb')
            .then(response => {
                setInstitutions(response.data);
            })
            .catch(error => {
                console.error('Error fetching institutions', error);
            });
    }, []);

    const handleCreateRequisition = async (institutionId) => {
        setLoading(true);
        try {
            const response = await api.post('/gocardless/requisition', {
                redirect: 'http://localhost:5173/',  // This is just an example redirect
                institution_id: institutionId,
                reference: `ref_${Date.now()}`,
                user_language: 'EN'
            });
            const requisition = response.data;
            // Follow the link returned in the requisition response
            window.location.href = requisition.link;
        } catch (error) {
            console.error('Error creating requisition', error);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div>
            <h2>Available Institutions</h2>
            {loading ? (
                <p>Loading...</p>
            ) : (
                <ul>
                    {institutions.map(institution => (
                        <li key={institution.id}>
                            <button onClick={() => handleCreateRequisition(institution.id)}>
                                {institution.name}
                            </button>
                        </li>
                    ))}
                </ul>
            )}
        </div>
    );
};

export default InstitutionsPage;
