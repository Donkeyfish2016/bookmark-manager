FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/bookmark-manager-1.0-SNAPSHOT.jar app.jar
ENV BOOKMARK_DB_PATH=/data/bookmarkmgr.db
VOLUME /data
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
CMD []