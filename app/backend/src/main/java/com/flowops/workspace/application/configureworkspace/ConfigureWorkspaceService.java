package com.flowops.workspace.application.configureworkspace;

import com.flowops.workspace.application.shared.exception.NotAuthenticatedException;
import com.flowops.workspace.application.shared.exception.UnknownSettingValueException;
import com.flowops.workspace.application.shared.port.AppendWorkspaceEventPort;
import com.flowops.workspace.application.shared.port.IdentifyCallerPort;
import com.flowops.workspace.application.shared.port.LoadWorkspacePort;
import com.flowops.workspace.application.shared.port.LoadWorkspaceSettingsPort;
import com.flowops.workspace.application.shared.port.SaveSettingsChangePort;
import com.flowops.workspace.application.shared.port.SaveWorkspacePort;
import com.flowops.workspace.application.shared.port.SaveWorkspaceSettingsPort;
import com.flowops.workspace.domain.enums.WorkspaceUse;
import com.flowops.workspace.domain.event.WorkspaceEvent;
import com.flowops.workspace.domain.model.AnalysisThresholds;
import com.flowops.workspace.domain.model.EscalationLadder;
import com.flowops.workspace.domain.model.PersonId;
import com.flowops.workspace.domain.model.QuietHours;
import com.flowops.workspace.domain.model.Timezone;
import com.flowops.workspace.domain.model.WorkingWeek;
import com.flowops.workspace.domain.model.Workspace;
import com.flowops.workspace.domain.model.WorkspaceName;
import com.flowops.workspace.domain.model.WorkspaceSettings;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConfigureWorkspaceService implements ConfigureWorkspaceUseCase, ViewWorkspaceSettingsUseCase {
    private final IdentifyCallerPort identifyCallerPort;
    private final LoadWorkspacePort loadWorkspacePort;
    private final SaveWorkspacePort saveWorkspacePort;
    private final LoadWorkspaceSettingsPort loadWorkspaceSettingsPort;
    private final SaveWorkspaceSettingsPort saveWorkspaceSettingsPort;
    private final SaveSettingsChangePort saveSettingsChangePort;
    private final AppendWorkspaceEventPort appendWorkspaceEventPort;
    private final Clock clock;

    public ConfigureWorkspaceService(
            IdentifyCallerPort identifyCallerPort,
            LoadWorkspacePort loadWorkspacePort,
            SaveWorkspacePort saveWorkspacePort,
            LoadWorkspaceSettingsPort loadWorkspaceSettingsPort,
            SaveWorkspaceSettingsPort saveWorkspaceSettingsPort,
            SaveSettingsChangePort saveSettingsChangePort,
            AppendWorkspaceEventPort appendWorkspaceEventPort,
            Clock clock) {
        this.identifyCallerPort = identifyCallerPort;
        this.loadWorkspacePort = loadWorkspacePort;
        this.saveWorkspacePort = saveWorkspacePort;
        this.loadWorkspaceSettingsPort = loadWorkspaceSettingsPort;
        this.saveWorkspaceSettingsPort = saveWorkspaceSettingsPort;
        this.saveSettingsChangePort = saveSettingsChangePort;
        this.appendWorkspaceEventPort = appendWorkspaceEventPort;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceSettingsView execute() {
        identifyCallerPort.currentCaller().orElseThrow(NotAuthenticatedException::new);
        Workspace workspace = loadWorkspacePort.load();
        return view(workspace, inForce(workspace), List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public int atRiskWindowHours() {
        return inForce(loadWorkspacePort.load()).atRiskWindowHours();
    }

    @Override
    @Transactional(readOnly = true)
    public AnalysisThresholdsView analysisThresholds() {
        AnalysisThresholds thresholds = inForce(loadWorkspacePort.load()).analysisThresholds();
        return new AnalysisThresholdsView(
                thresholds.closureCoverageThresholdPercent(), thresholds.templateIdleWindowDays());
    }

    @Override
    @Transactional
    public WorkspaceSettingsView execute(ConfigureWorkspaceCommand command) {
        Instant now = clock.instant();
        PersonId caller = identifyCallerPort
                .currentCaller()
                .orElseThrow(NotAuthenticatedException::new)
                .id();

        Workspace workspace = loadWorkspacePort.load();
        WorkspaceSettings current = inForce(workspace);

        Timezone timezone = new Timezone(command.timezone());
        WorkingWeek week = new WorkingWeek(
                daysFrom(command.workingDays()), command.workingHoursStart(), command.workingHoursEnd());
        EscalationLadder ladder = new EscalationLadder(command.escalationIntervalsHours());
        QuietHours quiet = new QuietHours(command.quietHoursStart(), command.quietHoursEnd());

        AnalysisThresholds thresholds = new AnalysisThresholds(
                command.closureCoverageThresholdPercent() == null
                        ? current.analysisThresholds().closureCoverageThresholdPercent()
                        : command.closureCoverageThresholdPercent(),
                command.templateIdleWindowDays() == null
                        ? current.analysisThresholds().templateIdleWindowDays()
                        : command.templateIdleWindowDays());

        List<SaveSettingsChangePort.FieldChange> moved =
                whatMoved(workspace, current, command, timezone, week, ladder, quiet, thresholds);

        if (moved.isEmpty()) {
            return view(workspace, current, List.of());
        }

        Workspace renamed = workspace.named(new WorkspaceName(command.name()), useFrom(command.use()));
        saveWorkspacePort.save(renamed);

        WorkspaceSettings superseding = current.supersededBy(
                timezone,
                week,
                command.atRiskWindowHours(),
                ladder,
                quiet,
                command.invitationApprovalRequired(),
                thresholds,
                now);
        saveWorkspaceSettingsPort.save(superseding);

        WorkspaceEvent event = WorkspaceEvent.byActor(
                com.flowops.workspace.domain.enums.WorkspaceAction.SETTINGS_CHANGED, caller, workspace.id(), now);
        appendWorkspaceEventPort.append(event);

        saveSettingsChangePort.record(event.id(), workspace.id(), moved);

        return view(
                renamed,
                superseding,
                moved.stream().map(SaveSettingsChangePort.FieldChange::field).toList());
    }

    private WorkspaceSettings inForce(Workspace workspace) {
        return loadWorkspaceSettingsPort
                .inForce(workspace.id())
                .orElseGet(() -> WorkspaceSettings.shippingDefaults(
                        workspace.id(), new Timezone("Europe/Bucharest"), clock.instant()));
    }

    private List<SaveSettingsChangePort.FieldChange> whatMoved(
            Workspace workspace,
            WorkspaceSettings current,
            ConfigureWorkspaceCommand command,
            Timezone timezone,
            WorkingWeek week,
            EscalationLadder ladder,
            QuietHours quiet,
            AnalysisThresholds thresholds) {
        List<SaveSettingsChangePort.FieldChange> moved = new ArrayList<>();
        compare(moved, "name", workspace.name().map(WorkspaceName::value).orElse(null), command.name());
        compare(
                moved,
                "use",
                workspace.use().map(Enum::name).orElse(null),
                useFrom(command.use()).name());
        compare(moved, "timezone", current.timezone().value(), timezone.value());
        compare(moved, "workingDays", current.workingWeek().mask(), week.mask());
        compare(
                moved,
                "workingHoursStart",
                current.workingWeek().start().toString(),
                week.start().toString());
        compare(
                moved,
                "workingHoursEnd",
                current.workingWeek().end().toString(),
                week.end().toString());
        compare(
                moved,
                "atRiskWindowHours",
                String.valueOf(current.atRiskWindowHours()),
                String.valueOf(command.atRiskWindowHours()));
        compare(moved, "escalationIntervalsHours", current.escalationLadder().stored(), ladder.stored());
        compare(
                moved,
                "quietHoursStart",
                current.quietHours().start().toString(),
                quiet.start().toString());
        compare(
                moved,
                "quietHoursEnd",
                current.quietHours().end().toString(),
                quiet.end().toString());
        compare(
                moved,
                "invitationApprovalRequired",
                String.valueOf(current.invitationApprovalRequired()),
                String.valueOf(command.invitationApprovalRequired()));
        compare(
                moved,
                "closureCoverageThresholdPercent",
                String.valueOf(current.analysisThresholds().closureCoverageThresholdPercent()),
                String.valueOf(thresholds.closureCoverageThresholdPercent()));
        compare(
                moved,
                "templateIdleWindowDays",
                String.valueOf(current.analysisThresholds().templateIdleWindowDays()),
                String.valueOf(thresholds.templateIdleWindowDays()));
        return moved;
    }

    private void compare(List<SaveSettingsChangePort.FieldChange> moved, String field, String before, String after) {
        if (!java.util.Objects.equals(before, after)) {
            moved.add(new SaveSettingsChangePort.FieldChange(field, before, after));
        }
    }

    private WorkspaceSettingsView view(Workspace workspace, WorkspaceSettings settings, List<String> changed) {
        return new WorkspaceSettingsView(
                workspace.name().map(WorkspaceName::value).orElse(null),
                workspace.use().map(Enum::name).orElse(null),
                settings.timezone().value(),
                settings.workingWeek().days().stream()
                        .sorted()
                        .map(DayOfWeek::name)
                        .toList(),
                settings.workingWeek().start(),
                settings.workingWeek().end(),
                settings.atRiskWindowHours(),
                settings.escalationLadder().hours(),
                settings.quietHours().start(),
                settings.quietHours().end(),
                settings.invitationApprovalRequired(),
                settings.analysisThresholds().closureCoverageThresholdPercent(),
                settings.analysisThresholds().templateIdleWindowDays(),
                settings.effectiveFrom(),
                changed);
    }

    private Set<DayOfWeek> daysFrom(List<String> names) {
        EnumSet<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (names != null) {
            names.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(ConfigureWorkspaceService::dayNamed)
                    .forEach(days::add);
        }
        return days;
    }

    private static DayOfWeek dayNamed(String name) {
        try {
            return DayOfWeek.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notADay) {
            throw new UnknownSettingValueException("workingDays");
        }
    }

    private WorkspaceUse useFrom(String use) {
        try {
            return WorkspaceUse.valueOf(use.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notAUse) {
            throw new UnknownSettingValueException("use");
        }
    }
}
