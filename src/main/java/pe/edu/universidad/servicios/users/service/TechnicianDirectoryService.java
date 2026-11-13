package pe.edu.universidad.servicios.users.service;

import java.util.Optional;

import org.springframework.stereotype.Service;

import pe.edu.universidad.servicios.shared.dto.PageQuery;
import pe.edu.universidad.servicios.shared.dto.PageResponse;
import pe.edu.universidad.servicios.shared.security.RoleCode;
import pe.edu.universidad.servicios.shared.web.Ids;
import pe.edu.universidad.servicios.users.dto.UserDtos.EligibleTechnicianResponse;
import pe.edu.universidad.servicios.users.repository.UserRecord;
import pe.edu.universidad.servicios.users.repository.UserRepository;

/**
 * Técnicos activos de un área, candidatos para asignación.
 */
@Service
public class TechnicianDirectoryService {

    private final UserRepository users;

    public TechnicianDirectoryService(UserRepository users) {
        this.users = users;
    }

    public PageResponse<EligibleTechnicianResponse> eligible(long areaId, PageQuery page) {
        return new PageResponse<>(users.activeTechnicians(areaId, page).stream()
                .map(u -> new EligibleTechnicianResponse(Ids.str(u.id()), u.nombreCompleto(), Ids.str(u.areaId())))
                .toList(), page.page(), page.size(), users.countActiveTechnicians(areaId));
    }

    /** Técnico activo del área indicada, o vacío si no es elegible. */
    public Optional<UserRecord> findEligible(long technicianId, long areaId) {
        return users.findById(technicianId)
                .filter(u -> u.role() == RoleCode.TECNICO && u.canLogin() && Long.valueOf(areaId).equals(u.areaId()));
    }
}
