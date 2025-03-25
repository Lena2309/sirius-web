package org.eclipse.sirius.web.ai.agent;

import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.components.core.api.IInput;

import java.util.List;

public interface Agent {
    default void initializeSpecifications(List<Agent> agents, IInput input) {
        for (var agent : agents) {
            if (agent instanceof DiagramAgent diagramAgent) {
                diagramAgent.setInput(input);
            }
        }
    }
}