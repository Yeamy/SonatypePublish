package io.github.yeamy.sonatype;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository;

import java.io.File;

public class MainPlugin implements Plugin<Project> {
    private BridgeHttpServer server;

    @Override
    public void apply(Project project) {
        SonatypePublishExtension extension = project.getExtensions().create("sonatypePublish", SonatypePublishExtension.class);

        project.getPlugins().withId("maven-publish", plugin -> project.afterEvaluate(p ->
                p.getTasks().withType(PublishToMavenRepository.class).forEach(task -> {
                    task.doFirst(t -> beforePublish(p, extension.getPort().get()));
                    task.doLast(t -> afterPublish(p, extension.getPush().get(), extension.getAutoPublish().get()));
                })
        ));
    }

    private void beforePublish(Project project, int port) {
        project.getExtensions().getByType(PublishingExtension.class).repositories(repo ->
                repo.maven(action -> {
                    action.setUrl("http://127.0.0.1:" + port);
                    action.setAllowInsecureProtocol(true);
                })
        );
        File dir = tmpDir(project);
        cleanDir(dir);
        if (server == null) {
            server = new BridgeHttpServer();
            server.start(port, dir);
        }
    }

    private void afterPublish(Project project, boolean push, boolean autoPublish) {
        String auth = null;
        if (server != null) {
            auth = server.getAuth();
            server.close();
            server = null;
        }
        File dir = tmpDir(project);
        File zip = Zipper.zip(dir);
        if (push) {
            Uploader.upload(zip, autoPublish, auth);
        }
    }

    private File tmpDir(Project project) {
        return new File(project.getLayout().getBuildDirectory().getAsFile().get(), "sonatype-publish");
    }

    private void cleanDir(File dir) {
        File[] fs = dir.listFiles();
        if (fs == null) return;
        for (File f : fs) {
            if (f.isDirectory()) {
                cleanDir(f);
            }
            f.delete();
        }
    }
}
