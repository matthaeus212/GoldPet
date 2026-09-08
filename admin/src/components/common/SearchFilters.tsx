import React, { useState, useEffect, useRef } from 'react';
import clsx from 'clsx';

// Field type definitions
type FilterField =
  | { type: 'text'; key: string; placeholder: string }
  | { type: 'select'; key: string; label: string; options: { value: string; label: string }[] }
  | { type: 'date'; key: string; label: string }
  | { type: 'number'; key: string; label: string; min?: number; max?: number }
  | { type: 'button-group'; key: string; options: { value: string; label: string }[] };

interface SearchFiltersProps {
  fields: FilterField[];
  values: Record<string, string>;
  onChange: (key: string, value: string) => void;
  onSearch?: () => void;
  debounceMs?: number;
}

export const SearchFilters: React.FC<SearchFiltersProps> = ({
  fields,
  values,
  onChange,
  onSearch,
  debounceMs = 300,
}) => {
  const [localText, setLocalText] = useState<Record<string, string>>(() => {
    const textFields = fields.filter(f => f.type === 'text');
    const initial: Record<string, string> = {};
    textFields.forEach(f => { initial[f.key] = values[f.key] || ''; });
    return initial;
  });
  const debounceTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});

  const handleTextChange = (key: string, value: string) => {
    setLocalText(prev => ({ ...prev, [key]: value }));

    if (debounceTimers.current[key]) {
      clearTimeout(debounceTimers.current[key]);
    }

    debounceTimers.current[key] = setTimeout(() => {
      onChange(key, value);
    }, debounceMs);
  };

  // Cleanup timers
  useEffect(() => {
    const timers = debounceTimers.current;
    return () => {
      Object.values(timers).forEach(clearTimeout);
    };
  }, []);

  const renderField = (field: FilterField) => {
    switch (field.type) {
      case 'text':
        return (
          <div key={field.key} className="flex-1 min-w-[200px]">
            <input
              type="text"
              placeholder={field.placeholder}
              value={localText[field.key] ?? values[field.key] ?? ''}
              onChange={(e) => handleTextChange(field.key, e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>
        );

      case 'select':
        return (
          <div key={field.key} className="min-w-[150px]">
            <select
              value={values[field.key] ?? ''}
              onChange={(e) => onChange(field.key, e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            >
              <option value="">{field.label}</option>
              {field.options.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
        );

      case 'date':
        return (
          <div key={field.key} className="min-w-[160px]">
            <label className="block text-xs text-gray-500 mb-1">{field.label}</label>
            <input
              type="date"
              value={values[field.key] ?? ''}
              onChange={(e) => onChange(field.key, e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>
        );

      case 'number':
        return (
          <div key={field.key} className="min-w-[120px]">
            <label className="block text-xs text-gray-500 mb-1">{field.label}</label>
            <input
              type="number"
              min={field.min}
              max={field.max}
              value={values[field.key] ?? ''}
              onChange={(e) => onChange(field.key, e.target.value)}
              className="w-full px-3 py-2 border border-gray-300 rounded-lg text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
            />
          </div>
        );

      case 'button-group':
        return (
          <div key={field.key} className="flex gap-1">
            {field.options.map((opt) => (
              <button
                key={opt.value}
                onClick={() => onChange(field.key, values[field.key] === opt.value ? '' : opt.value)}
                className={clsx(
                  'px-3 py-2 text-sm rounded-lg border transition-colors',
                  values[field.key] === opt.value
                    ? 'bg-indigo-600 text-white border-indigo-600'
                    : 'bg-white text-gray-700 border-gray-300 hover:bg-gray-50',
                )}
              >
                {opt.label}
              </button>
            ))}
          </div>
        );
    }
  };

  return (
    <div className="flex flex-wrap items-end gap-3 mb-4">
      {fields.map(renderField)}
      {onSearch && (
        <button
          onClick={onSearch}
          className="px-4 py-2 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700 transition-colors"
        >
          검색
        </button>
      )}
    </div>
  );
};
