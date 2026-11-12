package org.telegram.messenger;

import android.os.Handler;
import android.os.Looper;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;

public class PluginManager {
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String API_KEY = "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";
    private static final String MODEL = "llama-3.3-70b-versatile";
    private final OkHttpClient client = new OkHttpClient();

    public interface Callback {
        void onResult(boolean success, String message, String generatedCode);
    }

    public void generatePlugin(String prompt, Callback callback) {
        String sysPrompt = "Ты — генератор плагинов для Telegram-мода на Lua. Пользователь опишет плагин. Ты должен выдать ТОЛЬКО чистый код на Lua (без markdown, без слов 'Вот код', без объяснений). Код должен использовать функции мода. Если запрос бредовый, верни 'ERROR: причина'.";
        
        try {
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "system").put("content", sysPrompt));
            messages.put(new JSONObject().put("role", "user").put("content", prompt));

            JSONObject jsonBody = new JSONObject();
            jsonBody.put("model", MODEL);
            jsonBody.put("messages", messages);
            jsonBody.put("temperature", 0.2);

            RequestBody body = RequestBody.create(jsonBody.toString(), MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new okhttp3.Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false, "Ошибка сети", null));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.isSuccessful()) {
                        String resBody = response.body().string();
                        try {
                            JSONObject json = new JSONObject(resBody);
                            String code = json.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                            // Очистка от markdown
                            code = code.replace("```lua", "").replace("```", "").trim();
                            
                            final String finalCode = code;
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (finalCode.startsWith("ERROR:")) {
                                    callback.onResult(false, finalCode, null);
                                } else {
                                    callback.onResult(true, "Успешно", finalCode);
                                }
                            });
                        } catch (Exception e) {
                            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false, "Ошибка парсинга", null));
                        }
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> callback.onResult(false, "Ошибка API Groq", null));
                    }
                }
            });
        } catch (Exception e) {
            callback.onResult(false, "Ошибка создания запроса", null);
        }
    }
}
