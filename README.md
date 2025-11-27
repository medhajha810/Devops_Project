🤖 CI/CD for Customer Sentiment Analyzer (MLOps DevOps Project)

This is a full-fledged web application designed as an advanced DevOps project to demonstrate the seamless integration of application code and Machine Learning (ML) models into a Continuous Integration/Continuous Delivery (CI/CD) pipeline. 

The project uses a Java/Spring Boot backend built with Maven, and a simple HTML/JavaScript frontend.

🔑 Key DevOps & MLOps Integrations

This project focuses on proving the following capabilities:

Git/GitHub Workflow: All code (Java service, frontend, and the ML model artifact) is versioned in this repository. Developers use branches and Pull Requests (PRs) to propose changes, which triggers the CI/CD pipeline.

Maven as the Unified Build Tool: Maven is used to build the entire Java application. Crucially, the pom.xml is configured to ensure the ML model is included as a resource in the final JAR/WAR artifact.

Artifact Bundling (Unique MLOps Step): The maven-resources-plugin (configured in pom.xml) explicitly copies the sentiment_model.txt artifact into the final deployment package. This guarantees that the application logic and the specific, tested ML model version are deployed together.

Continuous Testing (CT) Placeholder: The CI/CD pipeline should include a step to execute mvn test. In a real MLOps scenario, this would include:

Unit Tests: Testing the Java code logic.

Model Validation Tests: Automated tests that verify the model's accuracy on a holdout dataset before the artifact is packaged (This is the stage dedicated to ensuring model quality).

🛠️ Project Setup and Run Instructions

1. Directory Structure (Mandatory)

To compile the project successfully, you must organize the files into the following structure:

sentiment-analyzer/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/devops/mlops/
│   │   │       └── SentimentAnalyzer.java
│   │   ├── resources/
│   │   │   └── application.properties (Optional Spring Boot configuration)
│   │   └── ml-model-artifacts/
│   │       └── sentiment_model.txt <--- MAVEN COPIES THIS
└── index.html
└── README.md


2. Running the Java Backend

Build the Project (Maven):

mvn clean install


Run the Application:

java -jar target/sentiment-analyzer-0.0.1-SNAPSHOT.jar


The application will start on http://localhost:8080.

3. Running the Frontend

Open the index.html file directly in your web browser.

Enter text in the field and click "Analyze Sentiment." The JavaScript will send an AJAX request to the running Java service.

CI/CD Pipeline Mockup (GitHub Actions)

This demonstrates how a minimal CI/CD workflow would integrate Maven and GitHub:

# .github/workflows/maven.yml

name: Maven CI/CD Pipeline

on:
  push:
    branches: [ "main", "develop" ]
  pull_request:
    branches: [ "main" ]

jobs:
  build_and_validate:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v4
    
    # Setup Java Environment
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven
        
    # Build and Test (Maven Step)
    - name: Run Build and Model Validation
      # This command triggers the Maven lifecycle: compile, run tests (including simulated model validation), 
      # and package the final deployable JAR with the bundled ML model artifact.
      run: mvn clean verify package 

    # Deployment Step (In a real scenario, this would deploy the JAR/Container)
    - name: Deploy to Staging (Simulated)
      if: github.ref == 'refs/heads/develop'
      run: |
        echo "Deployment triggered for develop branch."
        echo "Artifact sentiment-analyzer-0.0.1-SNAPSHOT.jar is ready."
        # Placeholder for Docker build and Kubernetes/Cloud deployment logic
