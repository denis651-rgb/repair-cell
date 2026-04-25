import { AlertCircle, LockOpen, Wallet } from 'lucide-react';
import Modal from '../common/Modal';

export default function CashOpeningModal({
  open,
  montoApertura,
  onMontoAperturaChange,
  onSubmit,
  loading,
  error,
}) {
  return (
    <Modal
      open={open}
      onClose={() => {}}
      title="Abre la caja para iniciar la jornada"
      subtitle="Este paso es obligatorio para que el sistema registre correctamente entradas, salidas y movimientos del dia."
      size="md"
      dismissible={false}
      closeOnBackdrop={false}
    >
      <div className="cash-opening-modal">
        <div className="cash-opening-modal__notice">
          <div className="cash-opening-modal__icon">
            <Wallet size={20} />
          </div>
          <div>
            <strong>La caja del dia aun no esta abierta</strong>
            <p>
              Define el monto inicial disponible en efectivo. Desde ese momento las ventas, cobros,
              egresos y ajustes quedaran asociados a esta jornada.
            </p>
          </div>
        </div>

        {error && (
          <div className="cash-opening-modal__error" role="alert">
            <AlertCircle size={16} />
            <span>{error}</span>
          </div>
        )}

        <form className="cash-opening-modal__form" onSubmit={onSubmit}>
          <label>
            <span>Monto de apertura</span>
            <input
              type="number"
              min="0"
              step="0.01"
              value={montoApertura}
              onChange={(event) => onMontoAperturaChange(event.target.value)}
              placeholder="0.00"
              autoFocus
              required
            />
          </label>

          <button type="submit" disabled={loading || !montoApertura}>
            <LockOpen size={16} />
            {loading ? 'Abriendo caja...' : 'Abrir caja'}
          </button>
        </form>
      </div>
    </Modal>
  );
}
