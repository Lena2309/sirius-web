package org.eclipse.sirius.ai.dto;

import org.eclipse.sirius.components.core.api.IPayload;

import java.util.Objects;
import java.util.UUID;

public record AiRequestSuccessPayload(UUID id) implements IPayload {
    public AiRequestSuccessPayload {
        Objects.requireNonNull(id);
    }
}
