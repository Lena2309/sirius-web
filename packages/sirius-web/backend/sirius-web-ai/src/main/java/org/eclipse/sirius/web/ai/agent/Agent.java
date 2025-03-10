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
    default Collection<ToolSpecification> initializeSpecifications(List<Agent> agents, IInput input, List<AiTool> toolClasses) {
        var specifications = new ArrayList<ToolSpecification>();
        for (var agent : agents) {
            if (agent instanceof DiagramAgent diagramAgent) {
                diagramAgent.setInput(input);
            }
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(agent));
        }
        for (var toolClass : toolClasses) {
            specifications.addAll(ToolSpecifications.toolSpecificationsFrom(toolClass));
        }

        return specifications;
    }
}
