import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '../../stores/authStore';
import axios from 'axios';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081';

export const LoginSuccessPage = () => {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const login = useAuthStore((state) => state.login);

    useEffect(() => {
        const token = searchParams.get('token');
        const refreshToken = searchParams.get('refreshToken') || '';
        // 토큰을 URL에서 즉시 제거 (브라우저 히스토리 보호)
        window.history.replaceState({}, '', '/loginSuccess');
        if (token) {
            // apiClient 대신 plain axios 사용 — 인터셉터가 localStorage의 이전 토큰으로 덮어쓰는 버그 방지
            axios.get(`${API_BASE_URL}/api/v1/users/me`, { headers: { Authorization: `Bearer ${token}` } })
                .then((response) => {
                    const user = response.data;

                    // Check if running in popup
                    if (window.opener) {
                        if (!user.signupCompleted) {
                            // Incomplete -> Send SIGNUP_REQUIRED message
                            window.opener.postMessage({
                                type: 'LOGIN_ERROR',
                                message: 'SIGNUP_REQUIRED',
                                signupRequired: true,
                                provider: user.oauthProvider
                            }, window.location.origin);
                        } else {
                            // Complete -> Send LOGIN_SUCCESS message
                            window.opener.postMessage({
                                type: 'LOGIN_SUCCESS',
                                accessToken: token,
                                refreshToken,
                                user
                            }, window.location.origin);
                        }
                        // Close popup is handled by opener or self? 
                        // Usually self closes after sending message, but let's wait a tick
                        // setTimeout(() => window.close(), 100);
                        // Actually, let's let the opener close it or close it here.
                        // Ideally, we close it here.
                        // But let's keep the logic simple.
                        // window.close(); 
                        // The authService handler closes it. But if we close it here, the handler might miss it?
                        // No, postMessage is synchronous-ish.
                        // But let's leave it open for a split second or let the opener close it?
                        // The authService code: popup.close() is called.
                        // So we just need to send the message.
                    } else {
                        // Fallback for non-popup (e.g. mobile redirect if popup failed)
                        if (!user.signupCompleted) {
                            navigate('/signup', { state: { isSns: true, provider: user.oauthProvider } });
                        } else {
                            login(token, refreshToken, user);
                            const redirect = sessionStorage.getItem('postLoginRedirect');
                            if (redirect) {
                                sessionStorage.removeItem('postLoginRedirect');
                                navigate(redirect, { replace: true });
                                return;
                            }
                            navigate('/home', { replace: true });
                        }
                    }
                })
                .catch((error) => {
                    console.error('Failed to fetch user info', error);
                    if (!window.opener) {
                        navigate('/login', { replace: true });
                    } else {
                        window.close();
                    }
                });
        } else {
            if (!window.opener) {
                navigate('/login', { replace: true });
            } else {
                window.close();
            }
        }
    }, [searchParams, navigate, login]);

    return (
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
            <p>로그인 중입니다...</p>
        </div>
    );
};
