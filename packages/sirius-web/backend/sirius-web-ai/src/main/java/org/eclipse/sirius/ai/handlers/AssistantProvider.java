package org.eclipse.sirius.ai.handlers;

import org.eclipse.sirius.ai.agent.ReasonAgent;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.core.api.IInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AssistantProvider {
    private final Logger logger = LoggerFactory.getLogger(AssistantProvider.class);

    private final ReasonAgent reasonAgent;

    AssistantProvider(ReasonAgent reasonAgent) {
        this.reasonAgent = reasonAgent;
    }

    public void handle(IInput input) {
        logger.info("Generating AI response");
        if (input instanceof AiRequestInput aiRequestInput) {
            try {
                this.reasonAgent.compute(aiRequestInput);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
