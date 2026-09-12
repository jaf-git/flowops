package com.flowops.aiexport.seed;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class SeedDiscovery {
    private static final Logger LOG = LoggerFactory.getLogger(SeedDiscovery.class);

    private final SeedCompany company;
    private final SeedSchedule schedule;

    private final Map<String, String> engagementByClient = new LinkedHashMap<>();

    private int engagements;
    private int requestsMarked;
    private int completionsMarked;
    private int threadsEnded;
    private int requestsLeftRunning;
    private int externalWaits;
    private int internalWaits;
    private final Map<String, Integer> endingsByCompleteness = new TreeMap<>();

    SeedDiscovery(SeedCompany company, SeedSchedule schedule) {
        this.company = company;
        this.schedule = schedule;
    }

    record Exchange(
            String client,
            SeedCompany.Person requester,
            SeedCompany.Person doer,
            int conversationIndex,
            int round,
            JsonNode requestMessage,
            JsonNode replyMessage) {
        String requestMessageId() {
            return requestMessage.path("id").asText();
        }

        String replyMessageId() {
            return replyMessage.path("id").asText();
        }
    }

    void observe(Exchange exchange) {
        if (openTheEngagementIfThisIsTheFirstWeHaveHeardOfThem(exchange)) {
            return;
        }
        String engagement = engagementByClient.get(exchange.client());

        SeedObservationPolicy.Observation what =
                SeedObservationPolicy.whatBecomesOf(exchange.conversationIndex(), exchange.round());

        JsonNode request = mark(
                exchange.requester(),
                exchange.requestMessageId(),
                engagement,
                "REQUEST",
                exchange.doer().personId().toString());
        requestsMarked++;
        schedule.advanceMinutes(11);

        if (what == SeedObservationPolicy.Observation.CARRY_TO_AN_ENDING) {
            answerItAndEndTheThread(exchange, engagement, request);
        } else if (what == SeedObservationPolicy.Observation.END_IT_UNANSWERED) {
            endTheThread(exchange.requester(), request);
        } else {
            requestsLeftRunning++;
        }
    }

    private boolean openTheEngagementIfThisIsTheFirstWeHaveHeardOfThem(Exchange exchange) {
        if (engagementByClient.containsKey(exchange.client())) {
            return false;
        }
        JsonNode opened = exchange.requester()
                .browser()
                .postOrFail(
                        "/api/discovery/jobs",
                        "{\"messageId\":\"" + exchange.requestMessageId() + "\",\"name\":"
                                + SeedJson.quote(exchange.client()) + "}",
                        "opening the engagement with " + exchange.client());
        engagementByClient.put(exchange.client(), opened.path("jobId").asText());
        engagements++;
        schedule.advanceMinutes(7);
        return true;
    }

    private void answerItAndEndTheThread(Exchange exchange, String engagement, JsonNode request) {
        JsonNode completion = mark(exchange.doer(), exchange.replyMessageId(), engagement, "COMPLETION", null);
        completionsMarked++;
        refuseAThreadTheProductDidNotJoin(request, completion, exchange);
        schedule.advanceMinutes(23);

        waitOnSomebodyIfThisIsOneOfTheOnesThatDid(exchange, completion);

        exchange.doer()
                .browser()
                .postOrFail(
                        "/api/discovery/nodes/" + completion.path("nodeId").asText() + "/output",
                        "{\"outputType\":\""
                                + SeedObservationPolicy.whatIsProducedBy(SeedOrganisation.statedJobOf(
                                        exchange.doer().displayName()))
                                + "\"}",
                        "recording what the work produced");
        schedule.advanceMinutes(19);

        endTheThread(exchange.doer(), completion);
    }

    private void waitOnSomebodyIfThisIsOneOfTheOnesThatDid(Exchange exchange, JsonNode completion) {
        String waitingOn = SeedObservationPolicy.whoKeptTheWorkWaiting(exchange.conversationIndex(), exchange.round());
        if (waitingOn == null) {
            return;
        }
        String node = "/api/discovery/nodes/" + completion.path("nodeId").asText();

        exchange.doer()
                .browser()
                .postOrFail(node + "/block", "{\"waitingOn\":\"" + waitingOn + "\"}", "the work stopping to wait");
        if ("CLIENT".equals(waitingOn)) {
            externalWaits++;
        } else {
            internalWaits++;
        }

        schedule.advanceHours(6);
        exchange.doer().browser().postOrFail(node + "/resume", null, "whoever was waited on coming back");
        schedule.advanceMinutes(31);
    }

    private JsonNode mark(
            SeedCompany.Person speaker, String messageId, String engagement, String direction, String performerId) {
        return speaker.browser()
                .postOrFail(
                        "/api/discovery/nodes",
                        "{\"messageId\":\"" + messageId + "\",\"jobId\":\"" + engagement + "\",\"direction\":\""
                                + direction + "\",\"performerId\":"
                                + (performerId == null ? "null" : "\"" + performerId + "\"") + "}",
                        "marking a " + direction.toLowerCase() + " about " + engagement);
    }

    private void endTheThread(SeedCompany.Person who, JsonNode node) {
        JsonNode ended = who.browser()
                .postOrFail(
                        "/api/discovery/tracks/" + node.path("trackId").asText() + "/end",
                        null,
                        "ending a thread of work");
        threadsEnded++;
        endingsByCompleteness.merge(ended.path("completeness").asText(), 1, Integer::sum);
        schedule.advanceMinutes(13);
    }

    private void refuseAThreadTheProductDidNotJoin(JsonNode request, JsonNode completion, Exchange exchange) {
        String asked = request.path("trackId").asText();
        String answered = completion.path("trackId").asText();
        if (!asked.equals(answered)) {
            throw new SeedFailedException(
                    """
                    %s answered %s about %s, and the product put the completion in thread %s while the request \
                    sits in %s. The thread would close as a shape of one unit of work rather than two, be \
                    counted as such, and nothing downstream could tell — so the calibration would be wrong \
                    and every figure quoted from it unreproducible."""
                            .formatted(
                                    exchange.doer().displayName(),
                                    exchange.requester().displayName(),
                                    exchange.client(),
                                    answered,
                                    asked));
        }
    }

    void sayWhatDiscoveryMadeOfIt() {
        SeedCompany.Person owner = company.owner();
        Map<String, Integer> shapesByStatus = new TreeMap<>();
        for (JsonNode type : owner.browser().getOrFail("/api/discovery/types", "reading the discovered types")) {
            shapesByStatus.merge(type.path("status").asText(), 1, Integer::sum);
        }

        LOG.info(
                """
                Discovery observed the same conversations, having been told nothing.
                  engagements opened      {}
                  work marked             {} requests, {} completions, {} boundary markers
                  threads ended           {} ({})
                  work still running      {} requests nobody has answered
                  waits recorded          {} external, {} internal
                  shapes in the catalogue {}
                A shape needs five completed threads before the product will propose it and three before it
                will hold it as a candidate, so CANDIDATE rows are shapes it has noticed and declines to
                discuss — and the shapes below three are not here at all, which is the floor working.""",
                engagements,
                requestsMarked,
                completionsMarked,
                engagements,
                threadsEnded,
                endingsByCompleteness,
                requestsLeftRunning,
                externalWaits,
                internalWaits,
                shapesByStatus.isEmpty() ? "none" : shapesByStatus);
    }
}
