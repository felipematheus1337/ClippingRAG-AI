package scheduler_rag.clipping.llms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenAIImpl implements LLMGenericInterface<Object> {

    private static final ChatOptions options = ChatOptions
            .builder()
            .model("gpt-5.2")
            .temperature(.99)
            .topP(.95)
            .build();
    private final ChatClient client;

    public OpenAIImpl(ChatClient.Builder builder) {
        this.client = builder
                .defaultOptions(options.mutate())
                .build();
    }

    @Override
    public Object call(Object o) {
        return this.client
                .prompt()
                .user(o.toString())
                .call();
    }
}
