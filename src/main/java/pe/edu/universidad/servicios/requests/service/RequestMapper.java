package pe.edu.universidad.servicios.requests.service;

import java.time.Instant;

import pe.edu.universidad.servicios.requests.dto.RequestDtos.PersonResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestDetailResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.RequestSummaryResponse;
import pe.edu.universidad.servicios.requests.dto.RequestDtos.SlaResponse;
import pe.edu.universidad.servicios.requests.repository.RequestActivityRepository.Person;
import pe.edu.universidad.servicios.requests.repository.RequestRow;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;
import pe.edu.universidad.servicios.sla.service.SlaResolutionService;

public final class RequestMapper {

    private RequestMapper() {
    }

    public static RequestSummaryResponse summary(RequestRow r) {
        return new RequestSummaryResponse(Ids.str(r.id()), r.codigo(), Ids.str(r.categoriaId()), Ids.str(r.areaId()),
                Ids.str(r.prioridadId()), r.estadoCodigo(), Times.iso(r.creadoEn()), responsable(r));
    }

    public static RequestDetailResponse detail(RequestRow r, Person ultimo, Instant now) {
        String slaStatus = SlaResolutionService.status(r.esFinal(), r.fechaLimiteSla(), r.fechaAvisoSla(), now);
        return new RequestDetailResponse(Ids.str(r.id()), r.codigo(), Ids.str(r.categoriaId()), r.categoriaNombre(),
                Ids.str(r.areaId()), r.areaNombre(), Ids.str(r.prioridadId()), r.prioridadNombre(), r.estadoCodigo(), r.estadoNombre(),
                Times.iso(r.creadoEn()), new PersonResponse(Ids.str(r.solicitanteId()), r.solicitanteNombre()),
                r.ubicacion(), r.descripcion(), r.motivoRechazo(), responsable(r),
                ultimo == null ? null : new PersonResponse(Ids.str(ultimo.id()), ultimo.nombreCompleto()),
                new SlaResponse(slaStatus, Times.iso(r.fechaAvisoSla()), Times.iso(r.fechaLimiteSla())),
                Times.iso(r.resueltaEn()), Times.iso(r.cerradaEn()));
    }

    private static PersonResponse responsable(RequestRow r) {
        return r.responsableId() == null ? null : new PersonResponse(Ids.str(r.responsableId()), r.responsableNombre());
    }
}
