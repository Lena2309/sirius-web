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

    @PostMapping("/chat")
    public void chatRequest() {
        AiRequestInput aiRequestInput = new AiRequestInput(UUID.randomUUID(),
                "Now I want the processor inside the Composite Processor have a status inactive. Then generate a data source, that is linked to the processor inside the composite processor.",
                "11c64a92-8bfc-432e-aaf3-7cde39c0f35b",
                "3f2755ac-c8ad-4a52-9f8d-13657069f7ea"
        );

        this.aiRequestEventHandler.handle(Sinks.one(), Sinks.many().unicast().onBackpressureBuffer(), null, aiRequestInput);

    }

    @PostMapping("/chatwithparameters")
    public void chatRequest(@RequestBody Request request) {
        AiRequestInput aiRequestInput = new AiRequestInput(UUID.randomUUID(),
                request.prompt,
                request.editingContextID,
                request.representationId
        );

        this.aiRequestEventHandler.handle(Sinks.one(), Sinks.many().unicast().onBackpressureBuffer(), null, aiRequestInput);
    }

    record Request(String prompt, String editingContextID, String representationId) { }
}
