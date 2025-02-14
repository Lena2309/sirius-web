package org.eclipse.sirius.ai;

import org.eclipse.sirius.ai.handlers.AiRequestEventHandler;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.springframework.web.bind.annotation.PostMapping;
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
                "Generate one composite processor, and 1 processor inside. Outside create 1 data source.",
                "1a6568cd-43fb-4c72-9cb9-2a413ee48056",
                "c38c79d7-e78a-4e29-a1cd-8f97955e9d46"
        );

        this.aiRequestEventHandler.handle(Sinks.one(), Sinks.many().unicast().onBackpressureBuffer(), null, aiRequestInput);

    }
}
