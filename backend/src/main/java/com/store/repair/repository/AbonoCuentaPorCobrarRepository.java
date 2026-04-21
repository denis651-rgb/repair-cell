package com.store.repair.repository;

import com.store.repair.domain.AbonoCuentaPorCobrar;
import com.store.repair.dto.ClienteMontoAcumuladoDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AbonoCuentaPorCobrarRepository extends JpaRepository<AbonoCuentaPorCobrar, Long> {

    @Query("""
            select new com.store.repair.dto.ClienteMontoAcumuladoDto(
                c.id,
                c.nombreCompleto,
                coalesce(sum(a.monto), 0)
            )
            from AbonoCuentaPorCobrar a
            join a.cuentaPorCobrar cxc
            join cxc.cliente c
            group by c.id, c.nombreCompleto
            """)
    java.util.List<ClienteMontoAcumuladoDto> sumarAbonosPorCliente();
}
