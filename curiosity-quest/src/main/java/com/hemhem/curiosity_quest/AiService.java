package com.hemhem.curiosity_quest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiService {

    @Value("${app.openai.api-key}")
    private String apiKey;

    @Autowired
    private QuestRepository questRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    public String getQuest(String question) throws IllegalArgumentException {
        if (!isValidQuestion(question)) {
            throw new IllegalArgumentException("意味のある質問ではありません。");
        }

        // AIへのクエストマップ生成指示
        String prompt = String.format("""
            あなたは優秀な教育者であり、生徒の知的好奇心を刺激する専門家です。
            生徒からの質問「%s」を起点として、答えを直接教えるのではなく、生徒自身が多角的な視点から探求したくなるような「問い」を複数生成してください。

            # 指示
            - 各ステップ（ノード）には、以下の教科リストから最も関連性の高い教科を一つだけ割り当ててください。
            - 教科リスト: [数学, 国語, 地理, 歴史, 化学, 物理, 生物, 地学, 経済学, 政治学, 哲学, 心理学, 芸術]
            - 最初の問いから、少なくとも2つ以上の異なる教科に枝分かれさせてください。
            - 各ステップは、事実を述べるのではなく、必ず「問いかけ」の形式にしてください。
            - 全体で5〜7個のステップ（ノード）になるように構成してください。

            # 出力形式
            - 応答は、必ず以下の構造に従った有効なJSON配列の形式にしてください。
            - JSON配列以外の余計な文字列や説明は一切含めないでください。

            [
              {"id": "1", "type": "input", "data": {"label": "探求の始まり: [ユーザーの質問]", "subject": "総合"}},
              {"id": "2", "data": {"label": "[生成された問い1]", "subject": "[関連する教科]"}},
              {"id": "3", "data": {"label": "[生成された問い2]", "subject": "[関連する教科]"}},
              {"id": "e1-2", "source": "1", "target": "2", "animated": true}
            ]
            """, question);

        String aiResponseJson = callOpenAI(prompt, true);

        // AI応答を解析し、データベースに保存
        try {
            JsonNode root = mapper.readTree(aiResponseJson);
            List<JsonNode> nodes = new ArrayList<>();
            List<JsonNode> edges = new ArrayList<>();
            for (JsonNode node : root) {
                if (node.has("source")) {
                    edges.add(node);
                } else {
                    nodes.add(node);
                }
            }

            Quest newQuest = new Quest();
            newQuest.setQuestion(question);
            newQuest.setNodesJson(mapper.writeValueAsString(nodes));
            newQuest.setEdgesJson(mapper.writeValueAsString(edges));
            questRepository.save(newQuest);

        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse AI response or save quest: " + e.getMessage());
            e.printStackTrace(); // Keep stack trace for debugging persistence issues
        }

        return aiResponseJson;
    }

    // AIに質問が妥当か判断させる
    private boolean isValidQuestion(String question) {
        String validationPrompt = String.format(
                """
                以下のテキストは、学習探求のきっかけとなる具体的で意味のある「質問」ですか？
                単なる挨拶、無意味な単語、短すぎる単語の場合は「NO」とだけ答えてください。
                具体的な質問の場合は「YES」とだけ答えてください。
    
                テキスト: "%s"
                """, question);

        String response = callOpenAI(validationPrompt, false);
        return response.trim().toUpperCase().contains("YES");
    }

    // 特定の問いに対する概要と穴埋め問題3問を生成させる
    public String getQuestDetails(String questText) {
        try {
            String summaryPrompt = String.format("「%s」という問いに対する答えの概要を、小中学生にも分かりやすいように300文字程度で簡潔に説明してください。", questText);
            String summary = callOpenAI(summaryPrompt, false);

            String quizPrompt = String.format(
                    """
                    以下の文章を読んで、重要なキーワードを[BLANK]に置き換えた穴埋め問題を3問作成してください。
                    応答は必ず以下の形式の有効なJSONオブジェクトにしてください。JSON以外の文字列は絶対に含めないでください。
                    {
                      "quizzes": [
                        {"quiz": "問題文1", "answer": "答え1"},
                        {"quiz": "問題文2", "answer": "答え2"},
                        {"quiz": "問題文3", "answer": "答え3"}
                      ]
                    }
                    
                    文章:
                    %s
                    """, summary);
            String quizJsonString = callOpenAI(quizPrompt, false);

            var quizData = mapper.readTree(quizJsonString);
            Map<String, Object> responseMap = Map.of(
                    "summary", summary,
                    "quizzes", quizData.get("quizzes")
            );

            return mapper.writeValueAsString(responseMap);

        } catch (Exception e) {
            System.err.println("Failed to generate quest details: " + e.getMessage());
            e.printStackTrace(); // Keep stack trace for debugging detail generation
            return "{\"error\": \"詳細の生成に失敗しました。\"}";
        }
    }

    // OpenAI API呼び出し共通メソッド
    private String callOpenAI(String prompt, boolean isQuestGeneration) {
        String apiUrl = "https://api.openai.com/v1/chat/completions";
        try {
            Map<String, Object> message = Map.of("role", "user", "content", prompt);
            Map<String, Object> bodyMap = Map.of("model", "gpt-3.5-turbo", "messages", List.of(message));
            String requestBody = mapper.writeValueAsString(bodyMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(apiUrl, requestEntity, String.class);

            var rootNode = mapper.readTree(response);
            String content = rootNode.at("/choices/0/message/content").asText();

            // AI応答が ```json ... ``` で囲まれている場合の処理
            if (isQuestGeneration && content.startsWith("```json")) {
                content = content.substring(7, content.length() - 3).trim();
            }
            return content;

        } catch (Exception e) {
            // API呼び出し失敗は致命的なので RuntimeException を投げる
            throw new RuntimeException("OpenAI API呼び出しに失敗しました: " + e.getMessage(), e);
        }
    }

    // DBから全履歴を取得 (新しい順)
    public String getHistory() {
        List<Quest> quests = questRepository.findAllByOrderByIdDesc();
        List<Map<String, Object>> historyList = quests.stream()
                .map(quest -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", quest.getId());
                    map.put("question", quest.getQuestion());
                    return map;
                })
                .collect(Collectors.toList());
        try {
            return mapper.writeValueAsString(historyList);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to serialize history: " + e.getMessage());
            return "[]"; // エラー時は空リスト
        }
    }

    // ID指定で特定のクエストデータをDBから取得
    public String getQuestById(Long id) {
        return questRepository.findById(id)
                .map(quest -> {
                    try {
                        // DB保存用に分割したnodesとedgesを結合してReact Flow形式に戻す
                        JsonNode nodes = mapper.readTree(quest.getNodesJson());
                        JsonNode edges = mapper.readTree(quest.getEdgesJson());
                        ((ArrayNode) nodes).addAll((ArrayNode) edges);
                        return nodes.toString();
                    } catch (JsonProcessingException e) {
                        System.err.println("Failed to parse quest data from DB for ID " + id + ": " + e.getMessage());
                        return "[]"; // パースエラー時は空リスト
                    }
                })
                .orElse("[]"); // IDが見つからない場合は空リスト
    }
}