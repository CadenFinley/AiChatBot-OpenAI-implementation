# OpenAiAssistantEngine

## Overview
OpenAiAssistantEngine is a Java library designed to communicate with OpenAI's Assistant API (beta, v2). It provides custom methods to:
- Upload files.
- Create vector stores.
- Create and manage assistants.
- Create threads to hold conversation flows.
- Run queries and retrieve responses.

## Usage

### 1. Instantiate the Engine
Provide your OpenAI API key when constructing the engine:
```java
OpenAiAssistantEngine engine = new OpenAiAssistantEngine("YOUR_API_KEY");
```
This key is used for all subsequent requests.

### 2. Upload Files
You can upload files for the assistant to reference:
```java
File myFile = new File("path/to/file.txt");
String fileId = engine.uploadFile(myFile, "assistants");
```
If successful, `fileId` will be the unique identifier for the uploaded file.

### 3. Create Vector Store
Once files are uploaded, you can create a vector store:
```java
String vectorStoreId = engine.createVectorStore(
    "MyVectorStore", 
    List.of(fileId), 
    null, 
    null, 
    null
);
```
This groups your files into a searchable index.

### 4. Create an Assistant
Set up a new assistant with a specified model and other optional parameters:
```java
String assistantId = engine.createAssistant(
    "gpt-4-1106-preview",
    "My Assistant",
    "An example assistant",
    "Global instructions here",
    "auto",
    List.of("file_search"),
    null,
    0.7,
    1.0,
    null
);
```

### 5. Create & Use Threads
A thread is a container for conversation messages:
```java
// Creating a new thread with an initial user message
List<JSONObject> initialMessages = List.of(
    new JSONObject().put("role", "user").put("content", "Hello!")
);
String threadId = engine.createThread(initialMessages, null, null);
```
Adding messages:
```java
engine.addMessageToThread(threadId, "How are you?");
```
Then create a run to get the assistant’s response and poll for completion:
```java
String runId = engine.createRun(
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
boolean completed = engine.waitForRunCompletion(threadId, runId, 60);
```
Retrieve the assistant’s messages:
```java
List<String> messages = engine.listMessages(threadId, runId);
messages.forEach(System.out::println);
```

### 6. Update an Assistant
You can change assistant properties, such as adding more tool resources:
```java
Map<String, Object> toolResources = new HashMap<>();
toolResources.put("file_search", Map.of("vector_store_ids", List.of(vectorStoreId)));
boolean updateSuccess = engine.updateAssistant(assistantId, toolResources);
```

### 7. Housekeeping
- You can delete resources if needed:
```java
engine.deleteResource("threads", threadId);
engine.deleteResource("assistants", assistantId);
```

## Contributing
Contributions are welcome. Please fork this repository and submit pull requests.

## License
Choose your license and specify it here.
