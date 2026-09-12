package com.flowops.workspace.application.exportowndata;

import com.flowops.workspace.application.shared.exception.ExportLimitReachedException;
import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.port.DataExportPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.viewowndata.ViewOwnDataQuery;
import com.flowops.workspace.application.viewowndata.ViewOwnDataResult;
import com.flowops.workspace.application.viewowndata.ViewOwnDataService;
import com.flowops.workspace.application.viewowndata.ViewOwnDataUseCase;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportOwnDataService implements ExportOwnDataUseCase {
    private final ViewOwnDataUseCase viewOwnData;
    private final IdentifyCallerPort identifyCallerPort;
    private final DataExportPort dataExportPort;
    private final Clock clock;

    public ExportOwnDataService(
            ViewOwnDataUseCase viewOwnData,
            IdentifyCallerPort identifyCallerPort,
            DataExportPort dataExportPort,
            Clock clock) {
        this.viewOwnData = viewOwnData;
        this.identifyCallerPort = identifyCallerPort;
        this.dataExportPort = dataExportPort;
        this.clock = clock;
    }

    @Override
    @Transactional
    public ViewOwnDataResult execute(ViewOwnDataQuery query) {
        Instant now = clock.instant();
        var caller = identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);

        ViewOwnDataResult file = viewOwnData.execute(query);
        if (dataExportPort.countProducedSince(file.person(), now.minus(ViewOwnDataService.EXPORT_WINDOW))
                >= ViewOwnDataService.EXPORT_LIMIT) {
            throw new ExportLimitReachedException();
        }

        dataExportPort.record(file.person(), caller.id(), now);
        return file;
    }
}
