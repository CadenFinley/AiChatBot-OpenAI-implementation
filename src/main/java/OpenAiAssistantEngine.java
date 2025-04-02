
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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

    // Map to store responses by category (e.g., "run", "assistant", "thread", etc.)
    private final Map<String, List<String>> responseLog;

    // Maximum number of responses to keep per category (to avoid memory issues)
    private final int maxResponsesPerCategory;

    /**
     * Constructs a new OpenAiAssistantEngine with the specified API key.
     *
     * @param apiKey The OpenAI API key to use for authentication
     */
    public OpenAiAssistantEngine(String apiKey) {
        this.USER_API_KEY = apiKey;
        this.responseLog = new HashMap<>();
        this.maxResponsesPerCategory = 100;
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
        this.responseLog = new HashMap<>();
        this.maxResponsesPerCategory = maxResponsesPerCategory;
    }

    /*
     * Response Logging Methods
     */
    public void logResponse(String category, String response) {
        if (response == null) {
            return;
        }

        if (!responseLog.containsKey(category)) {
            responseLog.put(category, new ArrayList<>());
        }

        List<String> categoryResponses = responseLog.get(category);
        categoryResponses.add(response);
        if (categoryResponses.size() > maxResponsesPerCategory) {
            categoryResponses.remove(0);
        }
    }

    public List<String> getResponsesByCategory(String category) {
        return responseLog.getOrDefault(category, new ArrayList<>());
    }

    public String getLatestResponse(String category) {
        List<String> responses = getResponsesByCategory(category);
        if (responses.isEmpty()) {
            return null;
        }
        return responses.get(responses.size() - 1);
    }

    public void clearCategory(String category) {
        responseLog.remove(category);
    }

    public void clearAllResponses() {
        responseLog.clear();
    }

    public List<String> getCategories() {
        return new ArrayList<>(responseLog.keySet());
    }

    public static boolean testAPIKey(String apiKey) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> {
            String url = "https://api.openai.com/v1/engines";
            try {
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + apiKey);
                int responseCode = con.getResponseCode();
                return responseCode == 200;
            } catch (IOException e) {
                return false;
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (InterruptedException | ExecutionException e) {
            return false;
        } finally {
            executor.shutdown();
        }
    }

    /*
     * File Management Methods
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
                logResponse("file_upload", responseStr);
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
                logResponse("file_info", responseStr);
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

    /*
     * Vector Store Methods
     */
    public String createVectorStore(String name, List<String> fileIds, JSONObject chunkingStrategy,
            JSONObject expiresAfter, Map<String, String> metadata) {
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
                logResponse("vector_store", responseStr);
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

    public String modifyVectorStore(String vectorStoreId, JSONObject expiresAfter, Map<String, String> metadata, String name) {
        String url = "https://api.openai.com/v1/vector_stores/" + vectorStoreId;
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
            if (expiresAfter != null) {
                body.put("expires_after", expiresAfter);
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }
            if (name != null) {
                body.put("name", name);
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
                logResponse("vector_store_modify", responseStr);
                return responseStr;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to modify vector store: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to modify vector store: " + e.getMessage());
            return null;
        }
    }

    /*
     * Assistant Management Methods
     */
    public String createAssistant(String model, String name, String description, String instructions,
            String reasoningEffort, List<String> toolNames, Map<String, String> metadata,
            Double temperature, Double topP, Map<String, String> toolResources) {
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
                logResponse("assistant", responseStr);
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

    public String retrieveAssistant(String assistantId) {
        String url = "https://api.openai.com/v1/assistants/" + assistantId;
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
                logResponse("assistant_retrieve", responseStr);
                return responseStr;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to retrieve assistant: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to retrieve assistant: " + e.getMessage());
            return null;
        }
    }

    public boolean modifyAssistant(String assistantId, String description, String instructions,
            Map<String, String> metadata, String model, String name, String reasoningEffort,
            JSONObject responseFormat, Double temperature, Map<String, Object> toolResources,
            List<JSONObject> tools, Double topP) {
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
            if (description != null) {
                body.put("description", description);
            }
            if (instructions != null) {
                body.put("instructions", instructions);
            }
            if (metadata != null && !metadata.isEmpty()) {
                body.put("metadata", metadata);
            }
            if (model != null) {
                body.put("model", model);
            }
            if (name != null) {
                body.put("name", name);
            }
            if (reasoningEffort != null) {
                body.put("reasoning_effort", reasoningEffort);
            }
            if (responseFormat != null) {
                body.put("response_format", responseFormat);
            }
            if (temperature != null) {
                body.put("temperature", temperature);
            }
            if (toolResources != null && !toolResources.isEmpty()) {
                body.put("tool_resources", new JSONObject(toolResources));
            }
            if (tools != null && !tools.isEmpty()) {
                body.put("tools", tools);
            }
            if (topP != null) {
                body.put("top_p", topP);
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
                logResponse("assistant_update", responseStr);
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

    public String listAssistants(String after, String before, int limit, String order) {
        StringBuilder urlBuilder = new StringBuilder("https://api.openai.com/v1/assistants?");
        if (after != null) {
            urlBuilder.append("after=").append(after).append("&");
        }
        if (before != null) {
            urlBuilder.append("before=").append(before).append("&");
        }
        if (limit > 0) {
            urlBuilder.append("limit=").append(Math.min(limit, 100)).append("&");
        }
        if (order != null) {
            urlBuilder.append("order=").append(order);
        }

        try {
            URL obj = new URL(urlBuilder.toString());
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + USER_API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse("assistants_list", responseStr);
                return responseStr;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to list assistants: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to list assistants: " + e.getMessage());
            return null;
        }
    }

    /*
     * Thread Management Methods
     */
    public String createThread(List<JSONObject> messages, Map<String, String> toolResources,
            Map<String, String> metadata) {
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
                logResponse("thread", responseStr);
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
                logResponse("message_add", responseStr);
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

    public List<String> listMessages(String threadId, String runId) {
        StringBuilder urlBuilder = new StringBuilder("https://api.openai.com/v1/threads/" + threadId + "/messages");
        if (runId != null) {
            urlBuilder.append("?run_id=").append(runId);
        }

        try {
            URL obj = new URL(urlBuilder.toString());
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("Authorization", "Bearer " + USER_API_KEY);
            con.setRequestProperty("Content-Type", "application/json");
            con.setRequestProperty("OpenAI-Beta", "assistants=v2");

            try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                String responseStr = response.toString();
                logResponse("messages", responseStr);
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

    /*
     * Run Management Methods
     */
    public String createRun(String threadId, String assistantId, String model, String reasoningEffort,
            String instructions, String additionalInstructions, List<JSONObject> additionalMessages,
            List<JSONObject> tools, Map<String, String> metadata, Double temperature, Double topP,
            Boolean stream, Integer maxPromptTokens, Integer maxCompletionTokens,
            JSONObject truncationStrategy, JSONObject toolChoice, Boolean parallelToolCalls,
            JSONObject responseFormat) {
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
                logResponse("run", responseStr);
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
                logResponse("run_status", responseStr);
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

    public String retrieveRunStatus(String threadId) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/runs";
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
                JSONObject jsonResponse = new JSONObject(responseStr);
                if (jsonResponse.getJSONArray("data").length() > 0) {
                    return jsonResponse.getJSONArray("data").getJSONObject(0).toString();
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to retrieve run status: " + e.getMessage());
            return null;
        }
    }

    public boolean waitForRunCompletion(String threadId, String runId, int timeoutSeconds, int pollIntervalMiliSeconds) {
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
                Thread.sleep(pollIntervalMiliSeconds); // Poll every second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Polling interrupted: " + e.getMessage());
                return false;
            }
        }

        System.out.println("Run timed out after " + timeoutSeconds + " seconds");
        return false;
    }

    public String cancelRun(String threadId, String runId) {
        String url = "https://api.openai.com/v1/threads/" + threadId + "/runs/" + runId + "/cancel";
        String apiKey = USER_API_KEY;
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("POST");
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
                logResponse("run_cancel", responseStr);
                return responseStr;
            } catch (IOException e) {
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(con.getErrorStream()))) {
                    String inputLine;
                    StringBuilder errorResponse = new StringBuilder();
                    while ((inputLine = errorReader.readLine()) != null) {
                        errorResponse.append(inputLine);
                    }
                    System.out.println("Failed to cancel run: " + errorResponse.toString());
                } catch (IOException ex) {
                    System.out.println("Failed to read error response: " + ex.getMessage());
                }
                return null;
            }
        } catch (IOException e) {
            System.out.println("Failed to cancel run: " + e.getMessage());
            return null;
        }
    }

    /*
     * Resource Management Methods
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
