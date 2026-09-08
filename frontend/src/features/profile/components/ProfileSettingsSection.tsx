import React from 'react';

interface ProfileSettingsSectionProps {
    onEdit: () => void;
    editLabel?: string;
    idPrefix?: string;
    isNotificationEnabled?: boolean;
    onNotificationChange?: (enabled: boolean) => Promise<void>;
}

export const ProfileSettingsSection: React.FC<ProfileSettingsSectionProps> = () => {
    return <></>;
};
