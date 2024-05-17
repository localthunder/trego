import React, { useState } from 'react';
import Accounts from '../components/Accounts';

const AccountsPage = () => {
    const [requisitionId, setRequisitionId] = useState('');

    return (
        <div>
            <input
                type="text"
                placeholder="Requisition ID"
                value={requisitionId}
                onChange={(e) => setRequisitionId(e.target.value)}
            />
            <Accounts requisitionId={requisitionId} />
        </div>
    );
};

export default AccountsPage;
