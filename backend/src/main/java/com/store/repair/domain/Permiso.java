package com.store.repair.domain;

public enum Permiso {
    DASHBOARD_VIEW,
    CLIENTES_VIEW,
    DISPOSITIVOS_VIEW,
    REPARACIONES_VIEW,
    INVENTARIO_VIEW,
    PROVEEDORES_VIEW,
    COMPRAS_VIEW,
    VENTAS_VIEW,
    CUENTAS_POR_COBRAR_VIEW,
    CONTABILIDAD_VIEW,
    REPORTES_VIEW,
    BACKUPS_VIEW;

    public String authority() {
        return "PERM_" + name();
    }
}
