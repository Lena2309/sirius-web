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
               // "Now I want the processor inside the Composite Processor to have a status inactive. Then generate a data source, that is linked to the processor inside the composite processor.",
                "Build a computer.",
                "dee56c04-ac12-4eab-914e-266a2abe3c08",
                "00070f0e-d53e-47a9-ae12-5df70e53187c"
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
