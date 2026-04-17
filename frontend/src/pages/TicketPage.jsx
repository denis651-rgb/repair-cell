import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  ArrowLeft,
  CheckCircle2,
  Download,
  FileText,
  LoaderCircle,
  MessageCircle,
  Printer,
  ShieldCheck,
  Smartphone,
  Ticket,
  UserRound,
  Wrench,
} from 'lucide-react';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import { formatDate, formatDateTime, money, resolvePartSubtotal, resolveVisibleOrderAmount } from '../utils/formatters';
import '../styles/ticket.css';

function getStatusLabel(status) {
  switch (status) {
    case 'RECIBIDO':
      return 'Recibido';
    case 'EN_DIAGNOSTICO':
      return 'En diagnostico';
    case 'EN_REPARACION':
      return 'En reparacion';
    case 'LISTO':
      return 'Listo para entrega';
    case 'ENTREGADO':
      return 'Entregado';
    case 'CANCELADO':
      return 'Cancelado';
    default:
      return status || 'Sin estado';
  }
}

function getStatusTone(status) {
  switch (status) {
    case 'ENTREGADO':
      return 'is-success';
    case 'LISTO':
      return 'is-info';
    case 'EN_REPARACION':
      return 'is-warning';
    case 'CANCELADO':
      return 'is-danger';
    default:
      return 'is-neutral';
  }
}

function getDisplayAmount(order) {
  return resolveVisibleOrderAmount(order);
}

export default function TicketPage() {
  const { id } = useParams();
  const [html, setHtml] = useState('');
  const [order, setOrder] = useState(null);
  const [whatsapp, setWhatsapp] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [sharing, setSharing] = useState(false);

  useEffect(() => {
    let active = true;

    async function loadTicket() {
      setLoading(true);
      setError('');

      try {
        const [ticketHtml, orderData, whatsappData] = await Promise.all([
          api.raw(`/tickets/orden-reparacion/${id}/html`),
          api.get(`/ordenes-reparacion/${id}`),
          api.get(`/tickets/orden-reparacion/${id}/whatsapp`),
        ]);

        if (!active) return;
        setHtml(ticketHtml);
        setOrder(orderData);
        setWhatsapp(whatsappData);
      } catch (err) {
        if (!active) return;
        setError(err.message);
      } finally {
        if (active) setLoading(false);
      }
    }

    loadTicket();
    return () => {
      active = false;
    };
  }, [id]);

  const partsSummary = useMemo(() => {
    if (!order?.partes?.length) {
      return ['Servicio tecnico general'];
    }

    return order.partes.map((part, index) => {
      const quantity = Number(part.cantidad || 0);
      const subtotal = resolvePartSubtotal(part);
      return {
        id: `${part.nombreParte || 'parte'}-${index}`,
        text: `${part.nombreParte || 'Repuesto'} x${quantity} - ${money.format(subtotal)}`,
      };
    });
  }, [order]);

  const canShareWhatsApp = Boolean(whatsapp?.telefonoNormalizado && whatsapp?.mensaje);

  const handleShareWhatsApp = () => {
    if (!canShareWhatsApp) {
      setError('La orden no tiene un telefono valido para WhatsApp.');
      return;
    }

    setSharing(true);
    const text = encodeURIComponent(whatsapp.mensaje);
    window.open(`https://wa.me/${whatsapp.telefonoNormalizado}?text=${text}`, '_blank', 'noopener,noreferrer');
    setTimeout(() => setSharing(false), 500);
  };

  const descargarHtml = () => {
    if (!html) return;
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ticket-${order?.numeroOrden || id}.html`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (loading) {
    return (
      <div className="page-stack ticket-page-shell">
        <PageHeader title="Ticket profesional" subtitle="Estamos preparando la vista del ticket." />
        <div className="ticket-loading-card">
          <LoaderCircle size={24} className="ticket-spin" />
          <span>Cargando informacion de la orden...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="ticket-page-shell page-stack">
      <PageHeader
        title="Ticket profesional"
        subtitle="Listo para impresion termica, exportacion y envio por WhatsApp con un mensaje ordenado."
      >
        <div className="ticket-actions">
          <Link to="/reparaciones">
            <button className="secondary">
              <ArrowLeft size={18} />
              Volver
            </button>
          </Link>
          <button onClick={() => window.print()} disabled={!html}>
            <Printer size={18} />
            Imprimir / PDF
          </button>
          <button className="secondary" onClick={descargarHtml} disabled={!html}>
            <Download size={18} />
            Descargar HTML
          </button>
          <button className="ticket-whatsapp-button" onClick={handleShareWhatsApp} disabled={!canShareWhatsApp || sharing}>
            <MessageCircle size={18} />
            {sharing ? 'Abriendo...' : 'Enviar WhatsApp'}
          </button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      {order && (
        <>
          <section className="ticket-hero-card">
            <div className="ticket-hero-copy">
              <span className="ticket-kicker">Orden de servicio</span>
              <h2>{order.numeroOrden}</h2>
              <p>
                {order.cliente?.nombreCompleto || 'Cliente no disponible'} - {order.dispositivo?.marca || 'Equipo'}{' '}
                {order.dispositivo?.modelo || ''}
              </p>
            </div>
            <div className={`ticket-status-pill ${getStatusTone(order.estado)}`}>{getStatusLabel(order.estado)}</div>
          </section>

          <section className="ticket-summary-grid">
            <article className="ticket-summary-card">
              <div className="ticket-summary-icon icon-soft">
                <UserRound size={20} />
              </div>
              <span>Cliente</span>
              <strong>{order.cliente?.nombreCompleto || 'Sin cliente'}</strong>
              <p>{order.cliente?.telefono || 'Sin telefono registrado'}</p>
            </article>

            <article className="ticket-summary-card">
              <div className="ticket-summary-icon icon-warning">
                <Smartphone size={20} />
              </div>
              <span>Equipo</span>
              <strong>{`${order.dispositivo?.marca || ''} ${order.dispositivo?.modelo || ''}`.trim() || 'Sin equipo'}</strong>
              <p>{order.dispositivo?.imeiSerie || 'IMEI / Serie no registrada'}</p>
            </article>

            <article className="ticket-summary-card">
              <div className="ticket-summary-icon icon-info">
                <Ticket size={20} />
              </div>
              <span>Monto actual</span>
              <strong>{money.format(getDisplayAmount(order))}</strong>
              <p>Garantia: {order.diasGarantia || 0} dias</p>
            </article>

            <article className="ticket-summary-card">
              <div className="ticket-summary-icon icon-success">
                <CheckCircle2 size={20} />
              </div>
              <span>Recepcion</span>
              <strong>{formatDateTime(order.recibidoEn || order.createdAt)}</strong>
              <p>Entrega estimada: {formatDate(order.fechaEntregaEstimada)}</p>
            </article>
          </section>

          <section className="ticket-workspace-grid">
            <article className="ticket-info-panel">
              <div className="ticket-panel-block">
                <div className="ticket-panel-header">
                  <FileText size={18} />
                  <h3>Resumen de la orden</h3>
                </div>
                <div className="ticket-detail-list">
                  <div className="ticket-detail-row">
                    <span>Tecnico</span>
                    <strong>{order.tecnicoResponsable || 'Sin asignar'}</strong>
                  </div>
                  <div className="ticket-detail-row">
                    <span>Costo estimado</span>
                    <strong>{money.format(Number(order.costoEstimado || 0))}</strong>
                  </div>
                  <div className="ticket-detail-row">
                    <span>Costo final</span>
                    <strong>{money.format(getDisplayAmount(order))}</strong>
                  </div>
                  <div className="ticket-detail-row">
                    <span>Firma cliente</span>
                    <strong>{order.nombreFirmaCliente || order.cliente?.nombreCompleto || 'No registrada'}</strong>
                  </div>
                </div>
              </div>

              <div className="ticket-panel-block">
                <div className="ticket-panel-header">
                  <Wrench size={18} />
                  <h3>Falla y diagnostico</h3>
                </div>
                <div className="ticket-note-card">
                  <strong>Falla reportada</strong>
                  <p>{order.problemaReportado || 'Sin detalle registrado.'}</p>
                </div>
                <div className="ticket-note-card">
                  <strong>Diagnostico tecnico</strong>
                  <p>{order.diagnosticoTecnico || 'Pendiente de diagnostico tecnico.'}</p>
                </div>
              </div>

              <div className="ticket-panel-block">
                <div className="ticket-panel-header">
                  <ShieldCheck size={18} />
                  <h3>Detalle incluido</h3>
                </div>
                <div className="ticket-parts-list">
                  {partsSummary.map((item) => (
                    <div className="ticket-part-item" key={item.id}>
                      <span>{item.text}</span>
                    </div>
                  ))}
                </div>
              </div>
            </article>

            <article className="ticket-preview-panel">
              <div className="ticket-preview-header">
                <div>
                  <h3>Vista previa del ticket</h3>
                  <p>Disenado para impresion termica y exportacion en PDF.</p>
                </div>
              </div>

              <div className="ticket-preview-wrapper">
                <div className="ticket-scale">
                  <div className="thermal-ticket">
                    <div className="ticket-cut" />
                    <div dangerouslySetInnerHTML={{ __html: html }} />
                  </div>
                </div>
              </div>
            </article>
          </section>
        </>
      )}

      <style>{`
        @media print {
          body { background: white !important; }
          .app-shell .sidebar, .top-bar, .page-header, .ticket-actions, .ticket-hero-card, .ticket-summary-grid, .ticket-info-panel, .ticket-preview-header { display: none !important; }
          .content, .ticket-page-shell, .ticket-workspace-grid, .ticket-preview-panel, .ticket-preview-wrapper { padding: 0 !important; margin: 0 !important; background: white !important; border: none !important; box-shadow: none !important; }
          .ticket-workspace-grid { display: block !important; }
          .thermal-ticket { box-shadow: none !important; margin: 0 auto !important; width: 80mm !important; max-width: 80mm !important; }
          .ticket-cut { display: none !important; }
        }
      `}</style>
    </div>
  );
}
