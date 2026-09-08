import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import PetForm from '../pet/PetForm';

const ProfilePetWritePage: React.FC = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const editPetId = searchParams.get('petId') ? Number(searchParams.get('petId')) : null;

    return (
        <SubPageLayout title="반려동물 수정">
            <PetForm
                petId={editPetId ?? undefined}
                onSuccess={() => navigate('/profile')}
                onCancel={() => navigate('/profile?tab=pet')}
            />
        </SubPageLayout>
    );
};

export default ProfilePetWritePage;
