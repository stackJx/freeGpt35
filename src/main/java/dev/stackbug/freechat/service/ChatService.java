package dev.stackbug.freechat.service;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import cn.hutool.log.LogFactory;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.stackbug.freechat.model.ChatCompletionRequest;
import dev.stackbug.freechat.model.MessageDTO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatService {

    private static final Log    log = LogFactory.get();
    @Value("${api.url}")
    private              String apiUrl;

    @Value("${base.url}")
    private String baseUrl;

    @Value("${proxy.host}")
    private String proxyHost;

    @Value("${proxy.port}")
    private       int          proxyPort;
    private       String       token;
    private       String       oaiDeviceId;
    private final ObjectMapper objectMapper = new ObjectMapper();


    public void getNewSessionId() throws JsonProcessingException {
        String      newDeviceId = UUID.randomUUID().toString();
        HttpRequest request     = HttpUtil.createPost("https://chat.openai.com/backend-anon/sentinel/chat-requirements");
        request.header("Oai-Device-Id", newDeviceId);
        request.setHttpProxy(proxyHost, proxyPort);
        request.body("{}");
        HttpResponse response = request.execute();
        if (response.isOk()) {
            JsonNode jsonNode = objectMapper.readTree(response.body());
            oaiDeviceId = newDeviceId;
            token       = jsonNode.get("token").asText();

        } else {
            System.err.println("Failed to refresh session ID. Status: " + response.getStatus());
            System.err.println("Response: " + response.body());
        }
    }

    public HttpRequest createConfiguredRequest(String url) {
        HttpRequest request = HttpUtil.createPost(url);
        request.header("accept", "*/*");
        request.header("accept-language", "en-US,en;q=0.9");
        request.header("Accept-Encoding", "gzip, deflate, br, zstd");
        request.header("cache-control", "no-cache");
        request.header("content-type", "application/json");
        request.header("oai-language", "en-US");
        request.header("origin", baseUrl);
        request.header("pragma", "no-cache");
        request.header("referer", baseUrl + "/");
        request.header("sec-ch-ua", "\"Google Chrome\";v=\"123\", \"Not:A-Brand\";v=\"8\", \"Chromium\";v=\"123\"");
        request.header("sec-ch-ua-mobile", "?0");
        request.header("sec-ch-ua-platform", "\"Windows\"");
        request.header("sec-fetch-dest", "empty");
        request.header("sec-fetch-mode", "cors");
        request.header("sec-fetch-site", "same-origin");
        request.header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36");
        request.setHttpProxy(proxyHost, proxyPort);

        return request;
    }


    public SseEmitter handleChatCompletion(ChatCompletionRequest request) {
        JSONObject  requestBody = createRequestBody(request);
        HttpRequest httpRequest = createConfiguredRequest(apiUrl, requestBody);

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        CompletableFuture.supplyAsync(() -> {
            JSONObject lastResponse = null;
            try {
                HttpResponse response = httpRequest.execute();

                String fullContent = "";
                String requestId   = GenerateCompletionId("chatcmpl-");
                Date   created     = new Date();

                if (response.isOk()) {
                    try (InputStream inputStream = response.bodyStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            // 使用正则表达式过滤掉心跳消息
                            if (!line.matches("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{6}$")) {
                                // 使用正则表达式删除 data: 前缀
                                line = line.replaceFirst("^data: ", "");

                                // 如果行是空的,则跳过本次循环
                                if (line.isEmpty()) {
                                    continue;
                                }

                                // 如果行内容为 [DONE],则表示数据读取完毕,完成发送并退出循环
                                if ("[DONE]".equals(line)) {
                                    break;
                                }

                                // 使用链式操作解析 JSONObject,获取 "message" 对象
                                JSONObject messageObj = JSONUtil.parseObj(line).getJSONObject("message");
                                // 如果 "message" 对象为空,则跳过本次循环
                                if (messageObj == null) {
                                    continue;
                                }

                                // 获取 "author" 对象,并从中获取 "role" 字段的值
                                JSONObject authorObj = messageObj.getJSONObject("author");
                                String     role      = authorObj.getStr("role");

                                // 如果作者角色是 "user",则跳过本次循环
                                if (!"assistant".equals(role)) {
                                    continue;
                                }

                                // 获取 "content" 对象,并从中获取 "parts" 数组的第一个元素作为消息内容
                                JSONObject contentObj = messageObj.getJSONObject("content");
                                String     content    = contentObj.getJSONArray("parts").get(0).toString();

                                // 如果消息内容为空,则跳过本次循环
                                if (ObjectUtil.isEmpty(content)) {
                                    continue;
                                }

                                // 构建一个 JSONObject 用于响应
                                JSONObject res = new JSONObject();
                                res.set("id", requestId) // 设置请求 ID
                                        .set("created", created) // 设置创建时间
                                        .set("object", "chat.completion.chunk") // 设置对象类型
                                        .set("model", "gpt-3.5-turbo"); // 设置模型名称

                                // 创建一个 JSONArray 用于存储 choices
                                JSONArray choices = new JSONArray();

                                // 创建一个 JSONObject 用于存储 choice
                                JSONObject choice = new JSONObject();
                                JSONObject delta  = new JSONObject();
                                delta.set("content", content); // 设置 delta 内容
                                choice.set("delta", delta) // 设置 delta 对象
                                        .set("index", 0) // 设置 choice 索引
                                        .set("finish_reason", null); // 设置完成原因

                                // 将 choice 添加到 choices 数组中
                                choices.add(choice);

                                // 将 choices 添加到 res 对象中
                                res.set("choices", choices);

                                // 记录响应数据
                                log.info(res.toString());

                                // 更新 lastResponse 变量
                                lastResponse = res;

                                // 更新 fullContent 变量
//                            fullContent = content.length() > fullContent.length() ? content : fullContent;
                            }

                        }
                        if (lastResponse != null) {
                            emitter.send(SseEmitter.event().name("message").data(lastResponse.toString(), MediaType.APPLICATION_JSON));
                        }
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                } else {
                    emitter.completeWithError(new RuntimeException("处理聊天完成失败。状态：" + response.getStatus()));
                }
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
            return null;
        });

        return emitter;
    }

    private JSONObject createRequestBody(ChatCompletionRequest request) {
        JSONArray messagesArray = JSONUtil.createArray();
        for (MessageDTO message : request.getMessages()) {
            messagesArray.add(JSONUtil.createObj()
                    .put("author", JSONUtil.createObj().put("role", message.getRole()))
                    .put("content", JSONUtil.createObj().put("content_type", "text").put("parts", Arrays.asList(message.getContent()))));
        }
        return JSONUtil.createObj()
                .set("action", "next")
                .set("messages", messagesArray)
                .set("parent_message_id", UUID.randomUUID().toString())
                .set("model", "text-davinci-002-render-sha")
                .set("timezone_offset_min", -180)
                .set("suggestions", JSONUtil.createArray())
                .set("history_and_training_disabled", true)
                .set("conversation_mode", JSONUtil.createObj().set("kind", "primary_assistant"))
                .set("websocket_request_id", UUID.randomUUID().toString());
    }

    private HttpRequest createConfiguredRequest(String url, JSONObject requestBody) {
        HttpRequest request = HttpUtil.createPost(url);
        request.body(requestBody.toString());
        request.header("oai-device-id", oaiDeviceId);
        request.header("openai-sentinel-chat-requirements-token", token);
        request.header("openai-sentinel-chat-requirements-token", token);
        request.setHttpProxy(proxyHost, proxyPort);
        return request;
    }


    private String GenerateCompletionId(String prefix) {
        String        characters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random        random     = new Random();
        StringBuilder sb         = new StringBuilder(prefix);
        for (int i = 0; i < 28; i++) {
            sb.append(characters.charAt(random.nextInt(characters.length())));
        }
        return sb.toString();
    }

    @Scheduled(fixedDelayString = "${refresh.interval}")
    public void refreshSessionId() {
        try {
            String publicIp = getCurrentPublicIP();
            System.out.println("Public IP address via proxy is: " + publicIp);
            getNewSessionId();
            getNewSessionId();
            System.out.println("Successfully refreshed session ID: " + token);
        } catch (Exception e) {
            System.err.println("Failed to refresh session ID: " + e.getMessage());
        }
    }

    public String getCurrentPublicIP() {
        HttpRequest request = HttpUtil.createGet("https://api.ipify.org");
        request.setHttpProxy(proxyHost, proxyPort);
        HttpResponse response = request.execute();
        return response.body();
    }
}
