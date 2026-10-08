// One labelled input (or textarea / select) with its error message underneath. Password fields get a show/hide button.
import { useState } from 'react';

export default function FormField({ id, label, error, hint, type = 'text', as: Control = 'input', children, ...inputProps }) {
  const [revealed, setRevealed] = useState(false);
  const isPassword = type === 'password';
  const describedBy = [error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(' ') || undefined;

  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      <div className="field-control">
        <Control
          id={id}
          name={id}
          className={isPassword ? 'has-toggle' : undefined}
          type={Control === 'input' ? (isPassword && revealed ? 'text' : type) : undefined}
          aria-invalid={error ? 'true' : undefined}
          aria-describedby={describedBy}
          {...inputProps}
        >
          {children}
        </Control>
        {isPassword && (
          <button
            type="button"
            className="field-toggle"
            onClick={() => setRevealed((shown) => !shown)}
            aria-pressed={revealed}
          >
            {revealed ? 'Hide' : 'Show'}
          </button>
        )}
      </div>
      {hint && !error && (
        <p id={`${id}-hint`} className="field-hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="field-error" role="alert">
          {error}
        </p>
      )}
    </div>
  );
}
