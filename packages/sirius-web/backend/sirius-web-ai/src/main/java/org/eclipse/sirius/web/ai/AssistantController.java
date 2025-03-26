package org.eclipse.sirius.web.ai;

import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.web.ai.handler.AiRequestEventHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Sinks;

import java.util.UUID;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AiRequestEventHandler aiRequestEventHandler;

    public AssistantController(AiRequestEventHandler aiRequestEventHandler) {
        this.aiRequestEventHandler = aiRequestEventHandler;
    }

    @PostMapping("/chatwithparameters")
    public void chatRequest(@RequestBody Request request) {
        AiRequestInput aiRequestInput = new AiRequestInput(UUID.randomUUID(),
                request.prompt,
                request.editingContextID,
                request.diagramId
        );

        this.aiRequestEventHandler.handle(Sinks.one(), Sinks.many().unicast().onBackpressureBuffer(), null, aiRequestInput);
    }

    record Request(String prompt, String editingContextID, String diagramId) { }
}
