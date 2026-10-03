FROM eclipse-temurin:17-jdk-alpine
VOLUME /tmp

# Limites de recursos da JVM pensados para correr em container:
#  - MaxRAMPercentage: dimensiona a heap a partir do limite de memoria do container
#    (definir mem_limit no docker-compose; ver docs/DoS-PROTECTION.md)
#  - MaxMetaspaceSize: evita crescimento ilimitado de metaspace
#  - ExitOnOutOfMemoryError: o container reinicia em vez de ficar "pendurado"
#  - ActiveProcessorCount: nucleos fixos (GC/JIT previsiveis e mais leves)
#  - java.io.tmpdir=/tmp: multipart grande vai para disco e nao para heap
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70 -XX:InitialRAMPercentage=40 -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp -Djava.io.tmpdir=/tmp -Djava.security.egd=file:/dev/./urandom -Dfile.encoding=UTF-8"

EXPOSE 8080
COPY target/*.jar app.jar
# 'exec' garante que o java recebe o SIGTERM e o graceful shutdown funciona
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app.jar"]