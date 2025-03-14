package org.eclipse.sirius.web.ai.reason;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import org.eclipse.sirius.components.core.api.IInput;
import org.eclipse.sirius.web.ai.configuration.AiModelsConfiguration;
import org.eclipse.sirius.web.ai.tool.context.BuildContextTool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
public class PromptInterpreter {

    private final Logger logger = LoggerFactory.getLogger(PromptInterpreter.class);

    private final ChatLanguageModel model;

    private final BuildContextTool buildContextTool;

    public PromptInterpreter(BuildContextTool buildContextTool) {
        this.model = AiModelsConfiguration.buildChatModel(AiModelsConfiguration.ModelType.REASON).get();
        this.buildContextTool = Objects.requireNonNull(buildContextTool);
    }

    public void setInput(IInput input) {
        this.buildContextTool.setInput(input);
    }

    @Tool("List all the relevant and appropriate concepts that are necessary for the user's request in the context of the Diagram.")
    public String think(@P("The user's original prompt") String prompt) {
        var rateLimiter = AiModelsConfiguration.getRateLimiter(this.model);
        var context = this.buildContextTool.buildDomainContext();
//        logger.info(context);

        var systemMessage = new SystemMessage("""
             You are a reasoning agent for data Generation.
             Your purpose is to transform the user needs into a prompt that relies on the provided domain concepts in the prompt.
             You must provide an answer, even if the domain is not suitable.
             Your representation must be rich and complete. You have to be clear about what to create and what to link, as well as what special properties to set.
             Links and objects both have properties, specify what special properties to set when possible.
             You have to specify if the concept has to be created or if it should be modified/deleted from the existing diagram.
             You have to be clear if a concept is the child of another.
             Do not hallucinate.
             """);

        var chatRequest = ChatRequest.builder()
                .messages(List.of(systemMessage))
                .parameters(ChatRequestParameters.builder()
                        .build())
                .build();

        loadFewShotExamples(chatRequest);

        chatRequest.messages().add(new UserMessage("Considering the following domain, "+prompt+": \n"+context+"\n"+prompt));

//        logger.info("Rate limit is " + rateLimiter.getPermits());
//        rateLimiter.acquire(logger);

//        Instant responseStart = Instant.now();
        var response = this.model.chat(chatRequest);
//        Instant responseFinish = Instant.now();

//        long responseDuration = Duration.between(responseStart, responseFinish).toMillis();
//        logger.debug("Reason answered in {} ms", responseDuration);

        logger.info(response.toString());
        return response.aiMessage().text();
    }

    private void loadFewShotExamples(ChatRequest chatRequest) {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path fewshotPath = projectRoot.resolve("sirius-web/backend/sirius-web-ai/src/main/java/org/eclipse/sirius/web/ai/reason/fewshot");

        if (!Files.exists(fewshotPath) || !Files.isDirectory(fewshotPath)) {
            logger.error("Error: The directory '{}' does not exist.", fewshotPath);
            return;
        }

        var promptsDir = getSubdirectory(fewshotPath.toString(), "prompts");
        var outputsDir = getSubdirectory(fewshotPath.toString(), "outputs");

        Objects.requireNonNull(promptsDir);
        Objects.requireNonNull(outputsDir);

        try {
            var prompts = loadFilesInDirectory(promptsDir);
            var outputs = loadFilesInDirectory(outputsDir);

            if (prompts.size() != outputs.size()) {
                logger.warn("Number of prompts ({}) and outputs ({}) do not match.", prompts.size(), outputs.size());
            }

            int minSize = Math.min(prompts.size(), outputs.size());
            for (int i = 0; i < minSize; i++) {
                chatRequest.messages().add(new UserMessage(prompts.get(i)));
                chatRequest.messages().add(new AiMessage(outputs.get(i)));
            }

        } catch (Exception e) {
            logger.error("Error while loading few-shot learning: {}", e.getMessage());
        }
    }

    private String getSubdirectory(String parentDir, String subDirName) {
        try {
            return Files.list(Paths.get(parentDir))
                    .filter(Files::isDirectory)
                    .map(Path::toString)
                    .filter(path -> path.endsWith(subDirName))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            logger.error("Error while searching for subdirectory {}: {}", subDirName, e.getMessage());
            return null;
        }
    }

    private List<String> loadFilesInDirectory(String directoryPath) throws IOException {
        var filesContent = new ArrayList<String>();

        Files.walk(Paths.get(directoryPath))
                .filter(Files::isRegularFile)
                .sorted()
                .forEach(file -> {
                    try {
                        String content = Files.readString(file);
                        filesContent.add(content);
                    } catch (IOException e) {
                        logger.error("Error while reading file {}: {}", file, e.getMessage());
                    }
                });

        return filesContent;
    }
}
