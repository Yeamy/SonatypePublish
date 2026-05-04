# Sonatype Publish Gradle插件
中文 | [English](README-EN.md)

[![Gradle Plugin Portal Version](https://img.shields.io/gradle-plugin-portal/v/io.github.yeamy.sonatype-publish)](https://plugins.gradle.org/plugin/io.github.yeamy.sonatype-publish)

用于支持使用**maven-publish**插件发布**Maven**库到**Sonatype**服务器的Gradle插件。
## 原理
当运行`publish`任务时开启本地http服务器接收maven-publish创建的文件并打包成zip包，上传至Sonatype服务器。
## Gradle 配置

```groovy
plugins {
    id 'java-library'
    id 'maven-publish'
    id 'signing'
    id 'io.github.yeamy.sonatype-publish' version '1.0'
}

sonatypePublish {
    port = 8082 // 本地服务器运行端口
    push = true // 打包成zip后是否上传
    autoPublish = true // 上传后是否自动发布
}

signing {
    sign publishing.publications.maven
}

publishing {
    // ...
    repositories {
        maven {
            // 将服务器地址修改成127.0.0.1，端口为sonatypePublish.port
            url = 'http://127.0.0.1:8082'
            credentials {
                username tokenUsername
                password tokenPassword
            }
        }
    } 
}
```

## 注意
- 插件依赖`maven-publish`，必须配置好pom发布信息
```groovy
plugins {
    id 'maven-publish'
}

publishing {
    //...
}
```
- 必须使用sonatype的token账号密码配置验证信息。

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

- 如果使用localhost替代127.0.0.1会导致异常，必须配置允许使用不安全协议
```groovy
maven {
    url = 'http://localhost:8082'
    allowInsecureProtocol = true 
}
```
- 当publish执行异常后会导致http服务器无法正常关闭，端口占用，下次运行publish时无法正常开启，需要关闭进程后重新启动。

```shell
#shell
lsof -i :8082
kill -9 [进程PID]
``` 
```shell
#cmd
netstat -ano | findstr :8082
taskkill /F /PID [进程PID]
```