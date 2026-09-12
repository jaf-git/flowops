package com.flowops.chat.application.published;

import com.flowops.chat.application.viewconversation.ViewConversationResult;
import com.flowops.chat.application.viewconversation.ViewConversationUseCase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationExcerptService implements ConversationExcerptUseCase {
    private static final int LINES_CONSIDERED = 60;

    private final ViewConversationUseCase conversations;

    public ConversationExcerptService(ViewConversationUseCase conversations) {
        this.conversations = conversations;
    }

    @Override
    @Transactional(readOnly = true)
    public Excerpt of(UUID conversationId) {
        ViewConversationResult read = conversations.execute(conversationId, Optional.empty(), LINES_CONSIDERED);

        List<ViewConversationResult.Row> oldestFirst = new ArrayList<>(read.messages());
        oldestFirst.sort(Comparator.comparingLong(ViewConversationResult.Row::seq));

        Map<UUID, String> whoSaidWhat = new LinkedHashMap<>();
        List<Line> lines = new ArrayList<>();
        for (ViewConversationResult.Row row : oldestFirst) {
            if (row.deletedAt().isPresent()) {
                continue;
            }

            Optional<String> said = row.body();
            if (said.isEmpty()) {
                continue;
            }
            String label = whoSaidWhat.computeIfAbsent(row.authorId(), whoever -> "P" + (whoSaidWhat.size() + 1));
            lines.add(new Line(label, said.get()));
        }
        List<Speaker> speakers = new ArrayList<>();
        whoSaidWhat.forEach((person, label) -> speakers.add(new Speaker(label, person)));
        return new Excerpt(List.copyOf(lines), List.copyOf(speakers));
    }
}
