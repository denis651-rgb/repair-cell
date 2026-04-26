package com.store.repair.config;

import com.store.repair.domain.Permiso;
import com.store.repair.domain.Rol;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    private static final EnumSet<Permiso> ALL_PERMISSIONS = EnumSet.allOf(Permiso.class);
    private static final Map<Rol, Set<Permiso>> ROLE_PERMISSION_MAP = new EnumMap<>(Rol.class);

    static {
        ROLE_PERMISSION_MAP.put(Rol.ADMIN, EnumSet.copyOf(ALL_PERMISSIONS));
        ROLE_PERMISSION_MAP.put(Rol.TIENDA, EnumSet.complementOf(EnumSet.of(
                Permiso.DASHBOARD_VIEW,
                Permiso.CONTABILIDAD_VIEW,
                Permiso.REPORTES_VIEW,
                Permiso.BACKUPS_VIEW
        )));

        EnumSet<Permiso> defaultOperationalPermissions = EnumSet.of(
                Permiso.CLIENTES_VIEW,
                Permiso.DISPOSITIVOS_VIEW,
                Permiso.REPARACIONES_VIEW
        );
        ROLE_PERMISSION_MAP.put(Rol.TECNICO, EnumSet.copyOf(defaultOperationalPermissions));
        ROLE_PERMISSION_MAP.put(Rol.CAJERO, EnumSet.of(
                Permiso.CLIENTES_VIEW,
                Permiso.VENTAS_VIEW,
                Permiso.CUENTAS_POR_COBRAR_VIEW
        ));
    }

    private RolePermissions() {
    }

    public static Set<Permiso> permissionsFor(Rol rol) {
        if (rol == null) {
            return EnumSet.noneOf(Permiso.class);
        }
        return EnumSet.copyOf(ROLE_PERMISSION_MAP.getOrDefault(rol, EnumSet.noneOf(Permiso.class)));
    }

    public static Set<String> authoritiesFor(Rol rol) {
        EnumSet<Permiso> permissions = EnumSet.noneOf(Permiso.class);
        permissions.addAll(permissionsFor(rol));
        return permissions.stream()
                .map(Permiso::authority)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
