package com.flowops.workspace.application.viewsetupprefill;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.SetupNotPermittedException;
import com.flowops.workspace.application.shared.port.DetectServerTimezonePort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.domain.model.Timezone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewSetupPrefillService implements ViewSetupPrefillUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final DetectServerTimezonePort detectServerTimezonePort;

    public ViewSetupPrefillService(
            IdentifyCallerPort identifyCallerPort, DetectServerTimezonePort detectServerTimezonePort) {
        this.identifyCallerPort = identifyCallerPort;
        this.detectServerTimezonePort = detectServerTimezonePort;
    }

    @Override
    @Transactional(readOnly = true)
    public ViewSetupPrefillResult execute() {
        IdentifyCallerPort.Caller caller =
                identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);

        if (!caller.ownsWorkspaceSetup()) {
            throw new SetupNotPermittedException();
        }

        return new ViewSetupPrefillResult(caller.setupCompleted(), detectServerTimezonePort.detect(), Timezone.known());
    }
}
