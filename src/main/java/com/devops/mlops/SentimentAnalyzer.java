package com.devops.mlops;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

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
    public ResponseEntity<Map<String, String>> analyzeSentiment(@RequestBody Map<String, String> request) {
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
        if (positiveScore > negativeScore) {
            result = "Positive";
        } else if (negativeScore > positiveScore) {
            result = "Negative";
        } else {
            // Neutral if scores are equal (including 0=0)
            result = "Neutral";
        }

        // Simulating returning a model version (derived from the artifact)
        Map<String, String> response = new HashMap<>();
        response.put("sentiment", result);
        response.put("modelVersion", "v0.0.5-MaxLexicon"); // Updated version to reflect logic fix

        return ResponseEntity.ok(response);
    }
}