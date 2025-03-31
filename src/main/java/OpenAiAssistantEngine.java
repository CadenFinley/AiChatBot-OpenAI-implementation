
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A Java client for interacting with OpenAI's Assistant API (v2). This class
 * provides methods to create and manage assistants, threads, runs, and handle
 * file operations with the OpenAI API.
 *
 * All methods in this class require a valid OpenAI API key to function.
 *
 * @author Caden Finley
 * @version 1.0
 */
public class OpenAiAssistantEngine {

    /**
     * The OpenAI API key used for authentication
     */
    private final String USER_API_KEY;

    // List to store all responses (for logging purposes)
    private final ArrayList<Map<String, Object>> responseDataMap;

    private final int maxResponsesStored;

    /**
     * Constructs a new OpenAiAssistantEngine with the specified API key.
     *
     * @param apiKey The OpenAI API key to use for authentication
     */
    public OpenAiAssistantEngine(String apiKey) {
        this.USER_API_KEY = apiKey;
        this.responseDataMap = new ArrayList<>();
        this.maxResponsesStored = 100;
    }

    /**
     * Constructs a new OpenAiAssistantEngine with the specified API key and
     * maximum responses per category.
     *
     * @param apiKey The OpenAI API key to use for authentication
     * @param maxResponsesPerCategory Maximum number of responses to store per
     * category
     */
    public OpenAiAssistantEngine(String apiKey, int maxResponsesPerCategory) {
        this.USER_API_KEY = apiKey;
        this.responseDataMap = new ArrayList<>();
        this.maxResponsesStored = 100;
    }

    /**
     * Logs and parses a JSON API response, storing it in the response data map.
     *
     * @param response The JSON response string to log
     */
    public void logResponse(String response) {
        if (response == null) {
            return; // Don't log null responses
        }

        try {
            // Create a new map for this response (don't reuse the same map)
            Map<String, Object> responseData = new HashMap<>();
            JSONObject jsonResponse = new JSONObject(response);

            // Process all keys in the JSON response
            for (String key : jsonResponse.keySet()) {
                Object value = jsonResponse.get(key);
                responseData.put(key, value);
            }

            // Store the full response string as well
            responseData.put("full_response", response);

            // Add to the response data map
            responseDataMap.add(responseData);

            // If we exceed the maximum number of responses, remove the oldest one
            if (responseDataMap.size() > maxResponsesStored) {
                responseDataMap.remove(0);
            }
        } catch (JSONException e) {
            System.out.println("Failed to parse response as JSON: " + e.getMessage());
        }
    }

    /**
     * Gets a value from the most recent response by key.
     *
     * @param key The key to look up in the response
     * @return The value as a string, or null if not found
     */
    public String getDataFromLastResponse(String key) {
        if (responseDataMap.isEmpty()) {
            return null;
        }

        Map<String, Object> lastResponse = responseDataMap.get(responseDataMap.size() - 1);
        Object value = lastResponse.get(key);

        return value != null ? value.toString() : null;
    }

    /**
     * Gets a specific value from a response by index and key.
     *
     * @param index The index of the response (0 is oldest)
     * @param key The key to look up in the response
     * @return The value as a string, or null if not found
     */
    public String getDataFromResponse(int index, String key) {
        if (index < 0 || index >= responseDataMap.size()) {
            return null;
        }

        Map<String, Object> response = responseDataMap.get(index);
        Object value = response.get(key);

        return value != null ? value.toString() : null;
    }

    /**
     * Gets the most recent response as a Map.
     *
     * @return The most recent response Map, or null if none exists
     */
    public Map<String, Object> getLastResponseData() {
        if (responseDataMap.isEmpty()) {
            return null;
        }
        return responseDataMap.get(responseDataMap.size() - 1);
    }

    public List<Map<String, Object>> getResponseDataMap() {
        return responseDataMap;
    }

    /**
     * Uploads a file to OpenAI's API for processing.
     *
     * @param file The file to upload
     * @param purpose The purpose of the file (e.g., "fine-tune")
     * @return The ID of the uploaded file, or null if upload failed
     */
    public String uploadFile(File file, String purpose) {
        String url = "https://api.openai.com/v1/files";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "multipart/form-data; boundary=---Boundary");
            con.setDoOutput(true);

            String boundary = "---Boundary";
            StringBuilder body = new StringBuilder();
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"purpose\"\r\n\r\n");
            body.append(purpose).append("\r\n");
            body.append("--").append(boundary).append("\r\n");
            body.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
            body.append("Content-Type: application/octet-stream\r\n\r\n");

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
                try (BufferedReader fileReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        writer.write(line);
                    }
                }
                writer.write("\r\n--");
                writer.write(boundary);
                writer.write("--\r\n");
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to upload file: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to upload file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a vector store for storing and querying embeddings.
     *
     * @param name The name of the vector store
     * @param fileIds List of file IDs to include in the vector store
     * @param chunkingStrategy JSON object defining how to chunk the files
     * @param expiresAfter JSON object defining when the vector store should
     * expire
     * @param metadata Additional metadata for the vector store
     * @return The ID of the created vector store, or null if creation failed
     */
    public String createVectorStore(String name, List<String> fileIds, JSONObject chunkingStrategy, JSONObject expiresAfter, Map<String, String> metadata) {
        String url = "https://api.openai.com/v1/vector_stores";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            if (name != null) {
                body.put("name", name);
            }
            if (fileIds != null && !fileIds.isEmpty()) {
                body.put("file_ids", fileIds);
            }
            if (chunkingStrategy != null) {
                body.put("chunking_strategy", chunkingStrategy);
            }
            if (expiresAfter != null) {
                body.put("expires_after", expiresAfter);
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to create vector store: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException | JSONException e) {
            System.out.println("Failed to create vector store: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new assistant with the specified parameters.
     *
     * @param model The model to use (e.g., "gpt-4-1106-preview")
     * @param name Optional name for the assistant
     * @param description Optional description of the assistant
     * @param instructions Base instructions for the assistant
     * @param reasoningEffort Level of reasoning effort (e.g., "auto", "high")
     * @param toolNames List of tool names to enable (e.g., "code_interpreter",
     * "file_search")
     * @param metadata Additional metadata for the assistant
     * @param temperature Sampling temperature (0-2)
     * @param topP Top-p sampling parameter
     * @param toolResources Additional resources for tools
     * @return The ID of the created assistant, or null if creation failed
     */
    public String createAssistant(String model, String name, String description, String instructions, String reasoningEffort, List<String> toolNames, Map<String, String> metadata, Double temperature, Double topP, Map<String, String> toolResources) {
        String url = "https://api.openai.com/v1/assistants";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("model", model);
            if (name != null) {
                body.put("name", name);
            }
            if (description != null) {
                body.put("description", description);
            }
            if (instructions != null) {
                body.put("instructions", instructions);
            }
            if (reasoningEffort != null) {
                body.put("reasoning_effort", reasoningEffort);
            }
            if (toolNames != null && !toolNames.isEmpty()) {
                List<Map<String, Object>> tools = new ArrayList<>();
                for (String toolName : toolNames) {
                    Map<String, Object> tool = new HashMap<>();
                    tool.put("type", toolName); // Ensure toolName is one of the supported values: 'code_interpreter', 'function', 'file_search'
                    tools.add(tool);
                }
                body.put("tools", tools);
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (topP != null) {
                body.put("top_p", topP);
            }
            if (toolResources != null) {
                body.put("tool_resources", new JSONObject(toolResources));
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to create assistant: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException | JSONException e) {
            System.out.println("Failed to create assistant: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new thread for conversation.
     *
     * @param messages Optional initial messages for the thread
     * @param toolResources Additional resources for tools
     * @param metadata Additional metadata for the thread
     * @return The ID of the created thread, or null if creation failed
     */
    public String createThread(List<JSONObject> messages, Map<String, String> toolResources, Map<String, String> metadata) {
        String url = "https://api.openai.com/v1/threads";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            if (messages != null && !messages.isEmpty()) {
                body.put("messages", messages);
            }
            if (toolResources != null && !toolResources.isEmpty()) {
                body.put("tool_resources", new JSONObject(toolResources));
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to create thread: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException | JSONException e) {
            System.out.println("Failed to create thread: " + e.getMessage());
            return null;
        }
    }

    /**
     * Creates a new run within a thread.
     *
     * @param threadId The ID of the thread
     * @param assistantId The ID of the assistant to use
     * @param model Optional override for the model
     * @param reasoningEffort Optional override for reasoning effort
     * @param instructions Optional override for instructions
     * @param additionalInstructions Additional one-time instructions
     * @param additionalMessages Additional context messages
     * @param tools List of tools to use
     * @param metadata Additional metadata for the run
     * @param temperature Optional override for temperature
     * @param topP Optional override for top-p
     * @param stream Whether to stream the response
     * @param maxPromptTokens Maximum tokens for the prompt
     * @param maxCompletionTokens Maximum tokens for the completion
     * @param truncationStrategy How to truncate if needed
     * @param toolChoice How to choose between tools
     * @param parallelToolCalls Whether to allow parallel tool calls
     * @param responseFormat Format for the response
     * @return The ID of the created run, or null if creation failed
     */
    public String createRun(String threadId, String assistantId, String model, String reasoningEffort, String instructions, String additionalInstructions, List<JSONObject> additionalMessages, List<JSONObject> tools, Map<String, String> metadata, Double temperature, Double topP, Boolean stream, Integer maxPromptTokens, Integer maxCompletionTokens, JSONObject truncationStrategy, JSONObject toolChoice, Boolean parallelToolCalls, JSONObject responseFormat) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/runs";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("assistant_id", assistantId);
            if (model != null) {
                body.put("model", model);
            }
            if (reasoningEffort != null) {
                body.put("reasoning_effort", reasoningEffort);
            }
            if (instructions != null) {
                body.put("instructions", instructions);
            }
            if (additionalInstructions != null) {
                body.put("additional_instructions", additionalInstructions);
            }
            if (additionalMessages != null && !additionalMessages.isEmpty()) {
                body.put("additional_messages", additionalMessages);
            }
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (topP != null) {
                body.put("top_p", topP);
            }
            if (stream != null) {
                body.put("stream", stream);
            }
            if (maxPromptTokens != null) {
                body.put("max_prompt_tokens", maxPromptTokens);
            }
            if (maxCompletionTokens != null) {
                body.put("max_completion_tokens", maxCompletionTokens);
            }
            if (truncationStrategy != null) {
                body.put("truncation_strategy", truncationStrategy);
            }
            if (toolChoice != null) {
                body.put("tool_choice", toolChoice);
            }
            if (parallelToolCalls != null) {
                body.put("parallel_tool_calls", parallelToolCalls);
            }
            if (responseFormat != null) {
                body.put("response_format", responseFormat);
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to create run: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException | JSONException e) {
            System.out.println("Failed to create run: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves the status and details of a run.
     *
     * @param threadId The ID of the thread
     * @param runId The ID of the run
     * @return JSON string containing run details, or null if retrieval failed
     */
    public String retrieveRun(String threadId, String runId) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId;
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                return responseStr;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to retrieve run: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to retrieve run: " + e.getMessage());
            return null;
        }
    }

    /**
     * Lists all messages in a thread for a specific run.
     *
     * @param threadId The ID of the thread
     * @param runId The ID of the run
     * @return List of message contents, or null if retrieval failed
     */
    public List<String> listMessages(String threadId, String runId) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/messages";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url + "?run_id=" + runId); // Add run_id as a query parameter
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                List<String> messages = new ArrayList<>();
                for (Object messageObj : jsonResponse.getJSONArray("data")) {
                    JSONObject message = (JSONObject) messageObj;
                    for (Object contentObj : message.getJSONArray("content")) {
                        JSONObject content = (JSONObject) contentObj;
                        if (content.getString("type").equals("text")) {
                            messages.add(content.getJSONObject("text").getString("value"));
                        }
                    }
                }
                return messages;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to list messages: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to list messages: " + e.getMessage());
            return null;
        }
    }

    /**
     * Retrieves information about an uploaded file.
     *
     * @param fileId The ID of the file to retrieve
     * @return JSON object containing file details, or null if retrieval failed
     */
    public JSONObject retrieveFile(String fileId) {
        String url = "https://api.openai.com/v1/files/" + fileId;
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                return new JSONObject(responseStr);
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to retrieve file: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to retrieve file: " + e.getMessage());
            return null;
        }
    }

    /**
     * Updates an existing assistant's properties.
     *
     * @param assistantId The ID of the assistant to update
     * @param toolResources New tool resources to set
     * @return true if update was successful, false otherwise
     */
    public boolean updateAssistant(String assistantId, Map<String, Object> toolResources) {
        String url = "https://api.openai.com/v1/assistants/" + assistantId;
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            if (toolResources != null && !toolResources.isEmpty()) {
                body.put("tool_resources", new JSONObject(toolResources));
            }

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                return true;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to update assistant: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return false;
            }
        } catch (IOException e) {
            System.out.println("Failed to update assistant: " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a new message to an existing thread.
     *
     * @param threadId The ID of the thread
     * @param content The content of the message
     * @return The ID of the created message, or null if creation failed
     */
    public String addMessageToThread(String threadId, String content) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/messages";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            con.setDoOutput(true);

            JSONObject body = new JSONObject();
            body.put("role", "user");
            body.put("content", content);

            try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                writer.write(body.toString());
                writer.flush();
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse(responseStr);
                JSONObject jsonResponse = new JSONObject(responseStr);
                return jsonResponse.getString("id");
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to add message: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to add message: " + e.getMessage());
            return null;
        }
    }

    /**
     * Waits for a run to complete, polling its status at regular intervals.
     *
     * @param threadId The ID of the thread
     * @param runId The ID of the run
     * @param timeoutSeconds Maximum time to wait in seconds
     * @return true if run completed successfully, false if it failed or timed
     * out
     */
    public boolean waitForRunCompletion(String threadId, String runId, int timeoutSeconds) {
        long startTime = System.currentTimeMillis();
        long timeoutMillis = timeoutSeconds * 1000L;

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            String runResponse = retrieveRun(threadId, runId);
            if (runResponse == null) {
                System.out.println("Failed to retrieve run status");
                return false;
            }

            JSONObject jsonResponse = new JSONObject(runResponse);
            String status = jsonResponse.getString("status");

            if (status.equals("completed")) {
                return true;
            } else if (status.equals("failed") || status.equals("cancelled") || status.equals("expired")) {
                System.out.println("Run ended with status: " + status);
                if (jsonResponse.has("last_error")) {
                    System.out.println("Error: " + jsonResponse.getJSONObject("last_error").toString());
                }
                return false;
            }

            try {
                Thread.sleep(1000); // Poll every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Polling interrupted: " + e.getMessage());
                return false;
            }
        }

        System.out.println("Run timed out after " + timeoutSeconds + " seconds");
        return false;
    }

    /**
     * Deletes a resource (assistant, thread, message, or file) from OpenAI.
     *
     * @param resourceType The type of resource to delete (e.g., "assistants",
     * "threads", "files")
     * @param resourceId The ID of the resource to delete
     * @return true if deletion was successful, false otherwise
     */
    public boolean deleteResource(String resourceType, String resourceId) {
        String url = "https://api.openai.com/v1/" + resourceType + "/" + resourceId;
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("DELETE");
            con.setRequestProperty("Authorization", "Bearer " + apiKey);
            con.setRequestProperty("Content-Type", "application/json");
            if (!resourceType.equals("files")) {
                con.setRequestProperty("OpenAI-Beta", "assistants=v2");
            }

            int responseCode = con.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                return true;
            } else {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    logResponse("Failed to delete " + resourceType + ": " + errorResponse.toString());
                    System.out.println("Failed to delete " + resourceType + ": " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return false;
            }
        } catch (IOException e) {
            System.out.println("Failed to delete " + resourceType + ": " + e.getMessage());
            return false;
        }
    }
}
