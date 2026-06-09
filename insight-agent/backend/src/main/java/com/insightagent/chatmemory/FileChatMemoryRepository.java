package com.insightagent.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.util.DefaultInstantiatorStrategy;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Kryo-backed file persistence for chat memory. One file per conversation id.
 *
 * <p>Why Kryo over JSON: {@code Message} is a sealed-ish hierarchy with no no-arg ctor and
 * subclasses (UserMessage / AssistantMessage / SystemMessage / ToolResponseMessage) carry
 * different fields. Jackson chokes on this without custom (de)serializers; Kryo handles it
 * with {@link StdInstantiatorStrategy} (bypass constructor) out of the box.
 *
 * <p>Spring AI 1.0 GA decoupled storage from the memory algorithm:
 * {@link ChatMemoryRepository} = storage; {@code MessageWindowChatMemory} = algorithm.
 * We only implement storage here.
 */
@Component
@Slf4j
public class FileChatMemoryRepository implements ChatMemoryRepository {

    private static final Kryo KRYO = new Kryo();

    static {
        KRYO.setRegistrationRequired(false);
        KRYO.setInstantiatorStrategy(new DefaultInstantiatorStrategy(new StdInstantiatorStrategy()));
    }

    private final File baseDir;

    public FileChatMemoryRepository(@Value("${app.memory.dir:${user.dir}/chat-memory}") String dir) {
        this.baseDir = new File(dir);
        if (!baseDir.exists() && !baseDir.mkdirs()) {
            log.warn("Failed to create chat memory dir: {}", baseDir.getAbsolutePath());
        }
        log.info("FileChatMemoryRepository initialised at {}", baseDir.getAbsolutePath());
    }

    @Override
    public List<String> findConversationIds() {
        File[] files = baseDir.listFiles((d, name) -> name.endsWith(".kryo"));
        if (files == null) return List.of();
        return Arrays.stream(files)
                .map(f -> f.getName().substring(0, f.getName().length() - ".kryo".length()))
                .toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Message> findByConversationId(String conversationId) {
        File file = fileFor(conversationId);
        if (!file.exists()) return new ArrayList<>();
        try (Input input = new Input(new FileInputStream(file))) {
            return (List<Message>) KRYO.readClassAndObject(input);
        } catch (Exception e) {
            log.error("Failed to read chat memory for {}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        File file = fileFor(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            KRYO.writeClassAndObject(output, messages);
        } catch (Exception e) {
            log.error("Failed to save chat memory for {}", conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        File file = fileFor(conversationId);
        if (file.exists() && !file.delete()) {
            log.warn("Failed to delete chat memory for {}", conversationId);
        }
    }

    private File fileFor(String conversationId) {
        // sanitise: only allow alphanumerics, dash, underscore in conversation id
        String safe = conversationId.replaceAll("[^a-zA-Z0-9_-]", "_");
        return new File(baseDir, safe + ".kryo");
    }
}
