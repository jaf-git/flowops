package com.flowops.chat.application.assignwork;

import com.flowops.chat.application.shared.exception.ConversationNotDirectException;
import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.AppendChatEventPort;
import com.flowops.chat.application.shared.port.AssignableCheckPort;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.CreateTaskPort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.application.shared.port.ResolveWorkTemplatePort;
import com.flowops.chat.application.shared.port.StartRunPort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import com.flowops.chat.domain.model.WorkMark;
import com.flowops.chat.domain.model.WorkSubject;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignWorkInConversationService implements AssignWorkInConversationUseCase {
    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final ChatDirectoryPort directory;
    private final CreateTaskPort tasks;
    private final ResolveWorkTemplatePort workTemplates;
    private final AssignableCheckPort assignable;
    private final StartRunPort runs;
    private final AppendChatEventPort events;
    private final Clock clock;

    public AssignWorkInConversationService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            ChatDirectoryPort directory,
            CreateTaskPort tasks,
            ResolveWorkTemplatePort workTemplates,
            AssignableCheckPort assignable,
            StartRunPort runs,
            AppendChatEventPort events,
            Clock clock) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.directory = directory;
        this.tasks = tasks;
        this.workTemplates = workTemplates;
        this.assignable = assignable;
        this.runs = runs;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AssignmentContext contextFor(UUID conversationId) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);

        Optional<ChatDirectoryPort.Person> other =
                conversation.counterpartOf(me).flatMap(person -> directory.describe(person.value()));

        StartRunPort.StartableTemplates startable =
                other.isPresent() ? runs.startableTemplates() : new StartRunPort.StartableTemplates(false, List.of());

        return new AssignmentContext(
                other.map(person -> new Counterpart(person.userId(), person.displayName(), person.active())),
                other.map(person -> assignable.mayAssign(person.userId())).orElse(false),
                startable.permitted(),
                startable.templates().stream()
                        .map(template -> new StartableTemplate(
                                template.id(), template.name(), template.overview(), template.stepCount()))
                        .toList());
    }

    @Override
    @Transactional
    public UUID assignTask(UUID conversationId, TaskDraft draft) {
        return creating(
                conversationId,
                counterpart -> tasks.createAdHoc(
                        draft.title(),
                        draft.description(),
                        counterpart.value(),
                        draft.deadline(),
                        draft.priority(),
                        workTemplates.resolve(draft.title(), draft.description(), me().value())),
                WorkSubject::task);
    }

    @Override
    @Transactional
    public UUID startRun(UUID conversationId, UUID templateId) {
        return creating(
                conversationId, counterpart -> runs.startRun(templateId, counterpart.value()), WorkSubject::run);
    }

    private UUID creating(UUID conversationId, CreateWork create, Subject subjectOf) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        PersonId counterpart = conversation.counterpartOf(me).orElseThrow(ConversationNotDirectException::new);

        UUID created = create.forThe(counterpart);

        WorkMark mark = WorkMark.recorded(
                MessageId.of(UUID.randomUUID()), conversation.id(), me, subjectOf.of(created), clock.instant());
        messages.appendMark(mark);
        events.workAssigned(conversation.id(), mark.id(), me);
        return created;
    }

    @FunctionalInterface
    private interface CreateWork {
        UUID forThe(PersonId counterpart);
    }

    @FunctionalInterface
    private interface Subject {
        WorkSubject of(UUID created);
    }

    private PersonId me() {
        return PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
    }

    private Conversation participatedBy(UUID conversationId, PersonId me) {
        return conversations
                .findParticipatedBy(ConversationId.of(conversationId), me)
                .orElseThrow(ConversationNotFoundException::new);
    }
}
