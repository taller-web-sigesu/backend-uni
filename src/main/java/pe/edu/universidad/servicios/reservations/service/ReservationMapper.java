package pe.edu.universidad.servicios.reservations.service;

import java.time.Instant;

import pe.edu.universidad.servicios.reservations.domain.ReservationTimePolicy;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationDetailResponse;
import pe.edu.universidad.servicios.reservations.dto.ReservationDtos.ReservationSummaryResponse;
import pe.edu.universidad.servicios.reservations.repository.ReservationRepository.ReservationRow;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.shared.web.Times;

final class ReservationMapper {

    private ReservationMapper() {
    }

    static ReservationSummaryResponse summary(ReservationRow r, Instant now) {
        return new ReservationSummaryResponse(Ids.str(r.id()), r.codigo(), Ids.str(r.espacioId()), r.espacioNombre(),
                r.fecha().toString(), Times.hhmm(r.horaInicio()), Times.hhmm(r.horaFin()), r.estado(),
                ReservationTimePolicy.operationalState(r.estado(), r.fecha(), r.horaInicio(), r.horaFin(), now),
                r.solicitanteNombre());
    }

    static ReservationDetailResponse detail(ReservationRow r, Instant now) {
        return new ReservationDetailResponse(Ids.str(r.id()), r.codigo(), Ids.str(r.espacioId()), r.espacioNombre(),
                r.fecha().toString(), Times.hhmm(r.horaInicio()), Times.hhmm(r.horaFin()), r.estado(),
                ReservationTimePolicy.operationalState(r.estado(), r.fecha(), r.horaInicio(), r.horaFin(), now),
                Ids.str(r.solicitanteId()), r.solicitanteNombre(), r.motivo(), r.numAsistentes(), r.motivoRechazo(),
                Times.iso(r.creadoEn()));
    }
}
