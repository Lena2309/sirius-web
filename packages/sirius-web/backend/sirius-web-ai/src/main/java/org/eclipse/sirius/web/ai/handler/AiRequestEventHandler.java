package org.eclipse.sirius.web.ai.handler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.eclipse.sirius.web.ai.agent.orchestration.OrchestratorAgent;
import org.eclipse.sirius.web.ai.dto.AiRequestInput;
import org.eclipse.sirius.web.ai.dto.AiRequestSuccessPayload;
import org.eclipse.sirius.components.collaborative.api.ChangeDescription;
import org.eclipse.sirius.components.collaborative.api.ChangeKind;
import org.eclipse.sirius.components.collaborative.api.IEditingContextEventHandler;
import org.eclipse.sirius.components.collaborative.api.Monitoring;
import org.eclipse.sirius.components.core.api.ErrorPayload;
import org.eclipse.sirius.components.core.api.IEditingContext;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.components.core.api.IPayload;
import org.eclipse.sirius.web.domain.services.api.IMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

import java.util.Objects;

@Service
public class AiRequestEventHandler implements IEditingContextEventHandler {
    private final Logger logger = LoggerFactory.getLogger(AiRequestEventHandler.class);

    private final OrchestratorAgent orchestratorAgent;

    private final IMessageService messageService;

    private final Counter counter;

    public AiRequestEventHandler(OrchestratorAgent orchestratorAgent, IMessageService messageService, MeterRegistry meterRegistry) {
        this.orchestratorAgent = Objects.requireNonNull(orchestratorAgent);
        this.messageService = Objects.requireNonNull(messageService);

        this.counter = Counter.builder(Monitoring.EVENT_HANDLER)
                .tag(Monitoring.NAME, this.getClass().getSimpleName())
                .register(meterRegistry);
    }

    @Override
    public boolean canHandle(IEditingContext editingContext, IInput input) {
        return input instanceof AiRequestInput;
    }

    @Override
    public void handle(Sinks.One<IPayload> payloadSink, Sinks.Many<ChangeDescription> changeDescriptionSink, IEditingContext editingContext, IInput input) {
        this.counter.increment();

        String message = this.messageService.invalidInput(input.getClass().getSimpleName(), AiRequestInput.class.getSimpleName());
        IPayload payload = new ErrorPayload(input.id(), message);
        ChangeDescription changeDescription = new ChangeDescription(ChangeKind.NOTHING, input.id().toString(), input);

        if (input instanceof AiRequestInput aiRequestInput) {
            try {
                logger.info("Generating AI response");
                this.orchestratorAgent.compute(aiRequestInput);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            payload = new AiRequestSuccessPayload(input.id());
            changeDescription = new ChangeDescription(ChangeKind.SEMANTIC_CHANGE, input.id().toString(), input);
        }

        payloadSink.tryEmitValue(payload);
        changeDescriptionSink.tryEmitNext(changeDescription);
    }
}
