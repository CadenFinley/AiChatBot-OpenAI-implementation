
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

public class Chatbot {

    private static OpenAiAssistantEngine assistantSelfCare;
    private static final String APIKEY = "you wish haha";
    private static final File USER_INFO = new File("user_info.txt");
    private static final File ACU_DATABASE = new File("acu_database.txt");
    private static final int RUN_TIMEOUT_SECONDS = 60;

    public static void main(String[] args) {
        assistantSelfCare = new OpenAiAssistantEngine(APIKEY);
        System.out.println("-------------------------");
        System.out.println("Setting up AI Academic Advisor...");

        String assistantId = setupAssistant();
        if (assistantId == null) {
            System.out.println("Failed to set up assistant. Exiting.");
            return;
        }

        startInteractiveChat(assistantId);
    }

    private static String setupAssistant() {
        String assistantId = assistantSelfCare.createAssistant(
                "gpt-4o-mini",
                "Personal AI Academic Advisor",
                null,
                "You are a real-time chat AI Academic Advisor for Abilene Christian University. Address the student by their first and last name based on the user info provided. Provide information about the student's academic journey, courses, and other academic-related topics. Please adress the student by their first and last name from the user info provided.",
                null,
                List.of("file_search"),
                null,
                0.2,
                0.1,
                null
        );

        if (assistantId == null) {
            System.out.println("Failed to create assistant");
            return null;
        }

        String fileId = assistantSelfCare.uploadFile(USER_INFO, "assistants");
        String fileId1 = assistantSelfCare.uploadFile(ACU_DATABASE, "assistants");

        if (fileId == null || fileId1 == null) {
            System.out.println("Failed to upload one or more files");
            return null;
        }

        Map<String, String> fileMetadata = new HashMap<>();
        fileMetadata.put(fileId, "This fileID is associated with the user info");
        fileMetadata.put(fileId1, "This fileID is associated with the ACU database");

        String vectorStoreId = assistantSelfCare.createVectorStore(
                "User Files",
                Arrays.asList(fileId, fileId1),
                null,
                null,
                fileMetadata
        );

        if (vectorStoreId == null) {
            System.out.println("Failed to create vector store");
            return null;
        }

        Map<String, Object> toolResources = new HashMap<>();
        Map<String, List<String>> fileSearch = new HashMap<>();
        fileSearch.put("vector_store_ids", List.of(vectorStoreId));
        toolResources.put("file_search", fileSearch);

        boolean updateSuccess = assistantSelfCare.updateAssistant(
                assistantId,
                toolResources
        );

        if (!updateSuccess) {
            System.out.println("Failed to update assistant with vector store");
            return null;
        }

        System.out.println("Assistant setup successfully with ID: " + assistantId);
        return assistantId;
    }

    private static void startInteractiveChat(String assistantId) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String threadId = null;

        System.out.println("\n=== ACU AI Academic Advisor Chat ===");
        System.out.println("Type 'exit' to end the conversation");

        try {
            String userInput;
            while (true) {
                System.out.print("\nYou: ");
                userInput = reader.readLine().trim();

                if (userInput.equalsIgnoreCase("exit")) {
                    break;
                }

                if (userInput.isEmpty()) {
                    continue;
                }

                if (threadId == null) {
                    List<JSONObject> messages = List.of(
                            new JSONObject()
                                    .put("role", "user")
                                    .put("content", userInput)
                    );
                    threadId = assistantSelfCare.createThread(messages, null, null);
                    if (threadId == null) {
                        System.out.println("Failed to create thread. Please try again.");
                        continue;
                    }
                } else {
                    String messageId = assistantSelfCare.addMessageToThread(threadId, userInput);
                    if (messageId == null) {
                        System.out.println("Failed to send message. Please try again.");
                        continue;
                    }
                }

                String runId = assistantSelfCare.createRun(
                        threadId,
                        assistantId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

                if (runId == null) {
                    System.out.println("Failed to create run. Please try again.");
                    continue;
                }

                boolean completed = assistantSelfCare.waitForRunCompletion(threadId, runId, RUN_TIMEOUT_SECONDS);

                if (!completed) {
                    System.out.println("The assistant encountered an issue. Please try again.");
                    continue;
                }
                List<String> retrievedMessages = assistantSelfCare.listMessages(threadId, runId);
                if (retrievedMessages != null && !retrievedMessages.isEmpty()) {
                    System.out.println("\nAdvisor: " + retrievedMessages.get(0));
                } else {
                    System.out.println("No response received. Please try again.");
                }
            }

            if (threadId != null) {
                assistantSelfCare.deleteResource("threads", threadId);
            }

        } catch (IOException e) {
            System.out.println("Error reading input: " + e.getMessage());
        }
    }
}
