package com.hemhem.curiosity_quest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin("http://localhost:5173")
public class HelloController {

    @Autowired
    private AiService aiService;

    @GetMapping("/generate-quest")
    public ResponseEntity<String> generateQuest(@RequestParam String question) {
        try {
            String result = aiService.getQuest(question);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/get-quest-details")
    public String getQuestDetails(@RequestBody Map<String, String> payload) {
        String questText = payload.get("questText");
        return aiService.getQuestDetails(questText);
    }

    @PostMapping("/complete-quest")
    public String completeQuest(@RequestBody Map<String, String> payload) {
        String nodeId = payload.get("id");
        System.out.println("クエスト完了: " + nodeId);
        return "{\"status\": \"success\", \"completedNode\": \"" + nodeId + "\"}";
    }

    @GetMapping("/history")
    public String getHistory() {
        return aiService.getHistory();
    }

    @GetMapping("/quest/{id}")
    public String getQuestById(@PathVariable Long id) {
        return aiService.getQuestById(id);
    }
}