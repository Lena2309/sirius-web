package org.eclipse.sirius.ai.dto;

import org.eclipse.sirius.components.core.api.IInput;

import java.util.UUID;

public record AiRequestInput(UUID id, String prompt, String editingContextId, String representationId) implements IInput {
}
