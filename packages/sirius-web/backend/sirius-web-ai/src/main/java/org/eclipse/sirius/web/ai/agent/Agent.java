package org.eclipse.sirius.web.ai.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.eclipse.sirius.web.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.web.ai.tool.AiTool;
import org.eclipse.sirius.components.core.api.IInput;

import java.util.ArrayList;
import java.util.Collection;
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