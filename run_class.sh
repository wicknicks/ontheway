./gradlew build jar
java -classpath src/main/resources/:lib/*:build/libs/experiments-0.1.jar com.ontheway.$*
