package scheduler_rag.clipping.llms;

public interface LLMGenericInterface<T> {

    T call(T t);
}
