package org.eclipse.sirius.ai.agent;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import org.eclipse.sirius.ai.agent.diagram.DiagramAgent;
import org.eclipse.sirius.ai.tool.AiTool;
import org.eclipse.sirius.components.core.api.IInput;

import java.util.List;

public interface Agent {
    default void initializeSpecifications(List<Agent> agents, IInput input, List<AiTool> toolClasses, List<ToolSpecification> specifications) {
        for (var agent : agents) {
            if (agent instanceof DiagramAgent diagramAgent) {
                diagramAgent.setInput(input);
            }
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(agent));
        }
        for (var toolClass : toolClasses) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolClass));
        }
    }
}
