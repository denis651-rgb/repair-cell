package com.store.repair.controller;

import com.store.repair.domain.OrdenReparacion;
import com.store.repair.domain.ParteOrdenReparacion;
import com.store.repair.dto.TicketWhatsappResponse;
import com.store.repair.service.OrdenReparacionService;
import com.store.repair.util.OrdenMontoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_REPARACIONES_VIEW')")
public class TicketController {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String STORE_NAME = "YiyoTech";
    private static final String STORE_TAGLINE = "Servicio tecnico especializado en celulares";
    private static final String STORE_CONTACT = "Yapacani - Mercado Agricola | Tel: 73610258";

    private final OrdenReparacionService ordenReparacionService;

    @GetMapping(value = "/orden-reparacion/{id}/html", produces = MediaType.TEXT_HTML_VALUE)
    public String getTicketHtml(@PathVariable Long id) {
        OrdenReparacion orden = ordenReparacionService.findById(id);
        String partesHtml = buildPartesHtml(orden);
        String condicionesHtml = buildCondicionesHtml(orden);
        String seguimientoHtml = buildSeguimientoHtml(orden);

        return """
                <html>
                <head>
                    <meta charset='UTF-8'/>
                    <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                    <title>Ticket %s</title>
                    <style>
                        * { box-sizing: border-box; }
                        body {
                            margin: 0;
                            background: #ffffff;
                            color: #111827;
                            font-family: "Segoe UI", Tahoma, sans-serif;
                        }
                        .ticket-content {
                            margin: 0;
                            padding: 18px 16px 24px;
                            width: 302px;
                            font-size: 12px;
                            line-height: 1.45;
                            background: #fff;
                        }
                        .header {
                            text-align: center;
                            padding-bottom: 12px;
                            border-bottom: 2px solid #111827;
                        }
                        .header h1 {
                            margin: 0;
                            font-size: 22px;
                            letter-spacing: 0.1em;
                            text-transform: uppercase;
                        }
                        .header p {
                            margin: 3px 0 0;
                            color: #4b5563;
                            font-size: 10px;
                        }
                        .ticket-pill {
                            display: inline-block;
                            margin-top: 10px;
                            padding: 5px 10px;
                            border: 1px solid #d1d5db;
                            border-radius: 999px;
                            background: #f3f4f6;
                            font-size: 10px;
                            font-weight: 700;
                            letter-spacing: 0.08em;
                            text-transform: uppercase;
                        }
                        .section {
                            margin-top: 12px;
                            padding-top: 12px;
                            border-top: 1px dashed #9ca3af;
                        }
                        .section h3 {
                            margin: 0 0 8px;
                            font-size: 11px;
                            letter-spacing: 0.08em;
                            text-transform: uppercase;
                        }
                        .row {
                            display: flex;
                            gap: 8px;
                            justify-content: space-between;
                            margin: 4px 0;
                        }
                        .label {
                            font-weight: 700;
                            color: #111827;
                            min-width: 94px;
                        }
                        .value {
                            flex: 1;
                            text-align: right;
                            color: #1f2937;
                            word-break: break-word;
                        }
                        .note-block {
                            margin: 8px 0 0;
                            padding: 8px 10px;
                            border-radius: 10px;
                            background: #f9fafb;
                            border: 1px solid #e5e7eb;
                            white-space: pre-wrap;
                        }
                        table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin-top: 8px;
                        }
                        th {
                            padding: 6px 0;
                            text-align: left;
                            font-size: 10px;
                            border-bottom: 1px solid #111827;
                            text-transform: uppercase;
                            letter-spacing: 0.06em;
                        }
                        td {
                            padding: 6px 0;
                            vertical-align: top;
                            border-bottom: 1px dashed #e5e7eb;
                        }
                        .item-title {
                            display: block;
                            font-weight: 700;
                            color: #111827;
                        }
                        .item-meta {
                            display: block;
                            color: #6b7280;
                            font-size: 10px;
                            margin-top: 2px;
                        }
                        .totals {
                            margin-top: 12px;
                            padding: 10px 0 0;
                            border-top: 2px solid #111827;
                        }
                        .totals .row {
                            margin: 5px 0;
                        }
                        .totals .grand-total {
                            font-size: 16px;
                            font-weight: 800;
                        }
                        .tracking-box {
                            margin-top: 12px;
                            padding: 10px 12px;
                            border-radius: 12px;
                            background: #eef2ff;
                            border: 1px solid #c7d2fe;
                        }
                        .tracking-box strong {
                            display: block;
                            margin-bottom: 4px;
                            font-size: 11px;
                            text-transform: uppercase;
                            letter-spacing: 0.06em;
                        }
                        .tracking-box p {
                            margin: 0;
                            color: #312e81;
                            font-size: 10px;
                        }
                        .warranty {
                            margin-top: 12px;
                            padding: 10px 12px;
                            border: 1px solid #e5e7eb;
                            border-radius: 12px;
                            background: #fafafa;
                        }
                        .warranty p {
                            margin: 0 0 5px;
                            font-size: 10px;
                        }
                        .footer {
                            margin-top: 16px;
                            text-align: center;
                            font-size: 10px;
                            color: #6b7280;
                        }
                        .signatures {
                            display: flex;
                            gap: 12px;
                            margin-top: 18px;
                        }
                        .signature-box {
                            flex: 1;
                            padding-top: 20px;
                            border-top: 1px solid #111827;
                            text-align: center;
                            font-size: 9px;
                        }
                    </style>
                </head>
                <body>
                    <div class='ticket-content'>
                        <div class='header'>
                            <h1>%s</h1>
                            <p>%s</p>
                            <p>%s</p>
                            <span class='ticket-pill'>Orden %s</span>
                        </div>

                        <div class='section'>
                            <h3>Recepcion</h3>
                            <div class='row'><span class='label'>Cliente:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Telefono:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Recibido:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Tecnico:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Estado:</span> <span class='value'>%s</span></div>
                        </div>

                        <div class='section'>
                            <h3>Equipo</h3>
                            <div class='row'><span class='label'>Equipo:</span> <span class='value'>%s %s</span></div>
                            <div class='row'><span class='label'>IMEI/Serie:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Entrega estimada:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Firma cliente:</span> <span class='value'>%s</span></div>
                            <div class='note-block'><strong>Falla reportada</strong><br/>%s</div>
                            %s
                        </div>

                        <div class='section'>
                            <h3>Detalle de servicio</h3>
                            <table>
                                <thead>
                                    <tr><th>Servicio / Repuesto</th><th style='text-align: right;'>Importe</th></tr>
                                </thead>
                                <tbody>
                                    %s
                                </tbody>
                            </table>
                        </div>

                        <div class='totals'>
                            <div class='row'><span class='label'>Estimado:</span> <span class='value'>%s</span></div>
                            <div class='row'><span class='label'>Anticipo:</span> <span class='value'>%s</span></div>
                            <div class='row grand-total'><span class='label'>Total:</span> <span class='value'>%s</span></div>
                        </div>

                        %s

                        <div class='warranty'>
                            <p><strong>Garantia:</strong> %d dias sobre el trabajo realizado.</p>
                            %s
                        </div>

                        <div class='footer'>
                            <p>Gracias por confiar en %s.</p>
                            <div class='signatures'>
                                <div class='signature-box'>Firma del cliente</div>
                                <div class='signature-box'>Recibido por</div>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """
                .formatted(
                        escape(orden.getNumeroOrden()),
                        STORE_NAME,
                        STORE_TAGLINE,
                        STORE_CONTACT,
                        escape(orden.getNumeroOrden()),
                        escape(orDefault(orden.getCliente().getNombreCompleto(), "Cliente general")),
                        escape(orDefault(orden.getCliente().getTelefono(), "Sin telefono")),
                        escape(formatDateTime(orden.getRecibidoEn())),
                        escape(orDefault(orden.getTecnicoResponsable(), "Sin asignar")),
                        escape(formatEstado(orden)),
                        escape(orDefault(orden.getDispositivo().getMarca(), "")),
                        escape(orDefault(orden.getDispositivo().getModelo(), "")),
                        escape(orDefault(orden.getDispositivo().getImeiSerie(), "N/D")),
                        escape(formatDate(orden.getFechaEntregaEstimada())),
                        escape(orDefault(orden.getNombreFirmaCliente(), orden.getCliente().getNombreCompleto())),
                        escapeMultiline(orDefault(orden.getProblemaReportado(), "Sin detalle registrado")),
                        buildDiagnosticoHtml(orden),
                        partesHtml,
                        formatMoney(orden.getCostoEstimado()),
                        formatMoney(0D),
                        formatMoney(resolveMontoPrincipal(orden)),
                        seguimientoHtml,
                        orden.getDiasGarantia() == null ? 0 : orden.getDiasGarantia(),
                        condicionesHtml,
                        STORE_NAME);
    }

    @GetMapping("/orden-reparacion/{id}/whatsapp")
    public TicketWhatsappResponse getTicketWhatsapp(@PathVariable Long id) {
        OrdenReparacion orden = ordenReparacionService.findById(id);
        String telefono = orDefault(orden.getCliente().getTelefono(), "");

        return TicketWhatsappResponse.builder()
                .numeroOrden(orden.getNumeroOrden())
                .telefono(telefono)
                .telefonoNormalizado(normalizePhone(telefono))
                .mensaje(buildWhatsappMessage(orden))
                .build();
    }

    private String buildPartesHtml(OrdenReparacion orden) {
        if (orden.getPartes() == null || orden.getPartes().isEmpty()) {
            return """
                    <tr>
                        <td>
                            <span class='item-title'>Servicio tecnico general</span>
                            <span class='item-meta'>Sin repuestos registrados</span>
                        </td>
                        <td style='text-align: right;'>%s</td>
                    </tr>
                    """.formatted(escape(formatMoney(resolveMontoPrincipal(orden))));
        }

        return orden.getPartes().stream()
                .map(this::buildParteRow)
                .collect(Collectors.joining());
    }

    private String buildParteRow(ParteOrdenReparacion parte) {
        double subtotal = OrdenMontoUtils.resolveParteSubtotal(parte);

        String meta = "Cant. " + (parte.getCantidad() == null ? 0 : parte.getCantidad())
                + " | " + formatTipoFuente(parte)
                + (parte.getNotas() == null || parte.getNotas().isBlank() ? "" : " | " + parte.getNotas().trim());

        return """
                <tr>
                    <td>
                        <span class='item-title'>%s</span>
                        <span class='item-meta'>%s</span>
                    </td>
                    <td style='text-align: right;'>%s</td>
                </tr>
                """.formatted(
                escape(orDefault(parte.getNombreParte(), "Repuesto")),
                escape(meta),
                escape(formatMoney(subtotal)));
    }

    private String buildDiagnosticoHtml(OrdenReparacion orden) {
        if (orden.getDiagnosticoTecnico() == null || orden.getDiagnosticoTecnico().isBlank()) {
            return "";
        }

        return """
                <div class='note-block'><strong>Diagnostico tecnico</strong><br/>%s</div>
                """.formatted(escapeMultiline(orden.getDiagnosticoTecnico()));
    }

    private String buildSeguimientoHtml(OrdenReparacion orden) {
        return """
                <div class='tracking-box'>
                    <strong>Seguimiento</strong>
                    <p>Conserva este numero para consultar el estado: %s</p>
                </div>
                """.formatted(escape(orDefault(orden.getNumeroOrden(), "Sin codigo")));
    }

    private String buildCondicionesHtml(OrdenReparacion orden) {
        String retiro = orden.getEntregadoEn() == null
                ? "Retira tu equipo en cuanto la orden figure como LISTO o ENTREGADO."
                : "Equipo entregado el " + formatDateTime(orden.getEntregadoEn()) + ".";

        return """
                <p>Equipos mojados, golpeados o intervenidos previamente pueden perder garantia.</p>
                <p>%s</p>
                <p>Luego de 90 dias sin retiro, el equipo puede pasar a resguardo para cubrir costos operativos.</p>
                """.formatted(escape(retiro));
    }

    private String buildWhatsappMessage(OrdenReparacion orden) {
        String partes = orden.getPartes() == null || orden.getPartes().isEmpty()
                ? "- Servicio tecnico general"
                : orden.getPartes().stream()
                        .map(parte -> "- " + orDefault(parte.getNombreParte(), "Repuesto")
                                + " x" + (parte.getCantidad() == null ? 0 : parte.getCantidad())
                                + " - " + formatMoney(OrdenMontoUtils.resolveParteSubtotal(parte)))
                        .collect(Collectors.joining("\n"));

        String diagnostico = orden.getDiagnosticoTecnico() == null || orden.getDiagnosticoTecnico().isBlank()
                ? "Pendiente de diagnostico tecnico."
                : orden.getDiagnosticoTecnico().trim();

        return """
                Hola %s,

                Te compartimos el resumen de tu orden de servicio.

                *Datos de la orden*
                - Nro. orden: %s
                - Estado actual: %s
                - Fecha de recepcion: %s
                - Tecnico responsable: %s

                *Equipo recibido*
                - Dispositivo: %s %s
                - IMEI / Serie: %s
                - Falla reportada: %s
                - Diagnostico: %s

                *Detalle*
                %s

                *Montos*
                - Costo estimado: %s
                - Total actual: %s
                - Garantia: %d dias

                %s

                Gracias por confiar en %s.
                """.formatted(
                orDefault(orden.getCliente().getNombreCompleto(), "cliente"),
                orDefault(orden.getNumeroOrden(), "Sin numero"),
                formatEstado(orden),
                formatDateTime(orden.getRecibidoEn()),
                orDefault(orden.getTecnicoResponsable(), "Sin asignar"),
                orDefault(orden.getDispositivo().getMarca(), ""),
                orDefault(orden.getDispositivo().getModelo(), "").trim(),
                orDefault(orden.getDispositivo().getImeiSerie(), "N/D"),
                sanitizeWhatsappLine(orDefault(orden.getProblemaReportado(), "Sin detalle registrado")),
                sanitizeWhatsappLine(diagnostico),
                partes,
                formatMoney(orden.getCostoEstimado()),
                formatMoney(resolveMontoPrincipal(orden)),
                orden.getDiasGarantia() == null ? 0 : orden.getDiasGarantia(),
                orden.getFechaEntregaEstimada() == null
                        ? "La fecha de entrega estimada sera confirmada por el tecnico."
                        : "Fecha estimada de entrega: " + formatDate(orden.getFechaEntregaEstimada()) + ".",
                STORE_NAME);
    }

    private String normalizePhone(String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() == 8) {
            return "591" + digits;
        }
        return digits;
    }

    private String sanitizeWhatsappLine(String value) {
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private String formatEstado(OrdenReparacion orden) {
        if (orden.getEstado() == null) {
            return "Sin estado";
        }
        return switch (orden.getEstado()) {
            case RECIBIDO -> "Recibido";
            case EN_DIAGNOSTICO -> "En diagnostico";
            case EN_REPARACION -> "En reparacion";
            case LISTO -> "Listo para entrega";
            case ENTREGADO -> "Entregado";
        };
    }

    private String formatTipoFuente(ParteOrdenReparacion parte) {
        if (parte.getTipoFuente() == null) {
            return "Fuente no especificada";
        }
        return switch (parte.getTipoFuente()) {
            case TIENDA -> "Stock tienda";
            case CLIENTE -> "Traido por cliente";
        };
    }

    private String formatMoney(Double value) {
        double safeValue = value == null ? 0D : value;
        return "Bs " + String.format(Locale.US, "%.2f", safeValue);
    }

    private Double resolveMontoPrincipal(OrdenReparacion orden) {
        return OrdenMontoUtils.resolveMontoVisible(orden);
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? "No registrado" : value.format(DATE_TIME_FORMATTER);
    }

    private String formatDate(LocalDate value) {
        return value == null ? "Pendiente" : value.format(DATE_FORMATTER);
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String escapeMultiline(String value) {
        return escape(value).replace("\n", "<br/>");
    }
}
