pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                // Jenkins가 GitHub에서 코드를 땡겨오는 단계
                // (실제 실행 시엔 Jenkins UI에서 설정한 Repo를 가져옴)
                echo 'Checking out code...'
                checkout scm
            }
        }

        stage('Permission Grant') {
            steps {
                // gradlew 실행 권한 주기
                sh 'chmod +x ./gradlew'
            }
        }

        stage('Build Common') {
            steps {
                echo 'Building Common Module...'
                sh './gradlew :common:clean :common:build -x test'
            }
        }

        stage('Build Order Service') {
            steps {
                echo 'Building Order Service...'
                sh './gradlew :order-service:clean :order-service:build -x test'
            }
        }

        stage('Build Settlement Service') {
            steps {
                echo 'Building Settlement Service...'
                sh './gradlew :settlement-service:clean :settlement-service:build -x test'
            }
        }
    }

    post {
        success {
            echo 'Build SUCCESS! 🎉'
        }
        failure {
            echo 'Build FAILED... 😭'
        }
    }
}