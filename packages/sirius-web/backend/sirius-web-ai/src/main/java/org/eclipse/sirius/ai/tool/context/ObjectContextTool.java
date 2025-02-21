package org.eclipse.sirius.ai.tool.context;

import org.eclipse.sirius.ai.tool.AiTool;
import org.eclipse.sirius.ai.service.AiToolService;
import org.eclipse.sirius.components.core.api.IInput;

public class ObjectContextTool implements AiTool {
    private final AiToolService aiToolService;

    public ObjectContextTool(AiToolService aiToolService) {
        this.aiToolService = aiToolService;
    }

    @Override
    public void setInput(IInput input) {
        this.aiToolService.setInput(input);
    }

    //
    //
    //

    public void getCurrentDiagram() {

    }
}
