package org.eclipse.sirius.ai;

import org.eclipse.sirius.ai.handlers.AiRequestEventHandler;
import org.eclipse.sirius.ai.dto.AiRequestInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.DiagramLayoutDataInput;
import org.eclipse.sirius.components.collaborative.diagrams.dto.NodeLayoutDataInput;
import org.eclipse.sirius.components.diagrams.layoutdata.Position;
import org.eclipse.sirius.components.diagrams.layoutdata.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Sinks;

import java.util.List;
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
                "Génère un data center de calculs distribués. ",
                "cf2f699a-03d1-401f-a9ad-0581d34d15e5",
                "6caf5618-9e4f-49b3-82aa-8eafbf8548f8",
                new DiagramLayoutDataInput(List.of(
                        new NodeLayoutDataInput(
                                "21db3c7f-f6fe-3227-a9be-fd621582f104",
                                new Position(12, 216),
                                new Size(66, 90),
                                false),
                        new NodeLayoutDataInput(
                                "f1c6034a-2de2-33c4-8e63-728e367a75b4",
                                new Position(207.13280151618267, 142),
                                new Size(201.0546875, 217.99998753440065),
                                false),
                        new NodeLayoutDataInput(
                                "53f388fc-55d7-3c73-a3e4-7e2bf4cfab19",
                                new Position(12, 43.99998753440064),
                                new Size(150, 150),
                                false)
                        ), List.of()));

        this.aiRequestEventHandler.handle(Sinks.one(), Sinks.many().unicast().onBackpressureBuffer(), null, aiRequestInput);

    }
}
