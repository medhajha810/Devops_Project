import pandas as pd
from sklearn.feature_extraction.text import CountVectorizer
from sklearn.naive_bayes import MultinomialNB
import io

# 1. Sample Training Data
data = {
    'text': [
        'I love this service', 'Amazing quality and great staff', 'Fantastic experience',
        'I hate this product', 'Terrible service and bad staff', 'Awful experience',
        'It was okay', 'Normal day', 'The weather is fine'
    ],
    'sentiment': [1, 1, 1, -1, -1, -1, 0, 0, 0] # 1: Pos, -1: Neg, 0: Neu
}

df = pd.DataFrame(data)

# 2. Train Model
vectorizer = CountVectorizer(stop_words='english')
X = vectorizer.fit_transform(df['text'])
y = df['sentiment']

clf = MultinomialNB()
clf.fit(X, y)

# 3. Extract Lexicon (Artifact)
# Mapping words to their log probability difference (Positive vs Negative)
feature_names = vectorizer.get_feature_names_out()
pos_log_prob = clf.feature_log_prob_[2] # Index 2 is usually Positive (1)
neg_log_prob = clf.feature_log_prob_[0] # Index 0 is usually Negative (-1)

lexicon = {}
for i, word in enumerate(feature_names):
    # Calculate a simple weight based on probability difference
    weight = pos_log_prob[i] - neg_log_prob[i]
    if weight > 0.5:
        lexicon[word] = 'positive'
    elif weight < -0.5:
        lexicon[word] = 'negative'

# 4. Export as sentiment_model.txt
with open('sentiment_model.txt', 'w') as f:
    for word, sentiment in lexicon.items():
        f.write(f"{word}:{sentiment}\n")

print("Artifact 'sentiment_model.txt' generated successfully.")