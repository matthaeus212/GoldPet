import React, { useState } from 'react';
import { MainHeader } from '../../components/common/MainHeader';
import { BottomNav } from '../../components/common/BottomNav';
import { TERMS_CONTENT } from '../../data/termsData';
import '../../assets/css/common.css';

// Using inline styles for specific component needs, but relying on global CSS for layout
const styles = {
    // Removed specific container padding to rely on #wrap padding from common.css
    container: {
        paddingBottom: '25vw', // Bottom padding for nav only
        minHeight: '100vh',
    },
    list: {
        display: 'flex',
        flexDirection: 'column' as const,
        gap: '15px',
        padding: '0 4.44vw', // Standard side padding
    },
    listItemContainer: {
        backgroundColor: '#fff',
        borderRadius: '15px',
        boxShadow: '0 2px 5px rgba(0,0,0,0.03)',
        overflow: 'hidden',
    },
    listItemHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        padding: '20px',
        cursor: 'pointer',
        fontSize: '16px',
        fontWeight: 500,
    },
    arrow: {
        fontSize: '14px',
        color: '#ccc',
        transition: 'transform 0.3s ease',
    },
    detailContent: {
        padding: '0 20px 20px',
        whiteSpace: 'pre-wrap' as const,
        fontSize: '14px',
        lineHeight: '1.6',
        color: '#555',
        borderTop: '1px solid #f0f0f0',
        marginTop: '-10px', // Pull up closer to title
        paddingTop: '20px',
    },
    title: {
        fontSize: '24px', 
        fontWeight: 700,
        marginBottom: '30px',
        color: '#333',
        textAlign: 'center' as const,
        paddingTop: '0', // Removed extra padding
    }
};

export const ProfileTermsPage: React.FC = () => {
    const [expandedTerm, setExpandedTerm] = useState<string | null>(null);

    const toggleTerm = (key: string) => {
        if (expandedTerm === key) {
            setExpandedTerm(null); // Collapse if already open
        } else {
            setExpandedTerm(key); // Expand clicked, auto-collapse others
        }
    };

    return (
        <div id="wrap">
            <MainHeader variant="logo-with-icons" />
            <div style={styles.container}>
                <h2 style={styles.title}>약관 및 정책</h2>
                <div style={styles.list}>
                    {Object.entries(TERMS_CONTENT).map(([key, term]) => (
                        <div key={key} style={styles.listItemContainer}>
                            <div 
                                style={styles.listItemHeader}
                                onClick={() => toggleTerm(key)}
                            >
                                <span>{term.title}</span>
                                <span style={{
                                    ...styles.arrow,
                                    transform: expandedTerm === key ? 'rotate(90deg)' : 'rotate(0deg)'
                                }}>▶</span>
                            </div>
                            
                            {expandedTerm === key && (
                                <div style={styles.detailContent}>
                                    {term.content}
                                </div>
                            )}
                        </div>
                    ))}
                </div>
            </div>
            <BottomNav />
        </div>
    );
};
