package org.eclipse.sirius.web.ai.agent.diagram;

import org.eclipse.sirius.web.ai.agent.Agent;
import org.eclipse.sirius.components.core.api.IInput;

public interface DiagramAgent extends Agent {
    void setInput(IInput input);
    void setToolsInput();
}