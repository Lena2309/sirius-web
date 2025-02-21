package org.eclipse.sirius.ai;

import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.ai.handler.AiRequestEventHandler;
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
                "2235cc2e-e531-4b83-88ba-3bd622250d56",
                "bc9cedc9-d0b4-4737-b289-98377744ff26"
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
