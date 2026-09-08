import React, { useState, useEffect, useRef } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import { useAuthStore, type User } from '../../stores/authStore';
import { userService } from '../../services/userService';
import ImageUploadSlider from '../../components/common/ImageUploadSlider';

import { useAlert } from '../../contexts/AlertContext';
import { useKeyboardDismiss } from '../../hooks/useKeyboardDismiss';
import CustomSelect from '../../components/common/CustomSelect';

const ProfileOwnerWritePage: React.FC = () => {
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { user, updateUser } = useAuthStore();
    const { showAlert } = useAlert();

    // Fetch user data for editing (uses default staleTime)
    const { data: fetchedUser } = useQuery({
        queryKey: ['users', 'me'],
        queryFn: userService.getMe,
    });

    const currentUser = fetchedUser || user;
    const isProfileLocked = currentUser?.isProfileLocked === true;
    
    // Refs for focus management
    const nameRef = useRef<HTMLInputElement>(null);
    const nicknameRef = useRef<HTMLInputElement>(null);
    const birthDateRef = useRef<HTMLInputElement>(null);
    const phoneNumberRef = useRef<HTMLInputElement>(null);
    const introRef = useRef<HTMLTextAreaElement>(null);
    const hasPetRef = useRef<HTMLInputElement>(null); // To focus the "Yes" radio button

    // State for all form fields
    // Initialize exactly 5 slots with nulls if empty
    const [profileImageUrls, setProfileImageUrls] = useState<(string | null)[]>(Array(5).fill(null));
    
    const [name, setName] = useState('');
    const [nickname, setNickname] = useState('');
    const [birthDate, setBirthDate] = useState('');
    const [phoneNumber, setPhoneNumber] = useState(''); 
    const [isPhoneVerified, setIsPhoneVerified] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isCheckingNickname, setIsCheckingNickname] = useState(false);
    const [isPhoneVerifying, setIsPhoneVerifying] = useState(false);

    const handlePhoneNumberChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value.replace(/[^0-9]/g, '');
        setPhoneNumber(value);
        // If phone number is reverted to original, consider it verified
        if (user?.phoneNumber && value === user.phoneNumber) {
             setIsPhoneVerified(true);
        } else {
             setIsPhoneVerified(false);
        }
    };
    const [verifyCode, setVerifyCode] = useState(''); // Setup for verification logic
    
    const [gender, setGender] = useState('MALE'); // Default or load
    const [hasPet, setHasPet] = useState<boolean | null>(null);
    const [intro, setIntro] = useState('');
    
    const [mbti, setMbti] = useState('INTJ');
    const [interests, setInterests] = useState<string[]>([]);
    const [hobbies, setHobbies] = useState<string[]>([]);

    const [availableInterests, setAvailableInterests] = useState<{id: number, name: string}[]>([]);
    const [availableHobbies, setAvailableHobbies] = useState<{id: number, name: string}[]>([]);
    const [isNicknameChecked, setIsNicknameChecked] = useState(false);

    useEffect(() => {
        const fetchTags = async () => {
             const [interestsData, hobbiesData] = await Promise.all([
                 userService.getInterests(),
                 userService.getHobbies()
             ]);
             setAvailableInterests(interestsData);
             setAvailableHobbies(hobbiesData);
        };
        fetchTags();
    }, []);

    const initializedRef = useRef(false);

    useEffect(() => {
        if (!currentUser || initializedRef.current) return;
        initializedRef.current = true;

        const hasCustomImages = currentUser.profileImageUrls && currentUser.profileImageUrls.length > 0;
        let loadedImages: (string | null)[] = hasCustomImages
            ? currentUser.profileImageUrls!
            : (currentUser.profileImageUrl ? [currentUser.profileImageUrl] : []);

        if (loadedImages.length < 5) {
            loadedImages = [...loadedImages, ...Array(5 - loadedImages.length).fill(null)];
        } else if (loadedImages.length > 5) {
            loadedImages = loadedImages.slice(0, 5);
        }

        const initState = () => {
            setProfileImageUrls(loadedImages);
            setName(currentUser.name || '');
            setNickname(currentUser.nickname || '');
            setBirthDate((currentUser.birthDate || '').replace(/-/g, ''));
            setPhoneNumber((currentUser.phoneNumber || '').replace(/-/g, ''));
            setGender(currentUser.gender || 'MALE');
            setHasPet(currentUser.hasPet ?? null);
            setIntro(currentUser.intro || '');
            setMbti(currentUser.mbti || 'INTJ');
            setInterests(currentUser.interests || []);
            setHobbies(currentUser.hobbies || []);
            if (currentUser.nickname) setIsNicknameChecked(true);
            if (currentUser.phoneNumber) setIsPhoneVerified(true);
        };

        // Defer to next microtask to avoid synchronous setState-in-effect
        Promise.resolve().then(initState);
    }, [currentUser]);

    const handleImageUpload = async (files: File[], targetIndex: number) => {
        const maxCount = 5;
        // Count currently filled slots
        const currentCount = profileImageUrls.filter(Boolean).length;
        
        if (currentCount >= maxCount) {
             showAlert(`최대 ${maxCount}장까지 등록 가능합니다.`);
             return; 
        }

        // Limit new files to available slots count (total max - current filled)
        // Wait, current logic: we just want to fill empty slots starting from targetIndex
        const availableSlotsCount = maxCount - currentCount;
        const filesToUpload = files.slice(0, availableSlotsCount);

        if (filesToUpload.length < files.length) {
            showAlert(`최대 ${maxCount}장까지 등록 가능합니다. ${filesToUpload.length}장만 업로드됩니다.`);
        }

        try {
            const uploadPromises = filesToUpload.map(file => userService.uploadImage(file));
            const newUrls = await Promise.all(uploadPromises);
            
            setProfileImageUrls(prev => {
                const newSlots = [...prev];
                let urlIndex = 0;
                
                // First fill targetIndex if empty
                if (!newSlots[targetIndex] && urlIndex < newUrls.length) {
                    newSlots[targetIndex] = newUrls[urlIndex++];
                }
                
                // Then fill subsequent empty slots
                for (let i = 0; i < maxCount; i++) {
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
        // Just set the slot to null
        setProfileImageUrls(prev => {
            const newSlots = [...prev];
            newSlots[index] = null;
            return newSlots;
        });
    };

    const handleInterestChange = (value: string, checked: boolean) => {
        if (checked) {
            setInterests(prev => [...prev, value]);
        } else {
            setInterests(prev => prev.filter(item => item !== value));
        }
    };

    const handleHobbyChange = (value: string, checked: boolean) => {
        if (checked) {
            setHobbies(prev => [...prev, value]);
        } else {
            setHobbies(prev => prev.filter(item => item !== value));
        }
    };

    const handleNicknameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
        const value = e.target.value;
        setNickname(value);
        // If nickname is reverted to the original one (and it was valid), consider it checked.
        if (user?.nickname && value === user.nickname) {
             setIsNicknameChecked(true);
        } else {
             setIsNicknameChecked(false); 
        }
    };

    const handleCheckNickname = async () => {
        if (isCheckingNickname) return;
        if (!nickname) {
            showAlert('닉네임을 입력해주세요.', () => nicknameRef.current?.focus());
            return;
        }

        setIsCheckingNickname(true);
        try {
            const isAvailable = await userService.checkNickname(nickname);
            if (isAvailable) {
                showAlert('사용 가능한 닉네임입니다.');
                setIsNicknameChecked(true);
            } else {
                showAlert('이미 사용 중인 닉네임입니다.', () => nicknameRef.current?.focus());
                setIsNicknameChecked(false);
            }
        } catch {
            showAlert('중복 확인에 실패했습니다.');
            setIsNicknameChecked(false);
        } finally {
            setIsCheckingNickname(false);
        }
    };

    const handleRequestVerifyCode = async () => {
        if (isPhoneVerifying) return;
        if (!phoneNumber || phoneNumber.length !== 11) {
            showAlert('휴대폰 번호 11자리를 정확히 입력해주세요.', () => phoneNumberRef.current?.focus());
            return;
        }

        // If specifically requesting re-verification (even if number matches), we allow it.
        // It resets the verified state until they confirm the new code.
        setIsPhoneVerified(false);

        setIsPhoneVerifying(true);
        try {
            const devCode = await userService.requestVerificationCode(phoneNumber);
            if (devCode) {
                setVerifyCode(devCode);
                showAlert('인증번호가 자동 입력되었습니다.');
            } else {
                showAlert('인증번호가 발송되었습니다.');
            }
        } catch {
             showAlert('인증번호 발송에 실패했습니다.');
        } finally {
            setIsPhoneVerifying(false);
        }
    };

    const handleConfirmVerifyCode = async () => {
        if (isPhoneVerifying) return;
        if (!verifyCode) {
            showAlert('인증번호를 입력해주세요.');
            return;
        }
        setIsPhoneVerifying(true);
        try {
            const success = await userService.confirmVerificationCode(phoneNumber, verifyCode);
            if (success) {
                setIsPhoneVerified(true);
                showAlert('인증되었습니다.');
            } else {
                showAlert('인증번호가 올바르지 않습니다.');
            }
        } catch {
            showAlert('인증 확인에 실패했습니다.');
        } finally {
            setIsPhoneVerifying(false);
        }
    };

    const handleSubmit = async () => {
        if (isSubmitting) return;
        // Validation: Basic Info
        if (!name.trim()) {
            showAlert('이름을 입력해주세요.', () => nameRef.current?.focus());
            return;
        }
        if (!nickname.trim()) {
            showAlert('닉네임을 입력해주세요.', () => nicknameRef.current?.focus());
            return;
        }
        if (!isNicknameChecked) {
            showAlert('닉네임 중복 확인을 해주세요.', () => {
                nicknameRef.current?.focus();
            });
            return;
        }
        
        // ... rest of validation
        if (!birthDate.trim() || birthDate.length !== 8) {
            showAlert('생년월일 8자리를 정확히 입력해주세요.', () => birthDateRef.current?.focus());
            return;
        }
        if (!phoneNumber.trim() || phoneNumber.length !== 11) {
            showAlert('휴대폰 번호 11자리를 정확히 입력해주세요.', () => phoneNumberRef.current?.focus());
            return;
        }
        if (hasPet === null) {
            showAlert('반려동물 유무를 선택해주세요.', () => hasPetRef.current?.focus());
            return;
        }
        if (!intro.trim()) {
            showAlert('간단한 자기소개를 입력해주세요.', () => introRef.current?.focus());
            return;
        }
         
         // Filter out nulls for actual count check and submission
         const validImages = profileImageUrls.filter((url): url is string => !!url);

        // Image validation
        if (validImages.length < 2) {
            showAlert('이미지는 최소 2장 이상 등록해야 합니다.');
            return;
        }

        setIsSubmitting(true);
        try {
            // Update profile images (send valid list only)
             await userService.updateProfileImages(validImages);
             
            // Format birthDate (YYYYMMDD -> YYYY-MM-DD)
            const formattedBirthDate = birthDate.length === 8 
                ? `${birthDate.slice(0, 4)}-${birthDate.slice(4, 6)}-${birthDate.slice(6, 8)}`
                : birthDate;
            
            // Format phoneNumber (01012345678 -> 010-1234-5678)
            const formattedPhoneNumber = phoneNumber.length === 11 && !phoneNumber.includes('-')
                ? `${phoneNumber.slice(0, 3)}-${phoneNumber.slice(3, 7)}-${phoneNumber.slice(7, 11)}`
                : phoneNumber;
             
            // Prepare update data with formatted values
            const updateData = {
                name,
                nickname,
                birthDate: formattedBirthDate,
                phoneNumber: formattedPhoneNumber,
                gender,
                hasPet: hasPet === true, // Ensure boolean
                intro,
                mbti,
                interests,
                hobbies
            };

            await userService.updateProfile(updateData);

            // Invalidate cache to ensure fresh data
            await queryClient.invalidateQueries({ queryKey: ['users', 'me'] });
            await queryClient.invalidateQueries({ queryKey: ['community'] });

            // Optimistic update — currentUser(API 데이터 우선)를 base로 사용하여 기존 필드 보존
            if (currentUser) {
                const updatedUser: User = {
                    ...currentUser,
                    ...updateData,
                    hasPet: hasPet === true,
                    profileImageUrls: validImages,
                    profileImageUrl: validImages.length > 0 ? validImages[0] : undefined
                };
                updateUser(updatedUser);
            }

            showAlert('프로필이 수정되었습니다.', () => navigate('/profile'));
        } catch (error: unknown) {
            console.error(error);
            const err = error as { response?: { data?: { message?: string } }; message?: string };
            const message = err?.response?.data?.message || err?.message || '저장에 실패했습니다.';
            showAlert(message);
        } finally {
            setIsSubmitting(false);
        }
    };

  const keyboardDismiss = useKeyboardDismiss();

    return (
        <SubPageLayout title="프로필 수정">
            <div id="profileContainer" {...keyboardDismiss}>
                <div className="profile_write">
                    <div className="profile_img_edit">
                            <div className="profile_img" style={{ 
                                backgroundImage: profileImageUrls[0] 
                                    ? `url(${profileImageUrls[0]})` 
                                    : `url(/assets/images/common/profile_none_img.svg)`,
                                backgroundSize: 'cover',
                                backgroundPosition: 'center',
                                backgroundColor: profileImageUrls[0] ? 'transparent' : '#f0f0f0' // Optional background color fallback
                            }}>
                                 {!profileImageUrls[0] && <div className="img_none"></div>}
                            </div>
                    </div>
                    
                    <ImageUploadSlider 
                        images={profileImageUrls} 
                        maxCount={5} 
                        onUpload={handleImageUpload} 
                        onDelete={handleImageDelete} 
                    />
                    
                    <div className="guide_box">
                        <dl>
                            <dt>등록 가이드</dt>
                            <dd>최대 5장 등록 가능하며 2장은 필수로 등록해주세요.</dd>
                            <dd>본인의 사진을 등록해주세요.</dd>
                            <dd>가장 먼저 등록한 사진이 대표 사진으로 설정 됩니다.</dd>
                        </dl>
                    </div>
                    
                    <div className="profile_write_wrap">
                        <strong>기본 정보</strong>
                        <div className="profile_write_box pink_color">
                            <div className={`write_box${isProfileLocked ? ' field_locked' : ''}`}>
                                <label htmlFor="owner_name" className={isProfileLocked ? 'field_lock_label' : ''}>
                                    {isProfileLocked && <span className="lock_icon">&#x1F512;</span>}
                                    이름
                                </label>
                                <input
                                    type="text"
                                    id="owner_name"
                                    ref={nameRef}
                                    placeholder="이름을 입력해주세요."
                                    value={name}
                                    onChange={(e) => !isProfileLocked && setName(e.target.value)}
                                    readOnly={isProfileLocked}
                                    maxLength={20}
                                />
                                {isProfileLocked && (
                                    <span className="field_lock_hint">인증 후 변경할 수 없습니다. 고객센터에 문의해주세요.</span>
                                )}
                            </div>
                            <div className="btn_write_box">
                                <div className="write_box">
                                    <label htmlFor="owner_nickname">닉네임</label>
                                    <input
                                        type="text"
                                        id="owner_nickname"
                                        ref={nicknameRef}
                                        placeholder="닉네임을 입력해주세요."
                                        value={nickname}
                                        onChange={handleNicknameChange}
                                        maxLength={15}
                                    />
                                </div>
                                <button
                                    type="button"
                                    // If checked, button is active (for 'Change'). If NOT checked, active (for 'Check').
                                    // Basically always active unless empty?
                                    className={nickname ? '' : 'disabled'}
                                    disabled={!nickname || isCheckingNickname}
                                    onClick={isNicknameChecked ? () => nicknameRef.current?.focus() : handleCheckNickname}
                                >
                                    {isNicknameChecked ? '닉네임 변경' : '중복 확인'}
                                </button>
                            </div>
                            <div className={`write_box${isProfileLocked ? ' field_locked' : ''}`}>
                                <label htmlFor="owner_birth_day" className={isProfileLocked ? 'field_lock_label' : ''}>
                                    {isProfileLocked && <span className="lock_icon">&#x1F512;</span>}
                                    생년월일
                                </label>
                                <input
                                    type="text"
                                    id="owner_birth_day"
                                    ref={birthDateRef}
                                    placeholder="생년월일 8자리"
                                    value={birthDate}
                                    onChange={(e) => !isProfileLocked && setBirthDate(e.target.value.replace(/[^0-9]/g, ''))}
                                    maxLength={8}
                                    readOnly={isProfileLocked}
                                />
                                {isProfileLocked && (
                                    <span className="field_lock_hint">인증 후 변경할 수 없습니다. 고객센터에 문의해주세요.</span>
                                )}
                            </div>
                            <div className="btn_write_box">
                                <div className={`write_box${isProfileLocked ? ' field_locked' : ''}`}>
                                    {isProfileLocked && (
                                        <label className="field_lock_label">
                                            <span className="lock_icon">&#x1F512;</span>
                                            휴대폰 번호
                                        </label>
                                    )}
                                    <input
                                        type="text"
                                        inputMode="numeric"
                                        id="owner_phone_number"
                                        ref={phoneNumberRef}
                                        placeholder="휴대폰 11자리"
                                        value={phoneNumber}
                                        onChange={(e) => !isProfileLocked && handlePhoneNumberChange(e)}
                                        maxLength={11}
                                        readOnly={isProfileLocked}
                                    />
                                    {isProfileLocked && (
                                        <span className="field_lock_hint">인증 후 변경할 수 없습니다. 고객센터에 문의해주세요.</span>
                                    )}
                                </div>
                                {!isProfileLocked && (
                                    <>
                                        <button
                                            type="button"
                                            className={phoneNumber.length === 11 ? '' : 'disabled'}
                                            disabled={phoneNumber.length !== 11 || isPhoneVerifying}
                                            onClick={handleRequestVerifyCode}
                                        >
                                            {isPhoneVerified ? '재인증' : '인증번호'}
                                        </button>
                                        <div className="write_box">
                                            <input
                                                type="text"
                                                id="owner_phone_verify_code"
                                                placeholder="인증번호 6자리"
                                                value={verifyCode}
                                                onChange={(e) => setVerifyCode(e.target.value.replace(/[^0-9]/g, ''))}
                                                maxLength={6}
                                                disabled={isPhoneVerified}
                                            />
                                        </div>
                                        <button
                                            type="button"
                                            className={verifyCode.length >= 4 && !isPhoneVerified ? '' : 'disabled'}
                                            disabled={isPhoneVerified || isPhoneVerifying}
                                            onClick={handleConfirmVerifyCode}
                                        >
                                            {isPhoneVerified ? '확인 완료' : '확인'}
                                        </button>
                                    </>
                                )}
                            </div>

                            <div className={`radio_wrap${isProfileLocked ? ' field_locked' : ''}`}>
                                <p className={isProfileLocked ? 'field_lock_label' : ''}>
                                    {isProfileLocked && <span className="lock_icon">&#x1F512;</span>}
                                    성별
                                </p>
                                <div className="radio_box">
                                    <input
                                        type="radio"
                                        name="owner_gender"
                                        id="owner_gender_man"
                                        value="MALE"
                                        checked={gender === 'MALE'}
                                        onChange={(e) => !isProfileLocked && setGender(e.target.value)}
                                        disabled={isProfileLocked}
                                    />
                                    <label htmlFor="owner_gender_man">남성</label>
                                </div>
                                <div className="radio_box">
                                    <input
                                        type="radio"
                                        name="owner_gender"
                                        id="owner_gender_woman"
                                        value="FEMALE"
                                        checked={gender === 'FEMALE'}
                                        onChange={(e) => !isProfileLocked && setGender(e.target.value)}
                                        disabled={isProfileLocked}
                                    />
                                    <label htmlFor="owner_gender_woman">여성</label>
                                </div>
                                {isProfileLocked && (
                                    <span className="field_lock_hint" style={{ width: '100%' }}>인증 후 변경할 수 없습니다. 고객센터에 문의해주세요.</span>
                                )}
                            </div>
                            
                            <div className="radio_wrap">
                                <p>반려동물 유무</p>
                                <div className="radio_box">
                                    <input 
                                        type="radio" 
                                        name="owner_pet" 
                                        id="owner_pet_Y" 
                                        ref={hasPetRef}
                                        value="true"
                                        checked={hasPet === true}
                                        onChange={() => setHasPet(true)}
                                    />
                                    <label htmlFor="owner_pet_Y">있음</label>
                                </div>
                                <div className="radio_box">
                                    <input 
                                        type="radio" 
                                        name="owner_pet" 
                                        id="owner_pet_N" 
                                        value="false"
                                        checked={hasPet === false}
                                        onChange={() => setHasPet(false)} 
                                    />
                                    <label htmlFor="owner_pet_N">없음</label>
                                </div>
                                <span>* 비반려인은 친구 찾기에서 반려인 사용자만 매칭 됩니다.</span>
                            </div>
                            
                            <div className="write_box">
                                <label htmlFor="pet_special_notes">간단한 자기소개</label>
                                <textarea
                                    id="pet_special_notes"
                                    ref={introRef}
                                    placeholder="간단한 자기소개을 적어주세요."
                                    value={intro}
                                    onChange={(e) => setIntro(e.target.value)}
                                    maxLength={100}
                                    onFocus={() => {
                                        setTimeout(() => {
                                            introRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                                        }, 300);
                                    }}
                                ></textarea>
                                <span style={{ float: 'right', fontSize: '12px', color: '#999', marginTop: '4px' }}>
                                    {intro.length}/100
                                </span>
                            </div>
                        </div>
                        
                        <strong>추가 정보</strong>
                        <div className="profile_write_box purple_color">
                            <div className="write_box write_select">
                                <label htmlFor="owner_mbti">MBTI</label>
                                <CustomSelect
                                    id="owner_mbti"
                                    value={mbti}
                                    options={[
                                        { label: 'INTJ', value: 'INTJ' }, { label: 'INTP', value: 'INTP' },
                                        { label: 'ENTJ', value: 'ENTJ' }, { label: 'ENTP', value: 'ENTP' },
                                        { label: 'INFJ', value: 'INFJ' }, { label: 'INFP', value: 'INFP' },
                                        { label: 'ENFJ', value: 'ENFJ' }, { label: 'ENFP', value: 'ENFP' },
                                        { label: 'ISTJ', value: 'ISTJ' }, { label: 'ISFJ', value: 'ISFJ' },
                                        { label: 'ESTJ', value: 'ESTJ' }, { label: 'ESFJ', value: 'ESFJ' },
                                        { label: 'ISTP', value: 'ISTP' }, { label: 'ISFP', value: 'ISFP' },
                                        { label: 'ESTP', value: 'ESTP' }, { label: 'ESFP', value: 'ESFP' },
                                    ]}
                                    onChange={(value) => setMbti(value)}
                                />
                            </div>
                            
                            <div className="txt_check_wrap">
                                <p>관심사 (다중 선택 가능)</p>
                                {availableInterests.map(interest => (
                                    <div className="txt_check_box" key={interest.id}>
                                        <input 
                                            type="checkbox" 
                                            id={`owner_interest_${interest.id}`} 
                                            name="owner_interest" 
                                            value={interest.name}
                                            checked={interests.includes(interest.name)}
                                            onChange={(e) => handleInterestChange(interest.name, e.target.checked)}
                                        />
                                        <label htmlFor={`owner_interest_${interest.id}`}>{interest.name}</label>
                                    </div>
                                ))}
                            </div>
                            
                            <div className="txt_check_wrap">
                                <p>취미 (다중 선택 가능)</p>
                                {availableHobbies.map(hobby => (
                                     <div className="txt_check_box" key={hobby.id}>
                                        <input 
                                            type="checkbox" 
                                            id={`owner_hobby_${hobby.id}`} 
                                            name="owner_hobby" 
                                            value={hobby.name}
                                            checked={hobbies.includes(hobby.name)}
                                            onChange={(e) => handleHobbyChange(hobby.name, e.target.checked)}
                                        />
                                        <label htmlFor={`owner_hobby_${hobby.id}`}>{hobby.name}</label>
                                    </div>
                                ))}
                            </div>
                        </div>
                    </div>
                    <div className="btn_wrap">
                        <button type="button" className="cancel_btn" onClick={() => navigate('/profile?tab=owner')}>취소</button>
                        <button type="button" className="submit_btn" onClick={handleSubmit} disabled={isSubmitting}>{isSubmitting ? '저장 중...' : '저장'}</button>
                    </div>
                </div>
            </div>
        </SubPageLayout>
    );
};

export default ProfileOwnerWritePage;
