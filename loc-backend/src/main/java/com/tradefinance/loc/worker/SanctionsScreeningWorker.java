package com.tradefinance.loc.worker;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.tradefinance.loc.mock.MockSanctionsList;
import com.tradefinance.loc.mock.MockSanctionsList.ScreeningResult;

import io.camunda.client.annotation.JobWorker;
import io.camunda.client.annotation.Variable;

/**
 * BPMN (compliance process): "Screen party against sanctions lists" (Task_ScreenParty),
 * job type "sanctions-screening". PARALLEL MULTI-INSTANCE: Camunda creates one job per
 * element of "parties"; each job sees its own element as variable "party".
 * We return "sanctionsResult"; Camunda collects them into the list "sanctionsResults".
 */
@Component
public class SanctionsScreeningWorker {

    private static final Logger log = LoggerFactory.getLogger(SanctionsScreeningWorker.class);

    private final MockSanctionsList sanctionsList;

    public SanctionsScreeningWorker(MockSanctionsList sanctionsList) {
        this.sanctionsList = sanctionsList;
    }

    @JobWorker(type = "sanctions-screening")
    public Map<String, Object> screen(@Variable String applicationId, @Variable Map<String, Object> party) {
        String name = (String) party.get("name");
        String country = (String) party.get("country");
        ScreeningResult result = sanctionsList.screen(name, country);
        log.info("[sanctions-screening] {} party={} ({}) -> match={}", applicationId, name, country, result.matchFound());

        Map<String, Object> sanctionsResult = new HashMap<>();
        sanctionsResult.put("partyName", name);
        sanctionsResult.put("role", party.get("role"));
        sanctionsResult.put("country", country);
        sanctionsResult.put("matchFound", result.matchFound());
        sanctionsResult.put("matchScore", result.matchScore());
        sanctionsResult.put("listName", result.listName());
        return Map.of("sanctionsResult", sanctionsResult);
    }
}
