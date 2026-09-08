import React from 'react';
import DatePicker, { registerLocale } from 'react-datepicker';
import { ko } from 'date-fns/locale';
import 'react-datepicker/dist/react-datepicker.css';
import './KoreanDatePicker.css';

// Register Korean locale
registerLocale('ko', ko);

interface KoreanDatePickerProps {
    selected: Date | null;
    onChange: (date: Date | null) => void;
    disabled?: boolean;
    placeholder?: string;
    id?: string;
    maxDate?: Date;
}

const KoreanDatePicker: React.FC<KoreanDatePickerProps> = ({
    selected,
    onChange,
    disabled = false,
    placeholder = '날짜를 선택해주세요',
    id,
    maxDate = new Date()
}) => {
    return (
        <div className="datepicker-container">
            <DatePicker
                id={id}
                selected={selected}
                onChange={onChange}
                locale="ko"
                dateFormat="yyyy-MM-dd"
                placeholderText={placeholder}
                disabled={disabled}
                maxDate={maxDate}
                showYearDropdown
                showMonthDropdown
                dropdownMode="select"
                yearDropdownItemNumber={30}
                scrollableYearDropdown
                shouldCloseOnSelect={true}
                className="datepicker-input"
                calendarClassName="korean-calendar"
                wrapperClassName="datepicker-wrapper"
            />
            <span className="datepicker-icon">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <rect x="3" y="4" width="18" height="18" rx="2" stroke="#999" strokeWidth="2"/>
                    <line x1="3" y1="10" x2="21" y2="10" stroke="#999" strokeWidth="2"/>
                    <line x1="8" y1="2" x2="8" y2="6" stroke="#999" strokeWidth="2" strokeLinecap="round"/>
                    <line x1="16" y1="2" x2="16" y2="6" stroke="#999" strokeWidth="2" strokeLinecap="round"/>
                </svg>
            </span>
        </div>
    );
};

export default KoreanDatePicker;
