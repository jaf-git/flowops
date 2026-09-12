package com.flowops.workspace.infrastructure.auth;

import com.flowops.auth.application.completesetup.CompleteSetupCommand;
import com.flowops.auth.application.completesetup.CompleteSetupUseCase;
import com.flowops.auth.application.setdisplayname.SetDisplayNameCommand;
import com.flowops.auth.application.setdisplayname.SetDisplayNameUseCase;
import com.flowops.auth.application.shared.ClientContext;
import com.flowops.auth.application.viewsetupstate.SetupState;
import com.flowops.auth.application.viewsetupstate.ViewSetupStateUseCase;
import com.flowops.auth.domain.exception.DisplayNameRequiredException;
import com.flowops.workspace.application.shared.exception.OwnerNameRequiredException;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.MarkSetupCompletePort;
import com.flowops.workspace.application.shared.port.SetDisplayNamePort;
import com.flowops.workspace.domain.model.PersonId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class AuthAccountAdapter implements IdentifyCallerPort, MarkSetupCompletePort, SetDisplayNamePort {
    private final ViewSetupStateUseCase viewSetupStateUseCase;
    private final CompleteSetupUseCase completeSetupUseCase;
    private final SetDisplayNameUseCase setDisplayNameUseCase;

    public AuthAccountAdapter(
            ViewSetupStateUseCase viewSetupStateUseCase,
            CompleteSetupUseCase completeSetupUseCase,
            SetDisplayNameUseCase setDisplayNameUseCase) {
        this.viewSetupStateUseCase = viewSetupStateUseCase;
        this.completeSetupUseCase = completeSetupUseCase;
        this.setDisplayNameUseCase = setDisplayNameUseCase;
    }

    @Override
    public Optional<Caller> currentCaller() {
        return viewSetupStateUseCase
                .execute()
                .map(state -> new Caller(
                        PersonId.of(state.userId()), state.ownsSetup(), state.completed(), state.landingTarget()));
    }

    @Override
    public String markCallerSetupComplete() {
        completeSetupUseCase.execute(new CompleteSetupCommand(currentClientContext()));
        return viewSetupStateUseCase
                .execute()
                .map(SetupState::landingTarget)
                .orElseThrow(() -> new IllegalStateException("the caller's session ended mid-setup"));
    }

    @Override
    public void setCallerDisplayName(String displayName) {
        try {
            setDisplayNameUseCase.execute(new SetDisplayNameCommand(displayName));
        } catch (DisplayNameRequiredException refused) {
            throw new OwnerNameRequiredException(refused);
        }
    }

    private ClientContext currentClientContext() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletRequest request = attributes.getRequest();
            return new ClientContext(request.getRemoteAddr(), request.getHeader("User-Agent"));
        }
        return new ClientContext(null, null);
    }
}
