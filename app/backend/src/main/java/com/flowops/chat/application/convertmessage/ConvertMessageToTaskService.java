package com.flowops.chat.application.convertmessage;

import com.flowops.chat.application.shared.exception.ConversationNotFoundException;
import com.flowops.chat.application.shared.exception.NotAuthenticatedException;
import com.flowops.chat.application.shared.port.AddTaskToInstancePort;
import com.flowops.chat.application.shared.port.AppendChatEventPort;
import com.flowops.chat.application.shared.port.AttachableInstancesPort;
import com.flowops.chat.application.shared.port.ChatCallerPort;
import com.flowops.chat.application.shared.port.ChatDirectoryPort;
import com.flowops.chat.application.shared.port.ConversationStorePort;
import com.flowops.chat.application.shared.port.CreateTaskPort;
import com.flowops.chat.application.shared.port.MessageStorePort;
import com.flowops.chat.application.shared.port.ResolveWorkTemplatePort;
import com.flowops.chat.domain.model.Conversation;
import com.flowops.chat.domain.model.ConversationId;
import com.flowops.chat.domain.model.Message;
import com.flowops.chat.domain.model.MessageId;
import com.flowops.chat.domain.model.PersonId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConvertMessageToTaskService implements ConvertMessageToTaskUseCase {
    private static final int TITLE_LENGTH = 120;

    private final ChatCallerPort caller;
    private final ConversationStorePort conversations;
    private final MessageStorePort messages;
    private final ChatDirectoryPort directory;
    private final CreateTaskPort tasks;
    private final ResolveWorkTemplatePort workTemplates;
    private final AttachableInstancesPort attachableInstances;
    private final AddTaskToInstancePort runs;
    private final AppendChatEventPort events;

    public ConvertMessageToTaskService(
            ChatCallerPort caller,
            ConversationStorePort conversations,
            MessageStorePort messages,
            ChatDirectoryPort directory,
            CreateTaskPort tasks,
            ResolveWorkTemplatePort workTemplates,
            AttachableInstancesPort attachableInstances,
            AddTaskToInstancePort runs,
            AppendChatEventPort events) {
        this.caller = caller;
        this.conversations = conversations;
        this.messages = messages;
        this.directory = directory;
        this.tasks = tasks;
        this.workTemplates = workTemplates;
        this.attachableInstances = attachableInstances;
        this.runs = runs;
        this.events = events;
    }

    @Override
    @Transactional(readOnly = true)
    public ConversionContext contextFor(UUID conversationId, UUID messageId) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        Message message = messageIn(conversation, messageId);

        Optional<ChatDirectoryPort.Person> suggested =
                conversation.counterpartOf(me).flatMap(person -> directory.describe(person.value()));

        return new ConversionContext(
                suggested.map(ChatDirectoryPort.Person::userId),
                suggested.map(ChatDirectoryPort.Person::displayName),
                suggested.map(ChatDirectoryPort.Person::active).orElse(false),
                titleFrom(message.body()),
                message.body(),
                attachableInstances.forSomebodyToChoose(suggested.map(ChatDirectoryPort.Person::userId)));
    }

    @Override
    @Transactional
    public UUID convert(UUID conversationId, UUID messageId, Draft draft) {
        PersonId me = me();
        Conversation conversation = participatedBy(conversationId, me);
        Message message = messageIn(conversation, messageId);

        message.requireConvertible();

        UUID task = draft.instanceId()
                .map(instance -> runs.createAndAttach(
                        instance,
                        draft.title(),
                        draft.description(),
                        draft.assigneeId(),
                        draft.deadline(),
                        draft.priority()))
                .orElseGet(() -> tasks.createAdHoc(
                        draft.title(),
                        draft.description(),
                        draft.assigneeId(),
                        draft.deadline(),
                        draft.priority(),
                        workTemplates.resolve(draft.title(), draft.description(), me.value())));

        message.becameTask(task);
        messages.update(message);
        events.messageConverted(conversation.id(), message.id(), me);
        return task;
    }

    private PersonId me() {
        return PersonId.of(caller.currentCaller().orElseThrow(NotAuthenticatedException::new));
    }

    private Conversation participatedBy(UUID conversationId, PersonId me) {
        return conversations
                .findParticipatedBy(ConversationId.of(conversationId), me)
                .orElseThrow(ConversationNotFoundException::new);
    }

    private Message messageIn(Conversation conversation, UUID messageId) {
        return messages.find(MessageId.of(messageId))
                .map(MessageStorePort.Stored::message)
                .filter(message -> message.conversation().equals(conversation.id()))
                .orElseThrow(ConversationNotFoundException::new);
    }

    private static String titleFrom(String body) {
        String line = body.lines().findFirst().orElse(body).strip();
        return line.length() <= TITLE_LENGTH ? line : line.substring(0, TITLE_LENGTH);
    }
}
