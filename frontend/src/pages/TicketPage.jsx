import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { api } from '../api/api';
import PageHeader from '../components/PageHeader';
import { Printer, Share2, Download, ArrowLeft } from 'lucide-react';

export default function TicketPage() {
  const { id } = useParams();
  const [html, setHtml] = useState('');
  const [order, setOrder] = useState(null);
  const [error, setError] = useState('');

  useEffect(() => {
    api.raw(`/tickets/orden-reparacion/${id}/html`).then(setHtml).catch((err) => setError(err.message));
    api.get(`/ordenes-reparacion/${id}`).then(setOrder).catch((err) => console.error(err));
  }, [id]);

  const shareWhatsApp = () => {
    if (!order) return;

    const phone = order.cliente?.telefono?.replace(/\D/g, '');

    const text = encodeURIComponent(
      `📄 *ORDEN DE SERVICIO*

👤 *Cliente:* ${order.cliente?.nombreCompleto}
📱 *Teléfono:* ${order.cliente?.telefono}

🧾 *Orden:* ${order.numeroOrden}
📅 *Fecha:* ${new Date(order.fechaCreacion).toLocaleString()}

📱 *Equipo:* ${order.dispositivo?.marca} ${order.dispositivo?.modelo}
🔢 *IMEI:* ${order.dispositivo?.imeiSerie || '-'}

⚠️ *Problema:*
${order.problemaReportado}

💰 *Costo estimado:* Bs ${order.costoEstimado}
💰 *Costo final:* Bs ${order.costoFinal}

📍 *Estado:* ${order.estado}

Gracias por confiar en nuestro servicio 🙌`
    );

    window.open(`https://wa.me/${phone}?text=${text}`, '_blank');
  };

  const descargarHtml = () => {
    const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ticket-${order?.numeroOrden || id}.html`;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="ticket-view-container page-stack">
      <PageHeader title="Ticket profesional" subtitle="Vista optimizada para impresión térmica, PDF o envío por WhatsApp.">
        <div className="actions-inline wrap-actions">
          <Link to="/reparaciones"><button className="secondary"><ArrowLeft size={18} /> Volver</button></Link>
          <button onClick={() => window.print()}><Printer size={18} /> Imprimir / PDF</button>
          <button className="secondary" onClick={descargarHtml}><Download size={18} /> Descargar HTML</button>
          <button onClick={shareWhatsApp} style={{ background: '#25D366' }}><Share2 size={18} /> WhatsApp</button>
        </div>
      </PageHeader>

      {error && <div className="alert">{error}</div>}

      <div className="ticket-preview-wrapper">
        <div className="ticket-scale">
          <div className="thermal-ticket">
            <div className="ticket-cut"></div>
            <div dangerouslySetInnerHTML={{ __html: html }} />
          </div>
        </div>
      </div>

      <style>{`
        .thermal-ticket * { max-width: 100%; }
        @media print {
          body { background: white !important; }
          .app-shell .sidebar, .top-bar, .page-header, .actions-inline { display: none !important; }
          .content, .ticket-preview-wrapper { padding: 0 !important; margin: 0 !important; background: white !important; }
          .thermal-ticket { box-shadow: none !important; margin: 0 !important; width: 80mm !important; max-width: 80mm !important; }
          .ticket-cut { display: none !important; }
        }
      `}</style>
    </div>
  );
}
