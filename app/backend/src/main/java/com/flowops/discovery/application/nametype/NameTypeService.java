package com.flowops.discovery.application.nametype;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.TypeNameTakenException;
import com.flowops.discovery.application.shared.exception.UnknownTrackTypeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.TypeCataloguePort;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.TrackType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NameTypeService implements NameTypeUseCase {
    private final IdentifyCallerPort caller;
    private final TypeCataloguePort catalogue;

    public NameTypeService(IdentifyCallerPort caller, TypeCataloguePort catalogue) {
        this.caller = caller;
        this.catalogue = catalogue;
    }

    @Override
    @Transactional
    public void name(UUID typeId, String name) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        TrackType type = theTypeOrRefuse(typeId);

        catalogue.findByName(name).ifPresent(taken -> {
            throw new TypeNameTakenException("a type of work already goes by " + name);
        });

        if (type.status() != TrackTypeStatus.PROPOSED) {
            throw new UnknownTrackTypeException("type " + typeId + " is " + type.status() + " and is not a proposal");
        }

        type.named(name);
        catalogue.save(type);
    }

    @Override
    @Transactional
    public void dismiss(UUID typeId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);
        TrackType type = theTypeOrRefuse(typeId);

        if (type.status() == TrackTypeStatus.REJECTED) {
            return;
        }
        if (type.status() != TrackTypeStatus.PROPOSED) {
            throw new UnknownTrackTypeException("type " + typeId + " is " + type.status() + " and is not a proposal");
        }

        type.rejected();
        catalogue.save(type);
    }

    private TrackType theTypeOrRefuse(UUID typeId) {
        return catalogue.findType(typeId).orElseThrow(() -> new UnknownTrackTypeException("no type of work " + typeId));
    }
}
