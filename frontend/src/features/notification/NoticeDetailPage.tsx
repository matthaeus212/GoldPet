import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { noticeService, type AppNoticeResponse } from '../../services/noticeService';
import { SubPageLayout } from '../../components/layout/SubPageLayout';
import './NoticeDetailPage.css';

const NoticeDetailPage = () => {
    const { id } = useParams<{ id: string }>();
    const [notice, setNotice] = useState<AppNoticeResponse | null>(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        if (!id) return;
        loadNotice();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- loadNotice is defined in component scope without useCallback; adding it would cause infinite re-fetch loop
    }, [id]);

    const loadNotice = async () => {
        setLoading(true);
        try {
            // Fetch all active notices and find by id
            const notices = await noticeService.getActiveNotices();
            const found = notices.find(n => n.id === Number(id));
            setNotice(found || null);
        } catch {
            // silent
        } finally {
            setLoading(false);
        }
    };

    const formatDate = (isoString: string) => {
        const date = new Date(isoString);
        return `${date.getFullYear()}.${String(date.getMonth() + 1).padStart(2, '0')}.${String(date.getDate()).padStart(2, '0')}`;
    };

    const typeLabel = notice?.type === 'NOTICE' ? '공지' : notice?.type === 'EVENT_BANNER' ? '이벤트' : '공지';

    return (
        <SubPageLayout title={typeLabel}>
            <div id="noticeDetailContainer">
                {loading ? (
                    <div className="loading">로딩 중...</div>
                ) : !notice ? (
                    <div className="empty_state">
                        <p>공지를 찾을 수 없습니다.</p>
                    </div>
                ) : (
                    <div className="notice_detail">
                        <div className="notice_detail_header">
                            <h2 className="notice_detail_title">{notice.title}</h2>
                            <span className="notice_detail_date">{formatDate(notice.startAt)}</span>
                        </div>
                        {notice.imageUrls.length > 0 && (
                            <div className="notice_detail_images">
                                {notice.imageUrls.map((url, idx) => (
                                    <img key={idx} src={url} alt="" />
                                ))}
                            </div>
                        )}
                        {notice.content && (
                            <div className="notice_detail_content">
                                <p>{notice.content}</p>
                            </div>
                        )}
                        {notice.linkUrl && (
                            <a
                                href={notice.linkUrl}
                                className="notice_detail_link"
                                target="_blank"
                                rel="noopener noreferrer"
                            >
                                자세히 보기
                            </a>
                        )}
                    </div>
                )}
            </div>
        </SubPageLayout>
    );
};

export default NoticeDetailPage;
