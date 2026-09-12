package com.flowops.chat.application.buildprocess;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.AppendChatEventPort;
import com.flowops.chat.application.shared.port.AssignableCheckPort;
import com.flowops.chat.application.shared.port.BuildProcessPort;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BuildProcessFromMessagesService implements BuildProcessFromMessagesUseCase {
    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final AssignableCheckPort assignable;
    private final BuildProcessPort processes;
    private final AppendChatEventPort events;
    private final Clock clock;

    public BuildProcessFromMessagesService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            AssignableCheckPort assignable,
            BuildProcessPort processes,
            AppendChatEventPort events,
            Clock clock) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.assignable = assignable;
        this.processes = processes;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessDraft draftFrom(UUID conversationId, List<UUID> messageIds) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        List<MessageStorePort.Stored> chosen = chosenIn(conversation, messageIds);

        return new DraftFromMessages(clock)
                .assemble(
                        chosen.stream()
                                .map(stored -> new DraftFromMessages.Ticked(
                                        stored.message().id().value(),
                                        stored.message().author().value(),
                                        stored.message().body(),
                                        stored.seq()))
                                .toList(),
                        me.value(),
                        whoIMayDirect(chosen));
    }

    @Override
    @Transactional
    public UUID startRun(UUID conversationId, RunSubmission submission) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        Map<UUID, Message> convertible = convertibleIn(conversation, submission.steps());

        BuildProcessPort.StartedRun run = processes.startRunFromDescriptions(
                submission.name(),
                me.value(),
                submission.steps().stream()
                        .map(step -> new BuildProcessPort.NewStep(
                                step.title(), step.description(), step.assigneeId(), step.deadline(), "NORMAL"))
                        .toList());

        markEach(conversation, convertible, submission.steps(), run.taskIds(), me);
        return run.instanceId();
    }

    @Override
    @Transactional
    public void appendToTemplate(UUID conversationId, TemplateSubmission submission) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        Map<UUID, Message> convertible = convertibleIn(conversation, submission.steps());

        processes.appendStepsToTemplate(
                submission.templateId(),
                submission.steps().stream()
                        .map(step -> new BuildProcessPort.TemplateStep(step.title(), step.description()))
                        .toList());

        convertible.keySet().forEach(message -> events.messageConverted(conversation.id(), MessageId.of(message), me));
    }

    private void markEach(
            Conversation conversation,
            Map<UUID, Message> convertible,
            List<SubmittedStep> steps,
            List<UUID> taskIds,
            PersonId me) {
        if (taskIds.size() != steps.size()) {
            throw new IllegalStateException(
                    "a run built from %d steps came back with %d tasks".formatted(steps.size(), taskIds.size()));
        }

        for (int at = 0; at < steps.size(); at++) {
            Message message = convertible.get(steps.get(at).messageId());
            if (message == null) {
                continue;
            }

            message.becameTask(taskIds.get(at));
            messages.update(message);
            events.messageConverted(conversation.id(), message.id(), me);
        }
    }

    private List<MessageStorePort.Stored> chosenIn(Conversation conversation, List<UUID> messageIds) {
        List<MessageStorePort.Stored> found = messageIds.stream()
                .distinct()
                .map(id -> messages.find(MessageId.of(id))
                        .filter(stored -> stored.message().conversation().equals(conversation.id()))
                        .orElseThrow(ConversationNotFoundException::new))
                .toList();
        return found;
    }

    private Map<UUID, Message> convertibleIn(Conversation conversation, List<SubmittedStep> steps) {
        List<MessageStorePort.Stored> chosen = chosenIn(
                conversation, steps.stream().map(SubmittedStep::messageId).toList());

        Map<UUID, Message> byId = new LinkedHashMap<>();
        for (MessageStorePort.Stored stored : chosen) {
            stored.message().requireConvertible();
            byId.put(stored.message().id().value(), stored.message());
        }
        return byId;
    }

    private Set<UUID> whoIMayDirect(List<MessageStorePort.Stored> chosen) {
        return chosen.stream()
                .map(stored -> stored.message().author().value())
                .distinct()
                .filter(assignable::mayAssign)
                .collect(Collectors.toSet());
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
