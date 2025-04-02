
import java.io.File;
import java.util.List;

import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenAiAssistantEngineTest {

    private static OpenAiAssistantEngine engine;
    private static String testAssistantId;
    private static String testThreadId;
    private static String testFileId;
    private static final String TEST_API_KEY = System.getenv("OPENAI_API_KEY");

    @BeforeAll
    static void setUp() {
        assertNotNull(TEST_API_KEY, "API key must be set in environment variables");
        engine = new OpenAiAssistantEngine(TEST_API_KEY);
    }

    @Test
    @Order(1)
    void testConstructor() {
        assertNotNull(engine);
        OpenAiAssistantEngine customEngine = new OpenAiAssistantEngine(TEST_API_KEY, 50);
        assertNotNull(customEngine);
    }

    @Test
    @Order(2)
    void testFileUpload() {
        File testFile = new File("user_info.txt");
        testFileId = engine.uploadFile(testFile, "assistants");
        assertNotNull(testFileId, "File upload should return a valid ID");
    }

    @Test
    @Order(3)
    void testCreateAssistant() {
        testAssistantId = engine.createAssistant(
                "gpt-3.5-turbo",
                "Test Assistant",
                "Test Description",
                "You are a test assistant",
                null,
                List.of("file_search"),
                null,
                0.7,
                0.7,
                null
        );
        assertNotNull(testAssistantId, "Assistant creation should return a valid ID");
    }

    @Test
    @Order(4)
    void testCreateThread() {
        List<JSONObject> messages = List.of(
                new JSONObject()
                        .put("role", "user")
                        .put("content", "Hello")
        );
        testThreadId = engine.createThread(messages, null, null);
        assertNotNull(testThreadId, "Thread creation should return a valid ID");
    }

    @Test
    @Order(5)
    void testAddMessageToThread() {
        assertNotNull(testThreadId, "Thread ID must be available");
        String messageId = engine.addMessageToThread(testThreadId, "Test message");
        assertNotNull(messageId, "Message addition should return a valid ID");
    }
}
