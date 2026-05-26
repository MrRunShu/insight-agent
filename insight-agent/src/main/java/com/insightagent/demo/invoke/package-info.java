/**
 * Four ways to invoke DeepSeek from a Java app. Each class has a {@code main} or a
 * (commented-out) {@code @Component} so they don't all auto-run at boot.
 *
 * <ul>
 *   <li>{@link com.insightagent.demo.invoke.HttpDeepSeekInvoke}    - Hutool HttpRequest -> DeepSeek REST API</li>
 *   <li>{@link com.insightagent.demo.invoke.SdkDeepSeekInvoke}     - openai-java SDK with baseUrl=DeepSeek</li>
 *   <li>{@link com.insightagent.demo.invoke.SpringAiDeepSeekInvoke}- Spring AI ChatModel (auto-wired)</li>
 *   <li>{@link com.insightagent.demo.invoke.LangChain4jDeepSeekInvoke} - LangChain4j OpenAiChatModel</li>
 *   <li>{@link com.insightagent.demo.invoke.OllamaInvoke}          - Spring AI Ollama ChatModel (local model)</li>
 * </ul>
 *
 * Pre-req: set the {@code DEEPSEEK_API_KEY} env var. Ollama-based demo needs a running Ollama
 * with the configured model pulled (e.g. {@code ollama pull gemma3:1b}).
 */
package com.insightagent.demo.invoke;
