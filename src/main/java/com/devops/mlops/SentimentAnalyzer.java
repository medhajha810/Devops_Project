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

    // Common interjections / exclamations mapping (helps when lexicon doesn't include these)
    private static final Map<String, String> INTERJECTION_MAP = Map.ofEntries(
        Map.entry("wow", "Positive"),
        Map.entry("yay", "Positive"),
        Map.entry("yayyy", "Positive"),
        Map.entry("yaay", "Positive"),
        Map.entry("oh", "Neutral"),
        Map.entry("ugh", "Negative"),
        Map.entry("ughh", "Negative"),
        Map.entry("shit", "Negative"),
        Map.entry("damn", "Negative"),
        Map.entry("crap", "Negative"),
        Map.entry("oops", "Neutral")
    );

    // Emotion mapping for finer-grained emotion counts used by frontend charts
    // Maps normalized tokens or lexicon keys to one of: joy, anger, sadness, fear, surprise
    private static final Map<String, String> EMOTION_MAP = Map.ofEntries(
        Map.entry("happy", "joy"),
        Map.entry("joy", "joy"),
        Map.entry("amazing", "joy"),
        Map.entry("excellent", "joy"),
        Map.entry("great", "joy"),
        Map.entry("surprise", "surprise"),
        Map.entry("surprised", "surprise"),
        Map.entry("wow", "surprise"),
        Map.entry("whoa", "surprise"),
        Map.entry("oh", "surprise"),
        Map.entry("sad", "sadness"),
        Map.entry("sadness", "sadness"),
        Map.entry("depressed", "sadness"),
        Map.entry("disappoint", "sadness"),
        Map.entry("disappointed", "sadness"),
        Map.entry("disappointment", "sadness"),
        Map.entry("sucks", "sadness"),
        Map.entry("shit", "sadness"),
        Map.entry("shitty", "sadness"),
        Map.entry("angry", "anger"),
        Map.entry("furious", "anger"),
        Map.entry("hate", "anger"),
        Map.entry("fear", "fear"),
        Map.entry("scared", "fear"),
        Map.entry("terrified", "fear"),
        Map.entry("horrible", "anger")
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

    // Normalize input token to handle repeated letters and stray non-letters
    private String normalizeToken(String token) {
        if (token == null) return "";
        String t = token.toLowerCase().trim();
        // remove any non-letter characters
        t = t.replaceAll("[^a-z]", "");
        if (t.isEmpty()) return t;
        // Reduce long repeated character sequences to at most 2 characters (e.g., "shitttt" -> "shitt")
        t = t.replaceAll("(.)\\1{2,}", "$1$1");
        return t;
    }

    // Levenshtein distance implementation for fuzzy matching
    private int levenshteinDistance(String a, String b) {
        if (a == null || b == null) return Integer.MAX_VALUE;
        int la = a.length();
        int lb = b.length();
        if (la == 0) return lb;
        if (lb == 0) return la;
        int[] prev = new int[lb + 1];
        int[] curr = new int[lb + 1];
        for (int j = 0; j <= lb; j++) prev[j] = j;
        for (int i = 1; i <= la; i++) {
            curr[0] = i;
            for (int j = 1; j <= lb; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[lb];
    }

    // Find best matching lexicon key for the candidate token using Levenshtein distance
    private String findBestLexiconMatch(String candidate) {
        if (candidate == null || candidate.isEmpty()) return null;
        String bestKey = null;
        int bestDist = Integer.MAX_VALUE;
        for (String key : sentimentKeywords.keySet()) {
            if (key == null || key.isEmpty()) continue;
            String normKey = key.toLowerCase().replaceAll("[^a-z]", "");
            if (normKey.isEmpty()) continue;
            int dist = levenshteinDistance(candidate, normKey);
            if (dist < bestDist) {
                bestDist = dist;
                bestKey = key;
            }
        }
        if (bestKey == null) return null;
        // choose threshold based on token length (allow 1 for short words, up to length/3 for longer)
        int threshold = Math.max(1, candidate.length() / 3);
        if (bestDist <= threshold) return bestKey;
        return null;
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
        // Emotion counters for detailed emotion distribution
        Map<String, Integer> emotionCounts = new HashMap<>();
        emotionCounts.put("joy", 0);
        emotionCounts.put("anger", 0);
        emotionCounts.put("sadness", 0);
        emotionCounts.put("fear", 0);
        emotionCounts.put("surprise", 0);

        // Enhanced prediction logic with normalization, interjection handling and fuzzy matching
        for (int i = 0; i < rawTokens.length; i++) {
            String token = rawTokens[i];
            if (token == null || token.trim().isEmpty()) continue;

            // Check negation by looking at previous raw token (stripped to letters)
            boolean isNegated = false;
            if (i > 0) {
                String previousToken = rawTokens[i - 1] == null ? "" : rawTokens[i - 1].toLowerCase().replaceAll("[^a-z]", "");
                if (NEGATION_WORDS.contains(previousToken)) {
                    isNegated = true;
                }
            }

            // Normalize token for matching
            String alpha = token.toLowerCase().replaceAll("[^a-z]", "");
            if (alpha.isEmpty()) continue;
            String normalized = normalizeToken(alpha);

            String matchedSentiment = null; // expects values like "Positive" or "Negative" or "Neutral"

            // Direct lexicon membership
            if (positiveWords.contains(alpha) || positiveWords.contains(normalized)) matchedSentiment = "Positive";
            else if (negativeWords.contains(alpha) || negativeWords.contains(normalized)) matchedSentiment = "Negative";

            // Interjection map (explicit exclamations)
            if (matchedSentiment == null) {
                if (INTERJECTION_MAP.containsKey(normalized)) {
                    matchedSentiment = INTERJECTION_MAP.get(normalized);
                }
            }

            // Fuzzy match against lexicon when still unmatched (catch typos like 'haapy' -> 'happy', 'sed' -> 'sad')
            String bestLexiconMatchCandidate = null;
            if (matchedSentiment == null) {
                bestLexiconMatchCandidate = findBestLexiconMatch(normalized);
                if (bestLexiconMatchCandidate != null) {
                    matchedSentiment = sentimentKeywords.get(bestLexiconMatchCandidate);
                }
            }

            // If we matched a sentiment, apply it (taking negation into account)
            if (matchedSentiment != null) {
                boolean isPositive = "Positive".equalsIgnoreCase(matchedSentiment);
                boolean isNegative = "Negative".equalsIgnoreCase(matchedSentiment);

                // determine emotion label (joy/anger/sadness/fear/surprise)
                String emotionLabel = null;
                // prefer explicit EMOTION_MAP for the normalized token
                if (EMOTION_MAP.containsKey(normalized)) {
                    emotionLabel = EMOTION_MAP.get(normalized);
                }
                // next prefer emotion assigned to matched lexicon key
                if (emotionLabel == null && bestLexiconMatchCandidate != null) {
                    String candidateKey = bestLexiconMatchCandidate;
                    if (candidateKey != null) {
                        String nk = candidateKey.toLowerCase().replaceAll("[^a-z]", "");
                        if (EMOTION_MAP.containsKey(nk)) emotionLabel = EMOTION_MAP.get(nk);
                    }
                }
                // fallback: sentiment -> emotion
                if (emotionLabel == null) {
                    if (isPositive) emotionLabel = "joy";
                    else if (isNegative) emotionLabel = "anger";
                }

                // Apply negation to polarity counters, but emotion remains the interpreted one
                if (isNegated) {
                    if (isPositive) negativeScore++;
                    else if (isNegative) positiveScore++;
                } else {
                    if (isPositive) positiveScore++;
                    else if (isNegative) negativeScore++;
                }

                // Increment emotion counter if present
                if (emotionLabel != null && emotionCounts.containsKey(emotionLabel)) {
                    emotionCounts.put(emotionLabel, emotionCounts.get(emotionLabel) + 1);
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
        // compute normalized emotion distribution from counters if available
        int sumEmotions = 0;
        for (Integer v : emotionCounts.values()) sumEmotions += v == null ? 0 : v.intValue();
        if (sumEmotions > 0) {
            emotions.put("joy", Double.valueOf(emotionCounts.getOrDefault("joy", 0) / (double) sumEmotions));
            emotions.put("anger", Double.valueOf(emotionCounts.getOrDefault("anger", 0) / (double) sumEmotions));
            emotions.put("sadness", Double.valueOf(emotionCounts.getOrDefault("sadness", 0) / (double) sumEmotions));
            emotions.put("fear", Double.valueOf(emotionCounts.getOrDefault("fear", 0) / (double) sumEmotions));
            emotions.put("surprise", Double.valueOf(emotionCounts.getOrDefault("surprise", 0) / (double) sumEmotions));
        } else {
            // fallback to polarity-derived emotions
            emotions.put("joy", Double.valueOf(posScore));
            emotions.put("anger", Double.valueOf(negScore));
            emotions.put("sadness", Double.valueOf(0.0));
            emotions.put("fear", Double.valueOf(0.0));
            emotions.put("surprise", Double.valueOf(0.0));
        }
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