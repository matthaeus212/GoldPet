import { useState, useEffect, useRef } from 'react';

interface CustomSelectProps {
  id?: string;
  className?: string;
  value: string;
  options: { label: string; value: string }[];
  placeholder?: string;
  onChange: (value: string) => void;
  disabled?: boolean;
}

export default function CustomSelect({
  id,
  className,
  value,
  options,
  placeholder = '선택해 주세요',
  onChange,
  disabled = false,
}: CustomSelectProps) {
  const [isOpen, setIsOpen] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);

  const selectedOption = options.find((opt) => opt.value === value);
  const displayText = selectedOption ? selectedOption.label : placeholder;
  const isPlaceholder = !selectedOption;

  const handleTriggerClick = () => {
    if (disabled) return;
    setIsOpen((prev) => !prev);
  };

  const handleOptionClick = (optionValue: string) => {
    onChange(optionValue);
    setIsOpen(false);
  };

  useEffect(() => {
    const handleOutside = (e: MouseEvent | TouchEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };

    document.addEventListener('mousedown', handleOutside);
    document.addEventListener('touchstart', handleOutside);

    return () => {
      document.removeEventListener('mousedown', handleOutside);
      document.removeEventListener('touchstart', handleOutside);
    };
  }, []);

  useEffect(() => {
    if (!isOpen) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setIsOpen(false);
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isOpen]);

  const rootClasses = [
    'custom-select',
    isOpen ? 'custom-select--open' : '',
    disabled ? 'custom-select--disabled' : '',
    className ?? '',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <div id={id} className={rootClasses} ref={wrapperRef}>
      <div className="custom-select__trigger" onClick={handleTriggerClick}>
        <span
          className={
            'custom-select__trigger-text' +
            (isPlaceholder ? ' custom-select__trigger-text--placeholder' : '')
          }
        >
          {displayText}
        </span>
        <span className="custom-select__arrow" />
      </div>
      {isOpen && (
        <div className="custom-select__dropdown" role="listbox">
          <div className="custom-select__divider" />
          {options.map((opt) => (
            <div
              key={opt.value}
              className="custom-select__option"
              role="option"
              aria-selected={opt.value === value}
              onClick={() => handleOptionClick(opt.value)}
            >
              {opt.label}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
