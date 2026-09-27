package ru.gits.api.session.selection;

/**
 * Hook for generating task variants with a language model (YandexGPT, GigaChat) in later versions. v1.0 has no
 * implementation: all tasks come from the validated static bank, and a generated variant would have to pass the
 * same validator before it could be offered to a candidate.
 */
public interface LlmClient {

    /**
     * Completes a prompt.
     *
     * @param systemPrompt instructions for the model
     * @param userPrompt   the request itself
     * @return the model answer
     */
    String complete(String systemPrompt, String userPrompt);
}
