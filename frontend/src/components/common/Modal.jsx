import { X } from 'lucide-react';
import '../../styles/components/modal.css';

export default function Modal({ open, title, subtitle, children, onClose, size = 'md' }) {
  if (!open) return null;

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className={`modal-shell modal-${size}`} onClick={(event) => event.stopPropagation()} role="dialog" aria-modal="true">
        <div className="modal-shell__header">
          <div>
            <h2>{title}</h2>
            {subtitle && <p>{subtitle}</p>}
          </div>
          <button type="button" className="icon-button icon-button--ghost" onClick={onClose} aria-label="Cerrar modal">
            <X size={18} />
          </button>
        </div>
        <div className="modal-shell__body">{children}</div>
      </div>
    </div>
  );
}
