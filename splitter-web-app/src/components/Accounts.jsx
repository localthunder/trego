import React, { useState, useEffect } from 'react';
import api from '../services/api';

const Accounts = ({ requisitionId }) => {
    const [accounts, setAccounts] = useState([]);

    useEffect(() => {
        api.get(`/gocardless/accounts/${requisitionId}`)
            .then(response => {
                setAccounts(response.data.accounts);
            })
            .catch(error => {
                console.error('Error fetching accounts', error);
            });
    }, [requisitionId]);

    return (
        <div>
            <h2>Accounts</h2>
            <ul>
                {accounts.map(accountId => (
                    <li key={accountId}>{accountId}</li>
                ))}
            </ul>
        </div>
    );
};

export default Accounts;
