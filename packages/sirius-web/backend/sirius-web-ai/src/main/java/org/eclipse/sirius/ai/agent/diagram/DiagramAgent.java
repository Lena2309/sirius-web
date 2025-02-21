package org.eclipse.sirius.ai.agent.diagram;

import org.eclipse.sirius.ai.agent.Agent;
import org.eclipse.sirius.components.core.api.IInput;

public interface DiagramAgent extends Agent {
    public void setInput(IInput input);
    public void setToolsInput();
}
