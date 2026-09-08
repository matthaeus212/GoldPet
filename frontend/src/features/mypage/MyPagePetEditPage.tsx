import React from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import PetForm from '../pet/PetForm';

const MyPagePetEditPage: React.FC = () => {
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
    const editPetId = searchParams.get('petId') ? Number(searchParams.get('petId')) : null;

    return (
        <SubPageLayout title={editPetId ? '반려동물 수정' : '반려동물 등록'} onBack={() => navigate('/mypage')}>
            <PetForm
                petId={editPetId ?? undefined}
                onSuccess={() => navigate('/mypage')}
                onCancel={() => navigate('/mypage')}
            />
        </SubPageLayout>
    );
};

export default MyPagePetEditPage;
