import React, { useState, useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import api from '../services/api';

const Requisitions = () => {
    const [requisition, setRequisition] = useState(null);
    const [redirect] = useState('http://localhost:5173/');
    const location = useLocation();
    const { institutionId } = location.state || {};

    useEffect(() => {
        if (institutionId) {
            handleCreateRequisition();
        }
    }, [institutionId]);

    const generateReference = () => {
        return `ref_${Date.now()}`;
    };

    const handleCreateRequisition = () => {
        const reference = generateReference();
        api.post('/gocardless/requisition', {
            redirect,
            institution_id: institutionId,
            reference: reference,
            user_language: 'EN'
        })
        .then(response => {
            setRequisition(response.data);
        })
        .catch(error => {
            console.error('Error creating requisition', error);
        });
    };

    if (!institutionId) {
        return <div>No institution selected.</div>;
    }

    return (
        <div>
            <h2>Create Requisition</h2>
            {requisition ? (
                <div>
                    <h3>Requisition Created</h3>
                    <pre>{JSON.stringify(requisition, null, 2)}</pre>
                </div>
            ) : (
                <p>Creating requisition for institution ID: {institutionId}</p>
            )}
        </div>
    );
};

export default Requisitions;
