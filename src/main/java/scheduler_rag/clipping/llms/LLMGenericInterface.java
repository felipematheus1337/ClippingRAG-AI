package scheduler_rag.clipping.llms;

public interface LLMGenericInterface<String> {

    String call(String prompt);
}
