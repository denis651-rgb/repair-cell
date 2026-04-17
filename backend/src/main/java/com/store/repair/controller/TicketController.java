package com.store.repair.controller;

import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ParteOrdenReparacion;
import com.store.repair.service.OrdenReparacionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final OrdenReparacionService ordenReparacionService;

    @GetMapping(value = "/orden-reparacion/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public String getTicketHtml(@PathVariable Long id) {
        OrdenReparacion orden = ordenReparacionService.findById(id);
        StringBuilder partesHtml = new StringBuilder();

        for (ParteOrdenReparacion parte : orden.getPartes()) {
            partesHtml.append("<tr>")
                    .append("<td style='padding: 4px 0;'>").append(escape(parte.getNombreParte())).append("</td>")
                    .append("<td style='padding: 4px 0; text-align: right;'>Bs ")
                    .append(String.format("%.2f", parte.getPrecioUnitario())).append("</td>")
                    .append("</tr>");
        }

        return """
                <html>
                <head>
                    <meta charset='UTF-8'/>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <title>Ticket %s</title>
                    <style>
                        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;700&display=swap');
                        .ticket-content {
                            font-family: 'Inter', sans-serif;
                            margin: 0;
                            padding: 20px;
                            width: 300px;
                            font-size: 12px;
                            line-height: 1.4;
                            color: #333;
                            background: #fff;
                        }
                        .header { text-align: center; margin-bottom: 15px; border-bottom: 2px solid #000; padding-bottom: 10px; }
                        .header h1 { font-size: 20px; margin: 0; color: #000; letter-spacing: 1px; }
                        .header p { margin: 2px 0; font-size: 10px; color: #666; }
                        .qr-container { text-align: center; margin: 15px 0; }
                        .qr-container img { width: 100px; height: 100px; border: 1px solid #eee; padding: 5px; }
                        .section { margin-bottom: 10px; border-bottom: 1px dashed #ccc; padding-bottom: 8px; }
                        .row { display: flex; justify-content: space-between; margin: 3px 0; }
                        .label { font-weight: bold; color: #000; flex: 1; }
                        .value { flex: 2; text-align: right; }
                        table { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                        th { text-align: left; border-bottom: 1px solid #000; font-size: 11px; padding: 4px 0; }
                        td { padding: 4px 0; font-size: 11px; }
                        .total { margin-top: 10px; text-align: right; font-size: 16px; font-weight: bold; color: #000; padding-top: 5px; border-top: 2px solid #000; }
                        .warranty { background: #f9f9f9; padding: 8px; border-radius: 4px; margin-top: 15px; font-size: 10px; border: 1px solid #eee; }
                        .warranty h4 { margin: 0 0 5px 0; font-size: 11px; text-transform: uppercase; }
                        .footer { text-align: center; margin-top: 20px; font-size: 10px; color: #777; }
                        .firma-area { margin-top: 30px; display: flex; justify-content: space-around; }
                        .firma-box { border-top: 1px solid #000; width: 120px; text-align: center; padding-top: 5px; font-size: 9px; }
                    </style>
                </head>
                <body>
                    <div class='ticket-content'>
                        <div class='header'>
                            <h1>YiyoTech</h1>
                            <p>SERVICIO TÉCNICO ESPECIALIZADO</p>
                            <p>Yapacani-Mercado Agricola - Tel: 73610258</p>
                            <h2 style='font-size: 14px; margin: 10px 0 0 0;'>ORDEN DE SERVICIO: %s</h2>
                        </div>

                        <div class='section'>
                            <div class='row'><span class='label'>Cliente:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Teléfono:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Fecha:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Técnico:</span> <span class='value'>%s</span></div>
                        </div>

                        <div class='section'>
                            <div class='row'><span class='label'>Equipo:</span> <span class='value'>%s %s</span></div>
                            <div class='row'><span class='label'>IMEI/Serie:</span> <span class='value'>%s</span></div>
                            <p style='margin: 8px 0 2px 0;'><span class='label'>Falla Reportada:</span></p>
                            <p style='margin: 0; font-style: italic;'>%s</p>
                        </div>

                        <table>
                            <thead>
                                <tr><th>Servicio/Repuesto</th><th style='text-align: right;'>Total</th></tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>

                        <div class='total'>
                            TOTAL: Bs %s
                        </div>

                        <div class='qr-container'>
                            <img src='https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=%s' alt='QR Status'/>
                            <p style='margin-top: 5px; font-size: 9px;'>Escanea para ver el estado de tu reparación</p>
                        </div>

                        <div class='warranty'>
                            <h4>Garantía y Condiciones</h4>
                            <p>• Esta reparación cuenta con <strong>%d días</strong> de garantía sobre el trabajo realizado.</p>
                            <p>• Equipos mojados o con golpes no tienen garantía.</p>
                            <p>• Si el equipo no es retirado en 90 días, pasa a remate para cubrir costos.</p>
                        </div>

                        <div class='footer'>
                            <p>¡Gracias por confiar en nosotros!</p>
                            <div class='firma-area'>
                                <div class='firma-box'>Firma Cliente</div>
                                <div class='firma-box'>Entregado por</div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        escape(orden.getNumeroOrden()),
                        escape(orden.getNumeroOrden()),
                        escape(orden.getCliente().getNombreCompleto()),
                        escape(orden.getCliente().getTelefono()),
                        orden.getRecibidoEn().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                        escape(orden.getTecnicoResponsable() == null ? "Sin asignar" : orden.getTecnicoResponsable()),
                        escape(orden.getDispositivo().getMarca()),
                        escape(orden.getDispositivo().getModelo()),
                        escape(orden.getDispositivo().getImeiSerie() == null ? "N/A"
                                : orden.getDispositivo().getImeiSerie()),
                        escape(orden.getProblemaReportado()),
                        partesHtml,
                        String.format("%.2f", orden.getCostoFinal()),
                        escape(orden.getNumeroOrden()),
                        orden.getDiasGarantia() != null ? orden.getDiasGarantia() : 30);
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
