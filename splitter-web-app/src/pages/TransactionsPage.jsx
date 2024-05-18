import React, { useState } from 'react';
import Transactions from '../components/Transactions';

const TransactionsPage = () => {
    const [accountId, setAccountId] = useState('');

    return (
        <div>
            <input
                type="text"
                placeholder="Account ID"
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
            />
            <Transactions accountId={accountId} />
        </div>
    );
};

export default TransactionsPage;
