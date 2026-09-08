import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import ImageUploadSlider from '../../components/common/ImageUploadSlider';
import { petService, FALLBACK_ATTRIBUTES_SCHEMA } from '../../services/petService';
import type { UpdatePetRequest } from '../../services/petService';
import { Loading } from '../../components/common/Loading';
import KoreanDatePicker from '../../components/common/KoreanDatePicker';
import { useAlert } from '../../contexts/AlertContext';
import CustomSelect from '../../components/common/CustomSelect';

interface PetFormProps {
    petId?: number;        // specific pet to edit (undefined = create mode)
    onSuccess: () => void; // called after save + query invalidation
    onCancel: () => void;  // called when cancel button clicked
}

const PetForm: React.FC<PetFormProps> = ({ petId, onSuccess, onCancel }) => {
    const queryClient = useQueryClient();
    const { showAlert } = useAlert();

    // Fetch Species (Cache for 24 hours)
    const { data: speciesList } = useQuery({
        queryKey: ['pets', 'species'],
        queryFn: petService.getSpecies,
        staleTime: Infinity,
        gcTime: 1000 * 60 * 60 * 24
    });

    // Fetch Attributes Schema
    const { data: attributesSchema, isLoading: isLoadingAttributes } = useQuery({
        queryKey: ['pets', 'attributes'],
        queryFn: petService.getPetAttributes,
        staleTime: 1000 * 60 * 60, // 1 hour
        gcTime: 1000 * 60 * 60 * 24
    });

    // Fetch My Pet to Edit
    const { data: myPets, isLoading } = useQuery({ queryKey: ['pets', 'my'], queryFn: petService.getMyPets });

    const [isEditMode, setIsEditMode] = useState(false);
    const [id, setId] = useState<number | null>(null);

    // Basic Form Stats
    const [name, setName] = useState('');
    const [speciesId, setSpeciesId] = useState<number | undefined>(undefined);
    const [selectedCategory, setSelectedCategory] = useState<string>('');
    const [breedId, setBreedId] = useState<number | undefined>(undefined);
    const [gender, setGender] = useState<'MALE' | 'FEMALE' | undefined>(undefined);
    const [birthDate, setBirthDate] = useState('');
    const [weightKg, setWeightKg] = useState<string>('');
    const [isNeutered, setIsNeutered] = useState<boolean | undefined>(undefined);
    const [profileImageUrls, setProfileImageUrls] = useState<(string | null)[]>(Array(5).fill(null));
    const [isBirthDateUnknown, setIsBirthDateUnknown] = useState(false);

    // Dynamic Attribute Values (Code -> Value)
    const [attributeValues, setAttributeValues] = useState<Record<string, string>>({});

    // Breeds fetching
    const { data: breedsList } = useQuery({
        queryKey: ['pets', 'breeds', speciesId],
        queryFn: () => petService.getBreeds(speciesId),
        enabled: !!speciesId,
        staleTime: Infinity,
        gcTime: 1000 * 60 * 60 * 24
    });

    // Derive unique categories from breeds
    const uniqueCategories = React.useMemo(() => {
        if (!breedsList) return [];
        const categories = new Set(breedsList.map(b => b.category).filter(Boolean));
        return Array.from(categories) as string[];
    }, [breedsList]);

    // Filter breeds based on selected category
    const filteredBreeds = React.useMemo(() => {
        if (!breedsList) return [];
        if (!selectedCategory) return breedsList;
        return breedsList.filter(b => b.category === selectedCategory);
    }, [breedsList, selectedCategory]);

    useEffect(() => {
        if (petId && myPets && myPets.length > 0) {
            const pet = myPets.find(p => p.id === petId) || myPets[0];
            setTimeout(() => {
            setId(pet.id);
            setIsEditMode(true);
            setName(pet.name);
            setSpeciesId(pet.speciesId);
            setBreedId(pet.breedId);
            setGender(pet.gender as 'MALE' | 'FEMALE' | undefined);
            setBirthDate(pet.birthDate || '');
            setIsBirthDateUnknown(!pet.birthDate);
            setWeightKg(pet.weightKg?.toString() || '');
            setIsNeutered(pet.isNeutered);

            let loadedImages: (string | null)[] = [];
            if (pet.profileImageUrls && pet.profileImageUrls.length > 0) {
                loadedImages = pet.profileImageUrls;
            } else if (pet.profileImageUrl) {
                loadedImages = [pet.profileImageUrl];
            }
            // Pad to minimum 5 slots (for create mode UX), never truncate
            if (loadedImages.length < 5) {
                loadedImages = [...loadedImages, ...Array(5 - loadedImages.length).fill(null)];
            }
            setProfileImageUrls(loadedImages);

            // Parse existing tags into map: "Activity:High" -> { Activity: "High" }
            const values: Record<string, string> = {};

            // Legacy Key Mapping (DB Migration V12 changed keys)
            const LEGACY_KEY_MAP: Record<string, string> = {
                'TRAIT_ACTIVITY': 'Activity',
                'TRAIT_FRIENDLINESS': 'Friendliness',
                'TRAIT_TRAINING': 'Training',
                'TRAIT_BARKING': 'Barking',
                'TRAIT_SPECIAL_NOTES': 'Note',
                'INTEREST_TOY_PLAY': 'Toy',
                'INTEREST_WALK': 'Walk',
                'INTEREST_LIFESPAN': 'Friendship', // Legacy 'Lifespan' -> New 'Friendship'
                'INTEREST_PETTING': 'Petting',
                'ALLERGY_NUTS': 'AllergyOther', // Legacy Nuts -> Other (or reset)
                'ALLERGY_SHELLFISH': 'AllergyOther', // Legacy Shellfish -> Other
                'ALLERGY_EGG_MEAT': 'AllergyMeat', // Legacy EggMeat -> Meat (Egg is now DairyEgg, causing potential mismatch)
                'ALLERGY_OTHER': 'AllergyOther'
            };

            // Legacy Value Mapping (DB Migration V14 Overhaul)
            const LEGACY_VALUE_MAP: Record<string, string> = {
                // Common
                'VERY_HIGH': 'ENERGIZER', 'HIGH': 'ACTIVE', 'NORMAL': 'NORMAL', 'LOW': 'CALM',
                'YES': 'YES', 'NO': 'NO',
                'UNKNOWN': 'UNKNOWN',

                // Friendliness (VERY_GOOD -> SOCIAL)
                'VERY_GOOD': 'SOCIAL', 'GOOD': 'SELECTIVE', 'BAD': 'SHY',

                // Training (WELL -> BASIC, POOR -> FREE_SPIRIT)
                'WELL': 'BASIC', 'POOR': 'FREE_SPIRIT',

                // Barking (OFTEN -> VOCAL, RARE -> QUIET)
                'RARE': 'QUIET', 'OFTEN': 'VOCAL',

                // Interests (LIKE -> LIKE, DISLIKE -> DISLIKE)
                'LIKE': 'LIKE', 'DISLIKE': 'DISLIKE',

                // Lifespan (HEALTHY -> Long -> ??)
                // Cannot map Long/Short to Social/Loner meaningfully. Leave unmapped to force user selection.
            };

            if (pet.temperamentTags) {
                pet.temperamentTags.forEach(tag => {
                    const parts = tag.split(':');
                    if (parts.length === 2) {
                        const rawCode = parts[0].trim();
                        const rawVal = parts[1].trim();

                        if (rawCode && rawVal) {
                            // Normalize rawCode to UPPERCASE to match LEGACY_KEY_MAP keys
                            let code = LEGACY_KEY_MAP[rawCode];
                            if (!code) {
                                code = LEGACY_KEY_MAP[rawCode.toUpperCase()];
                            }
                            // Default to rawCode if no map found
                            if (!code) code = rawCode;

                            let val = rawVal;

                            // Apply Value Mapping
                            if (LEGACY_VALUE_MAP[rawVal]) {
                                val = LEGACY_VALUE_MAP[rawVal];
                            }
                            // Try uppercase match
                            else if (LEGACY_VALUE_MAP[rawVal.toUpperCase()]) {
                                val = LEGACY_VALUE_MAP[rawVal.toUpperCase()];
                            }
                            // Specific logic for ambiguous 'NORMAL' mapping
                            else if (rawVal === 'NORMAL') {
                                if (code === 'Training') val = 'Average';
                                else if (code === 'Barking') val = 'Occasional';
                                else val = 'Normal';
                            }

                            values[code] = val;
                        }
                    }
                });
            }
            setAttributeValues(values);
            }, 0);
        }
        // If no petId, stay in create mode (isEditMode=false)
    }, [myPets, petId]);

    // Set category if breed is already selected (Edit Mode)
    useEffect(() => {
        if (isEditMode && breedId && breedsList && !selectedCategory) {
            const currentBreed = breedsList.find(b => b.id === breedId);
            if (currentBreed && currentBreed.category) {
                setTimeout(() => setSelectedCategory(currentBreed.category!), 0);
            }
        }
    }, [isEditMode, breedId, breedsList, selectedCategory]);

    const handleImageUpload = async (files: File[], targetIndex: number) => {
        const maxCount = 5;
        const currentCount = profileImageUrls.filter(Boolean).length;

        if (currentCount >= maxCount) {
            showAlert(`최대 ${maxCount}장까지 등록 가능합니다.`);
            return;
        }

        const availableSlotsCount = maxCount - currentCount;
        const filesToUpload = files.slice(0, availableSlotsCount);

        if (filesToUpload.length < files.length) {
            showAlert(`최대 ${maxCount}장까지 등록 가능합니다. ${filesToUpload.length}장만 업로드됩니다.`);
        }

        try {
            const uploadPromises = filesToUpload.map(file => petService.uploadPetImage(file));
            const newUrls = await Promise.all(uploadPromises);

            setProfileImageUrls(prev => {
                const newSlots = [...prev];
                let urlIndex = 0;

                // First fill targetIndex if empty
                if (!newSlots[targetIndex] && urlIndex < newUrls.length) {
                    newSlots[targetIndex] = newUrls[urlIndex++];
                }

                // Then fill subsequent empty slots
                for (let i = 0; i < prev.length; i++) {
                    if (urlIndex >= newUrls.length) break;
                    if (!newSlots[i]) {
                        newSlots[i] = newUrls[urlIndex++];
                    }
                }
                return newSlots;
            });
        } catch {
            showAlert('이미지 업로드에 실패했습니다.');
        }
    };

    const handleImageDelete = (index: number) => {
        setProfileImageUrls(prev => {
            const newSlots = [...prev];
            newSlots[index] = null;
            return newSlots;
        });
    };

    const createMutation = useMutation({
        mutationFn: petService.createPet,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['pets', 'my'] });
            showAlert('반려동물이 등록되었습니다.', () => onSuccess());
        },
        onError: () => showAlert('반려동물 등록에 실패했습니다.')
    });

    const updateMutation = useMutation({
        mutationFn: (data: UpdatePetRequest) => petService.updatePet(id!, data),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['pets', 'my'] });
            showAlert('반려동물 정보가 수정되었습니다.', () => onSuccess());
        },
        onError: () => showAlert('반려동물 수정에 실패했습니다.')
    });

    const handleSubmit = () => {
        if (!name) {
            showAlert('이름을 입력해주세요.');
            return;
        }

        // Serialize attribute values to CSV
        const tags = Object.entries(attributeValues)
            .filter(([, value]) => value && value.trim() !== '')
            .map(([code, value]) => `${code}:${value}`);

        const data = {
            name,
            speciesId: speciesId || 1,
            breedId,
            gender: gender as 'MALE' | 'FEMALE',
            birthDate: isBirthDateUnknown ? '' : birthDate,
            weightKg: weightKg ? parseFloat(weightKg) : undefined,
            isNeutered,
            temperamentTags: tags.join(','),
            profileImageUrls: profileImageUrls.filter((url): url is string => !!url),
            profileImageUrl: profileImageUrls.find(url => !!url) || undefined
        };

        if (isEditMode && id) {
            updateMutation.mutate(data);
        } else {
            createMutation.mutate(data);
        }
    };

    if (isLoading || isLoadingAttributes) return <Loading />;

    const renderDynamicSection = (title: string, category: string, boxColorClass: string) => {
        // Use API data if available, otherwise use fallback
        const sourceSchema = (attributesSchema && attributesSchema.length > 0) ? attributesSchema : FALLBACK_ATTRIBUTES_SCHEMA;
        const items = sourceSchema.filter(a => a.category === category);

        if (items.length === 0) return null;

        return (
            <>
                <strong>{title}</strong>
                <div className={`profile_write_box ${boxColorClass}`}>
                    {items.map(attr => (
                        <div key={attr.code} className={`write_box ${attr.inputType === 'SELECT' ? 'write_select' : ''}`}>
                            <label htmlFor={`attr_${attr.code}`}>{attr.name}</label>

                            {attr.inputType === 'SELECT' && (
                                <CustomSelect
                                    id={`attr_${attr.code}`}
                                    value={attributeValues[attr.code] || ''}
                                    options={attr.options}
                                    onChange={(value) => setAttributeValues(prev => ({ ...prev, [attr.code]: value }))}
                                />
                            )}

                            {attr.inputType === 'TEXT' && (
                                <textarea
                                    id={`attr_${attr.code}`}
                                    placeholder="입력해 주세요"
                                    value={attributeValues[attr.code] || ''}
                                    onChange={(e) => setAttributeValues(prev => ({ ...prev, [attr.code]: e.target.value }))}
                                ></textarea>
                            )}

                            {attr.inputType === 'RADIO' && (
                                <CustomSelect
                                    id={`attr_${attr.code}`}
                                    value={attributeValues[attr.code] || ''}
                                    options={attr.options}
                                    onChange={(value) => setAttributeValues(prev => ({ ...prev, [attr.code]: value }))}
                                />
                            )}
                        </div>
                    ))}
                </div>
            </>
        );
    };

    return (
        <div id="profileContainer">
            <div className="profile_write">
                <div className="profile_img_edit">
                    {profileImageUrls[0] ? (
                        <div className="profile_img">
                            <img src={profileImageUrls[0]} alt="Representative" />
                        </div>
                    ) : (
                        <div className="profile_img">
                            <img src="/assets/images/common/pet_none_img.svg" alt="기본 펫 이미지" />
                        </div>
                    )}
                </div>

                <ImageUploadSlider
                    images={profileImageUrls}
                    onUpload={handleImageUpload}
                    onDelete={handleImageDelete}
                    maxCount={Math.max(5, profileImageUrls.filter(Boolean).length)}
                />

                <div className="guide_box">
                    <dl>
                        <dt>등록 가이드</dt>
                        <dd>최대 5장 등록 가능하며 2장은 필수로 등록해주세요.</dd>
                        <dd>반려 동물의 사진을 등록해주세요.</dd>
                        <dd>가장 먼저 등록한 사진이 대표 사진으로 설정 됩니다.</dd>
                    </dl>
                </div>

                <div className="profile_write_wrap">
                    <strong>기본 정보</strong>
                    <div className="profile_write_box pink_color">
                        <div className="write_box">
                            <label htmlFor="pet_name">이름</label>
                            <input
                                type="text"
                                id="pet_name"
                                placeholder="입력해 주세요"
                                value={name}
                                onChange={(e) => setName(e.target.value)}
                                maxLength={20}
                            />
                        </div>

                        <div className="write_box">
                            <label htmlFor="pet_birth_day">생년월일</label>
                            <div className="unknown_check_box">
                                <input
                                    type="checkbox"
                                    id="pet_birth_unknown"
                                    checked={isBirthDateUnknown}
                                    onChange={(e) => setIsBirthDateUnknown(e.target.checked)}
                                />
                                <label htmlFor="pet_birth_unknown">알 수 없음</label>
                            </div>
                            <KoreanDatePicker
                                id="pet_birth_day"
                                selected={birthDate ? new Date(birthDate) : null}
                                onChange={(date) => setBirthDate(date ? date.toISOString().split('T')[0] : '')}
                                disabled={isBirthDateUnknown}
                                placeholder="날짜를 선택해주세요"
                            />
                        </div>

                        <div className="write_box write_select">
                            <label htmlFor="pet_species">종류</label>
                            <CustomSelect
                                id="pet_species"
                                value={String(speciesId || '')}
                                options={(speciesList || []).map(s => ({ label: s.name, value: String(s.id) }))}
                                onChange={(value) => {
                                    const newSpeciesId = Number(value);
                                    setSpeciesId(newSpeciesId);
                                    setSelectedCategory(''); // Reset Category
                                    setBreedId(undefined);   // Reset Breed
                                }}
                            />
                        </div>

                        {uniqueCategories.length > 0 && (
                            <div className="write_box write_select">
                                <label htmlFor="pet_category">분류</label>
                                <CustomSelect
                                    id="pet_category"
                                    value={selectedCategory}
                                    options={uniqueCategories.map(cat => ({ label: cat, value: cat }))}
                                    placeholder="전체 (선택 안함)"
                                    onChange={(value) => {
                                        setSelectedCategory(value);
                                        setBreedId(undefined); // Reset Breed when category changes
                                    }}
                                />
                            </div>
                        )}

                        <div className="write_box write_select">
                            <label htmlFor="pet_breed">품종</label>
                            <CustomSelect
                                id="pet_breed"
                                value={String(breedId || '')}
                                options={(filteredBreeds || []).map(breed => ({ label: breed.name, value: String(breed.id) }))}
                                onChange={(value) => setBreedId(Number(value))}
                                disabled={!speciesId}
                            />
                        </div>

                        <div className="radio_wrap">
                            <p>성별</p>
                            <div className="radio_box">
                                <input
                                    type="radio"
                                    id="pet_gender_male"
                                    name="pet_gender"
                                    value="MALE"
                                    checked={gender === 'MALE'}
                                    onChange={() => setGender('MALE')}
                                />
                                <label htmlFor="pet_gender_male">남아</label>
                            </div>
                            <div className="radio_box">
                                <input
                                    type="radio"
                                    id="pet_gender_female"
                                    name="pet_gender"
                                    value="FEMALE"
                                    checked={gender === 'FEMALE'}
                                    onChange={() => setGender('FEMALE')}
                                />
                                <label htmlFor="pet_gender_female">여아</label>
                            </div>
                        </div>

                        <div className="radio_wrap">
                            <p>중성화 여부</p>
                            <div className="radio_box">
                                <input
                                    type="radio"
                                    id="pet_neutered_done"
                                    name="pet_neutered"
                                    value="true"
                                    checked={isNeutered === true}
                                    onChange={() => setIsNeutered(true)}
                                />
                                <label htmlFor="pet_neutered_done">완료</label>
                            </div>
                            <div className="radio_box">
                                <input
                                    type="radio"
                                    id="pet_neutered_not"
                                    name="pet_neutered"
                                    value="false"
                                    checked={isNeutered === false}
                                    onChange={() => setIsNeutered(false)}
                                />
                                <label htmlFor="pet_neutered_not">미완료</label>
                            </div>
                        </div>

                        <div className="write_box">
                            <label htmlFor="pet_weight_kg">몸무게 (kg)</label>
                            <input
                                type="text"
                                id="pet_weight_kg"
                                placeholder="예: 3.5"
                                value={weightKg}
                                onChange={(e) => {
                                    const value = e.target.value;
                                    if (value === '' || /^\d*\.?\d*$/.test(value)) {
                                        setWeightKg(value);
                                    }
                                }}
                                inputMode="decimal"
                            />
                        </div>
                    </div>

                    {/* Dynamic Sections */}
                    {renderDynamicSection('성향', 'TRAIT', 'purple_color')}
                    {renderDynamicSection('관심사', 'INTEREST', 'blue_color')}
                    {renderDynamicSection('알러지 정보', 'ALLERGY', 'green_color')}

                </div>

                <div className="btn_wrap">
                    <button type="button" className="cancel_btn" onClick={onCancel}>취소</button>
                    <button type="button" className="submit_btn" onClick={handleSubmit}>저장</button>
                </div>
            </div>
        </div>
    );
};

export default PetForm;
