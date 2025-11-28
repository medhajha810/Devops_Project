package com.devops.mlops;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;

/**
 * SentimentAnalyzer Application
 * This class simulates a Spring Boot service that loads a serialized ML model 
 * (represented by a resource file) and exposes an API endpoint for prediction.
 */
@SpringBootApplication
@RestController
@CrossOrigin(origins = "*") // Allows the frontend HTML to connect
public class SentimentAnalyzer {

    // Simulating a loaded ML model artifact (a simple lookup table for this demo)
    private final Map<String, String> sentimentKeywords = new HashMap<>();
    private final Set<String> positiveWords;
    private final Set<String> negativeWords;
    
    // Define negation words for improved accuracy
    private static final Set<String> NEGATION_WORDS = Set.of(
        "not", "n't", "no", "never", "nothing", "barely", "hardly", "scarcely"
    );
    
    // This path is defined by the <outputDirectory> in pom.xml's maven-resources-plugin
    private static final String MODEL_RESOURCE_PATH = "/ml-resources/sentiment_model.txt"; 

    public static void main(String[] args) {
        SpringApplication.run(SentimentAnalyzer.class, args);
    }

    // Constructor runs once on startup to "load the model"
    public SentimentAnalyzer() {
        System.out.println("Attempting to load ML model artifact...");
        
        // Initialize the local variables used for assignments
        Set<String> calculatedPositiveWords;
        Set<String> calculatedNegativeWords;
        
        // Default empty sets for error handling 
        calculatedPositiveWords = new HashMap<String, String>().keySet();
        calculatedNegativeWords = new HashMap<String, String>().keySet();


        try (InputStream is = getClass().getResourceAsStream(MODEL_RESOURCE_PATH);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {

            if (is == null) {
                System.err.println("CRITICAL: ML model artifact not found at " + MODEL_RESOURCE_PATH + 
                                   ". Ensure 'mvn package' ran correctly to copy the artifact.");
                
                // Keep the default empty sets and continue to the end of constructor
            } else {

                // Simulating loading the model (loading keywords in this case)
                String keywords = reader.lines().collect(Collectors.joining("\n"));
                
                // Populate the keyword map for initialization
                for (String line : keywords.split(";")) {
                    if (!line.trim().isEmpty()) {
                        String[] parts = line.split(":");
                        if (parts.length == 2) {
                            sentimentKeywords.put(parts[0].trim(), parts[1].trim());
                        }
                    }
                }
                
                // Separate keywords into sets for faster lookup during prediction
                calculatedPositiveWords = sentimentKeywords.entrySet().stream()
                    .filter(entry -> "Positive".equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
                
                calculatedNegativeWords = sentimentKeywords.entrySet().stream()
                    .filter(entry -> "Negative".equals(entry.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

                System.out.println("ML Model loaded successfully. Keywords loaded: " + sentimentKeywords.size());
            }

        } catch (Exception e) {
            System.err.println("Error loading ML model artifact: " + e.getMessage());
            e.printStackTrace();
            // calculatedPositiveWords and calculatedNegativeWords remain the default empty sets
            calculatedPositiveWords = new HashMap<String, String>().keySet();
            calculatedNegativeWords = new HashMap<String, String>().keySet();
        }
        
        // FINAL ASSIGNMENT: Ensure the final fields are assigned exactly once here.
        this.positiveWords = calculatedPositiveWords;
        this.negativeWords = calculatedNegativeWords;
    }

    /**
     * REST API endpoint for sentiment prediction.
     * @param request A map containing the "text" to analyze.
     * @return A map containing the predicted sentiment and the model version.
     */
    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyzeSentiment(@RequestBody Map<String, String> request) {
        String rawText = request.getOrDefault("text", "");
        
        if (rawText.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("sentiment", "Error", "message", "Input text cannot be empty."));
        }

        // Tokenization and cleaning of input text
        String[] rawTokens = rawText.toLowerCase().split("[\\s\\p{Punct}]+"); 
        
        int positiveScore = 0;
        int negativeScore = 0;

        // Simplified prediction logic with Negation Handling
        for (int i = 0; i < rawTokens.length; i++) {
            String token = rawTokens[i];
            boolean isNegated = false;

            // Check if the preceding word is a negation word
            if (i > 0) {
                String previousToken = rawTokens[i - 1];
                if (NEGATION_WORDS.contains(previousToken)) {
                    isNegated = true;
                }
            }

            if (positiveWords.contains(token)) {
                if (isNegated) {
                    // Negated positive word (e.g., "not great") counts as negative
                    negativeScore++;
                } else {
                    positiveScore++;
                }
            } else if (negativeWords.contains(token)) {
                if (isNegated) {
                    // Negated negative word (e.g., "not bad") counts as positive
                    positiveScore++;
                } else {
                    negativeScore++;
                }
            }
        }

        String result;
        double posScore = 0.0;
        double negScore = 0.0;

        int total = positiveScore + negativeScore;
        if (total > 0) {
            posScore = ((double) positiveScore) / total;
            negScore = ((double) negativeScore) / total;
            if (positiveScore > negativeScore) result = "Positive";
            else if (negativeScore > positiveScore) result = "Negative";
            else result = "Neutral";
        } else {
            // No keywords matched — neutral
            result = "Neutral";
            posScore = 0.0;
            negScore = 0.0;
        }

        // Build richer response expected by frontend JS
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("sentiment", result);
        response.put("modelVersion", "v0.0.5-MaxLexicon");
        response.put("positiveScore", Double.valueOf(posScore));
        response.put("negativeScore", Double.valueOf(negScore));

        Map<String, Double> emotions = new HashMap<>();
        emotions.put("joy", Double.valueOf(posScore));
        emotions.put("anger", Double.valueOf(negScore));
        emotions.put("sadness", Double.valueOf(0.0));
        emotions.put("fear", Double.valueOf(0.0));
        emotions.put("surprise", Double.valueOf(0.0));
        response.put("emotions", emotions);

        // dominant emotion
        if (posScore > negScore) {
            response.put("dominantEmotion", "positive");
            response.put("dominantEmotionScore", Double.valueOf(posScore));
        } else if (negScore > posScore) {
            response.put("dominantEmotion", "negative");
            response.put("dominantEmotionScore", Double.valueOf(negScore));
        } else {
            response.put("dominantEmotion", "neutral");
            response.put("dominantEmotionScore", Double.valueOf(1.0 - (posScore + negScore)));
        }

        // timestamp for history UI
        response.put("timestamp", java.time.ZonedDateTime.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String,Object>>> getHistory(@RequestParam(name="limit", required=false) Integer limit) {
        List<Map<String,Object>> out = new ArrayList<>();
        ObjectMapper om = new ObjectMapper();
        java.nio.file.Path path = Paths.get("data", "analysis_history.jsonl");
        if (!Files.exists(path)) {
            return ResponseEntity.ok(out);
        }
        try (Stream<String> lines = Files.lines(path)) {
            // read lines and parse JSON per-line
            List<String> all = lines.collect(Collectors.toList());
            // apply limit to last N entries
            int start = 0;
            if (limit != null && limit > 0 && limit < all.size()) start = Math.max(0, all.size() - limit);
            for (int i = start; i < all.size(); i++) {
                String ln = all.get(i).trim();
                if (ln.isEmpty()) continue;
                try {
                    Map<String,Object> obj = om.readValue(ln, Map.class);
                    out.add(obj);
                } catch (IOException ex) {
                    // skip malformed line
                }
            }
        } catch (IOException e) {
            // return what we have so far
        }
        return ResponseEntity.ok(out);
    }
}