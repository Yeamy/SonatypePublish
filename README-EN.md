# Sonatype Publish Gradle Plugin
English | [中文](README.md)

[![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.yeamy.sonatype-publish)](https://plugins.gradle.org/plugin/io.github.yeamy.sonatype-publish)

Gradle plugin for publishing **Maven** library to **Sonatype** server with **maven-publish**.

## How it work
While running task `publish`, start a local http server to accept the `maven-publish` upload files, then package them
into a zip file, after that upload to Sonatype server.
## How to use

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'signing'
    id 'io.github.yeamy.sonatype-publish' version '1.0'
}

sonatypePublish {
    port = 8082 // local http server port
    push = true // push zip file to sonatype
    autoPublish = true // auto publish
}

signing {
    sign publishing.publications.maven
}

publishing {
    // ...
    repositories {
        maven {
            // set url to local and with port sonatypePublish.port
            url = 'http://127.0.0.1:8082'
            credentials {
                username tokenUsername
                password tokenPassword
            }
        }
    } 
}
```

## Warning
- This plugin depend on `maven-publish`, you must fill publishing(pom) info.
```groovy
plugins {
    id 'maven-publish'
}

publishing {
    //...
}
```
- must auth with Sonatype token userName and password.

```groovy
plugins {
    id 'signing'
}

signing {
    sign publishing.publications.maven
}

publishing {
    // ...
    repositories {
        maven {
            // ...
            credentials {
                username tokenUsername
                password tokenPassword
            }
        }
    } 
}
```

- if using `localhost` instead of `127.0.0.1` may cause exception, must allow unsafe http connection.
```groovy
maven {
    url = 'http://localhost:8082'
    allowInsecureProtocol = true
}
```
- when `publish` case exception, the local http server cannot auto close, must close it manually.

```shell
#shell
lsof -i :8082
kill -9 [PID]
``` 
```shell
#cmd
netstat -ano | findstr :8082
taskkill /F /PID [PID]
```