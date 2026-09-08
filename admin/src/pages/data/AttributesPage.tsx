import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { PetAttribute, CreateAttributeRequest } from '../../services/attributeService';
import { getAttributes, createAttribute, updateAttribute, deleteAttribute } from '../../services/attributeService';
import { Button } from '../../components/common/Button';
import { toast } from 'sonner'
import { useConfirm } from '@/hooks/useConfirm'

const AttributesPage: React.FC = () => {
    const queryClient = useQueryClient();
    const { confirm: confirmDialog, ConfirmDialog } = useConfirm()
    const { data: attributes, isLoading } = useQuery({ queryKey: ['attributes'], queryFn: getAttributes });

    // Modal State
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingAttribute, setEditingAttribute] = useState<PetAttribute | null>(null);

    // Form State
    const [category, setCategory] = useState<'TRAIT' | 'INTEREST' | 'ALLERGY'>('TRAIT');
    const [code, setCode] = useState('');
    const [name, setName] = useState('');
    const [inputType, setInputType] = useState<'SELECT' | 'RADIO' | 'TEXT'>('SELECT');
    const [displayOrder, setDisplayOrder] = useState(0);
    const [optionsJson, setOptionsJson] = useState('[]');

    const openModal = (attribute: PetAttribute | null) => {
        if (attribute) {
            setEditingAttribute(attribute);
            setCategory(attribute.category);
            setCode(attribute.code);
            setName(attribute.name);
            setInputType(attribute.inputType);
            setDisplayOrder(attribute.displayOrder);
            setOptionsJson(JSON.stringify(attribute.options, null, 2));
        } else {
            setEditingAttribute(null);
            setCategory('TRAIT');
            setCode('');
            setName('');
            setInputType('SELECT');
            setDisplayOrder(0);
            setOptionsJson('[]');
        }
        setIsModalOpen(true);
    };

    const closeModal = () => {
        setIsModalOpen(false);
    };

    const createMutation = useMutation({
        mutationFn: createAttribute,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['attributes'] });
            closeModal();
        }
    });

    const updateMutation = useMutation({
        mutationFn: (data: { id: number, req: CreateAttributeRequest }) => updateAttribute(data.id, data.req),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['attributes'] });
            closeModal();
        }
    });

    const deleteMutation = useMutation({
        mutationFn: deleteAttribute,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['attributes'] });
        }
    });

    const handleSubmit = () => {
        try {
            const options = JSON.parse(optionsJson);
            const req: CreateAttributeRequest = {
                category,
                code,
                name,
                inputType,
                displayOrder,
                options
            };
            if (editingAttribute) {
                updateMutation.mutate({ id: editingAttribute.id, req });
            } else {
                createMutation.mutate(req);
            }
        } catch {
            toast.error("잘못된 JSON 형식입니다");
        }
    };

    const handleDeleteAttr = async (attrId: number) => {
        if (!(await confirmDialog({ description: '삭제하시겠습니까?', variant: 'destructive' }))) return;
        deleteMutation.mutate(attrId);
    };

    const renderCategorySection = (cat: 'TRAIT' | 'INTEREST' | 'ALLERGY', title: string) => (
        <div className="mb-8">
            <h2 className="text-xl font-bold mb-4">{title}</h2>
            <div className="bg-white rounded-lg shadow overflow-hidden">
                <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-50">
                        <tr>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">순서</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">코드</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">이름</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">유형</th>
                            <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">관리</th>
                        </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                        {attributes?.filter((a: PetAttribute) => a.category === cat).map((attr: PetAttribute) => (
                            <tr key={attr.id}>
                                <td className="px-6 py-4 whitespace-nowrap">{attr.displayOrder}</td>
                                <td className="px-6 py-4 whitespace-nowrap">{attr.code}</td>
                                <td className="px-6 py-4 whitespace-nowrap">{attr.name}</td>
                                <td className="px-6 py-4 whitespace-nowrap">{attr.inputType}</td>
                                <td className="px-6 py-4 whitespace-nowrap">
                                    <Button variant="link" size="sm" onClick={() => openModal(attr)}>수정</Button>
                                    <Button variant="link" size="sm" className="text-red-600 hover:text-red-900" onClick={() => handleDeleteAttr(attr.id)}>삭제</Button>
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>
        </div>
    );

    if (isLoading) return <div>로딩 중...</div>;

    return (
        <div className="p-6">
            <div className="flex justify-between items-center mb-6">
                <h1 className="text-2xl font-semibold text-gray-900">반려동물 속성 관리</h1>
                <Button variant="primary" onClick={() => openModal(null)}>
                    속성 추가
                </Button>
            </div>

            {renderCategorySection('TRAIT', '성향')}
            {renderCategorySection('INTEREST', '관심사')}
            {renderCategorySection('ALLERGY', '알러지')}

            {isModalOpen && (
                <div className="fixed inset-0 bg-gray-600 bg-opacity-50 overflow-y-auto h-full w-full flex items-center justify-center">
                    <div className="bg-white p-8 rounded-lg shadow-xl w-full max-w-lg">
                        <h2 className="text-xl font-bold mb-4">{editingAttribute ? '속성 수정' : '속성 추가'}</h2>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">카테고리</label>
                            <select
                                value={category}
                                onChange={e => setCategory(e.target.value as 'TRAIT' | 'INTEREST' | 'ALLERGY')}
                                className="shadow border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                            >
                                <option value="TRAIT">성향</option>
                                <option value="INTEREST">관심사</option>
                                <option value="ALLERGY">알러지</option>
                            </select>
                        </div>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">코드 (고유)</label>
                            <input
                                type="text"
                                value={code}
                                onChange={e => setCode(e.target.value)}
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                            />
                        </div>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">이름</label>
                            <input
                                type="text"
                                value={name}
                                onChange={e => setName(e.target.value)}
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                            />
                        </div>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">입력 유형</label>
                            <select
                                value={inputType}
                                onChange={e => setInputType(e.target.value as 'SELECT' | 'RADIO' | 'TEXT')}
                                className="shadow border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                            >
                                <option value="SELECT">선택형</option>
                                <option value="RADIO">라디오</option>
                                <option value="TEXT">텍스트</option>
                            </select>
                        </div>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">표시 순서</label>
                            <input
                                type="number"
                                value={displayOrder}
                                onChange={e => setDisplayOrder(parseInt(e.target.value))}
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline"
                            />
                        </div>

                        <div className="mb-4">
                            <label className="block text-gray-700 text-sm font-bold mb-2">옵션 (JSON)</label>
                            <textarea
                                value={optionsJson}
                                onChange={e => setOptionsJson(e.target.value)}
                                className="shadow appearance-none border rounded w-full py-2 px-3 text-gray-700 leading-tight focus:outline-none focus:shadow-outline h-32 font-mono"
                            ></textarea>
                            <p className="text-xs text-gray-500 mt-1">Example: [{`{"label":"High", "value":"High"}`}]</p>
                        </div>

                        <div className="flex justify-end pt-4 gap-2">
                            <Button variant="secondary" onClick={closeModal}>취소</Button>
                            <Button variant="primary" onClick={handleSubmit}>저장</Button>
                        </div>
                    </div>
                </div>
            )}
        {ConfirmDialog}
        </div>
    );
};

export default AttributesPage;
