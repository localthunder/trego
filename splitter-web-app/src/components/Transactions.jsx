import React, { useState, useEffect } from 'react';
import api from '../services/api';

const Transactions = ({ accountId }) => {
    const [transactions, setTransactions] = useState([]);

    useEffect(() => {
        api.get(`/gocardless/transactions/${accountId}`)
            .then(response => {
                setTransactions(response.data.transactions.booked);
            })
            .catch(error => {
                console.error('Error fetching transactions', error);
            });
    }, [accountId]);

    return (
        <div>
            <h2>Transactions</h2>
            <ul>
                {transactions.map(transaction => (
                    <li key={transaction.transactionId}>
                        {transaction.remittanceInformationUnstructured} - {transaction.transactionAmount.amount} {transaction.transactionAmount.currency}
                    </li>
                ))}
            </ul>
        </div>
    );
};

export default Transactions;
