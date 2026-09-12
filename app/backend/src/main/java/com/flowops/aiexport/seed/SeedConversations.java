package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SeedConversations {
    private static final int CLOSED_TASKS_WANTED = 6;

    private static final int CHATTER_BETWEEN_REQUESTS = 2;

    private record Work(String sentence, boolean below) {
        static Work recurring(String sentence) {
            return new Work(sentence, false);
        }

        static Work rare(String sentence) {
            return new Work(sentence, true);
        }

        int occurrences() {
            return below ? 3 : SeedClosurePolicy.occurrencesNeededFor(sentence, CLOSED_TASKS_WANTED);
        }
    }

    private static final List<Work> WORK = List.of(
            Work.recurring("Answer the overnight messages"),
            Work.recurring("Rewrite the leaflet wording"),
            Work.recurring("Photograph the new menu items"),
            Work.recurring("Correct the spelling on the about page"),
            Work.recurring("Create three squares for Instagram"),
            Work.recurring("Work out what the adverts cost in July"),
            Work.recurring("Prepare this month's subscriber email"),
            Work.recurring("Change the shop times on the map listing"),
            Work.recurring("Crop the header pictures"),
            Work.recurring("Build a one-page site for the promotion"),
            Work.recurring("Have the client sign off the wording"),
            Work.recurring("Round up the brand files"),
            Work.recurring("List the new items in the online shop"),
            Work.recurring("Sketch out what we post in December"),
            Work.recurring("Trim the film to fifteen seconds"),
            Work.rare("Pitch a paragraph to the local paper"),
            Work.rare("Tidy everyone's email footers"),
            Work.rare("Raise the November invoice"));

    private static final List<String> CLIENTS = List.of(
            "Aurora Coffee",
            "Northwind Outdoors",
            "Lumen Skincare",
            "Harvest Table",
            "Verde Living",
            "Bright Path Clinic",
            "Ironbark Brewing",
            "Petal & Stem",
            "Sundial Travel",
            "Nimbus Fitness");

    private static final List<String> CHATTER = List.of(
            "Morning — did you get the files they sent over?",
            "Yes, all four. The logo is the old version again though.",
            "I will ask them for the right one.",
            "How did the shoot go?",
            "Good, we got more than we needed. I will pick the best twenty.",
            "Their new manager starts Monday, so expect a few questions.",
            "Noted. I will keep the notes tidy in case they want a handover.",
            "The numbers were up again this month, mostly from the reels.",
            "That is the third month running.",
            "Do you want to move our catch-up to Thursday?",
            "Thursday works.",
            "They pushed the launch back two weeks.",
            "Fine by me, that gives us room.",
            "Quick one — do we have their brand colours written down anywhere?",
            "In the shared folder, under brand basics.",
            "Found it, thanks.",
            "They liked the second option.",
            "I thought they would. It is the one I would have picked.",
            "Their card was declined on the ad account this morning.",
            "I let them know. They are sorting it out with the bank.",
            "Are we still on for the review call at four?",
            "Yes, I will send the link.",
            "The client said the last set of photos felt too dark.",
            "I will brighten them and send a new version.",
            "Nothing urgent from me today.",
            "Same here. Quiet week for once.",
            "They want to add a second location next year.",
            "That will be a whole new set of pages.",
            "The printer said Friday at the earliest.",
            "That still works if we get the files over tomorrow.");

    private static final List<String> OPENERS = List.of(
            "Just picked up the account for %s — I will be your point of contact on it.",
            "We are starting with %s this week, so we will be talking a lot.",
            "%s signed this morning. Everything for them comes through here.",
            "Taking over %s from the last account manager. Bear with me for a bit.",
            "%s is back with us after a year away.",
            "Adding you to %s — they are a smaller one but they keep us busy.");

    private final SeedCompany company;
    private final SeedSchedule schedule;
    private final SeedTaskLifecycle lifecycle;

    private final Map<String, UUID> templateByWork = new LinkedHashMap<>();

    SeedConversations(SeedCompany company, SeedSchedule schedule, SeedTaskLifecycle lifecycle) {
        this.company = company;
        this.schedule = schedule;
        this.lifecycle = lifecycle;
    }

    Map<String, UUID> emergentTemplates() {
        return Map.copyOf(templateByWork);
    }

    static List<String> everyWorkSentence() {
        return WORK.stream().map(Work::sentence).toList();
    }

    static List<String> theJobsEachWorkingConversationRunsBetween() {
        return THREADS.stream()
                .filter(Thread::carriesWork)
                .map(thread -> SeedOrganisation.statedJobOf(thread.requester()) + " -> "
                        + SeedOrganisation.statedJobOf(thread.doer()))
                .toList();
    }

    static int howManyRequestsAreMade() {
        return WORK.stream().mapToInt(Work::occurrences).sum();
    }

    static int howManyMessagesAreSaid() {
        long working = THREADS.stream().filter(Thread::carriesWork).count();
        long quiet = THREADS.size() - working;
        return THREADS.size()
                + (CHATTER_BETWEEN_REQUESTS + 1) * howManyRequestsAreMade()
                + (int) quiet * (CHATTER.size() / 2);
    }

    private record Thread(String requester, String doer, boolean carriesWork) {}

    private static final String THE_OWNER = "the owner";

    private static final List<Thread> THREADS = List.of(
            new Thread(SeedCompany.STRATEGY_MANAGER, "Andrei Munteanu", true),
            new Thread(SeedCompany.STRATEGY_MANAGER, SeedCompany.THE_COLLEAGUE_WHO_LEAVES, true),
            new Thread(SeedCompany.SOCIAL_MANAGER, "Cosmin Vasile", true),
            new Thread(SeedCompany.SOCIAL_MANAGER, "Daria Enache", true),
            new Thread(THE_OWNER, SeedCompany.STRATEGY_MANAGER, true),
            new Thread(THE_OWNER, SeedCompany.SOCIAL_MANAGER, true),
            new Thread(THE_OWNER, "Andrei Munteanu", true),
            new Thread(THE_OWNER, SeedCompany.THE_COLLEAGUE_WHO_LEAVES, true),
            new Thread(THE_OWNER, "Cosmin Vasile", true),
            new Thread(THE_OWNER, "Daria Enache", true),
            new Thread("Andrei Munteanu", "Cosmin Vasile", false),
            new Thread("Daria Enache", SeedCompany.THE_COLLEAGUE_WHO_LEAVES, false),
            new Thread(SeedCompany.STRATEGY_MANAGER, SeedCompany.SOCIAL_MANAGER, false),
            new Thread("Andrei Munteanu", "Daria Enache", false),
            new Thread("Cosmin Vasile", SeedCompany.THE_COLLEAGUE_WHO_LEAVES, false),
            new Thread("Andrei Munteanu", SeedCompany.THE_COLLEAGUE_WHO_LEAVES, false),
            new Thread("Cosmin Vasile", "Daria Enache", false));

    private SeedCompany.Person whoever(String displayName) {
        return THE_OWNER.equals(displayName) ? company.owner() : company.named(displayName);
    }

    private record Ask(Work work, int occurrence, String client) {}

    void seedTheHistoryAndMarkTheWorkInIt(SeedDiscovery discovery) {
        walkEveryConversation((conversations, thread, conversationIndex, ask, round) ->
                talkConvertAndObserve(discovery, conversations, thread, conversationIndex, ask, round));
    }

    void seedTheMessagesAndMarkTheWorkInThem(SeedDiscovery discovery) {
        walkEveryConversation((conversations, thread, conversationIndex, ask, round) ->
                talkAndObserve(discovery, conversations, thread, conversationIndex, ask, round));
    }

    @FunctionalInterface
    private interface AskHandler {
        void handle(Map<String, String> conversations, Thread thread, int conversationIndex, Ask ask, int round);
    }

    private void walkEveryConversation(AskHandler handler) {
        List<Ask> asks = everythingAskedFor();
        List<Thread> working = THREADS.stream().filter(Thread::carriesWork).toList();

        Map<String, String> conversations = openEveryConversation();

        int next = 0;
        for (int round = 0; next < asks.size(); round++) {
            for (int conversation = 0; conversation < working.size(); conversation++) {
                if (next >= asks.size()) {
                    break;
                }
                handler.handle(conversations, working.get(conversation), conversation, asks.get(next++), round);
            }

            if (round % 2 == 1) {
                schedule.nextWorkingMorning();
            }
        }

        theConversationsNobodyTurnedIntoWork(conversations);
    }

    private void talkAndObserve(
            SeedDiscovery discovery,
            Map<String, String> conversations,
            Thread thread,
            int conversationIndex,
            Ask ask,
            int round) {
        Said said = speakSoTheAnswerComesLast(conversations, thread, ask, round);
        discovery.observe(new SeedDiscovery.Exchange(
                ask.client(),
                whoever(thread.requester()),
                whoever(thread.doer()),
                conversationIndex,
                round,
                said.request(),
                said.answer()));

        schedule.advanceHours(
                8 + Math.floorMod(ask.occurrence() * 5 + thread.doer().length(), 12));
    }

    private record Said(JsonNode request, JsonNode answer) {}

    private Said speakSoTheAnswerComesLast(Map<String, String> conversations, Thread thread, Ask ask, int round) {
        String id = conversations.get(pairKey(thread));
        SeedCompany.Person requester = whoever(thread.requester());
        SeedCompany.Person doer = whoever(thread.doer());

        say(requester, id, chatter(thread, round, 1));
        schedule.advanceMinutes(4);

        JsonNode request = say(requester, id, sentenceFor(ask));
        schedule.advanceMinutes(6);

        JsonNode answer = say(doer, id, chatter(thread, round, 0));
        schedule.advanceMinutes(3);

        return new Said(request, answer);
    }

    private static String chatter(Thread thread, int round, int line) {
        return CHATTER.get(Math.floorMod(round * 7 + line * 3 + thread.doer().length(), CHATTER.size()));
    }

    private List<Ask> everythingAskedFor() {
        List<Ask> asks = new ArrayList<>();
        int deepest = WORK.stream().mapToInt(Work::occurrences).max().orElse(0);
        for (int occurrence = 0; occurrence < deepest; occurrence++) {
            for (Work work : WORK) {
                if (occurrence < work.occurrences()) {
                    asks.add(
                            new Ask(work, occurrence, CLIENTS.get((occurrence + WORK.indexOf(work)) % CLIENTS.size())));
                }
            }
        }
        return asks;
    }

    private Map<String, String> openEveryConversation() {
        Map<String, String> byPair = new LinkedHashMap<>();
        for (int i = 0; i < THREADS.size(); i++) {
            Thread thread = THREADS.get(i);
            SeedCompany.Person opener = whoever(thread.requester());
            SeedCompany.Person other = whoever(thread.doer());

            String id = opener.browser()
                    .postOrFail(
                            "/api/conversations",
                            "{\"personId\":\"" + other.personId() + "\"}",
                            "opening a conversation between " + thread.requester() + " and " + thread.doer())
                    .path("id")
                    .asText();
            byPair.put(pairKey(thread), id);

            say(opener, id, OPENERS.get(i % OPENERS.size()).formatted(CLIENTS.get(i % CLIENTS.size())));
            schedule.advanceMinutes(4);
        }
        return byPair;
    }

    private static String pairKey(Thread thread) {
        return thread.requester() + " -> " + thread.doer();
    }

    private void talkConvertAndObserve(
            SeedDiscovery discovery,
            Map<String, String> conversations,
            Thread thread,
            int conversationIndex,
            Ask ask,
            int round) {
        Said said = speakSoTheAnswerComesLast(conversations, thread, ask, round);

        UUID task = turnItIntoWork(conversations, thread, ask, said.request());

        discovery.observe(new SeedDiscovery.Exchange(
                ask.client(),
                whoever(thread.requester()),
                whoever(thread.doer()),
                conversationIndex,
                round,
                said.request(),
                said.answer()));

        seeItThrough(thread, ask, task);
    }

    private UUID turnItIntoWork(Map<String, String> conversations, Thread thread, Ask ask, JsonNode message) {
        SeedCompany.Person requester = whoever(thread.requester());
        SeedCompany.Person doer = whoever(thread.doer());

        UUID task = convert(
                requester,
                conversations.get(pairKey(thread)),
                message.path("id").asText(),
                sentenceFor(ask),
                doer);

        if (ask.occurrence() == 0) {
            rememberTheTemplate(requester, ask.work(), task);
            putAnEstimateOnIt(requester, ask.work());
        }
        return task;
    }

    private void seeItThrough(Thread thread, Ask ask, UUID task) {
        SeedCompany.Person doer = whoever(thread.doer());
        SeedCompany.Person requester = whoever(thread.requester());

        SeedEstimatePolicy.Guess guess = SeedEstimatePolicy.guessFor(ask.work().sentence());
        boolean elenaMustStayClear = SeedCompany.THE_COLLEAGUE_WHO_LEAVES.equals(thread.doer());

        if (elenaMustStayClear || SeedClosurePolicy.closes(ask.work().sentence(), ask.occurrence())) {
            lifecycle.driveToClosure(doer, requester, task, guess.actualWorkHours());
        } else {
            lifecycle.leaveItLive(doer, task, ask.occurrence(), "waiting on the client to come back to us");
        }
    }

    private static String sentenceFor(Ask ask) {
        return ask.occurrence() % 3 == 2
                ? ask.work().sentence() + " for " + ask.client()
                : ask.work().sentence();
    }

    private JsonNode say(SeedCompany.Person speaker, String conversation, String body) {
        return speaker.browser()
                .postOrFail(
                        "/api/conversations/" + conversation + "/messages",
                        "{\"body\":" + SeedJson.quote(body) + "}",
                        "saying something in the conversation");
    }

    private UUID convert(
            SeedCompany.Person requester,
            String conversation,
            String messageId,
            String sentence,
            SeedCompany.Person doer) {
        Instant deadline = schedule.deadlineIn(3 + Math.floorMod(sentence.hashCode(), 9));
        return UUID.fromString(requester
                .browser()
                .postOrFail(
                        "/api/conversations/" + conversation + "/messages/" + messageId + "/convert",
                        "{\"title\":" + SeedJson.quote(sentence) + ",\"description\":null,\"assigneeId\":\""
                                + doer.personId() + "\",\"deadline\":\"" + deadline + "\",\"priority\":\""
                                + priorityFor(sentence) + "\"}",
                        "turning a sentence into work")
                .path("taskId")
                .asText());
    }

    private void rememberTheTemplate(SeedCompany.Person reader, Work work, UUID task) {
        String template = reader.browser()
                .getOrFail("/api/tasks/" + task, "reading back the task to find its template")
                .path("templateId")
                .asText();
        if (template == null || template.isBlank()) {
            throw new SeedFailedException("the task converted from \"" + work.sentence()
                    + "\" carries no template, so CONSTRAINT-TEMPLATE-FIRST-01 has been broken upstream");
        }
        templateByWork.put(work.sentence(), UUID.fromString(template));
    }

    private void putAnEstimateOnIt(SeedCompany.Person author, Work work) {
        Double hours = SeedEstimatePolicy.estimateTypedOnTheLibraryScreen(work.sentence());
        if (hours == null) {
            return;
        }
        UUID template = templateByWork.get(work.sentence());
        author.browser()
                .patchOrFail(
                        "/api/task-templates/" + template,
                        """
                        {"title":%s,"description":null,"type":null,"priority":"NORMAL",
                         "estimatedHours":%s,"checklist":[],"submitForApproval":false}
                        """
                                .formatted(SeedJson.quote(work.sentence()), hours),
                        "putting an estimate on " + work.sentence());
    }

    private static String priorityFor(String sentence) {
        int bucket = Math.floorMod(sentence.hashCode(), 10);
        if (bucket == 0) {
            return "URGENT";
        }
        return bucket < 4 ? "HIGH" : "NORMAL";
    }

    private void theConversationsNobodyTurnedIntoWork(Map<String, String> conversations) {
        for (Thread thread : THREADS) {
            if (thread.carriesWork()) {
                continue;
            }
            String id = conversations.get(pairKey(thread));
            SeedCompany.Person opener = whoever(thread.requester());
            SeedCompany.Person other = whoever(thread.doer());

            for (int line = 0; line < CHATTER.size() / 2; line++) {
                SeedCompany.Person speaker = line % 2 == 0 ? opener : other;
                say(
                        speaker,
                        id,
                        CHATTER.get(Math.floorMod(line * 5 + thread.doer().length(), CHATTER.size())));
                schedule.advanceMinutes(3 + (line % 5));
            }
            schedule.advanceHours(2);
        }
    }
}
