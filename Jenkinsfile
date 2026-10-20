// Declarative pipeline. Runs on the Jenkins VM (needs: JDK17, Maven, Docker, Azure CLI, kubectl, Ansible).
pipeline {
    agent any

    triggers {
        githubPush()                 // GitHub webhook -> instant build
        pollSCM('H/2 * * * *')       // fallback if the webhook is not set up
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        // ====== EDIT THESE THREE VALUES ======
        ACR_NAME       = 'cicddemo3351'
        RESOURCE_GROUP = 'cics'
        AKS_NAME       = 'cicd-demo-aks'
        // =====================================
        IMAGE_NAME     = 'cicd-demo'
        KUBECONFIG     = "${WORKSPACE}/.kube/config"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script { env.TAG = sh(script: 'git rev-parse --short HEAD', returnStdout: true).trim() }
                echo "Building commit ${env.TAG}"
            }
        }

        stage('Build & Test (Maven)') {
            steps {
                sh 'mvn -B clean verify'
            }
            post {
                always { junit 'target/surefire-reports/*.xml' }
            }
        }

        stage('Docker Build & Tag') {
            steps {
                sh '''
                  IMAGE=$ACR_NAME.azurecr.io/$IMAGE_NAME
                  docker build --build-arg BUILD_VERSION=$TAG -t $IMAGE:$TAG -t $IMAGE:latest .
                '''
            }
        }

        stage('Push to ACR') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'azure-sp', usernameVariable: 'AZ_APP_ID', passwordVariable: 'AZ_SECRET'),
                                 string(credentialsId: 'azure-tenant', variable: 'AZ_TENANT')]) {
                    sh '''
                      az login --service-principal -u "$AZ_APP_ID" -p "$AZ_SECRET" --tenant "$AZ_TENANT" >/dev/null
                      az acr login --name $ACR_NAME
                      IMAGE=$ACR_NAME.azurecr.io/$IMAGE_NAME
                      docker push $IMAGE:$TAG
                      docker push $IMAGE:latest
                    '''
                }
            }
        }

        stage('Deploy to AKS (Ansible)') {
            steps {
                sh '''
                  mkdir -p "$WORKSPACE/.kube"
                  az aks get-credentials --resource-group $RESOURCE_GROUP --name $AKS_NAME --file "$KUBECONFIG" --overwrite-existing
                  ansible-playbook -i ansible/inventory.ini ansible/deploy.yml \
                    -e image=$ACR_NAME.azurecr.io/$IMAGE_NAME:$TAG
                '''
            }
        }

        stage('Verify') {
            steps {
                sh '''
                  kubectl get pods -n demo -o wide
                  kubectl get deployment cicd-demo -n demo
                  IP=$(cat .rendered/service_ip.txt)
                  echo "Checking http://$IP/actuator/health"
                  for i in $(seq 1 12); do
                    if curl -fsS "http://$IP/actuator/health" | grep -q UP; then
                      echo "Application is healthy"; curl -s "http://$IP/api/info"; exit 0
                    fi
                    echo "Not ready yet ($i/12)"; sleep 10
                  done
                  echo "Application did not become healthy"; exit 1
                '''
            }
        }
    }

    post {
        success { echo "Deployed ${env.TAG} successfully" }
        failure { echo 'Pipeline failed - see the stage logs above' }
        always  { sh 'az logout || true' }
    }
}
